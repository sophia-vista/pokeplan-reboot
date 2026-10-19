// Supabase Edge Function: grants rewards. The player's app calls it with their session token; it loads their state, runs
// the reward engine (the Kotlin code compiled to JavaScript, see build.sh) and saves the outcome.
//
// Secrets (set by the platform): SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY.
import { createClient } from "npm:@supabase/supabase-js@2.45.4";
import { RewardApi } from "./engine/pokeplan-pokemon-core.mjs";
import { type Database, handleReward, RequestError } from "./core.ts";

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
      p_seed: a.seed, p_event: a.event, p_outcome: a.outcome,
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
};

Deno.serve(async (req) => {
  if (req.method !== "POST") return new Response("POST only", { status: 405 });
  try {
    const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer /i, "");
    const { data: { user } } = await service.auth.getUser(token);
    if (!user) throw new RequestError(401, "sign in first");

    const result = await handleReward(user.id, await req.json(), api, db, {
      nowMillis: () => Date.now(),
      newId: () => crypto.randomUUID(),
      seed: () => crypto.getRandomValues(new Int32Array(1))[0],
    });
    return Response.json(result);
  } catch (e) {
    if (e instanceof RequestError) return Response.json({ error: e.message }, { status: e.status });
    console.error(e);
    return Response.json({ error: "something went wrong" }, { status: 500 });
  }
});
