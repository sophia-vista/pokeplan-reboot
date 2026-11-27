# PLAN Module Specification

**Status:** Approved for implementation planning  
**Architecture decisions:** CARE owns self-care routines; reminder scheduling is shared; PLAN has one recurring-task system and no Habit record type.

## 1. Purpose and product role

PLAN is PokéLife's planning and execution region. It helps the user capture commitments, decide what matters now, organize future work, and spend focused time doing it.

Its core loop is: capture something that needs attention; decide whether it is actionable, scheduled, recurring, or reference material; put it somewhere appropriate; see the right amount of work at the right time; complete, defer, or deliberately leave it open; receive appropriate progression without duplicate rewards.

PLAN should not become a generic project-management suite. It owns:

- Tasks and subtasks.
- Task lists and projects.
- Task capture.
- Today, Upcoming Tasks, and planning views.
- Calendar events created within PLAN.
- Recurring tasks.
- Focus sessions and focus history.
- Goals and milestones.
- PLAN task/event reminder preferences and PLAN completion/progression events. Reminder scheduling and delivery follow `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`.

PLAN does not own CARE self-care routines, Diary Cards, journal entries, mood/context records, or coping tools; HEAL activity, fitness, nutrition, sleep, or health measurements; MINT accounts, cards, e-wallets, transactions, budgets, or bills; BASE chores, maintenance, supplies, household inventory, or home records; LINK contacts, relationship notes, important dates, or follow-ups; GEAR utilities or device-tool records; WORLD expeditions, campaigns, dungeons, missions, recruitment, or settlement records; or external calendar records. PLAN may display linked summaries or create an explicitly requested PLAN task linked to another module's record, but must not create competing canonical copies.

## 2. Architecture and ownership

### 2.1 Canonical ownership

| Record/capability | Owner | PLAN integration |
|---|---|---|
| One-time tasks | PLAN | Native |
| Recurring responsibilities | PLAN | Native |
| Self-care routines and CARE check-ins | CARE | Optional linked visibility; completion routes to CARE |
| PLAN-created calendar events | PLAN | Native |
| Google/device calendar events | External provider | Read-only |
| Focus sessions | PLAN | Native |
| Goals and milestones | PLAN | Native |
| HEAL activity and health records | HEAL | May link to a PLAN task only by explicit user action |
| MINT financial records and bills | MINT | May link to a PLAN task only by explicit user action |
| BASE chores, maintenance, supplies, and inventory | BASE | May link to a PLAN task only by explicit user action; no duplicate chore record |
| LINK contacts, important dates, and follow-ups | LINK | May link to a PLAN task only by explicit user action |
| GEAR utility usage and device-tool state | GEAR | No planning record by default |
| WORLD expedition and story progress | WORLD | No duplicate PLAN record |
| Reward events | Module owning the activity | Shared reward delivery, not shared ownership |

Distinguish linked records, imported read-only external events, and PLAN-owned tasks. They are not interchangeable.

### 2.2 CARE routine ownership

CARE retains its existing routine model and self-care-specific behavior: title, optional icon, optional time, daily/selected-weekday schedule, completion history, archived state, CARE-specific reward eligibility/payment history, CARE rhythm/trend calculations, and local-only persistence.

PLAN must not create a parallel task for each CARE routine. CARE routines may appear in a combined agenda/calendar as CARE-owned entries, clearly identified as such. Completing one from PLAN must route to CARE's canonical store and CARE reward logic.

Converting a CARE routine into a PLAN task requires an explicit user action and an explanation that the new record becomes PLAN-owned and follows PLAN completion/reward rules. No silent conversion or duplicated completion.

### 2.3 Reminder integration

Reminder scheduling, delivery, global quiet hours, focus deferral, notification permissions, recovery, and cross-module policy are shared capabilities defined in `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`. That feature specification is the single source of truth for shared reminder behavior and implementation contracts.

PLAN owns only its reminder requests and PLAN-specific preferences (section 7). CARE retains ownership of its routine and Diary Card reminder preferences and source records (`CARE.md`); they use the same shared contract, and no CARE reminder is converted into a PLAN task.

## 3. Information architecture

Use a small, stable navigation structure. Avoid a separate destination for every feature.

**Layout:** Modules → Plan opens a page with no on-page menu: the lead (112 dp), the overview (today's open tasks, three at most, each with a check key, and an "All N tasks" key; one title when nothing is planned), the action items in one **Quick actions** box (the quick add field, then the task to do next with **Focus** and **Pick for me**), and the party last, one short row of slots. The quick add field is the only capture key on the page (REP-065). Every Plan place (**Tasks**, **Calendar**, **Focus** on the phone, **Goals**, **Settings**) is in the shared Sections key in the header (`SectionsKey`), Tasks, Calendar and Focus first. `PlanSection` is TASKS, CALENDAR, FOCUS, GOALS, SETTINGS.

Primary destinations (the owner uses only these three, so nothing else is a destination):

1. **Tasks** — one workspace. It opens on **Today**. Four views as pills under the quick add box: **Today**, **Upcoming Tasks**, **All** (every open task grouped by list) and **Done** (finished and won't-do tasks, latest first). Under the views, one key per list shows the list's name and its outstanding count; a tap opens that list in **All** (from **Done** it keeps Done), so a list is one tap away instead of behind the Filter (owner, 27 November 2026). The lists themselves are managed in **Plan settings** (new list, edit, archive, delete), and the **Filter** under the search box on All and Done still narrows by search, status, difficulty and one list (a Checklist or Tracker list then keeps its finished items on view). A task in no list shows under "No list" in All. The quick add field reads "Add a task (date, #list)"; inside a chosen list a new task is filed in it, and on Today it is planned for today.
2. **Calendar** — the day, week and month views with PLAN events and the read-only device calendars (8); one tap from the page, through the header's Sections key (the Tasks header carries no Calendar key).
3. **Focus** — start and manage focus sessions, for any open task or none (6).

### 3.1 Today

*(A view inside Tasks, and the action items on the module page.)*

Today is the default landing view. It should answer what needs attention now without requiring the user to organize everything first.

Suggested order:

1. Date and concise daily status.
2. Prominent quick-capture action.
3. Overdue tasks, if any.
4. Today's scheduled and due tasks.
5. Recurring tasks due today.
6. Optional linked CARE routine summaries, if enabled.
7. Compact focus entry point.
8. Completed items, collapsed by default.

Do not fill Today with future tasks merely because they are incomplete. Do not automatically treat unscheduled tasks as due today. Overdue tasks remain visible until resolved or rescheduled. Completed tasks should not dominate. Linked CARE records must be distinguishable and must not generate PLAN rewards. Today remains useful when empty, with a restrained empty state and capture action.

### 3.2 Unfiled tasks

Anything the user does not want to organize immediately is an open task with no list and no date; it shows under "No list" in All. Giving it a date or a list files it. Title-only capture is valid. A task does not require a list, project, due date, priority, recurrence, or reminder.

The user may leave a task unfiled, assign a date/list/project, add recurrence/notes/subtasks, convert it to a PLAN event where appropriate, archive, or delete. Do not force mandatory triage.

### 3.3 Upcoming Tasks

Present future tasks chronologically with date grouping, including overdue items and recurring-task occurrences. It is the tasks place: PLAN events and read-only external calendar events stay in **Calendar**, the combined task-and-event view (owner, 27 November 2026). Default to a list; calendar grid is a secondary visualization. Keep filters minimal: all, and optionally a selected list.

### 3.4 Calendar

Calendar is a planning surface, not a second task database. Support month/week views and compact agenda. Display PLAN events (kept in step with one chosen phone calendar when two-way sync is on, 8.1A), PLAN task due dates/scheduled times, read-only external events, and optionally linked CARE routines.

A task is actionable and has completion state; an event represents something happening at a time; an external event is read-only. Do not create a task merely because an external event appears. **Overlays (owner, 24 November 2026):** the calendar also shows read-only all-day entries from in-app overlay sources; LINK's dates and open follow-ups are the first (LINK's accent, calendar id -2; a tap opens the person or LINK's place). PLAN's calendar knows nothing about LINK: it only asks `CalendarOverlay` sources (`CalendarEvent.isOverlay`, `isOpenable`). Overlay entries are never tasks, never pay and never reach reminders, the launcher or widgets.

**Guidance (owner, 27 November 2026).** The day/week Calendar opens with a short, dismissible guide — **Plan by the hour** — saying that a task is dragged onto an hour, that its edge is dragged to change its length, and that events move and resize the same way; the close key stores `Settings.calendarHintDismissed`, so it does not return (Help repeats it in full). The unscheduled tray under it splits **Today, no time** from **No day**, each with its count; a chip shows its estimate when it has one, and tap-then-hour and drag both place it.

Tasks, goals and lists carry no category: a list is the one way to group (a Work list, a Home list). The balance wheel and the weekly recap split by list; `#word` in quick add names a list; the calendar and widgets colour a task by its list.

### 3.5 Lists and projects

Use one flexible grouping model with two presentations: a **List** is a collection of related tasks; a **Project** is a list with optional target, status, and milestones. Projects use the same task engine.

Project capabilities: name, optional description, optional icon/color, status (active/on hold/completed/archived), start date and optional target date, task collection, optional milestones, and progress derived from relevant tasks/milestones. Exclude complex dependencies, Gantt charts, resource allocation, and enterprise workflow features from initial release.

### 3.5A List kinds, the resetting checklist and a reminder without a due date (owner, 15 November 2026; backlog REP-058)

*Why.* The owner used TickTick for groceries, learning and certification tracking as well as tasks, and asked for "reminders and checklist" with precise date and time control that is still highly customisable. A list is not always a collection of dated tasks.

- **List kind.** A list has a kind, chosen when it is created and changeable later without losing items: **Tasks** (the default, today's behaviour), **Checklist** (items to tick off and reset) or **Tracker** (a long-running list whose items are things to learn, earn or finish, such as a certification path, shown with a count and no due date). The kind changes the list's presentation and defaults only; every item is still a task on the same engine (3.5).
- **Resetting checklist.** A Checklist's items reset at its occurrence boundary (a recurring checklist, 5.1), so a grocery run or a weekly cleaning round starts clean. Items left unticked at reset are recorded as not done in the history with no penalty, no streak loss and no reward effect (5.3). Ticking items never completes the parent on its own (4.5); the list shows derived progress and offers "Done".
- **A reminder without a due date.** A task may carry a reminder time with no due date: a nudge. It shows on Today at that time, never becomes overdue, and clears when done or dismissed. It uses the shared reminder contract (7) like any other.
- **Checklist templates.** A Checklist can keep its items as a template ("Save items as template"); "New checklist" then offers "Start from" any list that has one, which makes a new list with the same items, unticked. A template is part of the list (no separate screen) and carries no dates.
- **The nudge.** A reminder without a due date is set under More in the task editor as "Remind me at" a date and time. It never makes the task overdue and is cleared when the task is done.
- **Precision stays.** Date, time, recurrence and reminder controls keep their precision; the default editor shows the basic fields and the rest sit under "More options" (REP-057). Nothing here removes a control.
- **Not in this section.** Checklists that mirror Google Calendar (8.1 stays read-only) and grocery prices or shopping workflows.

### 3.6 Goals and milestones

Use `Goal → Milestones → Tasks`. A goal describes an outcome; a milestone is a meaningful checkpoint; a task is actionable work. A task may link to a goal/milestone but remains a normal PLAN task.

Goal fields: title, optional description, optional target date, status, optional milestones, linked tasks. Milestone fields: title, optional target date, status, linked tasks, completion date. A goal completes when all its milestones are done (a milestone is ticked by the user); linked tasks being complete does not complete it. Avoid arbitrary numerical progress unless the user defines a measurable target.

## 4. Task system

### 4.1 Core task model

Audit and retain valid existing task behavior. The conceptual model should support stable ID, title, optional description/notes, creation/update timestamps, completion state/time, optional due date, optional scheduled date/time, optional estimated duration, optional list/project association, optional parent task, optional recurrence definition, optional reminder configuration, optional goal/milestone association, optional archive state, and ordering metadata.

Keep the default editor short; advanced fields are optional.

### 4.2 States

Minimum persisted states: open, completed, archived. Overdue is derived, not a persisted state. Avoid a large workflow state machine without a real use case.

### 4.3 Due date versus scheduled date

Due date means when the task should be completed. Scheduled date/time means when the user intends to work on it. A task may have either or both. Do not equate them automatically.

### 4.4 No priority field

A task has no priority field; urgency comes from its due and scheduled dates. Do not add an urgency system beside them.

### 4.5 Subtasks

Subtasks belong to one parent and can be completed independently. Parent completion behavior must be explicit. Do not automatically complete a parent when one subtask is completed. If all subtasks are complete, show derived progress without automatically completing the parent. One nesting level is sufficient initially.

### 4.6 Quick capture

Available from Today and other main PLAN destinations. Title entry is immediate; Enter/primary action saves. A new task is unfiled unless context makes a list or day obvious (inside a list it is filed in it; on Today it is planned for today). Date/list assignment may be secondary. Preserve typed content if the editor is accidentally dismissed. Do not open a full-screen editor for simple title-only capture unless platform constraints require it.

### 4.7 Dates

One set of day keys wherever a task or a Plan event gets a day (Move to, Deadline, quick add, a new or edited event): **Today, Tomorrow, This weekend** (the coming Saturday), **Next week** (the coming Monday), **Pick a day** (the shared month picker, `PixelDateField`) and, for a task's day or deadline, **No date**. A key that names the same day as an earlier one is left out (on a Friday, Tomorrow is the weekend), so two keys never light up together (`PlanDays`). A time is typed (`9`, `930`, `9pm`) or picked from four common times. Reminders: At the time, 10 min, 30 min, 1 hour, 2 hours, 1 day before, or **Other**, a typed length up to a week; three at most per task. Goals keep their own long horizons (In a month, In 3 months, In 6 months).

## 5. Recurring tasks

There is no separate PLAN Habits record type or primary Habits section. One recurring-task system supports calendar recurrence; it keeps the useful capabilities of the old `Recurrence` and `Habit` implementations. Frequency targets ("N a week/month") do not exist: an old save's target is converted to an ordinary recurring task on load (`GameStore.convertTargets`, plan-core `TargetConversion`), paying nothing. The `target` field and `plan_series` rows stay for old saves.

Examples:

| Intention | Representation |
|---|---|
| Take out trash every Tuesday | Tuesday calendar recurrence |
| Pay rent on the 30th | Monthly calendar recurrence |
| Read every day | Daily recurrence |
| Clean desk on weekdays | Weekday recurrence |

### 5.1 Recurrence modes

Calendar recurrence: daily, selected weekdays, weekly on selected days, monthly by date, monthly by weekday position if reliable, and yearly if required by existing use cases. Avoid custom recurrence expressions initially.

The task card's Repeat offers Never, Daily, Weekdays, Weekly, Monthly (on the start's day of the month) and Yearly. Every kind keeps an **Every** row: the common intervals as keys (the weekdays preset, 1 to 4 weeks, 1, 2, 3 or 6 months, 1 to 3 years) and **Other** for a typed interval (daily 1 to 365, weekly 1 to 52, monthly 1 to 24, yearly 1 to 10). Weekly keeps the **weekday keys** (several days, at least one stays chosen) and the card reads the repeat in words ("repeats every 2 weeks on Mon, Thu"). The same controls sit under the quick add's **More options**, so a repeat is configured before the task is made (owner, 27 November 2026). A repeat the keys cannot show (a monthly weekday position) is said in words. Moving one day of a repeating task moves that day only, and its When tab says so ("Moved from Tue 17 Nov: this one only.").

### 5.2 Occurrence model

Conceptually use a reusable `RecurringTask` definition and distinct `TaskOccurrence` instances or equivalent. Preserve a valid existing occurrence model where it already handles stable IDs, missed occurrences, and editing correctly.

Requirements:

- Completing one occurrence does not complete future occurrences.
- Editing recurrence does not rewrite completed history.
- Skipping an occurrence does not silently delete it.
- Rescheduling an occurrence does not unexpectedly shift the entire recurrence definition.
- Archive/delete preserves completed history according to retention policy.
- Generated occurrences are deterministic and idempotent.
- Restart/recalculation cannot create duplicate occurrences.

### 5.3 Forgiving progress

Streaks stay secondary and are derived from completion history. Missing a day must not produce punitive copy or erase prior progress. A streak is a display only: no reward, rarity, roll or chance may read it, and resetting it takes nothing away (reward contract, `../FEATURES/POKEMON_ECOSYSTEM.md` 10.0 rule 5). Do not show streaks on every card. Do not introduce streak freezes, decay, or expiring opportunities without separate approval. Default cards prioritize action and the current occurrence.

### 5.4 Completion and rewards

Every completed PLAN occurrence may produce the applicable PLAN reward event. Use stable event IDs and idempotent delivery. Undo/redo must respect payment policy. Preserve payment history. Do not reward editing, scheduling, viewing, or generating occurrences. Keep caps in the reward engine.

CARE routine completions remain CARE reward events, not PLAN task events.

### 5.5 Editing recurrence

Where safely supported, offer: this occurrence only; this and future occurrences; entire recurrence definition. If the current implementation cannot support all scopes safely, ship the subset it can support and document the limitation. Never silently rewrite historical completions.

## 6. Focus

> **First Pokémon** (`FEATURES/POKEMON_ECOSYSTEM.md` section 10.2.1): the first finished task gives one Common egg that hatches into Plan's party and leads it, once per save. It is not a task or focus reward and leaves no record of its own.

Focus is a core PLAN capability. Audit and reuse reliable existing timer and lifecycle behavior.

### 6.1 Session model

A session can be for **any open task**, not only Today's: the setup lists today's first, then a search over all open tasks; each row names the task's list, and the search can be narrowed to one list. The length row offers 5, 10, 15, 25, 45, 60 and 90 minute keys and a typed length; the last length and Deep Focus used are kept for next time. A session can also be started from an open task (More → **Focus on this**), which opens the setup with that task chosen (owner, 27 November 2026). When a session ends or is cut short, a **summary** shows the time focused (counting only time spent), whether it finished, the task and its list, and what it paid. An interrupted session (left, killed or restarted past the grace in 6.3) is still written to the history as not completed with its elapsed time, so nothing is lost.

Support start, pause, resume, complete, cancel, optional linked task, optional duration, and session history. A session can start without a task.

### 6.2 Timer reliability

Remain reliable through app backgrounding, screen lock, incoming calls, process death, and device restart where supported. Persist enough state to recover status and remaining time. Do not rely only on a continuously running UI coroutine. Derive elapsed/remaining time from stable timestamps and persisted pause intervals. Account for system time changes.

### 6.3 Interruption behavior

**Owner decision: focus is strict.** A focus session must hold: leaving the app for more than ten seconds, or the phone moving in Deep Focus, ends the session as not completed, and nothing is paid. The allowances are the existing ones: two emergency pauses a day of up to three minutes, a pause for a phone call, and Magic Guard. Gentle pause and resume belong to the Care calm break, not to focus. A session can still be ended by the player and the app can be returned to. Before starting, the Deep Focus **info key** states those rules once, without a wall of caption text (owner, 27 November 2026). A failed session is reported neutrally — what ended it and the time focused — never with blame; the Just 5 min retry and Magic Guard stay. When a session ends, publish the focus-state change so the shared reminder feature can re-evaluate eligible deferred requests under its policy.

### 6.4 History and rewards

Record start/end, planned duration, actual elapsed focus time, completion/cancellation state, and optional linked task. Do not require a note/rating after every session.

Focus rewards follow the existing progression design, based on qualifying focus time/session completion. Pause/resume events never pay. Cancelled sessions are not completed unless an explicit existing rule grants partial credit. Use stable IDs and idempotency. Avoid farming through many tiny sessions.

## 7. PLAN reminder requirements

Shared reminder behavior is specified exclusively in `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`. PLAN-specific requirements are:

- PLAN tasks, recurring-task occurrences, and supported PLAN events may request reminders.
- PLAN settings expose a PLAN-level reminder enable/disable preference and default timing where applicable; individual tasks/events may override defaults.
- PLAN provides the shared scheduler with a stable source reference, due/scheduled time, requested delivery time, and minimal notification presentation data.
- Notification actions for PLAN tasks/events route to the PLAN domain and validate the source record before acting.
- Completing, archiving, deleting, rescheduling, or disabling a PLAN source updates/cancels its reminder request through the shared contract.
- PLAN Focus exposes active-session state and expected end time through the shared focus-state interface; it does not implement its own cross-module reminder queue.
- PLAN must not implement its own scheduler, quiet-hours engine, notification permission handling, Android delivery, reboot recovery, or scheduling state.

## 8. Calendar integration

A week starts on **Monday** by default. Settings → General has a "Week starts on Monday" switch; turning it off starts weeks on Sunday, and the Calendar's month grid and the launcher widget's week row follow it (`AppData.Preferences.weekStart`).

### 8.1 External calendars

"Import" means choosing **which device calendars appear** in PLAN's Calendar; nothing is copied into PLAN and no task or PLAN event is made from an external event. The only thing ever written to a device calendar is PLAN's own events, and only under the opt-in two-way sync in 8.1A. PLAN tasks, PLAN events and external events each have their own look and label (not colour alone).

Device calendar integration is read-only for every event PokéLife did not make. PLAN may request permission, display external events, filter selected calendars, refresh data, and show provider details. It must not edit/delete/create external events, convert them automatically to PLAN tasks, or reward viewing/attending them. **Owner decision of 24 November 2026:** this reverses "read-only" for PLAN's own events through 8.1A, and (second decision, 24 November 2026) events the player makes in the ONE synced calendar are brought in as PLAN events (8.1A rule 3A); every other calendar and every event PokéLife did not make or bring in stays read-only, never becomes a task and never pays.

**Overlays are not device calendars (owner, 24 November 2026).** Entries from an in-app overlay (3.4) are not read from the Calendar Provider, are not listed in Settings → Calendars, need no permission and are never written to the phone.

### 8.1A Two-way sync of PLAN events (owner, 24 November 2026)

PLAN can keep its own events in step with ONE calendar on the phone through Android's Calendar Provider. No Google sign-in, no OAuth, no network code or tokens in the app; the phone's own Google account sync carries the calendar to Google. "Local-only, no accounts" stands.

1. **Opt-in.** Off until the player turns it on (Me > Settings > "Calendar sync"). `WRITE_CALENDAR` is asked only then (`READ_CALENDAR` as before). The player picks ONE writable calendar the phone already has (a calendar PokéLife could create itself would be local-only and never reach Google, so they pick a Google calendar, ideally one made for it).
2. **What syncs.** Only PLAN's own events: title, day, time, length, the days of an all-day event, one of the six repeats (daily, weekdays, weekly, every 2 weeks, monthly, yearly) with its end date and skipped days (RRULE and EXDATE). Colour goes out only (the provider may drop it). Companion, people, rewards and tasks never leave PokéLife; LINK dates are not sent.
3. **Only PokéLife's rows.** PokéLife keeps a link per event (PLAN series id to provider event id, plus a fingerprint of what both held when they last agreed) in the integration document `integrations` (no schema migration), and only linked rows are ever written, changed or deleted. Other events stay read-only as in 8.1 and never become tasks; an outside event never pays. Only the events rule 3A names are brought in.
3A. **Bringing in events the player makes there (owner, 24 November 2026, second decision).** Scope: ONLY the one calendar chosen for the sync; the other device calendars stay read-only overlays as in 8.1. Only events the player made there: the player is the organizer (an invite from someone else is NOT brought in and stays a read-only overlay), not deleted or cancelled, not a single edited occurrence of a series, and not a series that has edited occurrences (PLAN could not keep them, so those stay read-only overlays). Only what PLAN can hold: title, day, time, length, all-day span, one of the six repeats with its end and skipped days; a COUNT rule, a weekday set or an odd length stays in the calendar as a read-only overlay. A finished event older than 30 days is not brought in; at most 500 linked events together (rule 9). A brought-in event is a normal PLAN event from then on: linked (edits and deletes go both ways under rule 4), with no companion until the player gives it one; it pays only through a companion the player assigns, exactly like any PLAN event (the daily cap and 8.2's "only what the companion was there for" apply), and nothing else pays. A calendar-side import offers NO Undo (the calendar still holds the event, and an Undo that dropped the PLAN copy would read as a deletion there); Undo of other calendar-made changes keeps anything brought in or made since. The same event already on both sides (same content) is linked, never copied: this covers turning the sync off and on, picking the calendar again, and a crash between two writes. A switch under Calendar sync, "Bring in events I make there", is on by default (`importNew`); the settings note says "brought in N". An event that did not land in PLAN is never left linked (the next pass would read it as deleted here and delete it in the calendar).
4. **Conflicts.** Changed on one side only goes to the other. Changed on both, the calendar's version wins (the provider keeps no edit time; a change made here is written within seconds, so a true tie is rare) and the PLAN version is offered back through Undo. Deleted on one side is deleted on the other (a deletion made by the calendar is offered back through Undo and, restored, is written to the calendar again). Deleted in the calendar but edited here since: kept and written again. A row the calendar holds in a form PLAN cannot show (a COUNT, a weekday set, an odd length) keeps PLAN's version and writes it back.
5. **When it runs.** With the app in front: a few seconds after PLAN's events change, every 5 minutes while the app is open, and on a "Sync now" key. Never in the background with the app closed.
6. **Companion and rewards.** A change that comes from the calendar goes through the same companion rule as any edit (8.2: a new day, time, length or repeat restarts what the companion was there for) and pays nothing itself. Nothing here is a reward input (`RewardRules.VERSION` unchanged).
7. **No doubles.** The phone calendar's copies of PokéLife's events, and of events brought in (3A), are hidden in the PLAN calendar and the launcher so each event shows once.
8. **Turning off** asks "Keep the events there" or "Remove them" (removes only PokéLife's rows). Turning it back on for the same calendar resumes without copying twice; a different calendar starts fresh and leaves the old copies.
9. **Limits.** Events older than 30 days and finished are not sent on the first run; at most 500 linked events.

*As built (0.241.0, JVM fake provider only, not run on a device):* app-core `app.pokeplan.app.integrations` (`CalendarEventMapping`, `CalendarSync`, `PlanCalendarSync`, `CalendarWriter`), `GameStore.applyEventChanges`, androidApp `AndroidCalendars` (`CalendarWriter`, `CalendarWriteAccess`), composeApp `ui/integrations/CalendarSyncCard`. *Open:* the Calendar Provider's handling of EXDATE for all-day events, UNTIL forms and Google's sync of the written rows are written from the provider's documentation and untested on a device; importing is built in 0.242.0 (3A): `CalendarWriter.importable`, `LocalChange.Add`, `CalendarSync.run` (matching, then importing), `PlanCalendarSync.setImport`, `GameStore.applyEventChanges(add)` and `newEventId`, `AndroidCalendars.importable` (IS_ORGANIZER = 1, ORIGINAL_ID IS NULL, series with exceptions skipped, scan limit 1000); IS_ORGANIZER for events made in the Google Calendar app and the exception query are from the provider's documentation and tested only against a fake provider.

### 8.2 PLAN events

PLAN may create its own events with title, start/end, optional location/description/reminder, and recurrence only if implemented reliably. Events are not tasks and have no task completion state or task rewards.

**Event companion:** an event can name a **companion**, a Pokémon from the **PLAN party** (the lead or one of the five; no new Pokémon place, so POKEMON_ECOSYSTEM.md 2.2 stands). After an occurrence **ends**, its companion gains exp and may bring back an item, with no tap. Rules *(numbers are tune; economy.md has them)*:
- **Pays once per occurrence.** A repeating event keeps the dates it has paid; a skipped occurrence never pays.
- **Only what the companion was there for.** An occurrence pays only if it ends after the moment the companion was set, **or the event's day, time, length or repeat was last edited**. A companion added to a past event, an event made for a past time, and an event dragged into time that has passed, pay nothing.
- **Exp by length:** one Exp Candy's worth of exp for each 30 minutes of a timed event (a quarter hour counts as one, four at most for one event), a daily cap on all events together, and a small chance of one item from the ordinary drop table when it paid. **An all-day event never pays** (it has no length).
- **The companion must be in the PLAN party when the occurrence ends**, and be able to take exp (not an egg, not a Guardian, not at the top level); otherwise the occurrence pays nothing and is not owed later.
- **When the app was closed:** the next time it runs it pays occurrences that ended up to two days ago, once each.
- The event itself stays unrewarded for viewing, editing or attending; only the companion's exp is paid, and the event carries no figure into the reward log beyond its title.

### 8.3 Permissions and display

Calendar access is optional. Denial must not impair PLAN. Explain denial and provide settings route without repeated prompting. Distinguish PLAN tasks, PLAN events, external events, and linked CARE routines; do not rely on color alone.

## 9. Goals, projects, and milestones

Keep the hierarchy simple: goal describes outcome; project groups work; milestone marks checkpoint; task is actionable work. Not every goal needs a project or milestones.

Goals: ID, title, optional description, created date, optional target date, status (active/on hold/completed/archived), optional linked project, milestones, and tasks. A goal completes when all its milestones are done.

Milestones: title, optional target date, status, optional linked tasks, completion date. Milestones are not recurring tasks and do not generate *task* rewards. **Owner decision: the current payout stays**: a goal milestone reached pays a Super Candy, and a finished goal pays a bonus item (`Goal.kt`; `docs/design.md`).

### 9.1 Goal badges and the Hall of Fame (owner, 15 November 2026; REP-053 chunk 5)

**Display only: nothing here pays, and `RewardRules.VERSION` does not change.** The Super Candy for a milestone and the bonus item for a finished goal stay as they are (section 9).

- **Badge case.** Every goal has a case with one slot for each milestone (a goal with no milestones has one). A finished milestone fills its slot with a Gym badge picture in colour; an unfinished one shows the same picture as a flat shape. Unticking a milestone empties its slot again.
- **Which picture.** A goal is given one set of eight Gym badges by its id (Kanto, Johto, Hoenn, Sinnoh or Unova: `assets/badges` 1 to 8, 9 to 16, 17 to 24, 25 to 32, 33 to 40), so it never changes and the player chooses nothing. Milestone 1 takes the set's first badge, milestone 2 the second, and so on, repeating after eight (`GoalRules.badgeSet`, `badgeId`, `badgeCase`).
- **Hall of Fame.** A goal whose milestones are all done is listed under a "Hall of Fame" box at the foot of the Goals view, newest first, with its case, the day it was finished and the Plan party of that day (lead first, at most six: form, level and shiny, saved on the goal as `hallOfFameParty`; JSON in `plan_goal`, so no schema step). Finishing it again after adding a milestone and ticking it records the later day's party. Its milestones can still be unticked, which returns it to the open goals.
- **Where.** On each goal card and in the Goals view; there is no new menu card.
- **Not built:** a Trainer Card in Me (P16) that shows the badges, and a Hall of Fame room scene (P4). Both come after this.

Project progress derives only from relevant tasks/milestones. Archived tasks are not incomplete work; unrelated tasks do not affect progress. User may complete/on-hold a project independently of task count.

## 10. Visual presentation

PLAN follows the shared PMD dialogue-box visual system and reusable component rules in `../FEATURES/DESIGN_SYSTEM.md`. That document owns global tokens, component anatomy, spacing, borders, typography, accessibility, states, and interaction behavior. This section defines only PLAN-specific adaptations.

### 10.1 Task and planning surfaces

Task cards prioritize the completion control, title, relevant date/recurrence, then optional list context. Do not display every available field on every card. The check key is first on every row (a finished task's reopens it), the rank as a small tag after the title, the Schedule tab (Move to, the planned time, the deadline, the repeat row and reminders, unfolded; a quiet **Saved** confirms the editor's autosave), a goal on the More tab (one goal per task, no reward), Focus on this on the More tab, Reopen and Delete on a finished task. The title remains primary; metadata is concise and secondary: the **planned** day/time and the **deadline** are said apart when both exist ("planned 9:00, due 17:00"), and a chevron at the row's end shows the card opens (owner, 27 November 2026). Today, Upcoming Tasks, and Calendar should use shared dialogue-box surfaces without nesting containers or turning every row into a large card.

### 10.2 Focus presentation

Focus is a distinct activity within the shared visual language: a clear timer, one primary action at a time, minimal supporting information, and a restrained session-state indicator. Optional buddy/PMD artwork is appropriate when it adds context. Avoid competing rings, charts, counters, and decoration. The Deep Focus rules sit behind one info key beside the toggle, and a failed session is reported as a neutral fact, not a scolding.

### 10.3 PLAN empty states

Use concise next-action guidance: Tasks suggests capture; Today suggests capture or reviewing upcoming work; Focus offers starting with or without a task. Follow the shared design-system rules for all component states, light/dark themes, typography, spacing, touch targets, motion, and accessibility.

## 11. Data architecture and persistence

### 11.1 PLAN domains

Recommended logical repositories: `TaskRepository`, `RecurringTaskRepository`, `TaskOccurrenceRepository` (or equivalent), `ListRepository`, `ProjectRepository`, `GoalRepository`, `MilestoneRepository`, `PlanEventRepository`, `FocusSessionRepository`, and `PlanSettingsRepository`. They may share physical storage; logical ownership remains clear.

### 11.2 Habit/recurrence consolidation

A Habit is not a record. Old saves merged each habit once, paying nothing, into a recurring task (same id, creation day, completion days and last paid day `paidOn`; the originals stay in `legacyHabits` with the merge time `habitsMergedAtMillis`), and a frequency target then converts to an ordinary recurring task (section 5). Historical completion and reward data are never lost. One completion per day per series, and an edit applies to the whole definition (5.5).

### 11.3 CARE remains separate

Do not move CARE routines into PLAN storage. Preserve CARE routine IDs, schedule fields, completion dates, payment history, archive state, and save format/migration compatibility. Shared reminder metadata does not replace CARE's canonical records.

### 11.4 Migration requirements

Migrations are versioned, idempotent, recoverable where possible, tested against existing save shapes, and non-destructive. Preserve IDs (or a stable mapping), creation dates, completion history and paid/completion accounting; a migrated task must not generate rewards merely because of migration; mark a migration version only after successful persistence; keep a recovery strategy for an interrupted or failed migration. Document any legacy field that cannot be preserved before shipping; do not silently discard it.

## 12. Progression and privacy

### 12.1 Reward-bearing actions

Potential PLAN reward-bearing actions are completing a PLAN task occurrence, completing a qualifying focus session, completing a qualifying PLAN milestone only if explicitly supported by progression rules, and (owner, 20 November 2026) the end of a PLAN event occurrence that has a companion (8.2).

**Reward focus on a task:** each task has a reward focus chosen when it is made (quick add under "More options", or the task editor): **Exp** (the default, and exactly what a task paid before), **Egg** or **Items**. The focus trades the same share of a task's payout for another kind: Egg pays half the candies and doubles the Egg points; Items pays half the candies and doubles the item-drop chance *(tune; economy.md)*. Poké and trainer points never change. A Settings default picks the focus a new task starts with; a repeating task's copies keep its focus; the focus is read when the task is ticked, so changing it during the undo window does not change the payout.

Do not reward opening PLAN, viewing/editing/creating an empty task, moving tasks, generating occurrences, starting then immediately cancelling focus, viewing external events (a device calendar's events never pay and never take a companion), or completing CARE-owned routines.

### 12.2 One action, one reward owner

A real-world action has one canonical owner and one primary reward owner. CARE routine completion pays through CARE. PLAN recurring-task completion pays through PLAN. A BASE record linked to a PLAN task must not pay twice. A CARE record shown in PLAN remains a CARE action.

### 12.3 Minimal event contract

Progression events contain only stable event ID, source module, opaque source record reference, event type, timestamp, and necessary reward-eligibility metadata. Do not transmit task titles/notes, project names, goal descriptions, journal content, CARE routine titles, mood/health data, or external event descriptions. WORLD receives minimal activity events, not planning content.

### 12.4 Idempotency and non-punitive mechanics

Retries cannot pay twice. Undo/redo respects payment policy. Recurring occurrences cannot duplicate events. Migration cannot replay historical rewards. Do not introduce expiring opportunities, punitive streak resets, attendance pressure, login rewards, unfinished-task penalties, or artificial urgency.

## 13. Settings

PLAN has its own short settings page (the Sections key in the header → Settings), grouped:

- **Alerts:** repeat until answered and the focus alerts (halfway and end). The task-reminders switch and the quiet hours are in Me, Settings, Notifications, with every module's reminders switch; global quiet hours, notification permissions, delivery and focus deferral are managed by `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`.
- **Lists:** made, edited, archived and deleted here (the Tasks screen only filters by them).
- **Rewards:** the reward focus a new task starts with ("New tasks pay most in", 12.1).
- **Calendar:** which device calendars appear (8.1). **Calendar sync** (8.1A): off by default; switch, the one calendar to sync with, "Bring in events I make there" (on by default; the note says "brought in N"), "Sync now" and the keep-or-remove choice when turned off. Only PLAN's own and brought-in events are written.
- **Focus:** the usual length and Deep Focus are what the last session was started with (6.1); no separate default is asked for.
- **Week start** is a General setting (shared by every calendar in the app), not a PLAN one.
- **Appearance:** follow PokéLife global appearance/accessibility; no duplicate PLAN theme controls.

Do not duplicate shared reminder settings or behavior in PLAN.

## 14. Implementation priorities

The order and status are tracked in `../IMPLEMENTATION_BACKLOG.md`. Priorities: P0 architecture and reliability (ownership boundaries, unified recurring-task domain, shared reminder contract, migration and reward idempotency, task/recurrence/focus recovery tests); P1 core experience (Today, capture, Upcoming Tasks, lists, basic projects, calendar agenda, recurring tasks, focus timer and history, reminders, read-only calendar, reward events); P2 depth (goals and milestones, advanced projects, more recurrence editing, richer calendar, linked CARE agenda entries, focus statistics, widgets). Do not delay P1 because P2 is unfinished.

## 15. Audit and implementation safeguards

Before modifying code, inspect the current branch. Preserve and test: task creation/editing, completion/undo, due dates, recurrence generation and edits, missed occurrences, occurrence identity, completion and payment history; focus start/pause/resume, background and screen-off behaviour, process-death recovery, cancellation, completion, linked-task behaviour and reward idempotency; CARE routine history, once-per-day payment, the five-paid-routines-a-day limit, weekly rhythm milestone and notification actions; PLAN reminder cancellation, rescheduling, action routing, focus-state integration and stale records (shared reminders: `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`). Review screens for excessive captions, nested-card overload, dark-mode contrast, task/event distinctions and loading/empty/error states; a visual rewrite does not prove the logic correct.

## 16. Acceptance criteria

### Core planning

- Title-only capture works.
- Unfiled tasks are not forced into dates or lists.
- Today does not treat every open task as due today.
- Upcoming Tasks separates overdue/future commitments.
- Tasks support completion, undo, archive, and restore per retention policy.
- Lists/projects do not duplicate task records.

### Recurring tasks

- Recurrence uses one PLAN system.
- No separate Habit record type or primary Habits destination remains.
- Existing Habit data migrates without losing history.
- Existing task recurrence behavior is preserved.
- Recurrence edits do not corrupt historical occurrences.
- Generation is deterministic/idempotent.
- Rewards are not duplicated.
- Missed occurrences do not erase progress or create punitive behavior.

### CARE ownership

- CARE remains canonical owner of self-care routines.
- CARE routines are not duplicated as PLAN tasks.
- CARE completion invokes CARE reward rules.
- CARE trends/rhythm use canonical history.
- CARE local-only behavior remains intact.

### Reminder integration

- PLAN task/event requests use the shared reminder feature contract.
- PLAN retains its module-level reminder preference and per-record choices.
- PLAN actions route to PLAN and validate current source state.
- PLAN does not maintain a parallel scheduler, quiet-hours policy, or notification-delivery implementation.
- Cross-module acceptance criteria are owned by `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`.

### Focus

- Sessions recover through common Android interruptions.
- Focus can start without a linked task.
- Timer derives from persisted timestamps, not only a running UI timer.
- History is accurate; rewards are idempotent.

### Privacy/progression

- Only minimal activity events reach WORLD.
- Private task content is excluded.
- Each real-world action has one primary reward owner.
- Migration does not reward historical actions again.

### Visual quality

- Shared PMD dialogue-box language is consistent.
- Interface remains minimal/readable.
- Dark mode is legible throughout.
- Empty/error states are concise and useful.
- Normal actions do not require understanding internal architecture.

## 17. Explicit non-goals

Not in initial scope unless separately approved: team/collaborative project management, task assignment to others, complex dependencies, Gantt/resource management, standalone Habit module, second CARE routine database, two-way Google Calendar sync, automatic conversion of external events to tasks, AI-generated plans/automatic rescheduling, complex custom recurrence expressions, heavy analytics dashboards, social competition/leaderboards, punitive streak mechanics, or mandatory goal/project links.

## 18. Documentation alignment

The Product Bible assigns planning recurrence to PLAN and self-care routines to CARE. Shared reminders are owned by `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`, CARE reminder preferences by `CARE.md`, PLAN task/event reminder requests by this file.

## 19. Architecture summary

Shared persistence, record identity, transactions, reward idempotency, backup/restore and migration follow `../ARCHITECTURE/DATA_AND_MIGRATIONS.md`. PLAN owns its task and planning records; linked records stay authoritative in their owning modules. WORLD receives minimal, idempotent activity events and owns no planning or self-care records.

## Shared Pokémon ecosystem contract

PLAN acquisition uses **Focus Eggs** and **Task Eggs**. PLAN owns the eligibility rules for completed focus sessions and task occurrences, including the approved duration/rarity bands, Deep Focus treatment, task-tier meter, carry-over and buddy-at-session-start context. The shared lifecycle for Egg records, hatching, canonical ownership, Pokédex updates, Bag/Poké and reward idempotency is defined in `../FEATURES/POKEMON_ECOSYSTEM.md`. PLAN's party is independent of all other module parties; it stores references to shared owned Pokémon IDs.

---

## Testing reference

PLAN task/focus/goal domain rules, recurrence, reminders, linked task ownership, interruption recovery and reward idempotency must follow [`../ARCHITECTURE/TEST_STRATEGY.md`](../ARCHITECTURE/TEST_STRATEGY.md). Test task completion from every supported entry point against the same authoritative owner.
