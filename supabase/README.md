# Supabase backend

One Supabase project serves every Pokémon app (see "Platform and apps" in `docs/design.md`). The shared game lives in the
`platform` schema, each app has its own schema (`plan` now, `health` later).

| Path | What |
|---|---|
| `migrations/` | The database, in order. `…01_platform` (profiles, Pokémon, bag, ledger), `…02_plan` (planner tables), `…03_state_functions` (`load_state`, `apply_outcome`), `…04_social` (friends, leaderboard, gifts) |
| `tests/` | SQL tests, run by `tests/run.sh` on a throwaway PostgreSQL 16 (no Supabase needed) |
| `functions/reward/` | The Edge Function that grants rewards by running the Kotlin engine compiled to JavaScript |

## How rewards work

1. The app calls the `reward` function with the player's session token and what happened (a focus session, a finished task…).
2. The function reads the state with `platform.load_state`, runs `RewardApi.effort` or `.achievement` (the same Kotlin code the apps use to preview rewards), and saves the result with `platform.apply_outcome`.
3. `apply_outcome` checks the state version (a stale one is retried), stores the outcome with the seed and event so it can be replayed, and treats a repeated idempotency key as already done.

Clients can read their own rows and change a few harmless columns (nickname, party, training plan, buddy, planner data).
They cannot set coins, levels, items, shininess or task completion: those columns have no client grants.

## Setting it up

1. Create a project at supabase.com (the free tier is enough).
2. Link and push the schema: `supabase link --project-ref <ref>` then `supabase db push`.
3. In *Project settings → API → Exposed schemas* add `platform` and `plan`.
4. Build and deploy the function: `supabase/functions/reward/build.sh && supabase functions deploy reward`. `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` are provided to functions automatically.
5. In *Authentication → Providers* turn on email and Google.

## Cloud save and sign-in

Players can keep playing as a guest. Signing in (email and password) uploads the whole app document to `platform.cloud_saves` through the `reward` function (`kind: "pull"` and `"push"`), so phone, desktop and web share one save.

- The game state part (Pokémon, bag, coins, rank, Guardians) is saved through `apply_outcome`, so the server's rules still apply (points never drop, the party and Guardian slots are checked) and a gift or a reward from another app is never lost: `pull_save` always reads that part from the shared tables.
- Each save has a version. A device remembers the version it last saw; if the cloud moved on while it had changes of its own, the player chooses which to keep (Settings → Account).
- To turn it on in the app, put the **Project URL** and the **anon (publishable) key** (Project Settings → API) into `composeApp/src/commonMain/kotlin/app/pokeplan/ui/CloudSettings.kt`. They are meant to be in the app. Until then Settings says online features are not set up.
- In Supabase: Authentication → Providers → Email on. For a quick start turn **Confirm email** off (Authentication → Sign In / Providers → Email), or players must confirm by email before signing in.
- The web build is served from another origin: the function already answers CORS.

## Known limits

The function trusts the client for `hourOfDay`, `firstOfDay`, `onTime` and the length of a focus session (capped at 240
minutes). That is fine for private use with friends; tighten it (record sessions server-side, check the clock) before a public release.
