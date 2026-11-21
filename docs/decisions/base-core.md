# Decision records: base-core

Moved here from code comments in the comment audit (21 November 2026). Each entry is a decision, rule or constraint that
the code alone does not show. Entries are grouped by source file; a line saying "Documented in" means the owning doc
already holds the rule and the entry only points to it.

## `base-core/build.gradle.kts`

### base-core boundary

Documented in `docs/pokelife/MODULES/BASE.md 2`.

## `base-core/src/commonMain/kotlin/app/pokeplan/base/BaseData.kt`

### BaseData records

Documented in `docs/pokelife/MODULES/BASE.md 5.1`.

### Recurrence

`Recurrence` repeats a chore or maintenance schedule from its first date, every N units, with no cron-like forms. A month shorter than the first date's day uses its last day. `weekdays` are ISO numbers (1 Monday to 7 Sunday), only meaningful for WEEKLY; empty means the weekday of the first date.

### BaseArea

Documented in `docs/pokelife/MODULES/BASE.md 4.1`.

### Chore

Documented in `docs/pokelife/MODULES/BASE.md 4.2`.

### Chore.remind

Documented in `docs/pokelife/MODULES/BASE.md 6`.

### ChoreEntry

A `ChoreEntry` records what the user did about one occurrence (done, skipped or put off to `deferTo`). The occurrence is named by `dueDay` (for an unscheduled chore, the day it was done), so one occurrence has at most one entry and undo is removing it. `paid` is set once Habitat Discovery has paid.

### ChoreView

`occurrence` is the occurrence that matters now and the key an entry is saved under; `dueDay` is when it is due and differs from it only for an occurrence that was put off. `entry` is what was done about it, if anything.

### Supply quantity

Documented in `docs/pokelife/MODULES/BASE.md 4.3`.

### Supply.remind

Documented in `docs/pokelife/MODULES/BASE.md 6`.

### StockAdjustment

Documented in `docs/pokelife/MODULES/BASE.md 4.3`.

### RestockEntry

Documented in `docs/pokelife/MODULES/BASE.md 4.3`.

### Asset

Documented in `docs/pokelife/MODULES/BASE.md 8`.

### MaintenanceSchedule

Documented in `docs/pokelife/MODULES/BASE.md 4.5`.

### MaintenanceSchedule.firstDue

`firstDue` is the date a maintenance schedule started and the anchor a calendar schedule counts from, so a month-end date never drifts after a clamped month.

### MaintenanceRecord.cost

Documented in `docs/pokelife/MODULES/BASE.md 4.5`.

### Warranty

Documented in `docs/pokelife/MODULES/BASE.md 4.6`.

### BaseSettings.progression

Documented in `docs/pokelife/MODULES/BASE.md 16`.

### BaseSettings.hidden

`hidden` holds the overview sections the user folded away, by key: "chores", "supplies" and "maintenance".

### BaseSettings.areasSeeded

`areasSeeded` records that the starting areas were offered once, so deleting them all does not bring them back.

### BaseSettings.reminders

Documented in `docs/pokelife/MODULES/BASE.md 6`.

### BaseSettings.reminderNames

Documented in `docs/pokelife/MODULES/BASE.md 6`.

### BaseData.room

Documented in `docs/pokelife/MODULES/BASE.md 9.5.6`.

### BaseKinds

Each kind of private BASE record has its own table family whose names start with `home_` (`home_area`, ...). The names are stable and keep the old module name after BASE was renamed from HOME, so existing saves and backups still read.

## `base-core/src/commonMain/kotlin/app/pokeplan/base/BaseRules.kt`

### Schedules

Documented in `docs/pokelife/MODULES/BASE.md 4.2`.

### Quantity

Documented in `docs/pokelife/MODULES/BASE.md 16`.

### Overview

Documented in `docs/pokelife/MODULES/BASE.md 3.1`.

### BaseRules principles

Documented in `docs/pokelife/MODULES/BASE.md 4.2`.

### ADJUSTMENTS_KEPT

Documented in `docs/pokelife/MODULES/BASE.md 16`.

### CHORES_PAID_PER_DAY

Documented in `docs/economy.md Base row, 5 chores and 2 pieces of maintenance a day`.

### ChoreView resolution

Documented in `docs/pokelife/MODULES/BASE.md 4.2`.

### BaseRules.view

The private `view` overload takes a chore's own entries already picked out, so listing many chores reads the history once instead of once per chore.

### BaseRules.history

Documented in `docs/pokelife/MODULES/BASE.md 4.2`.

### BaseRules.isLow

Documented in `docs/pokelife/MODULES/BASE.md 4.3`.

### BaseRules.restockList

Documented in `docs/pokelife/MODULES/BASE.md 4.3`.

### BaseRules.advance

Documented in `docs/pokelife/MODULES/BASE.md 4.5`.

### BaseRules.chorePays

`chorePays` only asks whether Habitat Discovery is on. The daily share (`CHORES_PAID_PER_DAY`) is counted once, by the game (`GameStore.BASE_CAPS`, which reads the constant), because BASE's history is trimmed and a deleted chore takes its entries with it, so BASE cannot count the day reliably.

### BaseRules.ENTRY_KEEP_DAYS

Chore history entries older than 400 days (`ENTRY_KEEP_DAYS`) are dropped when a new one is added (BASE.md 4.2). The history screen shows only the newest 60.

### BaseRules.search

Documented in `docs/pokelife/MODULES/BASE.md 16`.

## `base-core/src/commonMain/kotlin/app/pokeplan/base/Room.kt`

### Decoration

Documented in `docs/pokelife/MODULES/BASE.md 9.5.2`.

### CHORE_POINTS

Documented in `docs/pokelife/MODULES/BASE.md 9.5.4`.

### METER_SIZE

Documented in `docs/pokelife/MODULES/BASE.md 9.5.4`.

### Decorations.STARTERS

Documented in `docs/pokelife/MODULES/BASE.md 9.5.4`.

### Decorations.pick

Documented in `docs/pokelife/MODULES/BASE.md 9.5.4`.

### Decorations.pick hash

The decoration an upkeep payment finds is chosen with an FNV-1a hash over the event id's characters, not a platform hash, so the same event names the same decoration on every platform and on replay.

### RoomGrid

Documented in `docs/pokelife/MODULES/BASE.md 9.5.3`.

### Placement

Documented in `docs/pokelife/MODULES/BASE.md 9.5.4`.

### RoomLook

Documented in `docs/pokelife/MODULES/BASE.md 9.5.3`.

### RoomLook.forType

Documented in `docs/pokelife/MODULES/BASE.md 9.5.3`.

### Room

Documented in `docs/pokelife/MODULES/BASE.md 9.5.5`.

### Room.tidy

`Room.tidy` drops placements the player no longer owns, cells off the floor, and a second use of the same cell or decoration (a damaged or hand-edited save); the earlier record of each wins.

### Room.standingCells

Documented in `docs/pokelife/MODULES/BASE.md 9.5.3`.

## `base-core/src/commonTest/kotlin/app/pokeplan/base/BaseRulesTest.kt`

### Schedules property test

The schedule property test skips recurrences whose chosen weekdays leave out the anchor's own day, because the anchor is then not an occurrence; that case is covered by `anchorOutsideTheChosenWeekdaysIsNotAnOccurrence`.
