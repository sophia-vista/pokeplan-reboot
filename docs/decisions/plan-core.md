# Decision records: plan-core

Moved here from code comments in the comment audit (21 November 2026). Each entry is a decision, rule or constraint that
the code alone does not show. Entries are grouped by source file; a line saying "Documented in" means the owning doc
already holds the rule and the entry only points to it.

## `plan-core/build.gradle.kts`

### simulateEconomy task

The `simulateEconomy` Gradle task in plan-core plays months of simulated players through the reward engine and writes `docs/economy-report.md`. The report is generated on demand and git-ignored, never committed.

## `plan-core/src/commonMain/kotlin/app/pokeplan/plan/CalendarEvents.kt`

### Device calendar events

Documented in `docs/design.md Planner defaults`.

### Private events show as Busy

Documented in `docs/design.md Planner defaults`.

## `plan-core/src/commonMain/kotlin/app/pokeplan/plan/DayTimeline.kt`

### Day view and tray

Documented in `docs/design.md Planner defaults`.

### busyMinutes

Documented in `docs/design.md Planner defaults`.

### Week view tray

Documented in `docs/design.md Planner defaults`.

### DayTimeline.build tray and hours

Documented in `docs/design.md Planner defaults`.

## `plan-core/src/commonMain/kotlin/app/pokeplan/plan/FocusSession.kt`

### Screen lock is not leaving

Documented in `docs/design.md Focus`.

### FocusSession lead and lure locked at start

A `FocusSession` locks `leadIdAtStart` (Plan's party lead at that moment) and `lure` when it starts, so perks use that lead even if the party changes later. The lead was saved as `buddyIdAtStart` by older builds, so the field keeps `@SerialName("buddyIdAtStart")` to read old saves. `apply` and `tick` are pure: `apply` takes an event and the time and returns the next session; `tick` only moves the clock (grace expiring, a long emergency pause ending, the timer finishing).

### Time away inside grace counts as focus

Documented in `docs/design.md Focus`.

### Resume from a pause never subtracts

When a pause ends, the time added to `pausedMillis` is `now - pause start` clamped at 0, so a clock set back by the player cannot shorten the pause time already counted.

### FocusSession.toEffortEvent perks

The reward event of a completed session must be applied to a player state whose Plan lead is `leadIdAtStart`, so the perks used are the ones locked in when the session started.

## `plan-core/src/commonMain/kotlin/app/pokeplan/plan/Frequency.kt`

### Frequency target conversion

Documented in `docs/pokelife/MODULES/PLAN.md 11.2`.

## `plan-core/src/commonMain/kotlin/app/pokeplan/plan/Goal.kt`

### Badge slot pictures

Documented in `docs/pokelife/MODULES/PLAN.md 9.1`.

### Goal.hallOfFameParty for old goals

A goal finished before 0.197.0 has an empty `hallOfFameParty`, because the Plan party was not recorded then; its Hall of Fame row shows no party.

### Milestone minimum age

Documented in `docs/economy.md 2. What each event pays`.

### Badge sets

Documented in `docs/pokelife/MODULES/PLAN.md 9.1`.

### Badge set fixed by goal id

Documented in `docs/pokelife/MODULES/PLAN.md 9.1`.

### GoalRules.reopenMilestone keeps rewarded

Un-ticking a milestone keeps its `rewarded` mark, so ticking it again never pays the Super Candy twice.

## `plan-core/src/commonMain/kotlin/app/pokeplan/plan/Habit.kt`

### Legacy Habit shape

Documented in `docs/pokelife/MODULES/PLAN.md 11.2`.

### Habit.paidOn

`paidOn` holds the last day a habit completion paid, kept apart from `completions`, so undoing and ticking again on the same day never pays twice.

### Habit streak for per-week and per-month targets

A daily or weekday habit counts days (with the free miss per week). A per-week or per-month habit counts successful weeks or months, and a period that is one completion short still counts, so one slip never breaks the streak. The current period is ignored until it is over.

## `plan-core/src/commonMain/kotlin/app/pokeplan/plan/Insights.kt`

### Insights

Documented in `docs/design.md Growth: goals, habits, insights`.

## `plan-core/src/commonMain/kotlin/app/pokeplan/plan/Nudges.kt`

### Daily mini-quests and reroll

Documented in `docs/economy.md 2. What each event pays`.

## `plan-core/src/commonMain/kotlin/app/pokeplan/plan/PlanCalendar.kt`

### PlanCalendar.update on a repeating event

Changing an occurrence of a repeating event changes the whole series. Moving the occurrence's day moves every occurrence, together with the series' end and skipped days, by the same number of days. A blank title keeps the old one, and moving an all-day event's first day moves its last day with it.

### Moved event keeps what it paid

When an event is moved, its `paid` dates are shifted by the same number of days, so dragging a paid event to a later day does not pay it again.

### Moving an occurrence makes it a one-off

Documented in `docs/design.md Planner defaults`.

### Changing how a later occurrence repeats

Documented in `docs/design.md Planner defaults`.

## `plan-core/src/commonMain/kotlin/app/pokeplan/plan/PlanDays.kt`

### Day keys

Documented in `docs/pokelife/MODULES/PLAN.md 4.7`.

## `plan-core/src/commonMain/kotlin/app/pokeplan/plan/PlanEvents.kt`

### PlanEvent

Documented in `docs/design.md Planner defaults`.

### Event companion

Documented in `docs/pokelife/MODULES/PLAN.md 8.2`.

### companionSince

Documented in `docs/pokelife/MODULES/PLAN.md 8.2`.

### PlanEvent.paid

Documented in `docs/pokelife/MODULES/PLAN.md 8.2`.

### Occurrences are copies of the series

Documented in `docs/design.md Planner defaults`.

### PlanEvent.OWN_CALENDAR

PokéPlan's own events carry the calendar id -1 on the timeline, because device calendar ids are never negative.

### EventRepeat anchoring

Each `EventRepeat` rule follows the event's first day: Weekly on its weekday, Monthly on its date (the last day in shorter months), Yearly on its day and month. An event never repeats more often than daily.

## `plan-core/src/commonMain/kotlin/app/pokeplan/plan/QuickAdd.kt`

### QuickAdd grammar

Quick add turns a line like `Call mum tomorrow 5pm #Social !B` into task fields. `!A` to `!E` is the difficulty rank and `#Groceries` a list name. Dates: today, tomorrow, tonight, weekdays (`friday`, `next mon`: the next one after today), `in 3 days`, `in 2 weeks`, `2026-11-01`, `oct 25`, `25 oct`. Times: `5pm`, `5:30pm`, `17:30`, optionally after `at`. Durations: `30m`, `45 min`, `2h`, `1h30`, `for 90 minutes`. `by` or `due` before a date or time makes it the deadline instead of the planned day. Everything else is the title.

### QuickAdd longest estimate

Quick add reads estimates up to one day (1440 minutes). A longer or zero amount stays in the title as typed.

## `plan-core/src/commonMain/kotlin/app/pokeplan/plan/Recurrence.kt`

### Repeat.Weekly with no days

A `Repeat.Weekly` with empty `days` means the weekday of the start date.

### MonthlyRule.DayOfMonth in short months

A `MonthlyRule.DayOfMonth` rule falls on the last day of the month in months that are shorter, and is never skipped.

### Recurrence dates are wall-clock

`Recurrence` dates are wall-clock, so the time of day stays the same across daylight saving. Skipped dates are not generated but still count towards `RepeatEnd.After`.

### Moved recurring instance keeps its id

A recurring instance the player moved to another day is still the instance of its original date: its id (`<series>-<date>`) says so, and no second task may take that id. `RecurringTasks.instancesToCreate` therefore checks existing ids as well as planned dates.

### RecurringTasks.momentOf in a DST gap

The moment of a wall-clock time inside a daylight-saving gap is the time after the clock jumps forward past it.

## `plan-core/src/commonMain/kotlin/app/pokeplan/plan/Streak.kt`

### Forgiving streaks

Documented in `docs/design.md Retention`.

### Rest days capped per month

Rest days are capped at 14 per month (tune) so they stay a pause and not a way to hold a streak forever.

### Streak day rolls over at 4am

Documented in `docs/design.md Defaults (proposal, change if you disagree)`.

### Welcome-back gift

Documented in `docs/design.md Notifications`.

## `plan-core/src/commonMain/kotlin/app/pokeplan/plan/Task.kt`

### Linked task ownership

Documented in `docs/pokelife/PRODUCT_BIBLE.md Cross-module ownership rules`.

### Wall-clock task times

Documented in `docs/design.md Planner defaults`.

### Reopening never pays twice

Documented in `docs/design.md Planner defaults`.

### Linked task never pays from PLAN

Documented in `docs/pokelife/PRODUCT_BIBLE.md Cross-module ownership rules`.

### Nudge

Documented in `docs/pokelife/MODULES/PLAN.md 3.5A`.

### Task reward focus

Documented in `docs/economy.md 2. What each event pays`.

## `plan-core/src/commonMain/kotlin/app/pokeplan/plan/TaskList.kt`

### Lists

Documented in `docs/design.md Planner defaults`.

### List kinds

Documented in `docs/pokelife/MODULES/PLAN.md 3.5A`.

### List kind changes presentation only

Documented in `docs/pokelife/MODULES/PLAN.md 3.5A`.

## `plan-core/src/commonMain/kotlin/app/pokeplan/plan/TaskRules.kt`

### Task rules pointer

Documented in `docs/design.md Tasks`.

### Undo window

Documented in `docs/design.md Tasks`.

### Deleted tasks restore window

Documented in `docs/design.md Planner defaults`.

### Recurring instance pays from its own day

Documented in `docs/economy.md 3. Caps`.

### One completion one reward owner

Documented in `docs/pokelife/PRODUCT_BIBLE.md Cross-module ownership rules`.

### Reopening never pays twice

Documented in `docs/design.md Planner defaults`.

### Won't do

Documented in `docs/design.md Planner defaults`.

## `plan-core/src/commonMain/kotlin/app/pokeplan/plan/TaskViews.kt`

### Wall-clock time zones

Documented in `docs/design.md Planner defaults`.

### Inbox

Documented in `docs/pokelife/MODULES/PLAN.md 3.2`.

### TaskViews.outstanding grouping

`TaskViews.outstanding` puts tasks in no list, or in a list that is no longer in the given list order, into the Inbox group first, then the lists in order. The filter narrows by search, list and difficulty but its status is ignored (only open tasks); within a group the filter order applies (by day, undated last) and empty groups are left out.

### Nudge on Today

Documented in `docs/pokelife/MODULES/PLAN.md 3.5A`.
