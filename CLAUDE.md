# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# PokéPlan (reboot)

A fun productivity, growth and self-improvement app for Pokémon lovers: finishing a focus timer hatches an Egg, finishing tasks, habits and goals earns candies and items that level up and evolve Pokémon. The repo holds two codebases:

- **The rewrite (repo root):** Kotlin Multiplatform (Android, desktop, web) with a Supabase backend. **`docs/design.md` is the source of truth for what to build**; read it before changing game rules, and update it when a rule changes. Numbers marked *(tune)* there are starting values.
- **The original app (`legacy-android/`):** a native Android Java app from a MOBDEVE pair project, kept as the reference for the old rules. It is no longer developed.

## Layout
- `pokemon-core/`: the Pokémon side, reusable by every app in the planned family (see "Platform and apps" in docs/design.md); Kotlin Multiplatform (JVM and wasmJs), no UI, knows nothing about tasks or focus. Packages under `app.pokeplan.core`: `model` (egg rarity tables, candy reward tables, types, rarity), `dex` (the National Dex: species, forms, evolutions, regional dexes, Pokédex text, hatch rules; `dex/generated/DexData.kt` is generated), and `pokemon` (an owned Pokémon with IVs, EVs, nature, ability, gender, shiny, friendship; stat formula; hatching; items, bag, perks and transfer; candy, friendship and shiny rules; evolution rules; and the Guardian role of legendaries and mythicals in `Perks.kt`). `jvmMain/tools` holds JVM-only reports such as `./gradlew :pokemon-core:curationReport`.
- `plan-core/`: PokéPlan's own logic (focus sessions now; tasks, habits, goals and the planner next). Depends on `pokemon-core`. Package `app.pokeplan.plan`.
- `tools/species/generate.py`: stdlib Python that regenerates `DexData.kt` from the public PokeAPI CSVs. Run `python3 -I tools/species/generate.py`; it caches downloads in `tools/species/cache` (git-ignored).
- `assets/sprites/`: Pokémon sprites (96×96 PNG, `<formId>.png` and `<formId>-shiny.png`, plus `manifest.tsv`) from the public PokeAPI/sprites repo, fetched with `python3 -I tools/sprites/fetch.py` (needs `tools/species/cache`, so run the species generator first). Form ids match `FormInfo.id`.
- `supabase/`: the backend (migrations, SQL tests, the `reward` Edge Function); see `supabase/README.md`.
- `assets/items/`: real item icons from PokeAPI/sprites (`python3 -I tools/sprites/fetch_items.py`), used for the tab icons and the bag. **Art rule (owner's): use real game assets only (PokeAPI/sprites, Showdown); never hand-draw, generate or procedurally draw pixel art.** UI chrome (panels, buttons, bars) is drawn in code; icons and sprites are always real assets.
- `assets/backgrounds/`: Black/White PC box wallpapers from PKHeX, tiled behind each tab by `PixelBackground` (see `docs/assets.md` for sources and licences).
- `docs/design.md`: product and game design.
- `docs/home-region-curation-prompt.md` and `docs/home-region-candidates.md`: the kit for curating the Home Region (`tools/species/home-region.txt`) in a separate chat. Regenerate the candidates table with `./gradlew :pokemon-core:curationReport`.
- `legacy-android/`: the original Android app (Gradle project; see below). Single module `app`, package `com.mobdeve.s11.pokeplan`, Java 8, `minSdk 21` / `targetSdk 30`, view binding on, Android Gradle Plugin 7.0.2 with Gradle 7.0.2 (old: build with JDK 11, not 17 or newer).
- `app-core/`: the app's rules engine with no UI (`GameStore` applies every user action with the real game rules and saves one JSON document through a `Storage`; `AppData` is the saved shape). JVM and wasmJs; its tests run with `./gradlew :app-core:jvmTest`.
- Each tab with several sections (Planner, Pokémon, Me) opens on a **menu** of big cards with real item icons (`screens/Menu.kt`: `MenuGrid`, `SectionScaffold` with Back and the system back gesture), not tab pills.
- `composeApp/`: the shared Compose Multiplatform UI (targets: Android library, desktop JVM, wasmJs web), package `app.pokeplan.ui`; `App()` in `commonMain` is the entry point. Sprites (`assets/sprites`) and the Pokémon DS-era font (`assets/fonts`: `Pokemon-DP` and `-Bold` from the MIT-licensed hexparrot/pokemon-assets package, used for all text) are copied into Compose resources by the `prepareComposeAssets` task; the generated `Res` is in package `pokeplan.composeapp.generated.resources`. Screens are in `screens/`, the GBA-style theme and components (`TextBox`, `PixelButton`) in `theme/`. `ScreenshotTest` renders screens offscreen to `composeApp/build/screenshots/*.png` (`./gradlew :composeApp:jvmTest`), so a screen can be looked at without a device.
- Signing: every build (local and CI) uses the committed `androidApp/pokeplan-debug.keystore` (alias `pokeplan`, password `pokeplan`), SHA-1 `1A:28:99:CD:34:46:02:04:1E:AF:7B:15:7F:D1:07:41:BC:30:F0:A5`, so the app signature is stable. Private repo only; TODO before any public release: move to a secret key.
- `androidApp/`: the thin Android application (`MainActivity` calls `App()`, passing the accelerometer-based `AccelerometerMotion` for Deep Focus and an optional start tab); Glance widgets in `widgets/` (Today's tasks with a tick, Buddy and streak, Start focus) read and write the same `save.json` as the app through `widgetStore()`, and a task finished from a widget is paid when the app next opens; AGP 9, compileSdk 37, targetSdk 36, minSdk 26. Needs an Android SDK (`sdk.dir` in the git-ignored `local.properties`, or `ANDROID_HOME`) with `platforms;android-37.0` and build tools. `composeApp` sets `androidResources { enable = true }`, without which the APK ships without sprites, icons or fonts.

## Commands (rewrite, from the repo root)
- All tests (JVM): `./gradlew jvmTest`; one module: `./gradlew :pokemon-core:jvmTest`; one class: `./gradlew :pokemon-core:jvmTest --tests "app.pokeplan.core.EggTest"`
- Compile the web target: `./gradlew compileKotlinWasmJs` (wasm tests need yarn and a browser and are not run here)
- Regenerate Dex data: `python3 -I tools/species/generate.py`
- Server build (Kotlin compiled to JavaScript, ES modules): `./gradlew :pokemon-core:jsProductionLibraryCompileSync`, then check it under Node with `node tools/server/smoke.mjs pokemon-core/build/compileSync/js/main/productionLibrary/kotlin`. `tools/server/golden.json` is one fixed input and the exact JSON the engine must give; the JVM test and the Node script both check it. After a deliberate rule change regenerate it with `UPDATE_GOLDEN=1 ./gradlew :pokemon-core:jvmTest --tests "*GoldenVectorTest*"`.
- Supabase backend (`supabase/`, see `supabase/README.md`): SQL tests `supabase/tests/run.sh` (starts a throwaway PostgreSQL 16, applies `supabase/migrations/*.sql`, runs `supabase/tests/*.test.sql`; also reads `tools/server/golden.json` and the rank thresholds from `TrainerRank.kt`, so the SQL cannot drift from the Kotlin). The reward Edge Function's logic is tested under Node with the compiled engine: `node --experimental-strip-types supabase/functions/reward/core.test.ts` (after the JS build above). Deploying needs `supabase/functions/reward/build.sh`.
- CI (`.github/workflows/ci.yml`) runs on every PR: unit tests, the wasm and JS builds, the Node smoke test, the reward function test, the Supabase schema tests, and a check that regenerating the Dex data changes nothing. It passed on its first run (PR #23).
- `.github/workflows/release-apk.yml` publishes every merge to `main` as the "latest" GitHub release with `pokeplan.apk` (debug build, shared signing key), so the newest app can be installed from a phone. The "Tests and builds" job in `ci.yml` also keeps the APK of each PR as an artifact.
- Economy simulation: `./gradlew :plan-core:simulateEconomy` writes `docs/economy-report.md` (about 1 to 2 minutes). Re-run it after changing any reward, rank, egg, candy or transfer number and compare with the pacing targets in docs/design.md.
- Android app: `./gradlew :androidApp:assembleDebug` (APK in `androidApp/build/outputs/apk/debug/`). Desktop: `./gradlew :composeApp:run`. Web: `./gradlew :composeApp:wasmJsBrowserDevelopmentRun`. Compile checks without a display: `./gradlew :composeApp:compileKotlinJvm :composeApp:compileKotlinWasmJs`.
- Gradle 9.8.1, Kotlin 2.4.21, AGP 9.4.1, Compose Multiplatform 1.12.2, any JDK 17 to 21.
- In the cloud sandbox: `dl.google.com` (Google's Maven and the Android SDK) must be in the environment's allowed domains. The SDK is installed to `~/android-sdk` by the environment's setup script (cmdline-tools, `platform-tools`, `platforms;android-37.0`, `build-tools;37.0.0`). Maven Central rate-limits the shared proxy; a Gradle init script in `~/.gradle/init.d` that tries `https://maven-central.storage-download.googleapis.com/maven2` first avoids that. PokeAPI data and sprites are reachable through `raw.githubusercontent.com` (not `pokeapi.co`).

## Commands (legacy-android)
Run from `legacy-android/`:
- Build debug APK: `./gradlew assembleDebug`
- Install on a device or emulator: `./gradlew installDebug`
- Unit tests (JVM): `./gradlew testDebugUnitTest`; one test: `./gradlew testDebugUnitTest --tests "com.mobdeve.s11.pokeplan.ExampleUnitTest"`
- Instrumented tests (needs a device): `./gradlew connectedDebugAndroidTest`
- Lint: `./gradlew lint`

The only tests are the Android Studio templates (`ExampleUnitTest`, `ExampleInstrumentedTest`), so there is no real test coverage yet. Needs a local Android SDK (`local.properties` is git-ignored) with platform 30 and build tools 30.0.3.

## Firebase (legacy-android; read before running)
The app cannot run without Firebase: Auth (email and password), Realtime Database, plus Firestore, Storage and Analytics in the dependencies.
- `legacy-android/app/google-services.json` is committed and belongs to the original Firebase project `pokeplan-8930c`, which is not this repo's own.
- `DatabaseHelper.initLoggedInDB()` hardcodes that project's Realtime Database URL (`https://pokeplan-8930c-default-rtdb.asia-southeast1.firebasedatabase.app/`). For a reboot on a separate backend, create a new Firebase project, replace `google-services.json`, and change this URL; otherwise the app reads and writes the original project's data.
- Realtime Database layout: top-level nodes `Users/{uid}`, `UserPokemon/{uid}`, `Tasks/{uid}`.
- The login email and password are also stored in plain-text default `SharedPreferences` (`Keys.KEY_EMAIL`, `KEY_PASSWORD`) so `InitActivity` can sign the user in again on launch. Worth replacing if this ever leaves personal use.

## Architecture (legacy-android)
Single-activity-per-flow app, with all backend access funnelled through one class.

- **Entry flow:** `InitActivity` (launcher) checks `SharedPreferences` for saved credentials and re-authenticates with Firebase. If that works it goes to `MainActivity`; otherwise it shows the Login or Register screens. Register leads to `RegisterStarterActivity` (pick a starter Pokémon).
- **Main shell:** `MainActivity` hosts a `BottomNavigationView` wired to the Jetpack Navigation graph `res/navigation/nav_main.xml`. Its five fragments (`HomeFragment` start destination, `FocusTimerFragment`, `TaskListFragment`, `PokedexFragment`, `SettingsFragment`) are the tabs. Detail and edit screens (`TaskDetailsActivity`, `AddTaskActivity`, `PokemonDetailsActivity`, `PokedexDetailsActivity`, `PokemonPCActivity`, `UserProfileActivity`, `FaqsActivity`) are separate activities, all portrait-only and declared in `AndroidManifest.xml`.
- **Data layer (`data/`):** `DatabaseHelper` is the only class that talks to Firebase (Auth and Realtime Database). It is constructed with `loggedIn` true or false, and the three database references only exist when logged in. Firebase is asynchronous, so every call takes a callback interface (`FirebaseCallbackUser`, `FirebaseCallbackTask`, `FirebaseCallbackPokemon`) that returns `(result, isSuccessful, message)`. Follow that pattern for new queries rather than returning values.
- **Static game data:** the Pokedex is not in the database. `PokedexDataHelper.fillPokedex()` builds the whole list in code (name, dex number, rarity, evolution level, evolution target, types), and `FaqsDataHelper` does the same for the FAQ. Adding a Pokémon means adding an entry there.
- **Game rules live in models and screens, not the database:**
  - `models/Egg` rolls a rarity (Common, Uncommon, Rare, Super Rare) from a 100-slot pool using tables keyed by timer length, with boosted tables when Deep Focus is on, then picks a random Pokémon of that rarity.
  - `FocusTimerFragment` runs the countdown. `onPause()` stops and resets the timer (no egg), and in Deep Focus `onSensorChanged` does the same when accelerometer movement passes a threshold. Other behaviour toggles are `SharedPreferences` keys.
  - The candy rewards for finishing a task are computed in `TaskDetailsActivity` from the task priority (**5 pays the most**, 1 the least; an earlier version of this file said the opposite), using per-priority rate arrays. The rewrite renames priority to a difficulty rank E to A with the same direction.
  - `UserPokemon` holds a Pokémon's level and state. Ten Rare Candies make one level, one Super Candy evolves, and evolution is only allowed at the species' set level.
- **Models:** `UserDetails`, `UserTask`, `UserPokemon` are the Firebase-serialised objects (`setValue` / `getValue(Class)`), so keep their getters, setters and no-arg constructors compatible with Firebase's reflection when changing fields. `Pokemon` and `Pokedex` are the static species data.
- **Lists:** RecyclerView screens use the `adapters/` + `viewholders/` pairs (Task, Pokedex, Pokemon party, Pokemon PC, Faqs).
- **Reminders:** task notifications are scheduled with `AlarmManager` from `AddTaskActivity` / `TaskDetailsActivity` and delivered by `services/ReminderBroadcast` (a manifest-registered receiver). Settings can turn them off.
- **Preferences and Intent extras:** both use the `utils/Keys` enum (`Keys.KEY_X.name()`) as key names. Add new keys there instead of inventing string literals.
- **Custom UI:** `views/` has the app's own dialog and date and time pickers (`CustomDialog`, `CustomDatePicker`, `CustomTimePicker`); `models/CustomDate` is the date type used by tasks.

## Notes
- `.idea/` project files are committed (only caches, workspace and a few others are ignored).
- The README screenshots are hosted on GitHub's `user-images` CDN, not in the repo.

## Branches and pull requests
Adapted from the Balai Yllana website repo's workflow.
- `main` is the stable branch. Don't push to it directly (the one exception is the first "Initial commit"): every change reaches it through a pull request.
- **Every change starts a new branch from the latest `main`** (`git fetch origin main && git checkout -b <name> origin/main`), even a one-word fix. One topic per branch; unrelated changes go on separate branches and PRs. Never reuse a branch whose PR is merged or closed.
- **Branch names:** `<type>/<area>-<topic>`, lowercase, words joined with hyphens, the topic 1 to 4 words. It must match `^(feat|fix|content|design|chore|docs)/(auth|home|focus|tasks|pokedex|party|settings|data|ui|build|repo)-[a-z0-9]+(-[a-z0-9]+)*$`.
  - Types: `feat` (new behaviour or screen), `fix` (something broken), `content` (Pokédex entries, FAQ text, rarity and reward tables, strings), `design` (layouts, styles, drawables), `chore` (dependencies, Gradle, cleanup), `docs` (CLAUDE.md, README, comments).
  - Areas: `auth` (login, register, starter pick), `home`, `focus` (timer, eggs, Deep Focus), `tasks` (planner, reminders, candy rewards), `pokedex`, `party` (party, PC, levelling, evolution), `settings` (settings and profile), `data` (`DatabaseHelper`, Firebase, models), `ui` (shared widgets and navigation), `build` (Gradle, Firebase config, CI), `repo` (everything else). A change that touches several areas but serves one takes that area; otherwise `ui` or `repo`.
  - Examples: `fix/focus-deep-focus-threshold`, `content/pokedex-gen-three`, `feat/tasks-recurring-reminders`, `chore/build-upgrade-agp`.
- **Claude sessions** follow the same convention. A cloud session is handed an automatic branch name (`ccr-…`, `claude/…`); ignore it and create a correctly named branch from the latest `main` instead. Never push to `main`.
- **After the changes, open a PR** into `main`: bring in the latest `main` first, run `./gradlew jvmTest compileKotlinWasmJs` from the repo root (and `testDebugUnitTest assembleDebug` from `legacy-android/` only if you touched it), and read the diff once more. Title: `<type>(<area>): <what changed>`, sentence case, no final period, e.g. `fix(focus): Reset the timer when the screen turns off`. Claude stops there unless the owner has asked it to merge; the owner reviews and merges on GitHub, then deletes the branch.
