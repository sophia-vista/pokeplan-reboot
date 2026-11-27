# PLAN Calendar audit against the revised owner task list (27 November 2026)

**Scope.** The owner's P1 "Calendar Improvements" list (28 items, supplied 27 November 2026) checked against the code on `main`/branch `feat/tasks-plan-home-declutter` (0.257.0 plus the PLAN home declutter). Read-only desk audit, no device. Line references are from that branch and may move.

**Result:** 8 items already built, 13 partial, 7 missing. The calendar is not being rebuilt; the real missing chunk is **event enrichment** (items 19–24) plus event description/location/reminders (14) and visible provenance (10, 25–27).

## Calendar UX

| # | Item | Verdict | Evidence |
|---|---|---|---|
| 1 | Persistent, dismissible drag-and-drop guidance | Partial | One-line hint in `ui/plan/DayPlan.kt:546`, shown only while no task has a planned time (`:334`); no dismiss control or stored state. |
| 2 | Explain scheduling, moving and resizing | Partial | Only in Help (`ui/screens/Help.kt:55-56`); the Calendar itself says nothing, and :56 still says events never reach the phone calendar (8.1A changed that). |
| 3 | Rename Upcoming to "Upcoming Tasks" | Missing | `ui/plan/Tasks.kt:49` still `UPCOMING("Upcoming")`. |
| 4 | Calendar as the combined task and event view | Built | `ui/plan/DayPlan.kt:250-267` merges device events, overlays, PLAN events and tasks; the month view spans tasks and events (`MonthView.kt:111-115`). |
| 5 | Unscheduled-task planning tray | Partial | "To plan" tray with tap/drag chips (`DayPlan.kt:334,541-589`, `plan/DayTimeline.kt:62-68`); chips show icon and title only, untimed-today and undated mix, and the month view has no tray. |

## Google (device) calendar integration

| # | Item | Verdict | Evidence |
|---|---|---|---|
| 6 | Import and display Google Calendar events | Built | Device calendars chosen in Settings (`ui/screens/Settings.kt:217-236`), read through the Calendar Provider (`androidApp/.../AndroidCalendars.kt:80-120`), drawn in the day view; no Google sign-in. |
| 7 | Google Calendar strictly read-only | Partial | External events are display-only, but the opt-in 8.1A sync writes PLAN's own events to one chosen calendar (`app/integrations/CalendarSync.kt:109-181`, `AndroidCalendars.kt:220-246`). Owner decision needed: the P1 list says "never write", 8.1A says write only PLAN's own events. |
| 8 | Reflect changes made externally | Built | Device events are re-read when the range is built; the 8.1A sync pulls changes on Sync now and every 5 minutes while the app is open (`ui/App.kt:482-486`). |
| 9 | Never create/edit/delete Google events | Partial | Only linked PLAN rows are ever touched (`CalendarSync.kt:109-147,168-171`); external events are never edited, but PLAN does create its own events in the synced calendar. Same owner decision as 7. |
| 10 | Clearly identify imported events | Partial | Read-only events differ by colour/alpha and a screen-reader string only (`DayPlan.kt:131,813-816`); brought-in events become plain PLAN events with no provenance. |
| 11 | Preserve source event identifiers | Partial | Provider EVENT_ID is embedded in the display id (`AndroidCalendars.kt:109`) and persisted for linked rows (`CalendarSync.kt:40`); read-only display events store no id, so they cannot carry enrichment yet. |

## PLAN-only events

| # | Item | Verdict | Evidence |
|---|---|---|---|
| 12 | Create events in PLAN | Built | `app/GameStore.kt:724-730`; `ui/plan/DayPlan.kt:318-323`, `EventPanels.kt:74-124`. |
| 13 | Edit and delete | Built | `EventPanels.kt:128-175` (fields, repeat, companion, people, delete one/all, Undo), `GameStore.kt:732-745`. |
| 14 | Title, description, date, time, location, reminders | Partial | `PlanEvent` has title/day/start/minutes/all-day/repeat/colour/companion only (`plan/PlanEvents.kt:13-27`); no description, location or event reminder anywhere in model, form or store. |
| 15 | Recurring PLAN-only events | Built | Six repeats (`PlanEvents.kt:88-105`), repeat/end/skip edits (`plan/PlanCalendar.kt:73-92`), UI `EventPanels.kt:179-205`. |
| 16 | Stored locally | Built | `AppData.events`, SQLite `plan_event` table. |
| 17 | Independent of Google sync | Built | Sync is off by default; PLAN events exist without any calendar source and are created/deleted only through `GameStore`. |

## Event enrichment

| # | Item | Verdict | Evidence |
|---|---|---|---|
| 18 | LINK contacts on both Google and PLAN-only events | Partial | The people picker exists on PLAN events only (`EventPanels.kt:91,143`, `ui/integrations/EventPeople.kt`, `app/integrations/PlanPeople.kt:42-48`); read-only Google events have no editor. |
| 19 | Link related PLAN tasks | Missing | No event↔task field (`PlanEvents.kt:13-27`) and no UI. |
| 20 | Private notes and checklists | Missing | No notes/checklist fields. |
| 21 | Link relevant PokéLife records | Missing | Only LINK people exist; no task/goal/CARE/HEAL/BASE/WORLD link. |
| 22 | Persist enrichment apart from imported data | Partial | People links live in the `integrations` document, separate from `AppData.events` (`PlanPeople.kt:16-48`); nothing else is enrichment and Google events get none. |
| 23 | Keep enrichment when imported details refresh | Partial | A calendar pull keeps companion/paid fields (`CalendarSync.kt:185-188`) and people links keyed by stable id survive; there is no notes/task enrichment to preserve yet. |
| 24 | Handle a deleted/unavailable source without deleting enrichment | Missing | A vanished calendar row deletes the PLAN event unless edited here (`CalendarSync.kt:112-125`); pruning drops people links (`PlanPeople.kt:51-54`); no "source unavailable" state. |

## Event identification

| # | Item | Verdict | Evidence |
|---|---|---|---|
| 25 | Visually distinguish Google from PLAN events | Partial | PLAN teal vs external grey/provider colour plus fill/alpha (`DayPlan.kt:131,813`); same shape, colour alone (PLAN.md 8.1/8.3 want a label too). |
| 26 | Source indicators in event details | Missing | External events are not openable, so they have no details panel (`MonthView.kt:243`, `DayPlan.kt:818-830`); only the screen-reader string names the source. |
| 27 | Separate imported information from local enhancements | Missing | `PlanEvent` carries no provenance and the panel shows no imported/local split. |
| 28 | Local edits must not modify the source event | Partial | Read-only device events cannot be edited; brought-in events are normal PLAN events whose edits write back by design (8.1A rule 4). The P1 wording and 8.1A disagree (same decision as 7). |

## Missing work, in order

1. **Owner decision:** keep 8.1A's opt-in two-way sync for PLAN's own events, or make every Google event strictly read-only (no writing at all). Items 7, 9 and 28 cannot be finished without it.
2. **Spec first:** `PLAN.md` and `INTEGRATIONS_AND_PRIVACY.md` for event enrichment — people, linked PLAN tasks, notes, checklists and other records on both PLAN and read-only imported events, keyed by provider event id in the `integrations` document, with a "source unavailable" state that keeps the local data.
3. Rename the Tasks pill to "Upcoming Tasks" (`Tasks.kt:49`) and align `PLAN.md` 3.3; decide whether Upcoming lists events as well as tasks (3.3 says it should, the code is tasks-only).
4. Event description, location and reminders in the model, form and store.
5. Visible source badge/label and a read-only details surface for external events; provenance on brought-in events.
6. Tray: estimates, "today, no time" split from undated, reachable in the month view.
7. Persistent dismissible drag-and-drop guidance, and fix the stale Help line about phone calendars.
8. Fix the stale KDoc at `app/integrations/CalendarSync.kt:89` (it still says calendar events are never imported).
