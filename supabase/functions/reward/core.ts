// The reward function's logic, kept free of Deno and Supabase imports so it runs under Node in tests (core.test.ts).
// index.ts wires it to the real engine and database.

export interface Engine {
  effort(stateJson: string, eventJson: string, seed: number): string;
  achievement(stateJson: string, eventJson: string, seed: number): string;
  /** RewardRules.VERSION: recorded on every ledger row the engine's outcomes make. */
  rulesVersion(): number;
}

export interface Database {
  /** platform.load_state: {version, state}. */
  loadState(user: string): Promise<{ version: number; state: unknown }>;
  /** platform.apply_outcome. Throws an error with code "40001" when the version is stale. */
  applyOutcome(args: {
    user: string; expectedVersion: number; app: string; source: string; key: string; seed: number; event: unknown; outcome: unknown;
    rulesVersion: number;
  }): Promise<{ applied: boolean; version: number; rewards: unknown[] }>;
  /** plan.finish_task: the tier to pay, or null if the task pays nothing. */
  finishTask(user: string, taskId: string): Promise<number | null>;
  /** The task's focused minutes, for the "focused on it" bonus. */
  focusedMinutes(user: string, taskId: string): Promise<number>;
  rankFor(points: number): number;
  /** plan.pull_save: {version, data} where data is null if nothing was uploaded yet. */
  pullSave(user: string): Promise<{ version: number; data: unknown }>;
  /**
   * plan.push_save: the new version. `rulesVersion` is the rules the client's engine ran (0 if it did not say). Throws an
   * error with code "40001" when the version is stale.
   */
  pushSave(user: string, expectedVersion: number, data: unknown, rulesVersion: number): Promise<number>;
  /** platform.egg_tickets: records the spent tickets and returns the device's unspent stock topped up to `stock`. */
  eggTickets(user: string, device: string, spent: SpentTicket[], stock: number): Promise<{ tickets: unknown[]; refused: string[] }>;
  /** Deletes the sign-in; every table cascades from it, so nothing of the player is left. */
  deleteUser(user: string): Promise<void>;
}

/** A ticket the device spent: the session's start, and the Pokémon it hatched (null if the session failed). */
export interface SpentTicket {
  id: string;
  startedAtMillis: number;
  pokemonId: string | null;
}

export interface Clock {
  nowMillis(): number;
  newId(): string;
  seed(): number;
}

export class RequestError extends Error {
  readonly status: number;
  /** More fields for the response body (the current version on a conflict). */
  readonly extra: Record<string, unknown>;

  constructor(status: number, message: string, extra: Record<string, unknown> = {}) {
    super(message);
    this.status = status;
    this.extra = extra;
  }
}

// The released module regions (Region.isModule and released in pokemon-core), open from the start: the Plan Region
// (stored as HOME) and the Care Region. Module regions not released yet are refused.
const MODULE_REGIONS = ["HOME", "CARE"];
// The game regions, in the order trainer rank opens them, Kanto at rank 1 (TrainerRank.rankToUnlock).
const GAME_REGIONS = ["KANTO", "JOHTO", "HOENN", "SINNOH", "UNOVA", "KALOS", "ALOLA", "GALAR", "PALDEA"];
const REGIONS = [...MODULE_REGIONS, ...GAME_REGIONS];

function rankToUnlock(region: string): number {
  return MODULE_REGIONS.includes(region) ? 0 : GAME_REGIONS.indexOf(region) + 1;
}
const MAX_MINUTES = 240;
const MAX_RETRIES = 3;
const DAY_MILLIS = 86_400_000;
// Egg tickets a device holds (see "Focus" in docs/design.md). (tune)
const TICKET_STOCK = 20;
const MAX_SPENT = 200;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

type Body = Record<string, unknown>;

function int(body: Body, field: string, low: number, high: number, fallback?: number): number {
  const v = body[field] ?? fallback;
  if (typeof v !== "number" || !Number.isInteger(v) || v < low || v > high) {
    throw new RequestError(400, `${field} must be a whole number from ${low} to ${high}`);
  }
  return v;
}

function text(body: Body, field: string): string {
  const v = body[field];
  if (typeof v !== "string" || v.length === 0 || v.length > 200) throw new RequestError(400, `${field} is required`);
  return v;
}

/**
 * Handles one request from a signed-in player.
 *
 *   {"kind": "effort",  "app": "plan", "key": "...", "minutes": 25, "deepFocus": false, "region": "KANTO", "lure": null, "epochDay": 20745}
 *   {"kind": "task",    "app": "plan", "taskId": "...", "region": "KANTO", "hourOfDay": 9, "firstOfDay": true, "onTime": true}
 *   {"kind": "achievement", "app": "health", "key": "...", "tier": 3, "region": "KANTO"}
 *
 * `key` makes a retry harmless; a task's key is its id. Returns {applied, version, rewards}.
 */
export async function handleReward(user: string, body: Body, engine: Engine, db: Database, clock: Clock) {
  const kind = text(body, "kind");
  const app = text(body, "app");
  const region = text(body, "region");
  if (!REGIONS.includes(region)) throw new RequestError(400, `unknown region ${region}`);

  let key: string;
  let tier: number | null = null;
  let source: string;
  if (kind === "task") {
    const taskId = text(body, "taskId");
    key = `task:${taskId}`;
    source = "task";
    tier = await db.finishTask(user, taskId);
  } else if (kind === "achievement") {
    key = text(body, "key");
    source = text(body, "source");
    tier = int(body, "tier", 1, 5);
  } else if (kind === "effort") {
    key = text(body, "key");
    source = "effort";
  } else {
    throw new RequestError(400, `unknown kind ${kind}`);
  }

  // A task that pays nothing (too young, or already paid) is finished all the same.
  if (kind !== "effort" && tier === null) {
    const { version } = await db.loadState(user);
    return { applied: false, version, rewards: [] as unknown[] };
  }

  for (let attempt = 0; ; attempt++) {
    const { version, state } = await db.loadState(user);
    const points = (state as { trainerPoints: number }).trainerPoints;
    if (rankToUnlock(region) > db.rankFor(points)) throw new RequestError(403, `${region} is not open yet`);

    const now = clock.nowMillis();
    const seed = clock.seed();
    let event: Body;
    let outcomeJson: string;
    if (kind === "effort") {
      const epochDay = int(body, "epochDay", Math.floor(now / DAY_MILLIS) - 1, Math.floor(now / DAY_MILLIS) + 1, Math.floor(now / DAY_MILLIS));
      event = {
        minutes: int(body, "minutes", 1, MAX_MINUTES), deepFocus: body.deepFocus === true, region,
        lure: typeof body.lure === "string" ? body.lure : null, epochDay, newPokemonId: clock.newId(), nowMillis: now,
      };
      outcomeJson = engine.effort(JSON.stringify(state), JSON.stringify(event), seed);
    } else {
      const focused = kind === "task" ? (await db.focusedMinutes(user, text(body, "taskId"))) > 0 : false;
      event = {
        tier, region, hourOfDay: int(body, "hourOfDay", 0, 23, 12), firstOfDay: body.firstOfDay === true,
        onTime: body.onTime === true, focusedOnIt: focused, newPokemonId: clock.newId(), nowMillis: now,
      };
      outcomeJson = engine.achievement(JSON.stringify(state), JSON.stringify(event), seed);
    }

    try {
      return await db.applyOutcome({
        user, expectedVersion: version, app, source, key, seed, event, outcome: JSON.parse(outcomeJson),
        rulesVersion: engine.rulesVersion(),
      });
    } catch (e) {
      // Someone else changed the state in between: load it again and rerun the engine.
      if ((e as { code?: string }).code === "40001" && attempt < MAX_RETRIES) continue;
      throw e;
    }
  }
}

/**
 * The cloud save: {"kind": "pull"} returns {version, data}; {"kind": "push", "expectedVersion": n, "data": {...},
 * "rulesVersion": n} saves the document and returns {version}. `rulesVersion` (optional) is the client's RewardRules.VERSION. A stale version is a 409 carrying the version on the server; a document the rules refuse
 * (points that went down, a party that is too big) is a 422.
 */
export async function handleSync(user: string, body: Body, db: Database) {
  const kind = text(body, "kind");
  if (kind === "pull") return await db.pullSave(user);
  if (kind !== "push") throw new RequestError(400, `unknown kind ${kind}`);

  const expected = int(body, "expectedVersion", 0, 2_000_000_000);
  const rulesVersion = int(body, "rulesVersion", 0, 1_000_000, 0);
  const data = body.data;
  if (typeof data !== "object" || data === null || Array.isArray(data)) throw new RequestError(400, "data must be an object");
  if (typeof (data as { player?: unknown }).player !== "object" || (data as { player?: unknown }).player === null) {
    throw new RequestError(400, "data.player is required");
  }
  try {
    return { version: await db.pushSave(user, expected, data, rulesVersion) };
  } catch (e) {
    const code = (e as { code?: string }).code;
    if (code === "40001") {
      const { version } = await db.pullSave(user);
      throw new RequestError(409, "the save changed somewhere else", { version });
    }
    if (code === "P0001" || code === "23514" || code === "23503") throw new RequestError(422, (e as Error).message);
    throw e;
  }
}

/**
 * Egg tickets for offline hatching: {"kind": "tickets", "device": "...", "spent": [{"id", "startedAtMillis", "pokemonId"}]}
 * reports the tickets this device spent and returns {tickets: [{id, seed, issuedAtMillis}], refused: [ids]}, the unspent
 * stock topped up to TICKET_STOCK. A refused ticket was never issued to this device, was spent already, or is older than the
 * session; the device just forgets it.
 */
export async function handleTickets(user: string, body: Body, db: Database) {
  const device = text(body, "device");
  const raw = body.spent ?? [];
  if (!Array.isArray(raw) || raw.length > MAX_SPENT) throw new RequestError(400, `spent must be a list of at most ${MAX_SPENT}`);
  const spent: SpentTicket[] = [];
  const refused: string[] = [];
  for (const entry of raw) {
    const e = (typeof entry === "object" && entry !== null ? entry : {}) as Body;
    const id = typeof e.id === "string" ? e.id : "";
    const started = e.startedAtMillis;
    const pokemonId = e.pokemonId ?? null;
    // A malformed entry is refused on its own, so one bad ticket never blocks the rest.
    if (!UUID.test(id) || typeof started !== "number" || !Number.isSafeInteger(started) || started < 0 ||
      (pokemonId !== null && (typeof pokemonId !== "string" || pokemonId.length > 200))) {
      refused.push(id);
      continue;
    }
    spent.push({ id, startedAtMillis: started, pokemonId });
  }
  const result = await db.eggTickets(user, device, spent, TICKET_STOCK);
  return { tickets: result.tickets, refused: [...refused, ...result.refused] };
}

/**
 * Deletes the account and everything stored for it: {"kind": "delete-account", "confirm": "DELETE"}. The confirmation
 * guards against a stray request; the client asks the player twice before sending it.
 */
export async function handleDeleteAccount(user: string, body: Body, db: Database) {
  if (body.confirm !== "DELETE") throw new RequestError(400, 'confirm must be "DELETE"');
  await db.deleteUser(user);
  return { deleted: true };
}
