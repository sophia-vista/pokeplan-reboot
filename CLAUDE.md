# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# PokéPlan (reboot)

Native Android productivity app that gamifies tasks and focus time with Pokémon: finishing a focus timer hatches an Egg, finishing a task earns Rare Candies (level up) and Super Candies (evolve). Originally a MOBDEVE pair project; this repo is a private personal copy continued by one maintainer, starting from a single "Initial commit" with no inherited history. `README.md` describes the user-facing features (Focus Timer, Task Planner, Pokedex, Pokemon Party).

## Layout
The Android project is in `pokeplan/` (not the repo root). Run all Gradle commands from there. Single module `app`, package `com.mobdeve.s11.pokeplan`, Java 8, `minSdk 21` / `targetSdk 30`, view binding on, Android Gradle Plugin 7.0.2 with Gradle 7.0.2 (old: build with JDK 11, not 17 or newer).

## Commands
Run from `pokeplan/`:
- Build debug APK: `./gradlew assembleDebug`
- Install on a device or emulator: `./gradlew installDebug`
- Unit tests (JVM): `./gradlew testDebugUnitTest`; one test: `./gradlew testDebugUnitTest --tests "com.mobdeve.s11.pokeplan.ExampleUnitTest"`
- Instrumented tests (needs a device): `./gradlew connectedDebugAndroidTest`
- Lint: `./gradlew lint`

The only tests are the Android Studio templates (`ExampleUnitTest`, `ExampleInstrumentedTest`), so there is no real test coverage yet. Needs a local Android SDK (`local.properties` is git-ignored) with platform 30 and build tools 30.0.3.

## Firebase (read before running)
The app cannot run without Firebase: Auth (email and password), Realtime Database, plus Firestore, Storage and Analytics in the dependencies.
- `pokeplan/app/google-services.json` is committed and belongs to the original Firebase project `pokeplan-8930c`, which is not this repo's own.
- `DatabaseHelper.initLoggedInDB()` hardcodes that project's Realtime Database URL (`https://pokeplan-8930c-default-rtdb.asia-southeast1.firebasedatabase.app/`). For a reboot on a separate backend, create a new Firebase project, replace `google-services.json`, and change this URL; otherwise the app reads and writes the original project's data.
- Realtime Database layout: top-level nodes `Users/{uid}`, `UserPokemon/{uid}`, `Tasks/{uid}`.
- The login email and password are also stored in plain-text default `SharedPreferences` (`Keys.KEY_EMAIL`, `KEY_PASSWORD`) so `InitActivity` can sign the user in again on launch. Worth replacing if this ever leaves personal use.

## Architecture
Single-activity-per-flow app, with all backend access funnelled through one class.

- **Entry flow:** `InitActivity` (launcher) checks `SharedPreferences` for saved credentials and re-authenticates with Firebase. If that works it goes to `MainActivity`; otherwise it shows the Login or Register screens. Register leads to `RegisterStarterActivity` (pick a starter Pokémon).
- **Main shell:** `MainActivity` hosts a `BottomNavigationView` wired to the Jetpack Navigation graph `res/navigation/nav_main.xml`. Its five fragments (`HomeFragment` start destination, `FocusTimerFragment`, `TaskListFragment`, `PokedexFragment`, `SettingsFragment`) are the tabs. Detail and edit screens (`TaskDetailsActivity`, `AddTaskActivity`, `PokemonDetailsActivity`, `PokedexDetailsActivity`, `PokemonPCActivity`, `UserProfileActivity`, `FaqsActivity`) are separate activities, all portrait-only and declared in `AndroidManifest.xml`.
- **Data layer (`data/`):** `DatabaseHelper` is the only class that talks to Firebase (Auth and Realtime Database). It is constructed with `loggedIn` true or false, and the three database references only exist when logged in. Firebase is asynchronous, so every call takes a callback interface (`FirebaseCallbackUser`, `FirebaseCallbackTask`, `FirebaseCallbackPokemon`) that returns `(result, isSuccessful, message)`. Follow that pattern for new queries rather than returning values.
- **Static game data:** the Pokedex is not in the database. `PokedexDataHelper.fillPokedex()` builds the whole list in code (name, dex number, rarity, evolution level, evolution target, types), and `FaqsDataHelper` does the same for the FAQ. Adding a Pokémon means adding an entry there.
- **Game rules live in models and screens, not the database:**
  - `models/Egg` rolls a rarity (Common, Uncommon, Rare, Super Rare) from a 100-slot pool using tables keyed by timer length, with boosted tables when Deep Focus is on, then picks a random Pokémon of that rarity.
  - `FocusTimerFragment` runs the countdown. `onPause()` stops and resets the timer (no egg), and in Deep Focus `onSensorChanged` does the same when accelerometer movement passes a threshold. Other behaviour toggles are `SharedPreferences` keys.
  - The candy rewards for finishing a task are computed in `TaskDetailsActivity` from the task priority (1 is the best reward, 5 the lowest), using per-priority rate arrays.
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
- **After the changes, open a PR** into `main`: bring in the latest `main` first, run `./gradlew testDebugUnitTest assembleDebug` from `pokeplan/` (and `lint` for UI or manifest changes), and read the diff once more. Title: `<type>(<area>): <what changed>`, sentence case, no final period, e.g. `fix(focus): Reset the timer when the screen turns off`. Claude stops there; the owner reviews and merges on GitHub, then deletes the branch.
