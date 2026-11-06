# REP-011: reminder inventory and cutover (INTEGRATIONS_AND_PRIVACY.md section 9.11)

**Done:** 6 November 2026, 0.131.0. **Evidence:** 11 JVM tests of the policy, the adapters, the coordinator and the hold table (`ReminderPolicyTest`, `ReminderAdaptersTest`, `ReminderCoordinatorTest`, `PlanReminderAdapterTest`, `ReminderRepositoryTest`), and the DB check on a device for the hold table. **Not run on a device:** the alarms, notifications and buttons themselves (they need the owner's phone, see section 5).

## 1. Inventory of the 0.130.0 reminders

| Source | Rule (module) | Alarm identity | Channel | Receiver | Persisted state |
|---|---|---|---|---|---|
| PLAN task reminders | `Reminders.upcoming` (up to 3 per task, quiet hours, rest days, focus) | request code = hash of (task id, time); snooze `(task,-1)`; repeat `(task,-2)` | `task-reminders-2` | `ReminderReceiver` | SharedPreferences `task_reminders` (alarm codes, "asked" flag) |
| CARE routine reminders | `CareReminders.upcoming` (own time, own days, not done, not repeated) | hash of (routine, day); "in 30 min" = code+1 | `care-routines-2` | `RoutineReminderReceiver` | SharedPreferences `routine_reminders` (alarm codes) |
| Focus halfway and end | `Nudges.focusAlerts` | `AppAlerts` | `focus-halfway`, `focus-end-2` | `NudgeReceiver` | `AppAlerts` prefs |
| Streak reminder, weekly recap | `Nudges.streakAtRisk`, `weeklyRecap` | `AppAlerts` | `evening-nudges` | `NudgeReceiver` | `AppAlerts` prefs |
| World jobs, GEAR timers | World and Pokégear | `AppAlerts` | `world-jobs`, `gear-timers` | `NudgeReceiver`, `TimerActionReceiver` | `AppAlerts` prefs |

## 2. Decision: what moves to the shared feature and what stays

- **Moved (0.131.0):** PLAN task reminders and CARE routine reminders: the only two that are *module reminders on a source record* (section 9.1). They are one scheduler, one receiver (`SharedReminderReceiver`), one request shape (`ReminderRequest`, stable id `MODULE|ref|kind|occurrence`).
- **Stay in `AppAlerts` on purpose:** focus alerts (they are the focus session's own feedback, not a deferred reminder), World jobs and GEAR timers (user-started timers, not source-record reminders). The streak reminder and the weekly recap are PLAN nudges, not reminders on a record; they already respect the global quiet hours (`Nudges.streakAtRisk`, `weeklyRecap`) and, per `docs/design.md`, wait for the end of a focus session. They move when PLAN's own refactor (REP-014) gives them a source adapter.

## 3. What is now where

| Concern (section 9) | Where |
|---|---|
| Request contract, stable ids, dedupe, bounded horizon | `ReminderRequest`, `ReminderPolicy.plan` (`app-core`) |
| Global quiet hours (every module, midnight wrap, next eligible time) | `ReminderPolicy.deliverAt` |
| Focus deferral, re-checked at delivery, no loop | `ReminderPolicy.deliverAt`, `ReminderCoordinator.onFire` (Defer) |
| Source adapters | `PlanReminderAdapter`, `CareReminderAdapter` |
| Action routing (stale or unknown sources cannot mutate; snooze and dismiss never complete or pay) | `ReminderRouter` |
| Snooze per module (PLAN 10 min / 1 h / tomorrow; CARE 30 min) | `ReminderSnooze` |
| Repeats (PLAN's own, bounded to 3) | `PlanReminderAdapter.followUp` |
| Persistence (only snoozes and repeats; opaque ids, times) | table `reminder_hold` (schema v3), `ReminderRepository` |
| Android alarms, notification, buttons, recovery | `SharedReminders`, `SharedReminderReceiver`, `ReminderBootReceiver` |

## 4. Cutover steps (section 9.11 list)

1. Inventory: section 1. 2. Stable mapping: the old per-module alarm codes are not reused; the new id is `MODULE|ref|kind|occurrence`. 3. Active requests are **rebuilt from the canonical module saves** on every `reschedule`. 4. Module settings, schedule, snooze and action semantics are kept (PLAN's repeat and its three snoozes; CARE's one 30-minute snooze). 5. Old and new cannot both deliver: the old receivers (`LegacyReminders.kt`) show nothing. 6. Cut over by module with a version marker (`reminder_migration`, version 1). 7. `LegacyReminders.cancelOnce` runs **after** the new alarms are set. 8. No completion or reward is replayed; the router only acts on a tapped button. 9. Idempotent: the marker is written last, and cancelling twice does nothing. 10. Rollback information: the old classes and channels still exist; the saves are untouched.

Behaviour changes, on purpose: Care's reminders now respect the global quiet hours (0.126.0); a snooze survives a reboot (it is stored); nothing else changes for the player.

## 5. To check on the phone (nothing here is device-proven)

A routine with a time a few minutes ahead: it notifies, Done ticks it, "In 30 min" returns it. A task with a reminder: Done, 10 min and 1 hour. Snooze, restart the phone, and the reminder still comes. A reminder during a focus session waits for its end. Routine at 23:00 comes at 07:00. After the update, notifications posted by the old version still react to their buttons.
