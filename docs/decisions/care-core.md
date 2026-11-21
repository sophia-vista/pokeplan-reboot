# Decision records: care-core

Moved here from code comments in the comment audit (21 November 2026). Each entry is a decision, rule or constraint that
the code alone does not show. Entries are grouped by source file; a line saying "Documented in" means the owning doc
already holds the rule and the entry only points to it.

## `care-core/build.gradle.kts`

### care-core module boundary

Documented in `CLAUDE.md Layout`.

## `care-core/src/commonMain/kotlin/app/pokeplan/care/Care.kt`

### Routine has no streaks

Documented in `docs/pokelife/MODULES/CARE.md 3.1 Today and routines`.

### Routine.icon

`Routine.icon` is the id of a real item icon in `assets/items` (the art rule allows real game assets only), or null for none; it is not a free emoji or a path.

### Routine.paidOn

`Routine.paidOn` is the last day a completion paid, kept apart from `completions` so undoing and ticking a routine again the same day never pays twice.

### Routine.skipped

Documented in `docs/pokelife/MODULES/CARE.md 3.1 Today and routines`.

### Routine.paysOn

`Routine.paysOn` allows a payment only for the first completion of a day, even after an undo; the daily cap of paid routines is checked separately (`CareRules.ROUTINES_PAID_PER_DAY`).

### Emotion rating scale

Diary card feelings are rated 0 (not at all) to 5 (very strongly), the scale of a DBT diary card; the same 0 to 5 scale rates urges.

### DiaryCard

Documented in `docs/pokelife/MODULES/CARE.md 3.4 Privacy boundary`.

### DiaryCard.mood

`DiaryCard.mood` is a one-tap 'how is today' from 1 (rough) to 5 (great), set without filling in the card, null when not given. Like the rest of the card it is private and never earns.

### JournalEntry earns nothing

Documented in `docs/pokelife/MODULES/CARE.md 3.4 Privacy boundary`.

### CareData.visitorSeed

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

### CareData.visitorsDay and visitors

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

### CareData.dismissed

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

### CareData.visit

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

### CareData.met

`CareData.met` holds the species of every visitor the player has cared for, whether or not it became a friend; it is the 'met' album and is separate from `friends`.

### CareData.friends

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

### CareData.reunion

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

### ROUTINES_PAID_PER_DAY

Documented in `docs/economy.md Care: routine done`.

### Calm break length

Documented in `docs/economy.md Care: calm break`.

## `care-core/src/commonMain/kotlin/app/pokeplan/care/CarePlay.kt`

### Care Play and reunions

Documented in `docs/pokelife/MODULES/CARE.md 4. Care Play`.

### PlayReaction

Documented in `docs/pokelife/MODULES/CARE.md 4.3 Individual preferences`.

### Friend

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

### Reunion

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

### PAID_PER_POKEMON_PER_DAY

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

### CarePlay.preferences

Documented in `docs/pokelife/MODULES/CARE.md 4.3 Individual preferences`.

### CarePlay.suggestion

Documented in `docs/pokelife/MODULES/CARE.md 4.2 Entry and activity discovery`.

### Reunions.DAILY_PERCENT

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

### Reunions.MIN_DAYS_BETWEEN_VISITS

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

### Reunions.roll

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

## `care-core/src/commonMain/kotlin/app/pokeplan/care/CareTrends.kt`

### CareTrends

Documented in `docs/pokelife/MODULES/CARE.md 3.1 Today and routines`.

### FeelingWeek averages

A `FeelingWeek` averages each feeling only over the diary cards that rated it, so an unrated feeling has no average rather than a 0.

## `care-core/src/commonMain/kotlin/app/pokeplan/care/Skills.kt`

### SkillGroup DBT frame

Documented in `docs/pokelife/MODULES/CARE.md 3.2 Calm`.

### Skill.calmBreak

Documented in `docs/pokelife/MODULES/CARE.md 3.2 Calm`.

## `care-core/src/commonMain/kotlin/app/pokeplan/care/Visitors.kt`

### Visitor progress

Documented in `docs/pokelife/MODULES/CARE.md 6.3 Befriending and relationship progress`.

### Visitor.id

`Visitor.id` is built from the device seed, the day the visitor arrived and its place in that day's arrivals (`v<seed>-<day>-<n>`), so it is stable for as long as the visitor stays. A visitor from an old save without one is given `old-<species>-<day>` when loaded.

### Visitor.points legacy

`Visitor.points` holds the warm days of the old befriending system (four made a friend). It is read only from old saves and moved into `progress` once by `Visitors.migrated` (each day worth `LEGACY_DAY_PROGRESS`), then set to 0, so no visitor loses what it had.

### Visitor.favorite

Documented in `docs/pokelife/MODULES/CARE.md 6.3 Befriending and relationship progress`.

### Visitor.berryBonus

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

### Visitor.restUntilMillis

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

### CareAction

`CareAction` names the ways to care for a visitor after Chapman's five love languages. The evidence that matching a person's language improves a relationship is weak (Impett and colleagues), so every action warms a visitor and the favourite only warms more; no action is a wrong choice.

### Bid

A `Bid` is a small moment a visitor offers during a visit, in Gottman's sense of a bid for connection; noticing it ('turning toward', `Visitors.notice`) warms the visitor by one.

### PetSpot

Where a visitor likes a gentle pat (`PetSpot`) is the same for a species every time (`Visitors.likedSpot`), so what the player learned stays learned.

### Reaction

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

### Visit

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

### Visit.spotFound

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

### BefriendResult.waiting

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

### MAX_VISITORS

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

### MAX_QUEUE

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

### MAX_DISMISSED

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

### FIRST_ARRIVALS

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

### CATCH_UP_DAYS

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

### FRIEND_PROGRESS

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

### LEGACY_DAY_PROGRESS

`Visitors.LEGACY_DAY_PROGRESS` (a quarter of `FRIEND_PROGRESS`) is what one warm day of the old four-day befriending system is worth, so a migrated visitor keeps its place towards becoming a friend.

### REST_MILLIS

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

### KINDS

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

### Visitors.LEAN_WEIGHT

Care done in the last seven days tilts the type pool of new visitors by `LEAN_WEIGHT` (1.5): a paid calm break favours Water and Fairy, a routine Normal, a paid skill practice Grass. The tilt is small and arrival frequency never depends on care.

### Visitors.arrivals

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

### Visitors.act bids

In a visit, an unnoticed `Bid` is let go without cost, and after every action but the last the visitor makes a new bid, chosen from the species, day and number of actions so it is the same for the same visitor and day.

### Visitors.act spot bonus

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.

### Visitors.likedBerry

The berry a species likes (`Visitors.likedBerry`, one of the five flavours) is the same every time, so what the player learned stays learned.

### BERRY_BONUS

Documented in `docs/pokelife/MODULES/CARE.md 6.8 Adopted numbers and adaptations`.
