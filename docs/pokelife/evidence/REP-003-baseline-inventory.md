# REP-003: persisted data, cloud paths and permissions at 0.123.0

**Evidence for:** `IMPLEMENTATION_GUIDE.md` section 4 (P0 item 1) and `ARCHITECTURE/DATA_AND_MIGRATIONS.md` section 12.  
**Checked:** 6 November 2026, repository `main` at commit `2b22975d`, by reading source. Nothing here was run on a device. This is evidence of what exists, not of how well it works.

## 1. Persisted data

| Store | Where it lives | Shape | Who touches it |
|---|---|---|---|
| Main save | `filesDir/save.json` (`androidApp/.../SaveFile.kt`; a file on desktop; browser storage on web) | One JSON document, `AppData` (`app-core/.../AppData.kt`), `schema = 1` (`AppData.SCHEMA`) | The app's `GameStore`, and the widgets through `widgetStore()`, which load and write the same file |
| Care save | `filesDir/care.json` | One JSON document, `CareData` (`care-core/.../Care.kt`), kept by `CareStore` | The app, the Care widget and the routine reminders; pays the main save through the `World` interface |
| Damaged saves | `Storage.keepAside` | An unreadable document is kept aside and the store starts empty; it is never overwritten | `SavedDocument` (`app-core/.../SavedDocument.kt`) |

What `AppData` holds, grouped by the module that will own it under the target specs:

- **PLAN:** tasks, lists, events, series, habits, goals, weekly reviews, rest days, active days, focus session and history, pending completions, emergency pauses, calendars shown.
- **Shared Pokémon and economy:** `player` (`PlayerState` in `pokemon-core`: the owned Pokémon, `buddyId`, `careLeadId`, parties through `PartyIds`, the bag, coins, egg meter, trainer points, caught set, guardians), favorites, shiny species and forms, caught and seen species and forms, origins, quests and completed trials, counters, egg tickets, reward log, seen rank.
- **WORLD:** `world` (`WorldData`).
- **GEAR:** `gear` (`GearData`: timers, stopwatch, calculator, currency board and rates, units, picker, dice, flashlight, clocks), `gearRates`, `gearHelpers`, `gearLoans`, `pickerOptions`.
- **App and account:** trainer name, onboarding, `settings`, `account`, last seen version.

Findings against the target architecture:

1. Two JSON documents with no shared transaction; a payout to `world` from Care is two writes.
2. Whole-document load and rewrite is the update model, which is the stale-snapshot risk the data spec calls out (section 4.3). The widgets and the app protect against it only by calling `GameStore.reload()` when the app returns to the front.
3. `schema` is a single integer and there are **no fixture files** in any `src/*Test*/resources`. Migration tests will need saves to be created (REP-003 follow-up below).
4. GEAR records and the helper loans live inside the main save, next to Pokémon and rewards, where the target keeps GEAR's records apart and helpers visual only (`gearLoans` is a mechanic to simplify or retire, per `MODULES/GEAR.md` section 6).

## 2. Cloud and account paths

Present in `app-core/.../cloud/`: `CloudClient.kt`, `SyncManager.kt`, `CareSync.kt` and `Social.kt`, plus `AppData.account`, the `supabase/` backend, `tools/server` (the engine built for the server), the golden vector and the `deploy-reward.yml` workflow. All of these are retired after Gate B (backlog REP-012). Until then they are not extended.

## 3. Android manifest

Permissions declared in `androidApp/src/main/AndroidManifest.xml`:

| Permission | Target-spec view | To check |
|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Currency rates and the like only; no core dependency | Disappears from core paths once cloud is retired |
| `VIBRATE`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED` | Reminders | Request timing is in context (Integrations section 4) |
| `USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM` (max SDK 32) | Reminders | Justify per the Integrations spec before release |
| `READ_CALENDAR` | Read-only calendar foundation (PLAN section 8) | Keep; ask just in time |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `RECORD_AUDIO` | On-demand GEAR sensors only; no background location | Used from `AndroidCapabilities.kt` and `AndroidGearServices.kt`; confirm each is foreground and just in time |
| `QUERY_ALL_PACKAGES`, `REQUEST_DELETE_PACKAGES` | Launcher app list and uninstall | Needs Play-style justification if ever distributed that way |

Other manifest facts:

- `android:allowBackup="true"`: the target replaces uncontrolled Android Auto Backup with the user-controlled backup design (Integrations section 10).
- Exported components: two activities and the receivers for the five widgets (Today, Buddy, Focus, Care, World) and `ReminderBootReceiver`. The reminder receivers, `WidgetRefreshReceiver` and the provider are not exported.
- Reminder scheduling is spread over four receivers (`ReminderReceiver`, `NudgeReceiver`, `TimerActionReceiver`, `RoutineReminderReceiver`); the target has one shared scheduler.

## 4. Still to do for REP-003

- Create representative save fixtures (an empty save, a typical month, a heavy save, a Care save, a save with a running focus session and a pending widget completion) and commit them under a test `resources` folder with the app version that wrote them.
- Confirm which declared permissions are actually exercised, on a device.
