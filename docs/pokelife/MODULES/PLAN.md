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
- Today, Upcoming, and planning views.
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

**Layout:** Modules → Plan opens a page with no on-page menu: the lead (112 dp), the overview (today's open tasks, five at most, each with a check key, and an "All N tasks" button; one title when nothing is planned), the action items (quick add, the task to do next with **Focus** and **Pick for me**), and the party last. Every Plan place (**Tasks**, **Calendar**, **Focus** on the phone, **Goals**, **Settings**) is in the shared Sections key in the header (`SectionsKey`). `PlanSection` is TASKS, CALENDAR, FOCUS, GOALS, SETTINGS.

Primary destinations (the owner uses only these three, so nothing else is a destination):

1. **Tasks** — one workspace. It opens on **Today**. Four views as pills under the quick add box: **Today**, **Upcoming**, **All** (every open task grouped by list) and **Done** (finished and won't-do tasks, latest first). The lists are managed in **Plan settings** (new list, edit, archive, delete), and the **Filter** under the search box on All and Done narrows to one list (a Checklist or Tracker list then keeps its finished items on view). A task in no list shows under "No list" in All. The quick add field reads "Add a task (date, #list)"; inside a chosen list a new task is filed in it, and on Today it is planned for today.
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

### 3.3 Upcoming

Present future commitments chronologically with date grouping. Include overdue items, recurring-task occurrences, PLAN-created events, and read-only external calendar events when enabled. Default to a list; calendar grid is a secondary visualization. Keep filters minimal: all, tasks, events, and optionally a selected list.

### 3.4 Calendar

Calendar is a planning surface, not a second task database. Support month/week views and compact agenda. Display PLAN events, PLAN task due dates/scheduled times, read-only external events, and optionally linked CARE routines.

A task is actionable and has completion state; an event represents something happening at a time; an external event is read-only. Do not create a task merely because an external event appears.

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

The task card's Repeat offers Never, Daily, Weekdays, Weekly, Monthly (on the start's day of the month) and Yearly; Weekly then takes **every 1 to 4 weeks** and the **weekdays** (at least one stays chosen), and the card reads it in words ("repeats every 2 weeks on Mon, Thu"). A repeat made elsewhere that the keys cannot show (every 3 days) is said in words. Moving one day of a repeating task moves that day only, and its When tab says so ("Moved from Tue 17 Nov: this one only.").

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

A session can be for **any open task**, not only Today's: the setup lists today's first, then a search over all open tasks. The last length and Deep Focus used are kept for next time. When a session ends or is cut short, a **summary** shows the time focused (counting only time spent), whether it finished, the task, and what it paid. An interrupted session (left, killed or restarted past the grace in 6.3) is still written to the history as not completed with its elapsed time, so nothing is lost.

Support start, pause, resume, complete, cancel, optional linked task, optional duration, and session history. A session can start without a task.

### 6.2 Timer reliability

Remain reliable through app backgrounding, screen lock, incoming calls, process death, and device restart where supported. Persist enough state to recover status and remaining time. Do not rely only on a continuously running UI coroutine. Derive elapsed/remaining time from stable timestamps and persisted pause intervals. Account for system time changes.

### 6.3 Interruption behavior

**Owner decision: focus is strict.** A focus session must hold: leaving the app for more than ten seconds, or the phone moving in Deep Focus, ends the session as not completed, and nothing is paid. The allowances are the existing ones: two emergency pauses a day of up to three minutes, a pause for a phone call, and Magic Guard. Gentle pause and resume belong to the Care calm break, not to focus. A session can still be ended by the player and the app can be returned to. When a session ends, publish the focus-state change so the shared reminder feature can re-evaluate eligible deferred requests under its policy.

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

"Import" means choosing **which device calendars appear** in PLAN's Calendar; nothing is copied into PLAN, no task or PLAN event is made from an external event, and nothing is written back. PLAN tasks, PLAN events and external events each have their own look and label (not colour alone).

Google/device calendar integration is read-only initially. PLAN may request permission, display external events, filter selected calendars, refresh data, and show provider details. It must not edit/delete/create external events, convert them automatically to PLAN tasks, or reward viewing/attending them.

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

Task cards prioritize the completion control, title, relevant date/recurrence, then optional list context. Do not display every available field on every card. The check key is first on every row (a finished task's reopens it), the rank as a small tag after the title, the Schedule tab (Move to, the planned time, the repeat row and reminders, unfolded), a goal on the More tab (one goal per task, no reward), Reopen and Delete on a finished task. The title remains primary; metadata is concise and secondary. Today, Upcoming, and Calendar should use shared dialogue-box surfaces without nesting containers or turning every row into a large card.

### 10.2 Focus presentation

Focus is a distinct activity within the shared visual language: a clear timer, one primary action at a time, minimal supporting information, and a restrained session-state indicator. Optional buddy/PMD artwork is appropriate when it adds context. Avoid competing rings, charts, counters, and decoration.

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
- **Calendar:** which device calendars appear (8.1). Nothing is imported or written back.
- **Focus:** the usual length and Deep Focus are what the last session was started with (6.1); no separate default is asked for.
- **Week start** is a General setting (shared by every calendar in the app), not a PLAN one.
- **Appearance:** follow PokéLife global appearance/accessibility; no duplicate PLAN theme controls.

Do not duplicate shared reminder settings or behavior in PLAN.

## 14. Implementation priorities

The order and status are tracked in `../IMPLEMENTATION_BACKLOG.md`. Priorities: P0 architecture and reliability (ownership boundaries, unified recurring-task domain, shared reminder contract, migration and reward idempotency, task/recurrence/focus recovery tests); P1 core experience (Today, capture, Upcoming, lists, basic projects, calendar agenda, recurring tasks, focus timer and history, reminders, read-only calendar, reward events); P2 depth (goals and milestones, advanced projects, more recurrence editing, richer calendar, linked CARE agenda entries, focus statistics, widgets). Do not delay P1 because P2 is unfinished.

## 15. Audit and implementation safeguards

Before modifying code, inspect the current branch. Preserve and test: task creation/editing, completion/undo, due dates, recurrence generation and edits, missed occurrences, occurrence identity, completion and payment history; focus start/pause/resume, background and screen-off behaviour, process-death recovery, cancellation, completion, linked-task behaviour and reward idempotency; CARE routine history, once-per-day payment, the five-paid-routines-a-day limit, weekly rhythm milestone and notification actions; PLAN reminder cancellation, rescheduling, action routing, focus-state integration and stale records (shared reminders: `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`). Review screens for excessive captions, nested-card overload, dark-mode contrast, task/event distinctions and loading/empty/error states; a visual rewrite does not prove the logic correct.

## 16. Acceptance criteria

### Core planning

- Title-only capture works.
- Unfiled tasks are not forced into dates or lists.
- Today does not treat every open task as due today.
- Upcoming separates overdue/future commitments.
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
