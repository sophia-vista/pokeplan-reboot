# Decision records: app-core

Moved here from code comments in the comment audit (21 November 2026). Each entry is a decision, rule or constraint that
the code alone does not show. Entries are grouped by source file; a line saying "Documented in" means the owning doc
already holds the rule and the entry only points to it.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/AppData.kt`

### AppData.habits

Documented in `docs/pokelife/MODULES/PLAN.md 11.2`.

### AppData.legacyHabits

Documented in `docs/pokelife/MODULES/PLAN.md 11.2`.

### AppData.activeDays

AppData.activeDays holds the days that keep the daily streak going: a day with a paid task or a finished focus session. `Streaks.daily` reads it together with rest days, the 4am day rollover and the perks' extra forgiven days.

### AppData.pickedTaskId

Documented in `docs/design.md ADHD-friendly design (lowkey)`.

### AppData.shinySpecies

Documented in `docs/design.md Duplicates → candy`.

### AppData.caughtForms

Documented in `docs/design.md Roster`.

### AppData.seenSpecies

AppData.seenSpecies records species the player has seen without necessarily catching them (a friend's buddy, a quest's Pokémon). Once seen, a species stays seen, like caught ones.

### AppData.shownCalendars

AppData.shownCalendars holds the ids of the phone's calendars shown on the day timeline. Calendar ids differ per device, so the field is device-specific and left out of the data export.

### AppData.pokeMigration

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 11.2`.

### AppData.placesVersion

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 2.2.3`.

### AppData.healPaid

Documented in `docs/economy.md 3. Caps`.

### AppData.outbreaks

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 10.0`.

### AppData.ledgerNotes

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 7.3.0`.

### AppData.decorations

Documented in `docs/pokelife/MODULES/BASE.md 9.5.6`.

### AppData.decorationStarters

Documented in `docs/pokelife/MODULES/BASE.md 9.5.4`.

### PokeMigration

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 11.2`.

### Tutorials

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 10.2.1`.

### GearTimer.endsAt

GearTimer.endsAt is the wall-clock time (epoch milliseconds) a running timer ends at; 0 means it is not running.

### GearTimer.pausedLeftMs

GearTimer.pausedLeftMs is the time left while paused, in milliseconds; -1 means the timer is not paused.

### GearStopwatch.laps

GearStopwatch.laps stores each lap as the total elapsed time at that moment, not the split since the previous lap.

### GearData.manualRates

GearData.manualRates are rates the player typed (a money changer's quote), by currency code, in units of that currency per one unit of the home currency.

### GearData.unitFavorites

GearData.unitFavorites stores starred unit pairs as `category:from>to`; unitDecimals is how many decimals results show.

### GearData.pickerWeights

An option of the Picker with no entry in GearData.pickerWeights weighs 1; history is kept only while pickerHistoryOn is set.

### GearData.clockZones

GearData.clockZones is empty until the World clock is first opened, which then shows a few default places. seaLevelHpa is the sea-level pressure in hPa the Barometer uses to estimate height.

### GearData.soundOffsetDb

GearData.soundOffsetDb is added to the level below full scale to estimate a sound level in decibels; a phone microphone is not calibrated, so the Sound meter is an estimate the player can adjust.

### GearRates

GearRates.perUsd gives units of each currency per one US dollar, normalised to the dollar whatever base the rates service used. fetchedAtMillis is when this device got them (0 for a cache from before it was kept). They are reference rates, not what a bank, card or money changer gives.

### Settings.sound

Settings.sound is a master switch kept from the old Sound toggle: when off it silences effects and cries, and moving a volume slider turns it back on.

### Settings.criesVolume

Settings.criesVolume is 0 to 100; -1 means cries follow effectsVolume, which is the value of every save from before the separate cries setting existed.

### Settings.appBackground

Settings.appBackground is "day" (the time-of-day gradient) or the name of a scene or dungeon town; a veil keeps text readable over a scene.

### Settings.quietHours

Documented in `docs/pokelife/MODULES/PLAN.md 13. Settings`.

### Settings.focusHalfway

Documented in `docs/design.md Ideas adopted from other apps`.

### Settings.streakReminder

Documented in `docs/design.md Notifications`.

### Settings.defaultReward

Documented in `docs/pokelife/MODULES/PLAN.md 12.1 Reward-bearing actions`.

### TaskSeries.firstTaskId

TaskSeries.firstTaskId is the plain task the player turned into a repeating one: it stays the task of its own day and is not an instance, so it keeps the minimum-age rule, but it still shows that it repeats.

### TaskSeries.of

Documented in `docs/pokelife/MODULES/PLAN.md 11.2`.

### VisitorOrigin

Documented in `docs/pokelife/MODULES/CARE.md 6.6`.

### FocusEnd.guarded

Documented in `docs/design.md Buddy and perks`.

### QuestState

Documented in `docs/design.md Legendaries and mythicals`.

### DexState.CAUGHT

Documented in `docs/design.md Duplicates → candy`.

### AppData.streakAt

AppData.streakAt is the single place the daily streak is computed, for the app and the evening reminder alike: the day turns over at Streaks.ROLLOVER_HOUR and the perks forgive extra missed days.

### StepWalk

StepWalk tracks a steps evolution from the phone's step counter (steps since it was switched on). A counter that goes down means the phone restarted; the walk then continues from the new count and nothing is lost.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/BuddyMood.kt`

### BuddyScene

Documented in `docs/design.md Look and feel`.

### BuddyScene.BASE

Documented in `docs/pokelife/MODULES/BASE.md 9.5.1`.

### ReactionIcon

Buddy reactions show one icon above the head, as the Mystery Dungeon games show a feeling: a status icon where the games have one and a real item icon or Pixelarticons glyph otherwise. `key` is the stable name a widget keeps between runs.

### BuddyEvent

The buddy answers events with one icon above its head, like a game emote, and never with sentences: no permanent speech bubble, no needs and no mood meter.

### BuddyReaction

A pet or a berry makes the buddy show one icon, chosen from how that Pokémon is: its bond style (from its nature) decides most of them, its type adds flavour (a flame for Fire, a bolt for Electric) and the hour of day sometimes adds a sleepy or bright one. Never words, and never anything that reads as the buddy being unhappy.

### BuddyEventNow

BuddyEventNow pairs a BuddyEvent with the time its reward was paid, so a screen can celebrate it once instead of every time it is opened.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/Counters.kt`

### PeriodCounter

A PeriodCounter is a tally in AppData.counters for one period, named by its first day (for example `mq-2026-10-30-walk`: family, day, optional detail). Setting it drops the family's keys for other periods so the map never grows. The key format is part of the save: keep it.

### PeriodCounter.isOtherPeriod

Counter families can share a name prefix (`mq-` and `mq-reroll-`), so a key belongs to a family only if a date follows the prefix.

### Counters.EVENT_CANDIES

The event-candies counter is keyed to the streak day the candies were paid on, not the day the event fell, so the daily cap is one live period.

### Counters.WELCOME

The welcome-back gift counter is keyed by the last active day before the absence, so the gift pays once per absence.

### Counters.MAGIC_GUARD

The Magic Guard counter is keyed by the first day of the week, so it pays once a week.

### Counters.TOWN_FINDS

The counter for today's finds on the launcher town's floor holds one bit per find to record which are collected.

### Counters.mint

Mint counters are keyed per period: the day for a reconciled account, the first day of the week for a review.

### Counters.SAFARI

Documented in `docs/pokelife/MODULES/LINK.md 9.5`.

### Counters.HOME_EGG_METER

Documented in `docs/economy.md What each event pays`.

### Counters.HOME_DECOR_METER

Documented in `docs/pokelife/MODULES/BASE.md 9.5.4`.

### egg meters

Documented in `docs/economy.md What each event pays`.

### egg meters

Documented in `docs/economy.md What each event pays`.

### egg meters

Documented in `docs/economy.md What each event pays`.

### egg meters

Documented in `docs/economy.md What each event pays`.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/CrashLedger.kt`

### CrashLedger

A launcher that crashes at start is restarted by Android at once, so more than two crashes within a minute mean the normal screen cannot be trusted. The launcher then starts in safe mode (a plain list of installed apps and the crash log to copy) instead of looping. The logic is pure; the Android app keeps the encoded times in its preferences.

### CrashLog

A crash log holds the time, version and the exception types and where they were thrown, but never the exception message, because a message can carry what the player wrote or saved. This is the same rule as the rest of the app's logs.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/ExactInput.kt`

### ExactInput

Documented in `docs/pokelife/FEATURES/DESIGN_SYSTEM.md 6.4 Input fields and selectors`.

### ExactInput.parseMinuteOfDay

parseMinuteOfDay accepts `24:00` as 1440 only when allowEndOfDay is set, for the end of a block that runs to midnight.

### ExactInput.parseDurationSeconds

parseDurationSeconds reads a bare number in bareUnit (minutes for an estimate, seconds for a short timer). `1:30` is hours:minutes when bareUnit is minutes and minutes:seconds when it is seconds; `1:30:00` is always hours, minutes, seconds.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/FormDex.kt`

### FormDex

Documented in `docs/design.md Roster`.

### FormDex.caught

Saves from before the form dex know only the species. A species caught then, with no form on record and none owned now, counts as having its default form.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/GameStore.kt`

### GameStore.update

GameStore.update runs under SavedDocument.update, which holds the document's lock from reading the state before the change until the save, so two threads never start from the same state.

### GameStore.update

Documented in `docs/design.md Duplicates → candy`.

### GameStore.renameBaseLock

The module HOME was renamed BASE on 13 November 2026. A screen lock saved under the old name "HOME" is rewritten to "BASE" on every update, so players keep their lock after the rename; do not drop the HOME mapping while old saves can exist.

### GameStore.foldPoke

The Poké balance is `PlayerState.coins`, written by the ledger. World's rules hold no balance: what they earn arrives as `WorldData.pokeEarned`, which the store adds to the coins and clears in the same update, and the rules that spend are given the balance and charged by the store. `worldPay` reads pokeEarned itself in the same update for the reward log and the ledger note.

### GameStore.mergeHabits

Documented in `docs/pokelife/MODULES/PLAN.md 11.2`.

### GameStore.convertTargets

Documented in `docs/pokelife/MODULES/PLAN.md 11.2`.

### GameStore.migratePoke

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 11.2`.

### GameStore.migratePoke

World's old balance (`WorldData.legacyPoke`) is read once during the Poké merge and then set to 0 and never written again.

### GameStore.exportJson

The data export holds the player's own data, so it includes the Picker's list and what the Pokégear tools remember. It leaves out the calendars shown (device-specific) and the public exchange-rate cache.

### GameStore.startDecorations

Documented in `docs/pokelife/MODULES/BASE.md 9.5.4`.

### GameStore.migrateRoles

Saves from before Pokémon roles were split used the buddy as Plan's lead. The buddy stays the global buddy and also becomes the lead of Plan's party if it is in that party, so nothing changes for the player; from then on the two are chosen separately. Care's lead already was a party lead.

### GameStore.tutorialOffered

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 10.2.1`.

### GameStore.completeTutorial

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 10.2.1`.

### GameStore.firstRecordEgg

Documented in `docs/design.md Buddy and perks`.

### GameStore.start

Documented in `docs/design.md Buddy and perks`.

### GameStore.start

GameStore.start returns at once if the save is already onboarded, because a second tap on the last onboarding line must not start the game twice.

### GameStore.noted ledger note

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 7.3.0`.

### Streak day rolls over at 4am

Documented in `docs/design.md Defaults (proposal, change if you disagree)`.

### GameStore.care payment

GameStore.care pays a Care moment into the shared world, at most CARE_DAILY_CAP of each kind a day whatever the Care module asks (each module's share is capped, POKEMON_ECOSYSTEM.md). It returns null when nothing was paid (not started, or capped). The reward log records only 'Care' and the kind of moment, never what the routine was.

### Care payment ledger id

CareEvent carries no id of its own, so the ledger event id is built from the kind, the day and the count paid that day. The same count never repeats on a day because the daily tally that caps payments also numbers them.

### GameStore.heal payment

Documented in `docs/pokelife/MODULES/HEAL.md 12.4`.

### GameStore.mint payment

Documented in `docs/pokelife/MODULES/MINT.md 16.3`.

### Mint outbreak on a rare moment

Documented in `docs/pokelife/MODULES/MINT.md 16.2A`.

### GameStore.claimOutbreak

Documented in `docs/pokelife/MODULES/MINT.md 16.2A`.

### GameStore.base payment

Documented in `docs/pokelife/MODULES/BASE.md 9.2`.

### Decoration meter

Documented in `docs/pokelife/MODULES/BASE.md 9.5`.

### GameStore.link payment

Documented in `docs/pokelife/MODULES/LINK.md 9.3`.

### Converted follow-up is PLAN's to pay

Documented in `docs/pokelife/MODULES/LINK.md 2.4`.

### Friend Safari visit

Documented in `docs/pokelife/MODULES/LINK.md 9.5`.

### GameStore.befriend dice

GameStore.befriend rolls the visitor egg (egg or direct join, IVs) from the device's own random source rather than from the visitor's seed, and makes at most BEFRIEND_DAILY_CAP friends a day. It returns null when nothing was paid.

### GameStore.linkChore

Documented in `docs/pokelife/MODULES/BASE.md 2.1`.

### GameStore.planFollowUp

Documented in `docs/pokelife/MODULES/LINK.md 2.4`.

### GameStore.unlinkTask

Documented in `docs/pokelife/MODULES/BASE.md 2.1`.

### GameStore.syncLinks

GameStore.syncLinks brings every task linked to another module's record in line with that record: words, day and state follow the owner, and nothing is paid or recorded in PLAN. A repeating chore's task moves on to the next occurrence, and a chore that is gone takes its projection with it (restorable, never the chore's data). It runs when the owner's data changes and when the app opens, and writes nothing when nothing differs.

### GameStore.addList startFrom

Documented in `docs/pokelife/MODULES/PLAN.md 3.5A`.

### GameStore.resetChecklists

Documented in `docs/pokelife/MODULES/PLAN.md 3.5A`.

### GameStore.restartCompanions

Documented in `docs/pokelife/MODULES/PLAN.md 8.2`.

### Won't do on a linked task

For a task linked to another module's record, 'won't do' is the owner's skip: PLAN calls the link owner and records no state itself, and syncLinks then shows the result.

### Reopening a linked task

A PLAN task whose record another module owns is reopened by taking back the owner's completion through the link owner. PLAN does not change the task itself, and syncLinks then shows it open again.

### GameStore.completeTask on a linked task

Documented in `docs/pokelife/MODULES/BASE.md 2.1`.

### completeTask after a linked completion

After the link owner completes a linked task, its own save may already have shown the task done through syncLinks, so completeTask reads the task again from the store either way instead of trusting the copy it started with.

### Reopened subtask stays paid

Documented in `docs/economy.md Subtask done`.

### GameStore.makeRepeating

makeRepeating makes an open task repeat from its planned day (today when it has none). The task itself stays the one of its own day and pays as a one-off task with the minimum age; only the next instances are created ahead. On a task that already repeats, instances not yet touched are made again on the new schedule.

### Linked task cannot repeat

Documented in `docs/pokelife/MODULES/BASE.md 2.1`.

### GameStore.settleEvents

Documented in `docs/pokelife/MODULES/PLAN.md 8.2`.

### Event companion daily cap day

The event companions' daily candy cap counts on the day the occurrence is paid (the streak day), not the day of the event, so only one period is ever live and moving an event never resets it.

### GameStore.setEventCompanion

Documented in `docs/pokelife/MODULES/PLAN.md 8.2`.

### GameStore.linkTaskToGoal

Documented in `docs/pokelife/MODULES/PLAN.md 3.6`.

### Reopened milestone stays paid

Documented in `docs/pokelife/MODULES/PLAN.md 9`.

### GameStore.completeMilestone

Documented in `docs/pokelife/MODULES/PLAN.md 9`.

### Daily mini-quests

Documented in `docs/design.md Ideas adopted from other apps`.

### GameStore.completeMiniQuest

Documented in `docs/design.md Ideas adopted from other apps`.

### GameStore.startFocus double start

A second tap on Start before the screen has changed returns the session already running, rather than failing, so a double tap is harmless.

### Magic Guard

Documented in `docs/design.md Buddy and perks`.

### PlayerState.atStartOf

The perks that count for a focus session are those of Plan's lead when it started, kept by FocusSession.leadIdAtStart, and only while that Pokemon is still in Plan's party. Otherwise the current lead's perks apply.

### GameStore.setBuddy

Documented in `docs/design.md Guardians (legendaries and mythicals)`.

### GameStore.setInParty

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 2.2`.

### GameStore.sendToPc

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 2.2`.

### GameStore.slotGuardian

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 2.2`.

### GameStore.grantIn

grantIn is grantPokemon as one step of a larger update, so a 'grant once' flag and the Pokemon are written together. The widget and the app can both reach a grant, and whichever comes second finds the flag set and grants nothing (used by WorldActions for the Guardian and legends).

### Last party member rule is Plan's only

Documented in `docs/design.md Parties per module`.

### GameStore.sees Guardians screen

Feature visibility follows the trainer rank, except that the Guardians screen also opens as soon as the player owns one Guardian (a legendary or mythical from the World opening or a quest), whatever the rank. That way the Pokemon is listed somewhere reachable, its slot still waiting for the rank, and the PC does not hide it.

### GameStore.worldSummary

worldSummary holds what the World teams brought back since the player last dismissed the report. It is kept in memory only, because the reward log is the record, and is null when nothing came.

### Transfer gives up other places

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 2.2`.

### GameStore.moduleEvent

moduleEvent returns the moment a module's lead celebrates on that module's page, counting only what that module paid. The prefix is the module's log prefix; null means Plan, whose rewards carry no prefix, so anything another module, World or a mini-quest paid does not count.

### Hatch moment excludes World

A Pokemon granted by the World is not an egg hatching, so rewards logged by the World source are excluded from the buddy's hatch moments.

### GameStore.pet

Documented in `docs/economy.md Petting the buddy`.

### Petting ticks the mini-quest

Documented in `docs/design.md Ideas adopted from other apps`.

### GameStore.holdItem

Documented in `docs/design.md Guardians (legendaries and mythicals)`.

### GameStore.setMoves

Choosing moves is free and can change any time (POKEMON_ECOSYSTEM.md 8.3), but the World teams are settled first so that floors already climbed count with the moves they were climbed with.

### GameStore.WORLD_SOURCE

GameStore.WORLD_SOURCE ("World") is the reward-log source for what WORLD gives, such as a recruit or the Guardian. It is not an egg, so entries with this source get no hatch scene and no hatch reaction, and the buddy's event logic ignores any hatch in them.

### GameStore.BEFRIEND_DAILY_CAP

Documented in `docs/pokelife/MODULES/CARE.md 6.8`.

### GameStore.CARE_LOG_PREFIX

GameStore.CARE_LOG_PREFIX ("Care: ") starts every Care entry in the reward log. The entry names only the kind of moment, never what it was, so the log carries no routine, diary or mood text.

### GameStore.CARE_FRIEND_SOURCE

GameStore.CARE_FRIEND_SOURCE is the reward-log source of a befriended Care visitor. It carries the Care prefix, and unlike a Pokémon the World gives, its gift really is an egg, so it keeps the hatch scene and hatch reaction (MODULES/CARE.md section 6).

### GameStore.HEAL_DAILY_CAP

GameStore.HEAL_DAILY_CAP is the shared world's own per-day limit for each HealKind, built from the HealRules constants. It is a backstop to the same limits that HEAL enforces itself, so the world pays at most this many of a kind a day whatever the module asks. Values are *(tune)* starting values.

### GameStore.HEAL_LOG_PREFIX

GameStore.HEAL_LOG_PREFIX ("Heal: ") starts every HEAL entry in the reward log. The entry names only the kind of moment, never what it was, so the log carries no health detail.

### GameStore.MINT_CAPS

GameStore.MINT_CAPS is the shared world's own limit for each MintKind in its period, built from the MintRules constants: reconciled accounts per day, reviews per week. It is a backstop to the same limits that MINT enforces itself. Values are *(tune)* starting values.

### GameStore.MINT_LOG_PREFIX

GameStore.MINT_LOG_PREFIX ("Mint: ") starts every MINT entry in the reward log. The entry names only the kind of moment, never what it was, so the log carries no figure, account or merchant.

### GameStore.BASE_CAPS

GameStore.BASE_CAPS is the shared world's own per-day limit for each BaseKind, built from the BaseRules constants. It is a backstop to the same limits that BASE enforces itself. Values are *(tune)* starting values.

### GameStore.BASE_LOG_PREFIX

GameStore.BASE_LOG_PREFIX ("Base: ") starts every BASE entry in the reward log. The entry names only the kind of moment, never what it was, so the log carries no chore, area or supply.

### GameStore.OLD_BASE_LOG_PREFIX

GameStore.OLD_BASE_LOG_PREFIX ("Home: ") is what BASE reward-log entries started with before the module was renamed from Home (0.174.0). Entries with it in an old save are still recognised as BASE's, but new entries are never written with it.

### GameStore.LINK_CAPS

GameStore.LINK_CAPS is the shared world's own per-day limit for each LinkMoment, built from the LinkRules constants. It is a backstop to the same limit that LINK enforces itself. Values are *(tune)* starting values.

### GameStore.LINK_LOG_PREFIX

GameStore.LINK_LOG_PREFIX ("Link: ") starts every LINK entry in the reward log. The entry names only the kind of moment, never what it was, so the log carries no person, date or follow-up.

### GameStore.SAFARI_PER_DAY

Documented in `docs/pokelife/MODULES/LINK.md 9.5`.

### GameStore.LEDGER_NOTES_KEPT

GameStore.LEDGER_NOTES_KEPT (50) is how many LedgerNote records the save keeps. The database settles each note as it is written, so only the newest few are ever still waiting, which is why a small number is enough.

### GameStore.EXPORT_LEFT_OUT

Documented in `docs/pokelife/MODULES/GEAR.md 11`.

### GameStore.normalizeDocument

GameStore.normalizeDocument(text, nowMillis) returns a save as the app would hold it after loading it (every rule that tidies a save applied) and serialised again, or throws when it cannot be read. The live database applies it to a save it did not get from the app's own writes: the one it moves into the shared tables and a restored backup.

### GameStore.json classDiscriminator

GameStore.json uses the class discriminator "kind" because the rewards already have a field called "type". It is the same setting the reward engine uses over JSON.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/Integrations.kt`

### IntegrationClass

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 2`.

### IntegrationStatus

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 2`.

### IntegrationInfo

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 14`.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/Places.kt`

### Place: one place only

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 2.2`.

### Places.of priority

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 2.2.3`.

### Places.vacate

Places.vacate returns the save with a Pokémon out of every place it is in: no longer the buddy, in no party, not a Guardian, off its World team and not a Pokégear helper, so it ends in the PC. It does not re-derive party leads, so the caller must. A Pokémon whose World team is out on an expedition is left where it is.

### Places.guardiansToModules

Places.guardiansToModules migrates a save from before Guardians were per module (0.158.0): the old global slots, in order, become Plan's, Care's, Heal's, Mint's, Base's and Link's Guardian, which is the order the database already stored them in, so no table rows move, and the old list is emptied. A Pokémon that is not a Guardian species goes to the PC. Nothing is removed from the collection or paid, and running it again changes nothing.

### Places.resolve

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 2.2.3`.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/World.kt`

### World interface boundary

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 2.1`.

### World.care null result

`World.care` returns null, not an empty list, when nothing was paid (Care not started yet, or today's share is used up), so a caller can tell "paid nothing" from "paid a reward list".

### World.heal minimal event

Documented in `docs/pokelife/MODULES/HEAL.md 12.4`.

### World.mint minimal event

Documented in `docs/pokelife/MODULES/MINT.md 16.3`.

### World.base minimal event

Documented in `docs/pokelife/MODULES/BASE.md 9.4`.

### World.ownedDecorations

`World.ownedDecorations()` returns the Secret Base decorations the player owns, which is what Base's room may place (BASE.md 9.5). A store that does not know the game, such as a widget, answers with an empty set, so Base then places nothing.

### World.link minimal event

Documented in `docs/pokelife/MODULES/LINK.md 9.3`.

### World.safari event and null result

Documented in `docs/pokelife/MODULES/LINK.md 9.5`.

### World.followUpTask

Documented in `docs/pokelife/MODULES/LINK.md 2.4`.

### World.planFollowUp

Documented in `docs/pokelife/MODULES/LINK.md 2.4`.

### World.visitorLuck

`World.visitorLuck()` is Care's Visitor luck as a percentage (a Double such as 5.0 means 5%), summed from the lead and party of Care's party; it is 0.0 for a store without a game.

### FollowUpTask

Documented in `docs/pokelife/MODULES/LINK.md 2.4`.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/base/BaseReminders.kt`

### BaseReminderKind keys

The `key` of each `BaseReminderKind` (chore, maintenance and the other kinds) is stable and must never be renamed, because it is part of a reminder's id; a renamed key would orphan alarms already scheduled.

### BaseReminders

Documented in `docs/pokelife/MODULES/BASE.md 6`.

### BaseReminders low-stock reminder

A low supply asks once, at the first reminder hour after it became low or after its last quantity adjustment. Once that hour has passed it asks nothing more, so a supply that stays low never nags.

### BaseReminders.text

Documented in `docs/pokelife/MODULES/BASE.md 6`.

### BaseReminderAdapter

Documented in `docs/pokelife/MODULES/BASE.md 6`.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/base/BaseStore.kt`

### BaseStore reward boundary

Documented in `docs/pokelife/MODULES/BASE.md 9.4`.

### BaseStore.placeDecoration

Documented in `docs/pokelife/MODULES/BASE.md 9.5`.

### BaseStore.removeDecoration

Documented in `docs/pokelife/MODULES/BASE.md 9.5`.

### BaseStore.setAreaArchived

Documented in `docs/pokelife/MODULES/BASE.md 4.1`.

### BaseStore.deleteArea

Documented in `docs/pokelife/MODULES/BASE.md 4.1`.

### BaseStore.addChore

Documented in `docs/pokelife/MODULES/BASE.md 4.2`.

### BaseStore.updateChore

Documented in `docs/pokelife/MODULES/BASE.md 4.2`.

### BaseStore.complete

Documented in `docs/pokelife/MODULES/BASE.md 4.2`.

### BaseStore.undo

Documented in `docs/pokelife/MODULES/BASE.md 9.4`.

### BaseStore.updateSupply

`BaseStore.updateSupply` never changes a supply's quantity. The quantity changes only through `setQuantity` and `adjust`, each of which leaves an adjustment record, so every change of stock has a trace.

### BaseStore.setQuantity

Documented in `docs/pokelife/MODULES/BASE.md 4.3`.

### BaseStore.adjust

Documented in `docs/pokelife/MODULES/BASE.md 4.3`.

### BaseStore.addRestock

Documented in `docs/pokelife/MODULES/BASE.md 2.2`.

### BaseStore.restocked

Marking a restocking entry bought gives a supply its new quantity and removes the entry. A supply that is on the list only because it is low needs the new quantity, because nothing else takes it off the list; marking it bought without one is refused. It pays nothing.

### BaseStore.deleteAsset

Documented in `docs/pokelife/MODULES/BASE.md 4.4`.

### BaseStore.defaultFirstDue

A maintenance schedule added by name only gets its first due date as today plus one interval (a week for a one-time schedule), so a new schedule never starts due or overdue.

### BaseStore.updateSchedule

`MaintenanceSchedule.firstDue`, the date a schedule counts its repeats from, moves only when the next date or the kind of repeat changed. Renaming a schedule that started on the 31st therefore does not make it drift to the 28th. Records of work already done are never touched.

### BaseStore.deleteSchedule

Documented in `docs/pokelife/MODULES/BASE.md 4.5`.

### BaseStore.completeMaintenance

Documented in `docs/pokelife/MODULES/BASE.md 4.5`.

### BaseStore.logMaintenance

Documented in `docs/pokelife/MODULES/BASE.md 9.4`.

### BaseStore.updateMaintenanceRecord

Documented in `docs/pokelife/MODULES/BASE.md 9.4`.

### BaseStore.setProgression

Documented in `docs/pokelife/MODULES/BASE.md 9.1`.

### BaseStore.setReminders

Documented in `docs/pokelife/MODULES/BASE.md 6`.

### BaseStore.setReminderNames

Documented in `docs/pokelife/MODULES/BASE.md 6`.

### BaseStore.setChoreRemind

Turning on a chore's own reminder also turns on Base's master reminder switch (Base, Settings, Reminders) if it is off, so that "Remind me" is always offered and a chore's reminder never silently does nothing (REP-029 a).

### BaseStore.export

Documented in `docs/pokelife/MODULES/BASE.md 5.2`.

### BaseStore.erase

Documented in `docs/pokelife/MODULES/BASE.md 5.2`.

### BaseStore.eraseAll

Documented in `docs/pokelife/MODULES/BASE.md 5.2`.

### BaseExport

`BaseExport` is the one place that knows which fields of the Base document belong to each `BaseKinds` kind. A new field of `BaseData` must be added to its kind here, or it is missed by export and erase.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/care/CareReminders.kt`

### CareReminders

Care's routine reminders go off at each routine's own time, on the days it is meant for, while it is not yet done that day. They are gentle: no repeats and no streak talk. The user chose the time, so Plan's rest days do not move it. Delivery follows the shared `ReminderPolicy`: global quiet hours (no module may bypass them) and a focus session, which holds a reminder until it ends.

### CareReminders.DAYS_AHEAD

Care's reminder alarms are set only `DAYS_AHEAD` (2) days ahead; the app sets them again on every change and after each fired reminder, so the window keeps moving.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/care/CareStore.kt`

### CareStore reward boundary

Documented in `docs/pokelife/MODULES/CARE.md 4.5`.

### CareStore.completeRoutine

A routine ticked for today pays on the first tick of the day, up to `CareRules.ROUTINES_PAID_PER_DAY` routines a day; ticking again after an undo never pays twice. A routine may also be ticked for yesterday, but that only records the completion: nothing pays and no rhythm is counted for a day that is over. Any other day is ignored.

### CareStore.finishCalmBreak

Documented in `docs/economy.md Care: calm break`.

### CareStore.practiseSkill

Documented in `docs/economy.md Care: skill practised`.

### Care visitors arrival and queue

Documented in `docs/pokelife/MODULES/CARE.md 6.1`.

### CareStore.resting

Documented in `docs/economy.md Care: befriend a visitor`.

### CareStore.visitors

`CareStore.visitors()` adds the arrivals since the last visit day the first time it is called on a new day, up to the room there is. The same list holds on this device all day because the day's arrivals are saved the first time they are seen.

### CareStore.arrive

`CareStore.arrive()` brings the day's arrivals and a possible reunion once per day. Care's landing page and the Meadow call it when they open, so the landing page knows a friend is back too; the visitor getters still do the same on first use for callers that skip it.

### CareStore.startVisit

Documented in `docs/pokelife/MODULES/CARE.md 6.8`.

### CareStore.visitAct

Documented in `docs/pokelife/MODULES/CARE.md 6.8`.

### CareStore.visitNotice

Documented in `docs/pokelife/MODULES/CARE.md 6.8`.

### CareStore.leaveVisit

Documented in `docs/pokelife/MODULES/CARE.md 6.8`.

### Friends the world cannot pay today

Documented in `docs/pokelife/MODULES/CARE.md 6.8`.

### CareStore.leaveVisit friendship of a joined visitor

A visitor that joins directly starts with the high friendship that only a direct join gives (RewardEngine befriend friendship), while a visitor that leaves an egg starts low. `leaveVisit` checks the hatched Pokemon's friendship to tell the two outcomes apart for the Friend record.

### Dismissal

Documented in `docs/pokelife/MODULES/CARE.md 6.8`.

### Reunions (6.7)

Documented in `docs/pokelife/MODULES/CARE.md 6.8`.

### Reunions (6.7)

Documented in `docs/pokelife/MODULES/CARE.md 6.8`.

### Care Play suggestion never expires

Documented in `docs/pokelife/MODULES/CARE.md 6.8`.

### Care Play (4)

Documented in `docs/pokelife/MODULES/CARE.md 6.8`.

### CareStore.setMood

Today's one-tap mood (1 to 5) lives on today's diary card and keeps whatever else is on it; a card left with nothing is dropped. Setting or clearing a mood never pays, because Care journal and mood data stay private and never reach the world.

### CareStore.tidy save migration

Every Care save that comes in passes through `CareStore.tidy`: visitors of an old save get an id and their warm days become progress, so nothing earned is lost when the visitor model changed.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/gear/GearActions.kt`

### Gear helpers are cosmetic

Documented in `docs/pokelife/MODULES/GEAR.md 6`.

### Helper only by player choice

Documented in `docs/pokelife/MODULES/GEAR.md 6`.

### assignGearHelper one place only

Documented in `docs/pokelife/MODULES/GEAR.md 6`.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/gear/GearTimers.kt`

### GearTimers clock behaviour

A running Gear timer stores the wall-clock time it ends at, so it survives the app closing, the process dying and a reboot. If the phone clock is changed while it runs, the end moves with the clock (as the phone's own Clock app behaves), and a stopwatch never counts a clock set back as negative time. A Focus session is Plan state, not one of these timers, and a direct Gear timer pays nothing.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/heal/HealReminders.kt`

### HydrationReminder opaque ref

Documented in `docs/pokelife/MODULES/HEAL.md 22 Reminders`.

### HuaweiReadReminder

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 5.2.2`.

### HealReminders hydration policy

Documented in `docs/pokelife/MODULES/HEAL.md 22 Reminders`.

### HealReminderAdapter

Documented in `docs/pokelife/MODULES/HEAL.md 22 Reminders`.

### Reminder words are the same for everyone

Documented in `docs/pokelife/MODULES/HEAL.md 22 Reminders`.

### Reminder offers only a snooze

Documented in `docs/pokelife/MODULES/HEAL.md 22 Reminders`.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/heal/HealStore.kt`

### HealStore privacy boundary

Documented in `docs/pokelife/MODULES/HEAL.md 12.4`.

### HealStore.logActivity pays only when created now

Documented in `docs/pokelife/MODULES/HEAL.md 22 Rules in force`.

### Paid record saved in one write

Documented in `docs/pokelife/MODULES/HEAL.md 22 Rules in force`.

### Edit never pays

Documented in `docs/pokelife/MODULES/HEAL.md 22 Rules in force`.

### HealStore.logDrink first drink

Documented in `docs/pokelife/MODULES/HEAL.md 12.2`.

### Edit never pays

Documented in `docs/pokelife/MODULES/HEAL.md 22 Rules in force`.

### Only a finished night is a record

Documented in `docs/pokelife/MODULES/HEAL.md 22 Rules in force`.

### HealStore.importFromProvider

Documented in `docs/pokelife/MODULES/HEAL.md 22A`.

### HealStore.payStepsAndImports

Documented in `docs/economy.md Heal: steps`.

### Disconnect and delete

Documented in `docs/pokelife/MODULES/HEAL.md 22A`.

### Delete what an app added

Documented in `docs/pokelife/MODULES/HEAL.md 22A`.

### HealStore.export

`HealStore.export` returns the chosen kinds of Heal record (all by default) as readable JSON exactly as saved. Nothing of the game (Pokemon, Poke, Bag, rewards) is in it.

### HealStore.erase

`HealStore.erase(kind)` removes every record of that kind and nothing else: other kinds, the settings, Pokemon, Poke, the Bag and the other modules are not touched.

### HealStore.eraseAll

`HealStore.eraseAll` erases all of Heal's records but keeps its settings and what Field Research paid. `resetAll` is the separate full-device reset that also clears the settings.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/heal/HuaweiProbe.kt`

### Huawei provider sessions pay like manual ones

Documented in `docs/pokelife/MODULES/HEAL.md 22B`.

### HuaweiHealth.hideNumbers

`hideNumbers` replaces every digit with `#`: the layout of a Huawei Health capture without its values is the only form that leaves the phone's screen (shared as a sample); the numbers themselves never do.

### HuaweiSeen.sample

The shareable text of a capture hides every digit of what Huawei Health showed (texts, descriptions, titles, notification fields) with `HuaweiHealth.hideNumbers`; only view ids and the probe's own headings keep theirs. There is deliberately no variant with the numbers.

### HuaweiHome page has no times

Huawei Health's own Health page gives no times: sleep is a length and a get-up time, weight has no date, exercise is the day's distance. Each `HuaweiHome` field is null when absent (missing is never zero), and nothing in it becomes a Heal record by itself; the detail pages carry the times.

### Resting heart rate card not in capture

Documented in `docs/pokelife/MODULES/HEAL.md 22B`.

### HuaweiHome.stress

Only Huawei's own stress number from the summary is kept. The word beside it (such as "Normal") is Huawei's judgement and is deliberately not stored.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/heal/HuaweiRecords.kt`

### HuaweiRecords layout and rules

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 5.2.2`.

### Steps: largest count wins

Documented in `docs/pokelife/MODULES/HEAL.md 22B`.

### Vitals one per day

Documented in `docs/pokelife/MODULES/HEAL.md 22B`.

### Huawei sleep score timestamp

A sleep score read from the Sleep page is stamped with the capture time, or with noon of the night's own morning if the capture is later, so the reading lands on the night's day rather than the day it was captured.

### Huawei exercise history year

On the exercise history page the sticky header (listed last in the capture) names the month and year of the sessions shown above the first group header, so it supplies the year for those sessions.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/link/LinkReminders.kt`

### LinkReminderKind keys

The `key` strings of `LinkReminderKind` are stable because they are part of a reminder's id; renaming one would orphan reminders already scheduled.

### LinkReminders policy

Documented in `docs/pokelife/MODULES/LINK.md 5`.

### Reminder text is generic

Documented in `docs/pokelife/MODULES/LINK.md 5`.

### LinkReminderAdapter

Documented in `docs/pokelife/MODULES/LINK.md 5`.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/link/LinkStore.kt`

### LinkStore privacy boundary and eligible activity

Documented in `docs/pokelife/MODULES/LINK.md 9.2`.

### Safari pool

Documented in `docs/pokelife/MODULES/LINK.md 9.5`.

### Friend Safari visit

Documented in `docs/pokelife/MODULES/LINK.md 9.5`.

### Adding people never pays

Documented in `docs/pokelife/MODULES/LINK.md 9.2`.

### Me card

Documented in `docs/pokelife/MODULES/LINK.md 2.6`.

### Slambook profile

Documented in `docs/pokelife/MODULES/LINK.md 2.6`.

### Favourite is a shortcut, not a ranking

Documented in `docs/pokelife/MODULES/LINK.md 2.1`.

### LinkStore.setAvatar

A person's avatar is a Pokémon species id chosen by the player; 0 clears it. It is cosmetic and never pays.

### Possible duplicates are suggested, never merged

Documented in `docs/pokelife/MODULES/LINK.md 2.1`.

### Deleting a person asks about their dates and follow-ups

Documented in `docs/pokelife/MODULES/LINK.md 7`.

### Choosing a reminder is the opt-in

Documented in `docs/pokelife/MODULES/LINK.md 5`.

### Contacts import is one save and idempotent

Documented in `docs/pokelife/MODULES/LINK.md 15 As built: the manual slice`.

### LinkStore.importContacts counters

The counters are reset at the start of each run of the update block, because the document update can run its change again after a write clash and each run must count afresh.

### Quick-add follow-up needs only a title

Documented in `docs/pokelife/MODULES/LINK.md 2.4`.

### A follow-up pays once

Documented in `docs/pokelife/MODULES/LINK.md 2.4`.

### Converting a follow-up to a Plan task

Documented in `docs/pokelife/MODULES/LINK.md 2.4`.

### Deleted Plan task leaves the follow-up untouched

Documented in `docs/pokelife/MODULES/LINK.md 2.4`.

### Link follows its Plan task both ways, unpaid

Documented in `docs/pokelife/MODULES/LINK.md 15 As built: the manual slice`.

### Reopening never pays twice

Documented in `docs/pokelife/MODULES/LINK.md 2.4`.

### Due count leaves out follow-ups Plan owns

Documented in `docs/pokelife/MODULES/LINK.md 15 As built: the manual slice`.

### Postcard Trail off never touches records

Documented in `docs/pokelife/MODULES/LINK.md 9.1`.

### Reminders are all off until the user asks

Documented in `docs/pokelife/MODULES/LINK.md 5`.

### Master reminder switch

Documented in `docs/pokelife/MODULES/LINK.md 5`.

### Reminder names only after unlock

Documented in `docs/pokelife/MODULES/LINK.md 5`.

### Choosing a reminder turns the master switch on

Documented in `docs/pokelife/MODULES/LINK.md 5`.

### LinkStore.setFollowUpRemind

A follow-up handed to Plan cannot ask for a LINK reminder, because Plan is its only reminder source after conversion; this avoids two reminders for one task.

### Export of LINK records

Documented in `docs/pokelife/MODULES/LINK.md 7`.

### Erasing a kind keeps independent records

Documented in `docs/pokelife/MODULES/LINK.md 7`.

### LinkStore.eraseAll

Erasing all LINK records keeps its settings and what Postcard Trail already paid; only resetAll (the full-device reset) also clears settings.

### One real action, one completion

Documented in `docs/pokelife/MODULES/LINK.md 2.4`.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/mint/MintReminders.kt`

### Bill reminders

Documented in `docs/pokelife/MODULES/MINT.md 12`.

### MintReminders.upcoming lookahead

Bill reminders are planned over lead plus DAYS_AHEAD days from today, because a due day that far ahead can have its reminder fall inside the days the scheduler looks at.

### One reminder per monthly bill

Documented in `docs/pokelife/MODULES/MINT.md 5.6C`.

### Reminder refs are opaque

Documented in `docs/pokelife/MODULES/MINT.md 12`.

### MintReminders.stillWanted legacy refs

A reminder ref starting with 'plan:' was made for a single installment before bills became monthly (MINT.md 5.6C item 7). It is dropped so an alarm still scheduled with that ref is replaced by its monthly bill's reminder.

### Notification text is generic

Documented in `docs/pokelife/MODULES/MINT.md 12`.

### Lock-screen text is always generic

Documented in `docs/pokelife/MODULES/MINT.md 22 Implementation notes`.

### Reminder never marks a bill paid

Documented in `docs/pokelife/MODULES/MINT.md 12`.

### Reminder never marks a bill paid

Documented in `docs/pokelife/MODULES/MINT.md 12`.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/mint/MintStore.kt`

### MintStore.noonOf

A record made for a day other than today is stamped at noon on that day in the phone's zone, so it keeps its day when the zone changes later.

### Review streak adds shiny rolls

Documented in `docs/pokelife/MODULES/MINT.md 16.2A`.

### Logging a record pays a flat reward

Documented in `docs/pokelife/MODULES/MINT.md 16.2`.

### Hide values is one switch

Documented in `docs/pokelife/MODULES/MINT.md 3.3`.

### Receive-money QR image

Documented in `docs/pokelife/MODULES/MINT.md 4.2`.

### Account order shown as saved

Documented in `docs/pokelife/MODULES/MINT.md 4.3`.

### MintStore.updateAccount

An account's type may change only to another type on the same side (asset stays asset, liability stays liability). Its opening balance can be corrected because the balance is derived. Its currency changes only while it has no history, since every record and bill linked to it is in that currency (MINT.md 5.2).

### Archived and closed accounts

Documented in `docs/pokelife/MODULES/MINT.md 4.6`.

### MintStore.deleteAccount

An account with any history cannot be deleted and is archived instead, so no transaction, reconciliation or bill loses its account (MINT.md 4.6).

### MintStore.deleteCategory

A category used by a transaction, split, budget or bill cannot be deleted and is archived instead, so no history loses its category (MINT.md 6).

### MintStore.updateTransaction

An edit cannot change the account's currency under a record, and an ADJUSTMENT record keeps its source so a reconciliation difference stays an explicit adjustment (MINT.md 15.2 item 8).

### Transactions tied to an installment plan

A transaction that an installment plan counts on (its purchase or an allocated repayment) keeps its type, amount and accounts; the player changes the plan instead (MINT.md 5.6A).

### MintStore.updateTransaction on closed accounts

Editing a record on an account closed since stays allowed so its history remains readable; the check runs with every account treated as active, so only the amount rules apply.

### Interest estimates

Documented in `docs/pokelife/MODULES/MINT.md 4.3E`.

### Skipped interest estimate kept as voided

Documented in `docs/pokelife/MODULES/MINT.md 4.3E`.

### Statement import is one write, idempotent, pays nothing

Documented in `docs/pokelife/MODULES/MINT.md 22A. Statement import and provider status`.

### Removing an import removes only its records

Documented in `docs/pokelife/MODULES/MINT.md 22A. Statement import and provider status`.

### MintStore.setTransactionStatus

Transaction status meanings: excluded stays in history but is left out of spending; voided is kept but counted nowhere. A plan's purchase record cannot leave POSTED while the plan exists.

### MintStore.planOfPurchase

The purchase record of an installment plan cannot be deleted or voided while the plan exists (MINT.md 5.6A); the plan must be deleted first.

### Budget actuals are derived

Documented in `docs/pokelife/MODULES/MINT.md 15.2`.

### Twice-a-month bill days

Documented in `docs/pokelife/MODULES/MINT.md 8.3B`.

### Shared bills are display only

Documented in `docs/pokelife/MODULES/MINT.md 5.8A`.

### MintStore.updateBill

A bill's first due day and frequency change only while no occurrence has been settled, so no past occurrence is rewritten; with payments recorded they keep their old values (MINT.md 8.3).

### Settling a bill creates no transaction

Documented in `docs/pokelife/MODULES/MINT.md 8.2`.

### Paid, record it

Documented in `docs/pokelife/MODULES/MINT.md 8.2`.

### Pay-later purchase is one expense

Documented in `docs/pokelife/MODULES/MINT.md 5.6A`.

### Installments already owed

Documented in `docs/pokelife/MODULES/MINT.md 5.6A`.

### Repayment is a transfer

Documented in `docs/pokelife/MODULES/MINT.md 5.6A`.

### Paying a monthly bill

Documented in `docs/pokelife/MODULES/MINT.md 5.6C`.

### Repayments allocate in due-date order

Documented in `docs/pokelife/MODULES/MINT.md 5.6C`.

### Bill it records the fee

Documented in `docs/pokelife/MODULES/MINT.md 5.6A`.

### Late fee

Documented in `docs/pokelife/MODULES/MINT.md 5.6A`.

### Refund on a plan purchase

Documented in `docs/pokelife/MODULES/MINT.md 5.6A`.

### MintStore.updateInstallment

Only an installment that is unpaid and whose fee is not yet recorded can change its due day or fee, so nothing that already happened is rewritten; the installments stay in due-day order.

### Align installments to the account's due day

Documented in `docs/pokelife/MODULES/MINT.md 5.6C`.

### MintStore.deletePlan

Deleting an installment plan leaves every record it made (purchase, fees, repayments, refunds) in place, unlinked, as ordinary records.

### Reconciliation pays once per account a day

Documented in `docs/economy.md Mint: account reconciled`.

### Review pays once a week

Documented in `docs/economy.md Mint: weekly review finished`.

### MintStore.setProgression

Turning Ledger Research off stops new Mint rewards only; it never touches a record and never hides a feature.

### Overview window

Documented in `docs/pokelife/MODULES/MINT.md 3.2B`.

### Pay later counts in net position

Documented in `docs/pokelife/MODULES/MINT.md 3.2B`.

### Bill reminders off by default

Documented in `docs/pokelife/MODULES/MINT.md 12`.

### Access gate uses the phone's screen lock

Documented in `docs/pokelife/MODULES/MINT.md 13.2`.

### Built-in holidays count as days off

Documented in `docs/pokelife/MODULES/MINT.md 8.3B`.

### Built-in holidays count as days off

Documented in `docs/pokelife/MODULES/MINT.md 8.3B`.

### Player's own days off

Documented in `docs/pokelife/MODULES/MINT.md 8.3B`.

### MintStore.erase

Erasing the accounts kind also removes the transactions and reconciliations that point at those accounts. Nothing else is touched: other kinds, settings, Pokémon, Poké, the Bag and the other modules stay.

### MintStore.eraseAll

Erasing all Mint records keeps its settings and what Ledger Research already paid; categories come back as the starting set so a fresh start can record an expense at once.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/plan/Nudges.kt`

### Streak reminder and weekly recap rules

Documented in `docs/design.md Notifications`.

### Nudges.nextAt search window

The search over days 0 to 8 always finds the target time because nine consecutive days contain every weekday, so the error branch is unreachable.

### Streak at risk conditions

Documented in `docs/design.md Notifications`.

### Reminders never scold

Documented in `docs/design.md Notifications`.

### Weekly recap and empty week

Documented in `docs/design.md Notifications`.

### focusAlerts

Documented in `docs/design.md Icon timeline`.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/plan/TaskLinks.kt`

### LinkState

Documented in `docs/pokelife/MODULES/BASE.md 2.1`.

### LinkState.Open.occurrence

`LinkState.Open` carries the title and planned day to show plus an `occurrence` key. A repeating chore moves to the next occurrence when its turn comes, so a linked PLAN task follows the chore's current occurrence, not a fixed one.

### LinkOwner.undo

Documented in `docs/pokelife/MODULES/BASE.md 9.2 Qualifying activities`.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/quest/Quests.kt`

### Quest

Documented in `docs/design.md Legendaries and mythicals`.

### Quests

Documented in `docs/design.md Legendaries and mythicals`.

### RequirementKind.HABIT_DAYS counting

HABIT_DAYS counts the distinct days a repeating task was finished: the days recorded on a series (`Series.completions`, which also holds the days of frequency-target habits migrated from older saves) together with the completion days of its done task instances.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/reminders/ReminderAdapters.kt`

### ReminderActions

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.6.2`.

### ReminderActions snoozes

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.7`.

### ReminderSnooze

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.7`.

### Bill, household and connection reminder snooze

MINT, BASE and LINK reminders offer one snooze, until tomorrow morning, because a bill, household or connection reminder is not a minute-by-minute matter. Nothing overdue repeats by itself.

### ReminderRouter

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.6.2`.

### ReminderRouter snooze

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.7`.

### ReminderRouter.snoozed

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.7`.

### ReminderRouter.rebuild

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.8`.

### PlanReminderAdapter

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.2.2 Reminder request`.

### PlanReminderAdapter clock

`PlanReminderAdapter` makes the streak reminder and the Sunday recap only when it is given a clock. Without one (the default) only task reminders exist.

### PlanReminderAdapter.nudgeRequests

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.11.1 As built`.

### Streak and recap actions

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.11.1 As built`.

### CareReminderAdapter

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.6.2`.

### ReminderCoordinator

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.10 Implementation architecture`.

### ReminderCoordinator.schedule held requests

`schedule` returns everything the modules rebuild from their records plus the held requests (snoozes and repeats) whose source still wants them. A held request wins over a rebuilt one with the same id, so a snooze is not replaced by the original time.

### ReminderCoordinator.missed

`missed` returns the reminders that came due between the last plan and now without going off (phone off, or an alarm that came too late), rebuilt as planned at the earlier time with delivery rules applied. A reminder already delivered or no longer wanted is left out, only the latest per source and kind is kept (a daily routine missed three times is one reminder), and one the modules plan again under the same id is theirs. The caller holds each to go off at once and `onFire` checks it again.

### ReminderCoordinator.deliveredKey

A reminder counts as gone off by its id together with the time it was asked for (`id@atMillis`), so the next occurrence of a source that reuses one id is still owed.

### ReminderCoordinator.onFire

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.3`.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/reminders/ReminderPolicy.kt`

### ReminderModule

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.1 Purpose and boundaries`.

### ReminderModule.parse HOME alias

`ReminderModule.parse` maps the stored name "HOME" to BASE. A held reminder or an alarm written before the 13 November 2026 rename of HOME to BASE carries the old name and must still resolve.

### ReminderRequest

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.2.2 Reminder request`.

### ReminderRequest.deferrable

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.5 Focus deferral`.

### ReminderRequest.id

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.3`.

### FocusWindow

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.5 Focus deferral`.

### ReminderPolicy

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.4 Global quiet hours`.

### ReminderPolicy.MAX_PASSES

`deliverAt` repeats its focus-then-quiet-hours adjustment up to 4 times (`MAX_PASSES`): a focus end that lands inside quiet hours moves to their end, which a single focus window cannot cover again, so a few passes settle it and the loop cannot run forever.

### ReminderPolicy.deliverAt

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.5 Focus deferral`.

### ReminderPolicy.plan

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.3`.

### ReminderSourceAdapter

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.2.3 Source adapter`.

### ReminderSourceAdapter.requests

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.8`.

### ReminderSourceAdapter.isStillWanted

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 9.3`.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/storage/SavedDocument.kt`

### SavedDocument unreadable save

`SavedDocument` is the one place a store's save is read. A save that is unreadable (damaged, or holding something the rules reject) never blocks the app and is never overwritten: it is kept aside with `Storage.keepAside` and the document starts again as empty. A readable save goes through the store's own `tidy` check.

### SavedDocument.open backup fall-back

An unreadable save is kept aside and the storage's copy from before its last write is used instead, and written back so the next write keeps a good copy, not the damaged one. Only without a backup does the document start empty.

### SavedDocument.update

`update` builds the next document from the current one and saves it as one step under the store lock, so another thread's change waits and neither starts from a document the other already replaced. Listeners are told after the lock is let go.

### SavedDocument.sync

`sync` picks up what another store in the process wrote to the same document (a widget's tap, a notification's Done) and tells the listeners. It is cheap when nothing was written, so it can be called on every tick.

### SavedDocument catch-up

If another store wrote the shared document since this one last read or wrote it, this copy is old, so a change must start from the saved one rather than overwrite it.

### SavedDocument.current

The document every store in the process should see is what could not be saved yet (`SharedDocuments.unsaved`), else what is on the disk.

### SavedDocument.writeLocked

The memory follows the disk: when a save throws, the document and every listener stay as they were.

### SavedDocument.reload

`reload` picks up a change another process made to the save (an Android widget). Nothing is written back, so the app never overwrites a widget's tap with its older copy, and a save caught half-written changes nothing.

### SavedDocument.decode exception

A `SerializationException` is an `IllegalArgumentException`, and so is a failing `require` in a rule on a decoded value, so catching `IllegalArgumentException` covers both a damaged save and one the rules reject.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/storage/Storage.kt`

### Storage.saved full phone

`Storage.saved` says whether the text is now on the disk. It returns false when the storage could not keep it and chose not to throw (a full phone); the stores then carry on from memory and share that memory (`SharedDocuments`) until a save fits.

### Storage.keepAside

`keepAside` keeps a save that could not be read somewhere `save` will not overwrite, so it can still be recovered.

### Storage.loadBackup

`loadBackup` returns the save as it was before the last `save`, if the storage keeps one, to fall back on when `load` gives something unreadable.

### Storage.shareKey

`shareKey` names the document a storage holds, the same for every storage over that document in the process (the app's, a widget's, a notification's), so stores opened on the same key see each other's writes instead of saving an older copy over them. It is null for a storage nothing else opens.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/storage/StoreLock.kt`

### StoreLock

Every change to a store is made one at a time across all stores in the process: the app, a widget and a notification each open their own store on the same save, so a lock per store would not keep them apart. A store is changed from the UI thread, widgets, notification receivers and background work, and a change reads the document, builds the next and writes it, so two at once would start from the same document and the later write would silently drop the earlier. The lock is re-entrant so a change may call another store function, and changes are short.

### newStoreLock platforms

The platform lock is one re-entrant lock for the whole process on the JVM and Android, and none in the browser, where there is one thread.

### SharedDocuments.revision

`SharedDocuments` counts how many times each shared document (`Storage.shareKey`) was written by any store in the process. A store that last saw another number knows its copy is old. It is read and written only under the store lock.

### SharedDocuments.unsaved

`SharedDocuments.unsaved` holds the newest document of a key whose storage could not keep it (a full phone). Every store on the key reads it instead of the older one on the disk, so none writes the older one back over changes that live only in memory. The next save that fits clears it.

## `app-core/src/commonMain/kotlin/app/pokeplan/app/world/WorldActions.kt`

### worldSettle prunes transferred Pokémon

A Pokémon transferred away leaves its World team, and a team left empty comes home, so no team slot is held by a Pokémon that is gone.

### worldSettle clock rollback

Documented in `docs/pokelife/MODULES/WORLD.md 3.1`.

### worldSettle expedition event id

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 7.3.0`.

### World floor XP

Documented in `docs/pokelife/MODULES/WORLD.md 5.2`.

### worldGuardian

Documented in `docs/pokelife/MODULES/WORLD.md 14.6 Story, quests and Guardians`.

### worldGuardian single write

Setting the Guardian-granted flag and granting the Pokémon are one `update` that re-checks the flag under the store lock, so the widget and the app cannot both grant it.

### worldLegends

Documented in `docs/pokelife/MODULES/WORLD.md 14.6 Story, quests and Guardians`.

### worldLegends and Plan quests

Every legendary also has a quest in Plan's quest list (`Quests`). A Pokémon already owned from there is not given twice, and the World quest still counts as done so the layer's boost applies either way. Dialga is the exception: Primal Dialga (the home Guardian) is a form of its own, so owning it does not stand in for Sinnoh's Dialga.

### worldLegends single write

Each legend grant is one write that re-checks the quest under the store lock, like the Guardian grant.

### worldPay

`worldPay` applies a World change that pays as one write: the change, the Poké earned (folded into the balance by `update`), the staged items moved into the shared Bag, the reward-log line and a `LedgerNote` under the event id, so the database ledger records the event by its own stable id instead of a net change (DATA_AND_MIGRATIONS 7.3.0, WORLD.md 3.1 and 6). Entries of the same source within ten minutes share one log line, so a team climbing while the screen is open does not flood the log.

### worldAssign

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 2.2.2 Module parties and WORLD teams`.

### worldAssign vacates from the changing document

The Pokémon are vacated from the document being changed inside `update`, not from the copy checked before, because the save may have moved on in between (a widget, a notification).

### worldApplyPreset

Applying a preset puts on the team only the members of the preset that are still owned and either in the PC or already on this team. A Pokémon in a party, slotted as Guardian or helping a tool is not taken.

### worldQuickSend

Quick send takes the first team slot that is not out and gives it the best (up to four) of its own members and the idle Pokémon for that specific dungeon, so a type the dungeon favours counts for its 20% more (`WorldSim.bestTeam`) rather than levels alone. A Guardian candidate is never picked, the team keeps its members when they are already best, and it fails when every team is out, no one is free or the dungeon is not open.

### worldSceneDone

Documented in `docs/pokelife/MODULES/WORLD.md 4.1 Opening — Mythical Invitation`.

### Mew joins once

Documented in `docs/pokelife/MODULES/WORLD.md 4.1 Opening — Mythical Invitation`.

### worldMoment adoption

`worldMoment` first counts what an older save already reached as seen (`WorldMoments.adopted`), so an old save does not replay every rank or chapter moment it has already passed.

### worldRecruit single write

Taking a find off the list and granting its Pokémon are one write, so an interruption between them cannot leave a Pokémon both joined and still waiting.

### worldAcceptMission

Documented in `docs/pokelife/MODULES/WORLD.md 14.5 Missions (jobs)`.

### worldClaimMission event id

A job can come back on another day, so the day (in the player's time zone) is part of its ledger event id: `world:job:<id>:<day>`.

### worldBrew

Documented in `docs/pokelife/MODULES/WORLD.md 14.3 Skills, facilities and research`.

### worldSwap

Documented in `docs/pokelife/MODULES/WORLD.md 14.3 Skills, facilities and research`.

### World treasure Rare Candy chance

A silver treasure box gives a Rare Candy 15% of the time and a gold box 35%. The first guesses of 60% and 175% paid hundreds of Rare Candies a year in the World economy simulation. Numbers are starting values (tune).

## `app-core/src/commonMain/kotlin/app/pokeplan/app/world/WorldAlerts.kt`

### WorldAlerts

Documented in `docs/pokelife/MODULES/WORLD.md 14.5 Missions (jobs)`.

## `app-core/src/commonTest/kotlin/app/pokeplan/app/GoalHallOfFameTest.kt`

### Goal Hall of Fame party

Documented in `docs/pokelife/MODULES/PLAN.md 9.1`.

## `app-core/src/commonTest/kotlin/app/pokeplan/app/PlacesTest.kt`

### One Pokémon, one place

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 2.2 Pokémon roles`.

## `app-core/src/commonTest/kotlin/app/pokeplan/app/SaveCompatibilityTest.kt`

### Saved fields need defaults

Every field added to the save since the first release must have a default so a save from an older version still loads. `ALWAYS_THERE` lists only the fields that have always been there (`player`). Adding to it means a newer field lost its default, which breaks old saves.

### Region.PLAN stored name

The Plan region is still stored in saves under its first name, "HOME" (`Region.PLAN`'s serial name), so existing saves keep decoding after the renames.

## `app-core/src/commonTest/kotlin/app/pokeplan/app/TutorialTest.kt`

### Module tutorial Pokémon

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 10.2.1 A module's first Pokémon`.

## `app-core/src/commonTest/kotlin/app/pokeplan/app/base/BaseRemindersTest.kt`

### Base reminder planning window

Base reminders are only planned over the next few days (`BaseReminders.DAYS_AHEAD`, 4), so a reminder whose lead puts it further than that ahead (a month-long warranty lead) is not in the scheduled set yet; the maximum lead is clamped in `BaseStore`.

## `app-core/src/commonTest/kotlin/app/pokeplan/app/base/BaseRoomTest.kt`

### BaseRoom tests

Documented in `docs/pokelife/MODULES/BASE.md 9.5 The Secret Base room`.

## `app-core/src/commonTest/kotlin/app/pokeplan/app/care/CareExtrasTest.kt`

### Care reunions, Care Play, visitor inheritance

Documented in `docs/pokelife/MODULES/CARE.md 4. Care Play and 6. Visitor system`.

## `app-core/src/commonTest/kotlin/app/pokeplan/app/care/CareStoreTest.kt`

### Care warms only its own lead

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 2.2 Pokémon roles`.

## `app-core/src/commonTest/kotlin/app/pokeplan/app/heal/HealImportTest.kt`

### Health Connect import rules

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 5.1 Health Connect`.

## `app-core/src/commonTest/kotlin/app/pokeplan/app/heal/HealStepsAndVitalsTest.kt`

### Huawei steps and vitals records

Documented in `docs/pokelife/MODULES/HEAL.md 10. Integrations and data import`.

## `app-core/src/commonTest/kotlin/app/pokeplan/app/heal/HuaweiProbeTest.kt`

### Huawei Health page layout

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 5.2.2 Reading Huawei Health on the phone`.

## `app-core/src/commonTest/kotlin/app/pokeplan/app/heal/HuaweiRecordsTest.kt`

### Huawei Health pages as HEAL records

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 5.2.2 Reading Huawei Health on the phone`.

## `app-core/src/commonTest/kotlin/app/pokeplan/app/link/LinkSafariTest.kt`

### Friend Safari rules

Documented in `docs/pokelife/MODULES/LINK.md 9.5 Friend Safari`.

## `app-core/src/commonTest/kotlin/app/pokeplan/app/mint/MintFeesAndInterestTest.kt`

### Transfer fees and deposit interest

Documented in `docs/pokelife/MODULES/MINT.md 4.3E Interest a deposit account earns`.

## `app-core/src/commonTest/kotlin/app/pokeplan/app/mint/MintImportTest.kt`

### Statement import rules

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 7.2 Approved integration order`.

## `app-core/src/commonTest/kotlin/app/pokeplan/app/mint/MintLoggingRewardTest.kt`

### Mint logging reward is flat

Documented in `docs/pokelife/MODULES/MINT.md 16.2 Eligible activity families`.

## `app-core/src/commonTest/kotlin/app/pokeplan/app/mint/MintOutbreakTest.kt`

### Mint outbreak

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 10.0 Reward cadence and the loop of each module`.

## `app-core/src/commonTest/kotlin/app/pokeplan/app/mint/MintRemindersTest.kt`

### Bill reminders

Documented in `docs/pokelife/MODULES/MINT.md 12. Reminders`.

## `app-core/src/commonTest/kotlin/app/pokeplan/app/mint/MintSharedTest.kt`

### Shared bills

Documented in `docs/pokelife/MODULES/MINT.md 5.8A Shared bills`.

## `app-core/src/commonTest/kotlin/app/pokeplan/app/mint/MintStoreTest.kt`

### Test history must not pay

Documented in `docs/pokelife/MODULES/MINT.md 16.2 Eligible activity families`.

## `app-core/src/commonTest/kotlin/app/pokeplan/app/plan/EventCompanionTest.kt`

### Event companion exp

Documented in `docs/pokelife/MODULES/PLAN.md 8.2 PLAN events`.

### Starter is not in Plan's party

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 2.2 Pokémon roles`.

### Calendar snaps to 15 minutes

Documented in `docs/design.md Calendar`.

## `app-core/src/commonTest/kotlin/app/pokeplan/app/plan/PlanCareLedgerNotesTest.kt`

### Ledger notes under event ids

Documented in `docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md 7.3.0 Decision: the app keeps reading one AppData through the split`.

## `app-core/src/commonTest/kotlin/app/pokeplan/app/world/WorldLedgerNotesTest.kt`

### WORLD ledger event ids

Documented in `docs/pokelife/MODULES/WORLD.md 3.1 Expedition settlement and offline progress`.

## `app-core/src/commonTest/kotlin/app/pokeplan/app/world/WorldStoreTest.kt`

### Kanto legendary birds unlock

Documented in `docs/pokelife/MODULES/WORLD.md legendary table (Zapdos, Articuno, Moltres)`.

## `app-core/src/jvmMain/kotlin/app/pokeplan/app/storage/FileStorage.kt`

### FileStorage atomic write and .bak

FileStorage writes the save to a temporary file, syncs it to disk and replaces the save in one atomic step, so a reader such as a widget never sees half of it and a crash leaves either the old save or the new one; if the step fails the save throws and the file on disk is untouched, because the save is never written over in place. The save before the last write is kept as `<name>.bak`, which the document falls back on when the main file cannot be read.

### FileStorage.replace fallback

FileStorage moves a temporary file over the save with an atomic move; where the file system cannot do that atomically it falls back to the plainest move it has, which is still a replacement and never a write in place.

## `app-core/src/jvmTest/kotlin/app/pokeplan/app/SaveFixtureTest.kt`

### SaveFixtureTest 0.123.0 save fixtures

The real saves written by version 0.123.0 (REP-003) are committed under app-core/src/jvmTest/resources/fixtures/0.123.0 so the next storage layer can be tested against them: every one must still load unchanged, without being set aside as damaged, and keep what it holds. To write fixtures again, only when adding a new baseline in a new folder named after the version that wrote them, run POKEPLAN_WRITE_FIXTURES=1 ./gradlew :app-core:jvmTest --tests "*SaveFixtureTest*".
