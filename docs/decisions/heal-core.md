# Decision records: heal-core

Moved here from code comments in the comment audit (21 November 2026). Each entry is a decision, rule or constraint that
the code alone does not show. Entries are grouped by source file; a line saying "Documented in" means the owning doc
already holds the rule and the entry only points to it.

## `heal-core/build.gradle.kts`

### heal-core module boundary

Documented in `CLAUDE.md Layout`.

## `heal-core/src/commonMain/kotlin/app/pokeplan/heal/HealData.kt`

### Provenance

Documented in `docs/pokelife/MODULES/HEAL.md 10.3 Import workflow`.

### ActivityRecord

Documented in `docs/pokelife/MODULES/HEAL.md 22. As built: the manual slice`.

### HydrationEntry

Documented in `docs/pokelife/MODULES/HEAL.md 5.4 Hydration`.

### SleepRecord

Documented in `docs/pokelife/MODULES/HEAL.md 22A. As built: Health Connect, read only`.

### Measurement

Documented in `docs/pokelife/MODULES/HEAL.md 7. Body measurements`.

### HealGoal

Documented in `docs/pokelife/MODULES/HEAL.md 9. Goals and progress`.

### HealSettings defaults

Documented in `docs/pokelife/MODULES/HEAL.md 22. As built: the manual slice`.

### HealSettings.healthKinds

Documented in `docs/pokelife/MODULES/HEAL.md 22A. As built: Health Connect, read only`.

### HealSettings.healthLastSyncMillis

Documented in `docs/pokelife/MODULES/HEAL.md 10.3 Import workflow`.

### HealSettings.huaweiEveryDays

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 5.2.2`.

### HealSettings.paidMarks

Documented in `docs/pokelife/MODULES/HEAL.md 22B. As built: steps and readings from Huawei Health`.

### HealData

Documented in `docs/pokelife/MODULES/HEAL.md 22. As built: the manual slice`.

### StepsRecord

Documented in `docs/pokelife/MODULES/HEAL.md 22B. As built: steps and readings from Huawei Health`.

### VitalType

Documented in `docs/pokelife/MODULES/HEAL.md 22B. As built: steps and readings from Huawei Health`.

### VitalReading

Documented in `docs/pokelife/MODULES/HEAL.md 22B. As built: steps and readings from Huawei Health`.

### HealKinds names

Each kind of private HEAL record is kept in its own table family (`heal_activity`, `heal_hydration`, ...) so it can be read, exported or erased alone. `HealKinds.field` names are stable because they are persisted; renaming one needs a migration.

## `heal-core/src/commonMain/kotlin/app/pokeplan/heal/HealImport.kt`

### HealthKind

Documented in `docs/pokelife/MODULES/HEAL.md 22A. As built: Health Connect, read only`.

### HealthKind.unsupported

Documented in `docs/pokelife/MODULES/HEAL.md 22A. As built: Health Connect, read only`.

### Imported

Documented in `docs/pokelife/ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md 3.1`.

### Imported.kind

Documented in `docs/pokelife/MODULES/HEAL.md 22B. As built: steps and readings from Huawei Health`.

### Imported.Steps

Documented in `docs/pokelife/MODULES/HEAL.md 22B. As built: steps and readings from Huawei Health`.

### Imported.Vital value units

An `Imported.Vital` value is in the type's own unit (`VitalType.unit`): bpm, percent, and skin temperature in degrees Celsius.

### ProviderRead.failed

Documented in `docs/pokelife/MODULES/HEAL.md 22A. As built: Health Connect, read only`.

### ImportPlan.Candidate

Documented in `docs/pokelife/MODULES/HEAL.md 10.4 Deduplication and reconciliation`.

### ImportPlan

Documented in `docs/pokelife/MODULES/HEAL.md 22A. As built: Health Connect, read only`.

### HealImport rules

Documented in `docs/pokelife/MODULES/HEAL.md 10.4 Deduplication and reconciliation`.

### PROVIDER_HUAWEI_SCREEN

Documented in `docs/pokelife/MODULES/HEAL.md 22A. As built: Health Connect, read only`.

### PAYING_PROVIDERS

Documented in `docs/economy.md Heal: activity logged`.

### MAX_DAYS_BACK

Documented in `docs/pokelife/MODULES/HEAL.md 22A. As built: Health Connect, read only`.

### HealImport.plan steps and vitals

Documented in `docs/pokelife/MODULES/HEAL.md 22B. As built: steps and readings from Huawei Health`.

### HealImport.newerThanStored

Documented in `docs/pokelife/MODULES/HEAL.md 22B. As built: steps and readings from Huawei Health`.

### HealImport.duplicateOf

Documented in `docs/pokelife/MODULES/HEAL.md 10.4 Deduplication and reconciliation`.

### Steps and vitals duplicate check

`HealImport.duplicateOf` returns null for steps and vitals: a count or reading is never something the player wrote, and one per day replaces itself, so it cannot repeat a record of another source.

### HealImport.withoutImported

Documented in `docs/pokelife/MODULES/HEAL.md 10.4 Deduplication and reconciliation`.

## `heal-core/src/commonMain/kotlin/app/pokeplan/heal/HealRules.kt`

### HealRules principles

Documented in `docs/pokelife/MODULES/HEAL.md 2.2 Today dashboard`.

### HealRules.STEPS_MAX

`HealRules.STEPS_MAX` (200,000) is a validity bound, not a target: a day's step count above it is not a step count and is refused. Nothing about steps is ever judged.

### HealRules.checkSteps

Documented in `docs/pokelife/MODULES/HEAL.md 2.2 Today dashboard`.

### HealRules.vitalRange

Documented in `docs/pokelife/MODULES/HEAL.md 22B. As built: steps and readings from Huawei Health`.

### RESEARCH_MIN_MINUTES

Documented in `docs/economy.md Heal: activity logged`.

### ACTIVITIES_PAID_PER_DAY

Documented in `docs/economy.md Heal: activity logged`.

### PARTICIPATION_PAID_PER_DAY

Documented in `docs/economy.md Heal: first drink of the day`.

### FRESH_MILLIS

Documented in `docs/economy.md Heal: activity logged`.

### IMPORT_FRESH_MILLIS

Documented in `docs/economy.md Heal: activity logged`.

### HealRules.FUTURE_SLACK_MILLIS

`FUTURE_SLACK_MILLIS` (5 minutes) lets a session or drink that ends slightly in the future still pay today: a phone's clock can be a little ahead of what the user typed. Anything further ahead is refused for payment.

### HealRules.paysLikeManual

Documented in `docs/pokelife/MODULES/HEAL.md 22A. As built: Health Connect, read only`.

### HealRules.participationPays

`participationPays` does not check Field Research opt-in: the caller must, and only a player who has opted in is paid for a drink. It also pays only a drink logged now (not backfilled) and only the first one of the day.

### STEPS_PER_REWARD

Documented in `docs/economy.md Heal: steps`.

### STEPS_PAID_PER_DAY

Documented in `docs/economy.md Heal: steps`.

### IMPORTS_PAID_PER_DAY

Documented in `docs/economy.md Heal: records read in`.

### HealRules.importCounts

Documented in `docs/economy.md Heal: records read in`.

### HealRules.latestSleep

Documented in `docs/pokelife/MODULES/HEAL.md 2.2 Today dashboard`.

### HealRules.Progress

Documented in `docs/pokelife/MODULES/HEAL.md 9. Goals and progress`.

### TREND_MIN_OBSERVATIONS

Documented in `docs/pokelife/MODULES/HEAL.md 7. Body measurements`.

### HealRules.trendText

Documented in `docs/pokelife/MODULES/HEAL.md 2.3 History and trends`.

### HealUnits

Documented in `docs/pokelife/MODULES/HEAL.md 11.1 Source and value semantics`.

## `heal-core/src/commonTest/kotlin/app/pokeplan/heal/HealRulesTest.kt`

### HealRulesTest day

`HealRulesTest.day` (7 November 2026) is a Saturday, and the week tests assert dates derived from that weekday: the week starts on Monday 2 November (or Sunday 1 November when asked), so the day sits at the end of its week.
