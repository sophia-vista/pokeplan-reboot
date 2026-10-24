// Runs the reward function's logic with the real compiled engine and an in-memory database.
//   ./gradlew :pokemon-core:jsProductionLibraryCompileSync
//   node --experimental-strip-types supabase/functions/reward/core.test.ts [engine directory]
import assert from "node:assert/strict";
import { join, resolve } from "node:path";
import { pathToFileURL } from "node:url";
import { type Database, handleDeleteAccount, handleReward, handleSync, RequestError } from "./core.ts";

const dir = resolve(process.argv[2] ?? "pokemon-core/build/compileSync/js/main/productionLibrary/kotlin");
const lib = await import(pathToFileURL(join(dir, "pokeplan-pokemon-core.mjs")).href);
const engine = lib.RewardApi.getInstance();

const thresholds = [0, 625, 1375, 2250, 3250, 4375, 5625, 7125, 8750, 10625];

function fakeDb(overrides: Partial<Database> = {}) {
  const ledger = new Map<string, unknown[]>();
  let state = JSON.parse(engine.emptyState());
  let version = 0;
  let staleOnce = 0;
  let saved: unknown = null;
  let pushError: Error | null = null;
  const db: Database & { deleted: string[]; ledger: typeof ledger; setState(s: unknown): void; failStale(n: number): void; failPush(e: Error | null): void; loads: number } = {
    loads: 0,
    ledger,
    setState(s) { state = s; },
    failStale(n) { staleOnce = n; },
    failPush(e) { pushError = e; },
    async loadState() { db.loads++; return { version, state: structuredClone(state) }; },
    async applyOutcome(a) {
      const id = `${a.app}/${a.key}`;
      if (ledger.has(id)) return { applied: false, version, rewards: ledger.get(id)! };
      if (staleOnce > 0) { staleOnce--; version++; throw Object.assign(new Error("stale"), { code: "40001" }); }
      assert.equal(a.expectedVersion, version);
      state = (a.outcome as { state: unknown }).state;
      version++;
      ledger.set(id, (a.outcome as { rewards: unknown[] }).rewards);
      return { applied: true, version, rewards: (a.outcome as { rewards: unknown[] }).rewards };
    },
    async finishTask(_u, taskId) { return taskId === "young" ? null : 3; },
    async focusedMinutes(_u, taskId) { return taskId === "focused" ? 25 : 0; },
    rankFor: (p) => thresholds.filter((t) => p >= t).length - 1,
    async pullSave() { return { version, data: saved }; },
    async pushSave(_u, expected, data) {
      if (pushError) throw pushError;
      if (expected !== version) throw Object.assign(new Error("stale"), { code: "40001" });
      saved = data;
      return ++version;
    },
    deleted: [] as string[],
    async deleteUser(u) { db.deleted.push(u); },
    ...overrides,
  };
  return db;
}

let counter = 0;
const clock = { nowMillis: () => 1_700_000_000_000, newId: () => `p${++counter}`, seed: () => 42 };
const user = "u1";

// An effort event hatches an egg and is saved.
{
  const db = fakeDb();
  const out = await handleReward(user, { kind: "effort", app: "plan", key: "s1", minutes: 25, region: "HOME" }, engine, db, clock);
  assert.equal(out.applied, true);
  assert.equal(out.version, 1);
  assert.ok(out.rewards.some((r) => (r as { kind: string }).kind === "hatched"));
  // The same key again is harmless.
  const again = await handleReward(user, { kind: "effort", app: "plan", key: "s1", minutes: 25, region: "HOME" }, engine, db, clock);
  assert.equal(again.applied, false);
  assert.deepEqual(again.rewards, out.rewards);
}

// A task pays its tier from the database; one that pays nothing is not an error.
{
  const db = fakeDb();
  const paid = await handleReward(user, { kind: "task", app: "plan", taskId: "t1", region: "HOME", hourOfDay: 9 }, engine, db, clock);
  assert.equal(paid.applied, true);
  assert.ok(paid.rewards.some((r) => (r as { kind: string }).kind === "candies"));
  const young = await handleReward(user, { kind: "task", app: "plan", taskId: "young", region: "HOME" }, engine, db, clock);
  assert.equal(young.applied, false);
  assert.deepEqual(young.rewards, []);
}

// Other apps earn through the same door.
{
  const db = fakeDb();
  const out = await handleReward(user, { kind: "achievement", app: "health", key: "walk-1", source: "walk", tier: 2, region: "HOME" }, engine, db, clock);
  assert.equal(out.applied, true);
}

// A stale version is retried against the fresh state.
{
  const db = fakeDb();
  db.failStale(2);
  const out = await handleReward(user, { kind: "effort", app: "plan", key: "s2", minutes: 25, region: "HOME" }, engine, db, clock);
  assert.equal(out.applied, true);
  assert.equal(db.loads, 3);
}
{
  const db = fakeDb();
  db.failStale(10);
  await assert.rejects(handleReward(user, { kind: "effort", app: "plan", key: "s3", minutes: 25, region: "HOME" }, engine, db, clock));
}

// Bad requests and closed regions are refused before anything is saved.
{
  const db = fakeDb();
  const refused = async (body: Record<string, unknown>, status: number) => {
    await assert.rejects(handleReward(user, body, engine, db, clock), (e: unknown) => e instanceof RequestError && e.status === status);
  };
  await refused({ kind: "effort", app: "plan", key: "x", minutes: 25, region: "KANTO" }, 403);
  await refused({ kind: "effort", app: "plan", key: "x", minutes: 0, region: "HOME" }, 400);
  await refused({ kind: "effort", app: "plan", key: "x", minutes: 9999, region: "HOME" }, 400);
  await refused({ kind: "effort", app: "plan", key: "x", minutes: 25, region: "NARNIA" }, 400);
  await refused({ kind: "effort", app: "plan", key: "x", minutes: 25, region: "HOME", epochDay: 5 }, 400);
  await refused({ kind: "achievement", app: "health", key: "x", source: "s", tier: 9, region: "HOME" }, 400);
  await refused({ kind: "magic", app: "plan", region: "HOME" }, 400);
  await refused({ kind: "effort", app: "plan", region: "HOME", minutes: 25 }, 400);
  assert.equal(db.ledger.size, 0);
}

// A region opens with rank.
{
  const db = fakeDb();
  const state = JSON.parse(engine.emptyState());
  state.trainerPoints = 700;
  db.setState(state);
  const out = await handleReward(user, { kind: "effort", app: "plan", key: "k1", minutes: 25, region: "KANTO" }, engine, db, clock);
  assert.equal(out.applied, true);
}

// Deleting the account needs the confirmation word.
{
  const db = fakeDb();
  await assert.rejects(handleDeleteAccount(user, { kind: "delete-account" }, db), (e: unknown) => e instanceof RequestError && e.status === 400);
  await assert.rejects(handleDeleteAccount(user, { kind: "delete-account", confirm: "yes" }, db), (e: unknown) => e instanceof RequestError && e.status === 400);
  assert.deepEqual(db.deleted, []);
  assert.deepEqual(await handleDeleteAccount(user, { kind: "delete-account", confirm: "DELETE" }, db), { deleted: true });
  assert.deepEqual(db.deleted, [user]);
}

// The cloud save: pull, push, a stale push is a 409 with the server's version, rules violations are 422.
{
  const db = fakeDb();
  assert.deepEqual(await handleSync(user, { kind: "pull" }, db), { version: 0, data: null });
  const doc = { onboarded: true, player: JSON.parse(engine.emptyState()), tasks: [] };
  assert.deepEqual(await handleSync(user, { kind: "push", expectedVersion: 0, data: doc }, db), { version: 1 });
  assert.deepEqual(await handleSync(user, { kind: "pull" }, db), { version: 1, data: doc });
  const stale = async () => handleSync(user, { kind: "push", expectedVersion: 0, data: doc }, db);
  await assert.rejects(stale(), (e: unknown) => e instanceof RequestError && e.status === 409 && (e.extra as { version: number }).version === 1);
  db.failPush(Object.assign(new Error("trainer points cannot go down"), { code: "P0001" }));
  await assert.rejects(handleSync(user, { kind: "push", expectedVersion: 1, data: doc }, db), (e: unknown) => e instanceof RequestError && e.status === 422);
  db.failPush(null);
  for (const bad of [{ kind: "push", expectedVersion: 1 }, { kind: "push", expectedVersion: 1, data: {} }, { kind: "push", expectedVersion: -1, data: doc }, { kind: "nope" }]) {
    await assert.rejects(handleSync(user, bad as Record<string, unknown>, db), (e: unknown) => e instanceof RequestError && e.status === 400);
  }
}

console.log("reward function: all good");
