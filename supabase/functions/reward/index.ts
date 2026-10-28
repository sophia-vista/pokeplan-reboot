// Supabase Edge Function: grants rewards. The player's app calls it with their session token; it loads their state, runs
// the reward engine (the Kotlin code compiled to JavaScript, see build.sh) and saves the outcome.
//
// Secrets (set by the platform): SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY.
import { createClient } from "npm:@supabase/supabase-js@2.45.4";
import { RewardApi } from "./engine/pokeplan-pokemon-core.mjs";
import { type Database, handleCareSync, handleDeleteAccount, handleReward, handleSync, handleTickets, RequestError } from "./core.ts";
// Written by build.sh from the VERSION file and the commit being deployed.
import { COMMIT, VERSION } from "./version.ts";

const url = Deno.env.get("SUPABASE_URL")!;
const service = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });
const api = RewardApi.getInstance();

// Trainer rank thresholds (TrainerRank.thresholds in pokemon-core). The SQL tests check platform.rank_thresholds() against
// the Kotlin source, and this list is read from the database once on start-up.
const thresholds: number[] = await service.schema("platform").rpc("rank_thresholds").then((r) => r.data);
const rankFor = (points: number) => Math.max(0, thresholds.filter((t) => points >= t).length - 1);

const db: Database = {
  async loadState(user) {
    const { data, error } = await service.schema("platform").rpc("load_state", { p_user: user });
    if (error) throw error;
    return data;
  },
  async applyOutcome(a) {
    const { data, error } = await service.schema("platform").rpc("apply_outcome", {
      p_user: a.user, p_expected_version: a.expectedVersion, p_app: a.app, p_source: a.source, p_key: a.key,
      p_seed: a.seed, p_event: a.event, p_outcome: a.outcome, p_rules_version: a.rulesVersion,
    });
    if (error) throw error;
    return data;
  },
  async finishTask(user, taskId) {
    const { data, error } = await service.schema("plan").rpc("finish_task", { p_user: user, p_task: taskId });
    if (error) throw error;
    return data;
  },
  async focusedMinutes(user, taskId) {
    const { data } = await service.schema("plan").from("tasks").select("focused_minutes").eq("id", taskId).eq("user_id", user).single();
    return data?.focused_minutes ?? 0;
  },
  rankFor,
  async pullSave(user) {
    const { data, error } = await service.schema("plan").rpc("pull_save", { p_user: user });
    if (error) throw error;
    return data;
  },
  async pushSave(user, expectedVersion, data, rulesVersion) {
    const { data: version, error } = await service.schema("plan").rpc("push_save", {
      p_user: user, p_expected_version: expectedVersion, p_data: data, p_rules_version: rulesVersion,
    });
    if (error) throw error;
    return version;
  },
  async pullCareSave(user) {
    const { data, error } = await service.schema("care").rpc("pull_save", { p_user: user });
    if (error) throw error;
    return data;
  },
  async pushCareSave(user, expectedVersion, data) {
    const { data: version, error } = await service.schema("care").rpc("push_save", { p_user: user, p_expected_version: expectedVersion, p_data: data });
    if (error) throw error;
    return version;
  },
  async deleteCareSave(user) {
    const { data, error } = await service.schema("care").rpc("delete_save", { p_user: user });
    if (error) throw error;
    return data;
  },
  async eggTickets(user, device, spent, stock) {
    const { data, error } = await service.schema("platform").rpc("egg_tickets", { p_user: user, p_device: device, p_spent: spent, p_stock: stock });
    if (error) throw error;
    return data;
  },
  async deleteUser(user) {
    const { error } = await service.auth.admin.deleteUser(user);
    if (error) throw error;
  },
};

// The web version calls this from the browser, so it answers cross-origin requests.
const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Expose-Headers": "x-pokeplan-version",
  "x-pokeplan-version": VERSION,
};
const json = (body: unknown, status = 200) => Response.json(body, { status, headers: cors });

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
  // A plain GET says which version, and which reward rules, are deployed.
  if (req.method === "GET") return json({ function: "reward", version: VERSION, commit: COMMIT, rulesVersion: api.rulesVersion() });
  if (req.method !== "POST") return new Response("POST only", { status: 405, headers: cors });
  try {
    const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer /i, "");
    const { data: { user } } = await service.auth.getUser(token);
    if (!user) throw new RequestError(401, "sign in first");

    const body = await req.json();
    // "pull" and "push" are the cloud save, "care-pull", "care-push" and "care-delete" Care's opt-in copy, "tickets" the
    // egg tickets, "delete-account" removes the account; everything else earns rewards.
    const result = body.kind === "pull" || body.kind === "push"
      ? await handleSync(user.id, body, db)
      : typeof body.kind === "string" && body.kind.startsWith("care-")
      ? await handleCareSync(user.id, body, db)
      : body.kind === "tickets"
      ? await handleTickets(user.id, body, db)
      : body.kind === "delete-account"
      ? await handleDeleteAccount(user.id, body, db)
      : await handleReward(user.id, body, api, db, {
        nowMillis: () => Date.now(),
        newId: () => crypto.randomUUID(),
        seed: () => crypto.getRandomValues(new Int32Array(1))[0],
      });
    return json(result);
  } catch (e) {
    if (e instanceof RequestError) return json({ error: e.message, ...e.extra }, e.status);
    console.error(e);
    return json({ error: "something went wrong" }, 500);
  }
});
