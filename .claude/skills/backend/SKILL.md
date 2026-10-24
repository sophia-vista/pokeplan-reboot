---
name: backend
description: The Supabase backend and the server build of the Kotlin engine: migrations and SQL tests, the reward Edge Function, the JavaScript build and Node smoke test, the golden vector, cloud sync in app-core/cloud and deploying the function. Use before changing supabase/, tools/server/, app-core/cloud, CloudSettings.kt, the reward rules the server runs, or the deploy-reward workflow.
---

# Backend

One Supabase project serves every app in the family. `supabase/README.md` has the schema map, how rewards work, set-up and deploy steps: read the part you need (grep its headings), not the whole file.

## Build and tests
- Server build (Kotlin compiled to JavaScript, ES modules): `./gradlew :pokemon-core:jsProductionLibraryCompileSync`, then check it under Node with `node tools/server/smoke.mjs pokemon-core/build/compileSync/js/main/productionLibrary/kotlin`. `tools/server/golden.json` is one fixed input and the exact JSON the engine must give; the JVM test and the Node script both check it. After a deliberate rule change regenerate it with `UPDATE_GOLDEN=1 ./gradlew :pokemon-core:jvmTest --tests "*GoldenVectorTest*"`.
- Supabase backend (`supabase/`, see `supabase/README.md`): SQL tests `supabase/tests/run.sh` (starts a throwaway PostgreSQL 16, applies `supabase/migrations/*.sql`, runs `supabase/tests/*.test.sql`; also reads `tools/server/golden.json` and the rank thresholds from `TrainerRank.kt`, so the SQL cannot drift from the Kotlin). The reward Edge Function's logic is tested under Node with the compiled engine: `node --experimental-strip-types supabase/functions/reward/core.test.ts` (after the JS build above). Deploying needs `supabase/functions/reward/build.sh`.
- CI (`ci.yml`) runs the JS build, the Node smoke test, the reward function test and the Supabase schema tests on every PR. Run the ones your change touches before pushing.

## Deploy
- `.github/workflows/deploy-reward.yml` (Actions → Deploy the reward function → Run workflow) builds and deploys the function with the repository secret `SUPABASE_ACCESS_TOKEN`. Run it again after any change to the engine or the function. `build.sh` writes the version and commit into the function (GET it to see what is deployed).

## Cloud sync (client)
- Cloud: `app-core/cloud` (`CloudClient` over Supabase Auth and the `reward` function with Ktor, `SyncManager` with versions and conflicts; tested with a mock server in `SyncManagerTest`). `composeApp/.../CloudSettings.kt` holds the project URL and anon key of the owner's Supabase project (the anon key is public by design); Settings → Account has the UI. The saved document drops `account`; `GameStore` marks the account `unsynced` on any other change.
