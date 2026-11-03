# PokéLife

A fun productivity, growth and self-improvement app for Pokémon lovers. Finish a focus timer and an Egg hatches; finish tasks, habits and goals and earn candies and items that level up and evolve your Pokémon. The app is a family of small modules, each its own "region", around one shared collection of Pokémon.

*PokéLife was called PokéPlan until 0.47.0. PokéPlan is now the name of its Plan module. The code packages (`app.pokeplan.*`), this repository and the Android application id keep the old name.*

**Status:** a private project in active development (version in [`VERSION`](VERSION), history in [`CHANGELOG.md`](CHANGELOG.md)). It is not published: the art is the owner's private-use collection and part of it is licensed for non-commercial use only (see [Assets and credits](#assets-and-credits)).

## What is in it

| Module | What it is |
|---|---|
| **Plan** (PokéPlan) | A planner with a focus timer at its centre: Focus (eggs, Deep Focus), tasks with reminders, lists, habits, goals, a day planner and a review. |
| **Care** | Gentle self-care: routines, calm breaks, a daily diary card, skills, a journal and trends. Private by design: what you write never earns anything. |
| **World** | A Pokémon Mystery Dungeon style adventure. Send expedition teams into dungeons; they climb floors on their own, offline too, and bring back items, treasure and Pokémon to recruit. Five regions (the Mystery Dungeon home world, Kanto, Johto, Hoenn, Sinnoh) with a story told as scenes. |
| **Pokégear** | Everyday tools with a helper Pokémon: calculator, currency and unit converters, timers, world clock, compass, level, sound meter, QR, flashlight and more, all useful without any game. |
| **Launcher** | An optional Android home screen: widget grid, other apps' widgets, a town where your Pokémon walk about, weather, folders and an app list. |
| **Shared** | Home (your buddy), the Pokédex and collection (the National Dex with Mystery Dungeon sprites), the Bag, Pokémon care (candies, friendship, evolution), and Me. |

Rules of the game are written down: [`docs/design.md`](docs/design.md) (what to build), [`docs/economy.md`](docs/economy.md) (every reward, sink and cap), [`docs/ecosystem.md`](docs/ecosystem.md) (the modules and the roadmap), [`docs/world.md`](docs/world.md) and [`docs/world-story-engine.md`](docs/world-story-engine.md).

## How it is built

A Kotlin Multiplatform rewrite (Android, desktop and web) with a Supabase backend. Compose Multiplatform draws one UI for all targets in a Game Boy Advance and Mystery Dungeon style; the art is real game art only (nothing is hand-drawn or generated).

| Folder | Role |
|---|---|
| `pokemon-core/` | The Pokémon side: the Dex, rewards, items, hatching, evolution. No UI. |
| `plan-core/`, `care-core/`, `world-core/` | Each module's own rules, no UI. |
| `app-core/` | The rules engine (`GameStore`), the saved shape (`AppData`) and cloud sync. |
| `composeApp/` | The shared Compose UI. |
| `androidApp/` | The Android host: widgets, launcher, reminders, sensors, torch. |
| `supabase/` | The backend: migrations, SQL tests, the reward Edge Function. |
| `assets/`, `tools/` | The game art and the scripts that fetch it. |
| `legacy-android/` | The original Java app, the reference for the old rules. No longer developed. |

The modules stay independent: one reward owner per real-world action, and Care and World never reach into Plan's internals. The save is one JSON document that older versions load unchanged (new fields always have defaults).

## Building

Needs a JDK 17 to 21; Gradle (9.8) downloads itself. Android also needs an Android SDK with `platforms;android-37.0` (`sdk.dir` in `local.properties` or `ANDROID_HOME`).

```sh
./gradlew jvmTest compileKotlinWasmJs        # tests and the web compile check
./gradlew :composeApp:run                    # desktop
./gradlew :composeApp:wasmJsBrowserDevelopmentRun   # web
./gradlew :androidApp:assembleDebug          # APK in androidApp/build/outputs/apk/debug/
```

`./gradlew :composeApp:jvmTest` also renders screens to `composeApp/build/screenshots/*.png`, so a screen can be looked at without a device. Other tools: `:plan-core:simulateEconomy` and `:world-core:simulateWorldEconomy` (the economy reports in `docs/`).

Every merge to `main` is published as the "Latest build" release with a debug APK (`.github/workflows/release-apk.yml`). Pull requests are tested by `.github/workflows/ci.yml`. The contributor guide for Claude Code sessions, with the branch and PR rules, is [`CLAUDE.md`](CLAUDE.md).

## Assets and credits

Pokémon sprites, portraits and animations are from the [PMD SpriteCollab](https://github.com/PMDCollab/SpriteCollab) (CC BY-NC 4.0: non-commercial, credit the artists; each artist is named under the Pokédex entry), with mainline sprites from PokeAPI as the fallback. Dungeon tiles, world art, item icons and sound effects are from the Pokémon Mystery Dungeon games, ripped and shared by the community; the font is from [hexparrot/pokemon-assets](https://github.com/hexparrot/pokemon-assets) (MIT); control glyphs are [Pixelarticons](https://github.com/halfmage/pixelarticons) (MIT). Every source, licence and fetch script is listed in [`docs/assets.md`](docs/assets.md). Pokémon and Pokémon Mystery Dungeon are trademarks of Nintendo, Game Freak, Creatures and Spike Chunsoft; this is an unofficial fan project and is not affiliated with them.

## Before any public release

- The APK is signed with the committed `androidApp/pokeplan-debug.keystore`. Move the signing key to protected CI secrets (and rotate it) first.
- Review each asset family's licence in `docs/assets.md`: much of the art is for private use only.

## The original app

PokéPlan began as a native Android (Java) app made by a pair for a mobile development course: a focus timer that hatches Eggs, a task planner whose finished tasks pay Rare and Super Candies, a Pokédex and a Pokémon party. It lives on in [`legacy-android/`](legacy-android/) as the reference for the old rules.
