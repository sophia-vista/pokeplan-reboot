# PokéLife — HEAL Module Specification

**Version:** 1.0  
**Status:** Owner-approved target specification  
**Decision status:** Field Research approved as HEAL's Pokémon acquisition identity  
**Baseline reviewed:** Source archive `0.123.0`, as documented in the Implementation Guide  
**Last consolidated:** 5 November 2026

> This document defines the intended HEAL experience and its module-specific contracts. It is not a claim that HEAL or any health-provider integration is already implemented. The Product Bible remains the product-wide authority; the Implementation Guide records implementation status and delivery work. Shared visual rules are owned by `../FEATURES/DESIGN_SYSTEM.md`; shared reminder scheduling and delivery are owned by `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`.

## 1. Purpose and product role

HEAL is PokéLife's practical physical-health and wellbeing region. It provides one useful place to record, review and understand activity, exercise, nutrition, sleep, recovery measurements, body measurements and selected personal health records.

HEAL must remain useful without a wearable, internet connection, game rewards or a health-platform account. It is a personal tracking and organization tool, not a clinical system, diagnostic service, treatment planner or replacement for professional care.

HEAL's core loop is: record or import health information; preserve its source and context; review a clear daily summary or history; optionally work toward a user-defined goal; and receive positive, privacy-preserving Field Research progression for eligible participation.

### 1.1 HEAL owns

- Activity and movement records.
- Fitness and workout sessions, exercise definitions and workout templates.
- Nutrition records, meal entries, food definitions and hydration logs.
- Sleep records and imported recovery measurements.
- Body measurements and their history.
- Personal health records supported by this specification: medication lists, symptom notes, appointments and health notes.
- Health-specific goals, summaries, history and trends.
- Provider connections, import configuration, source provenance and import reconciliation state.
- HEAL's Field Research progression and HEAL-originated reward eligibility events.

### 1.2 HEAL does not own

- Tasks, calendar commitments, focus sessions or general recurring responsibilities (PLAN).
- Emotional reflection, mood journals, Diary Cards, coping tools or self-care routines (CARE).
- Financial accounts, transactions, budgets or medical-expense management (MINT).
- General device utilities, clocks, timers or generic sensor tools (GEAR).
- Clinical diagnosis, treatment plans, prescribing, medication dose decisions or an authoritative medical record.
- The shared Pokémon collection, Pokédex, HEAL-specific party, Bag, Poké currency, Rare Candy, Super Candy or global reward ledger.
- External provider source records; HEAL stores permitted local snapshots and references, not ownership of the provider's original facts.

### 1.3 Boundary examples

| User action or record | Owner | Relationship |
|---|---|---|
| Completed workout and exercise sets | HEAL | Native record |
| Task to attend a workout | PLAN | May link to HEAL workout/template |
| Self-care routine involving stretching | CARE | CARE owns the routine; a completed workout is separately recorded in HEAL only when explicitly logged |
| Mood or emotional response to exercise | CARE | HEAL does not copy the reflection |
| Medication list and adherence note | HEAL | Private health record |
| Purchase of medication | MINT | Financial transaction; no automatic health-record access |
| Watch-originated heart-rate sample | Original provider | HEAL local snapshot with provenance |
| Pokémon recruited through Field Research | Shared Pokémon service | HEAL owns the acquisition event, not Pokémon storage |

No module may create a competing editable copy of a HEAL record. Cross-module surfaces use linked references or read-only projections.

## 2. Information architecture

HEAL uses one configurable landing screen and a small set of focused destinations. It must not become a dashboard containing every available metric.

### 2.1 Primary destinations

1. **Today** — concise daily health summary, recent activity, quick logging and relevant reminders.
2. **Activity** — steps, distance, movement, active time and activity history.
3. **Fitness** — workout logging, exercise library, templates, sessions and training history.
4. **Nutrition** — meals, food entries, nutrients and hydration.
5. **Sleep** — sleep sessions, stages when available and recovery history.
6. **Health** — body measurements, selected health metrics and personal health records.
7. **History** — searchable consolidated timeline and trends.

Goals, provider connections, import history and settings are secondary destinations accessible from the relevant section or HEAL settings. Do not add a primary destination for every individual metric.

### 2.2 Today dashboard

Today should answer: what information is available, what has been recorded, and what can I quickly record now?

- Show a compact selection of user-enabled metrics and recent records.
- Support quick actions for workout, activity, meal, hydration and measurement logging.
- Show the latest completed sleep record, not an incomplete or misleading current-night total.
- Show medication/appointment reminders only when enabled and permitted by the shared reminder system.
- Show data freshness and source where relevant.
- Let users hide, reorder and select dashboard sections and metrics.
- Do not require any metric to be configured or tracked.
- Do not show missing information as zero. Distinguish unavailable, not recorded, permission denied, stale and genuine zero values.
- Avoid a composite health score or a single pass/fail assessment of the day.

### 2.3 History and trends

History is a consolidated, searchable timeline with filters for activity, workouts, nutrition, sleep, measurements and health records. Individual domains retain their focused histories. Trends use appropriate time ranges and clear units, with textual summaries and accessible data labels. Do not create a large analytics dashboard or imply causation from correlations.

## 3. Activity tracking

Support activity and movement records including:

- Steps and distance.
- Active minutes and activity duration.
- Walking, running, cycling, swimming and other supported activity types.
- Calories burned only when supplied by a source; label estimates and provider-derived values clearly.
- Activity start/end, duration, optional route/distance, source and notes where available.

Sources may include manual entry, supported health-platform imports, supported wearable integrations and justified Android device capabilities. Manual logging must work independently of all integrations.

A manual activity entry should be quick: activity type, date/time and duration are sufficient; distance and notes are optional. Do not require users to enter every available metric.

Imported activity records must retain source identifiers, timestamps, units and provenance. Reconcile imported and manually entered sessions to avoid duplicates. Passive daily totals and continuously changing measurements are not workout sessions and must not be fabricated into session records.

## 4. Fitness and workout tracking

Fitness is a usable workout logger, not merely a list of completed exercises.

### 4.1 Workout types

Support strength training, cardio, walking/running, mobility/flexibility and other user-defined or provider-supported workout types. Do not assume every workout type has sets and repetitions.

### 4.2 Exercise library

- Searchable built-in exercise definitions with names, aliases, movement category and optional equipment/body-area metadata.
- User-created exercises.
- Clear distinction between an exercise definition and an exercise performed in a session.
- Avoid medical claims or prescriptive exercise guidance in exercise descriptions.

### 4.3 Workout sessions

A session may include:

- Start/end time and duration.
- Workout type and optional template reference.
- Ordered exercises.
- Sets, repetitions, load and unit, distance, duration or other type-appropriate measurements.
- Optional rest intervals and timer.
- Optional notes and perceived effort, if user-entered.
- Source/provenance for imported sessions.

Users can start a free-form session without first creating a program or template. Support pause/resume, interruption recovery, completion, editing and deletion. An interrupted session must not disappear after process death; recovery must make its state clear and avoid silently recording an incomplete session as completed.

### 4.4 Templates and history

Workout templates are reusable HEAL records with editable exercise order and default set/rep/load guidance. Templates do not schedule themselves. PLAN may hold a linked commitment to perform one. Workout history supports filtering and simple progress views, including personal records only where the comparison is meaningful and based on comparable exercise/unit data.

Do not build a complex coaching plan, competitive leaderboard, mandatory progression scheme or advanced strength analytics before the basic logger is reliable.

## 5. Nutrition and hydration

Nutrition supports practical food and fluid logging with clear data provenance. It must not create guilt, pressure or a mandatory calorie-restriction loop.

### 5.1 Meal logging

- Meal groupings: breakfast, lunch, dinner and snacks, with editable labels.
- Food entries with portion amount and unit.
- Custom food creation and saved/frequently used foods.
- Meal history, optional notes and editing/deletion.
- Fast repeat of a previously logged food or meal.

### 5.2 Nutrient data

Support calories, protein, carbohydrates, fat, fiber, sugar, sodium and other nutrients when a source provides them. Every value must retain its source and whether it is verified database information, provider information, an estimate or user-entered. Missing nutrients are unknown, not zero. Portion scaling must preserve the underlying per-serving basis and unit conversion rules.

Food lookup may use an offline saved-food list first. Online database search and barcode lookup are later optional enhancements; neither may be required for core logging. Food database integration must be evaluated for licensing, availability, regional coverage, attribution and long-term maintenance before adoption.

### 5.3 Nutrition goals

Users may configure selected nutrient targets or ranges. Targets are optional and must not default to aggressive calorie restriction. Avoid moralized labels, food grades, red/green judgment, punitive streaks or recommendations that imply medical treatment. HEAL may summarize logged information but must not diagnose deficiencies, prescribe diets or infer health status from incomplete food logs.

### 5.4 Hydration

Support quick-add quantities, custom drinks/volumes, a user-defined optional target and historical totals. Do not assume all drinks have identical hydration properties or infer medical hydration needs. Hydration reminders are off by default and use the shared reminder feature when enabled.

## 6. Sleep and recovery

Support sleep start/end and duration, sleep stages and interruptions when the source provides them, plus supported recovery measurements such as resting heart rate, HRV and stress readings. Other measurements, including SpO2, may be stored only when a supported source provides them and the metric is clearly identified.

- Preserve source, timestamp, unit, sampling context and import freshness.
- Distinguish measured, provider-calculated and estimated values.
- Do not infer sleep stages or recovery scores from missing data.
- Present trends without diagnosing sleep disorders or making clinical conclusions.
- Do not shame users for short/long sleep or missed sleep logs.
- A single reading must not be treated as a definitive health assessment.

## 7. Body measurements

Support optional manual or imported records for weight, body-fat percentage, muscle mass and user-defined measurements such as waist or hip circumference. Add other measurements only when their meaning, units and source can be represented accurately.

Each record retains date/time, value, unit, source, method and any relevant estimate/measurement label. Manual corrections must not silently alter the original imported provider record. Display historical trends only when enough comparable observations exist. Users can hide individual metrics, delete records and choose whether measurements appear on Today. No body measurement is required for HEAL setup or Field Research progression.

## 8. Personal health records

HEAL provides a limited private reference area, not a clinical record system.

### 8.1 Medication records

Allow user-entered medication name, strength, user-provided instructions, schedule, start/end dates, active/paused/discontinued status and private notes. Do not calculate, recommend, alter or validate doses. Do not infer a medication schedule from a label or external source. Users remain responsible for entering and confirming instructions with their clinician/pharmacist.

Medication adherence logging is optional and private. It must never be a reward-bearing action. A reminder can notify the user of their own entered schedule but must not imply that PokéLife has clinically verified it.

### 8.2 Symptoms, appointments and notes

- Symptoms: user-authored description, date/time, optional severity and notes.
- Appointments: provider/name, date/time, location and private notes.
- Health notes: user-authored observations and reference information.

These records are private, searchable and editable. They do not enter CARE automatically; a user may explicitly create a separate CARE reflection if desired. They do not enter WORLD or progression.

## 9. Goals and progress

HEAL owns health-specific goals such as weekly activity, workout frequency, strength practice, walking, nutrition targets and hydration targets. Goals support optional target, unit, period, start date, status, progress history and pause/archive/completion.

- Goals are optional and user-configurable.
- A missed target does not erase progress, reduce rewards or create a penalty.
- Do not make body weight, calorie burn or a single biomarker the definition of success.
- Do not turn a target into a medical recommendation.
- Goal progress is calculated from the appropriate HEAL-owned records and must show missing/incomplete data honestly.
- A goal can be paused or removed without deleting its underlying records.

## 10. Integrations and data import

Integration must be capability-based and evidence-led. Do not advertise a metric or provider as supported until the actual permission flow, data availability, history depth and failure behavior have been verified on the target device/firmware.

### 10.1 Huawei Watch Fit 4 Pro investigation

The primary feasibility target is the Huawei Watch Fit 4 Pro paired with the user's supported phone/firmware. Investigate, in this order:

1. Official Huawei Health interfaces, SDKs, APIs and developer access available to this product and region.
2. Whether Huawei Health on the actual phone/firmware exposes the required metrics to Android Health Connect.
3. Legitimate Huawei Health export formats and whether they contain historical records, timestamps, units and stable identifiers.
4. Maintained free/open-source bridges that operate through supported or user-authorized mechanisms and do not require unsafe workarounds.
5. Direct Bluetooth/local access only where documented and legitimate; it is not a dependency for HEAL.

Test metric coverage individually: steps/distance, workout sessions, heart rate, resting heart rate, HRV, sleep and stages, stress, calories, SpO2 and any other accessible metrics. For each metric record API/route, permission, history depth, source timestamp behavior, sync delay, units, region/firmware constraints, cost and maintenance risk. Do not assume that the watch exposes every metric or that a similarly named Huawei watch has equivalent access.

Constraints: prioritize free options; no recurring paid sync service as a prerequisite; no rooting; no forced account/region changes; no credential scraping; no fragile accessibility automation; no security circumvention; no unsupported promise of direct watch access. If a route is unreliable or unsupported, mark it blocked and preserve manual/import alternatives.

### 10.2 Health Connect and other providers

Health Connect is an optional adapter, not HEAL's data model or an assumption of Huawei compatibility. Request only the permissions required for selected metrics and explain their purpose. Handle denied, revoked, partial and unavailable permissions. Support other providers only through documented interfaces and explicit user consent.

### 10.3 Import workflow

- Explain source, metric categories, history range and permissions before import.
- Preview available categories/range when the provider permits it.
- Import into local HEAL storage with provenance and source identifiers.
- Show import date, last successful sync, failures and data freshness.
- Support partial imports and retry without duplicating records.
- Never silently replace manual records with imported values.
- Let users disconnect a provider and delete imported data from that provider without deleting manual records or other sources.
- Imported historical records never generate retroactive Pokémon rewards.

### 10.4 Deduplication and reconciliation

Use matching priority: (1) provider record ID; (2) stable provider-specific identifier; (3) deterministic fingerprint using source, metric/type, timestamp, unit and value. Use appropriate tolerance for provider timestamp precision and unit normalization. If a match is uncertain, preserve both records and flag a possible duplicate rather than deleting one. Re-importing the same source data must be idempotent. Keep original provider values recoverable when users correct a local representation.

### 10.5 Manual fallback

HEAL must remain complete enough for core use without any provider. Support manual activity/workout, nutrition, hydration, sleep, body measurement and personal health record entry. Existing imported records remain readable when disconnected. No cloud service or provider account is mandatory.

## 11. Data ownership and conceptual model

HEAL owns its domain schema and repositories. External providers remain authoritative for their original records; HEAL stores local snapshots permitted by the provider and platform terms.

Suggested conceptual entities (adapt to the approved data architecture; these names are not a requirement to create separate Gradle modules):

| Entity | Responsibility |
|---|---|
| `HealthRecordMetadata` | Stable record ID, category, source, timestamps and common provenance |
| `MetricSample` | Timestamped measurement, unit, context and value representation |
| `ActivityRecord` | Activity type, duration, distance and source |
| `WorkoutSession` | Completed/in-progress workout and session metadata |
| `WorkoutExercise` | Exercise performed within a session |
| `WorkoutSet` | Reps, load, duration or type-appropriate set values |
| `WorkoutTemplate` | Reusable workout definition |
| `FoodItem` | Food identity, serving basis and nutrient data provenance |
| `MealEntry` | Food/portion consumed, meal grouping and timestamp |
| `HydrationEntry` | Drink/volume and timestamp |
| `SleepRecord` | Sleep interval and source-provided stages/attributes |
| `BodyMeasurement` | Measurement value, unit, method and source |
| `MedicationRecord` | User-entered medication reference and schedule |
| `SymptomRecord` | User-authored symptom observation |
| `AppointmentRecord` | Appointment details and private notes |
| `HealthNote` | User-authored health reference note |
| `HealthGoal` | Optional health target and progress state |
| `ImportSource` | Provider identity, permissions and sync configuration |
| `ImportBatch` | Import range, status, timestamps and reconciliation results |
| `HealResearchState` | HEAL-specific research progression, not a currency |
| `HealRewardEvent` | Minimal idempotent eligibility event and payment reference |

Common record metadata should include stable ID, created/updated timestamps, event/measurement timestamp, source kind, source/provider record ID where available, import time, unit, provenance/estimate flag and deletion/correction state as appropriate. Keep raw provider payloads only when necessary, lawful, documented and protected; do not retain redundant raw payloads by default.

### 11.1 Source and value semantics

Distinguish at least:
- **Manual:** entered by the user.
- **Imported:** supplied by a named provider/source.
- **Derived:** calculated from other stored records, with derivation/version recorded.
- **Estimated:** approximate value supplied or inferred by a documented source method.

Never convert missing to zero. Preserve source units/values and store normalized values only with explicit conversion metadata. Derived daily totals and charts are projections, not replacements for original records.

### 11.2 Cross-module contracts

- **PLAN:** may create a task/commitment linked to a workout template or health goal only after explicit user action. A PLAN task completion does not create a workout. A workout completion does not complete an unrelated PLAN task. The link is a reference, not a duplicate record.
- **CARE:** owns emotional reflection, mood and self-care routines. HEAL may link to a CARE record only through explicit user action; it does not read or import CARE content.
- **MINT:** owns financial transactions. No automatic health-data or medication purchase linkage. Any future expense association must be user-initiated and must not expose health details to MINT.
- **GEAR:** may expose shared generic unit-conversion utilities; HEAL owns health-specific units, labels and presentation.
- **WORLD/shared economy:** receives only the minimal approved Field Research eligibility event. WORLD does not query HEAL repositories, records, measurements or goals.

## 12. Pokémon progression — Field Research

Field Research is HEAL's approved Pokémon acquisition identity. The HEAL region is available from day one, with entry-level research immediately accessible and deeper discoveries tied to relevant progression. It frames eligible participation as exploration and discovery without making body measurements, medical status or health performance a competition.

### 12.1 Core loop

1. A qualifying user action is completed or confirmed in HEAL.
2. HEAL evaluates it under a versioned, module-owned eligibility rule.
3. HEAL records one idempotent, minimal progression event.
4. Accumulated Field Research progress unlocks a discovery/encounter opportunity.
5. Recruitment resolves through the shared Pokémon acquisition and collection services.

Field Research progress is HEAL-owned progression state, not a second currency. HEAL creates no separate Pokémon collection, Bag, egg system or Poké balance.

### 12.2 Eligible participation

Qualifying sources may include a user-completed workout session, a user-logged eligible activity session and a newly imported supported workout session when it is confidently identified as a new event. The same real-world session must be rewarded at most once, regardless of whether it is entered manually and later imported. New imported data may qualify only prospectively under the current eligibility policy; historical backfill never grants rewards.

Nutrition and hydration logging may contribute only a small, capped participation event if the user has opted into HEAL progression. The event must not contain food identity, portion, calories, nutrient values, drink type or volume. No reward is tied to consuming less/more, a nutrient target, a calorie target or a food quality judgment. Do not reward every individual meal/water entry; use a bounded daily participation rule to avoid encouraging compulsive logging.

### 12.3 Explicitly ineligible information

Never generate progression from:
- Weight, body-fat percentage, body measurements or changes in them.
- Calories burned, heart rate, HRV, stress, SpO2 or other biomarker values.
- Sleep duration, sleep stages, sleep quality or recovery scores.
- Symptoms, diagnoses, medication records, adherence, appointments or health notes.
- Mood, journal, Diary Card or other CARE content.
- Passive sensor samples or repeated imports by themselves.
- Meeting a weight-loss, calorie-restriction or other potentially harmful body target.

The system recognizes participation, not the user's body, medical condition or health outcome.

### 12.4 Privacy boundary and event contract

The reward service receives only the minimum event fields needed to prevent duplicate payment and apply the HEAL policy, such as an opaque event ID, module ID, broad eligible activity category, occurrence time and eligibility-policy version. Do not send measurements, source record payloads, exercise details, food information, goal values, notes, medication information or provider identifiers to WORLD. Where possible, reward evaluation occurs inside the HEAL/reward boundary and WORLD receives only a generic reward result suitable for presentation.

Reward events are idempotent. Edits, deletions, imports and migrations must not replay paid events. A user can disable Field Research rewards without disabling health tracking. No missed-day penalty, decay, streak loss, expiring reward or mandatory daily quota.

### 12.5 Acquisition and balance

Use the shared Pokémon collection, Pokédex, Bag, reward ledger, Poké currency and existing Rare Candy/Super Candy systems. Field Research should provide frequent positive discoveries and a sustainable acquisition rate comparable to other module regions. Encounter thresholds, rarity weights, reward quantities and caps are calibrated in the shared Pokémon/economy specification and simulations, not hard-coded independently in HEAL. All content is permanent; no paid Pokémon, trading requirement, seasonal exclusive or excessive grind.

## 13. Reminders and notifications

Shared scheduling, quiet hours, focus deferral, notification delivery, permissions, recovery and action routing are specified in `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`. HEAL owns its source records, reminder preferences and HEAL-specific action semantics only.

Potential HEAL reminder sources:

| Source | Default | Behavior |
|---|---|---|
| Medication schedule | Off until explicitly configured | User-entered schedule reminder; never verifies or changes dosage |
| Appointment | Off until explicitly enabled | Reminder for a user-entered appointment |
| Hydration | Off | Optional, low-frequency prompt; user-defined target only |
| Workout commitment | Managed by PLAN when scheduled there | HEAL may link to the PLAN commitment; no duplicate reminder |
| Nutrition logging | Off | No default meal logging prompts |
| Measurement logging | Off | No default weighing/body-measurement prompts |
| Health-goal check-in | Off | Optional and user-configured |

All HEAL reminders are opt-in. No compulsory prompts, repeated nagging, shame-oriented language or reward loss for dismissal. Shared reminder storage must contain only opaque references and minimum scheduling metadata; sensitive medication/appointment details are resolved as late as practical and lock-screen content remains privacy-conscious. Medication reminders use only user-entered instructions and are not medical advice.

## 14. Privacy, security and data lifecycle

Health data is highly sensitive. HEAL uses a local-first, privacy-preserving model consistent with PokéLife's product-wide no-mandatory-account/no-cloud-sync direction.

- Store HEAL records locally in the approved data layer.
- No mandatory account, cloud profile, PokéLife cloud sync, advertising, profiling or telemetry containing health data.
- Do not transmit health records to third parties except the explicitly connected provider operation the user authorized; explain what is shared and why.
- Use the shared encryption and backup architecture for sensitive local data and exports.
- Restrict permissions to the minimum selected data categories and provide clear disconnect/revoke behavior.
- Do not log raw provider payloads, medication details, symptom notes, meal content or measurements.
- Do not expose health measurements, nutrition, sleep, body records, symptoms, medication, appointments or health notes to WORLD, launcher surfaces or unrelated modules.
- Do not expose sensitive detail in lock-screen notifications by default.

Provide export of all HEAL data and selected categories in documented, usable formats. Provide deletion of individual records, selected imported-source data, provider connection metadata and all HEAL data. Deleting HEAL data must not delete unrelated Pokémon, Poké currency, Bag contents, PLAN tasks, CARE entries or other modules' records. Disconnecting a provider does not delete imported history unless the user separately chooses to delete it.

No cloud sync or third-party transmission of HEAL content is introduced by this module specification. Any future change requires an explicit product-level privacy decision.

## 15. Offline behavior and lifecycle

Core HEAL workflows work offline:

| Capability | Offline behavior |
|---|---|
| Dashboard and local history | Available from local records |
| Manual activity/workout logging | Fully available |
| Workout templates and exercise library | Saved local content available |
| Meal and hydration logging | Fully available using saved foods/custom entries |
| Sleep and body measurements | Manual logging and history available |
| Health records and goals | Fully available |
| Charts and summaries | Calculated from available local data |
| Existing imported records | Readable and editable as permitted |
| Provider synchronization | Paused until provider access returns |
| Online food/barcode lookup | Unavailable; manual/saved-food fallback |

Recover interrupted workouts and meal entry drafts where appropriate. Partial imports must be resumable or safely retried. Reconnection must not duplicate records or reward events. Time-zone changes preserve original instants and display local dates correctly. Handle process death, device reboot, permission revocation, provider disconnection, app update and migration without corrupting local records.

## 16. Visual presentation

HEAL follows the shared PMD dialogue-box visual system in `../FEATURES/DESIGN_SYSTEM.md` and uses its **utility presentation** intensity. It must feel like a polished PMD-inspired module, not a conventional medical dashboard with Pokémon decorations.

- Use shared dialogue-box/panel components for the Today summary, concise contextual prompts, workout completion feedback, Field Research discoveries, connection/import status and primary quick actions.
- Dense measurement histories, nutrition tables, timelines, workout sets, forms and charts use efficient native-feeling layouts with selective shared framing. Do not wrap every metric, row or paragraph in a dialogue box.
- Use restrained line charts for trends, compact bars for activity summaries, clear numeric values and readable historical timelines. Provide textual equivalents and accessible labels.
- Do not use excessive progress rings, medical-style gauges, arbitrary composite scores or red/green judgments of normal variation.
- The selected buddy may appear contextually and react to eligible completion/discovery events. It must not comment unsolicitedly on weight, food choices, sleep quality or medical measurements.
- Keep animations and sound subordinate, optional and consistent with shared accessibility/motion/sound preferences. Do not add continuous decorative movement to utility screens.
- Support light/dark themes, dynamic text scaling, TalkBack, minimum 48 dp touch targets, reduced motion, responsive widths and chart accessibility.

The design-system document owns reusable component rules, typography, spacing, borders, states and accessibility. HEAL may define content-specific chart layouts but cannot fork shared component anatomy or global tokens.

## 17. Existing implementation audit and reuse policy

The reviewed `0.123.0` baseline, as recorded in `IMPLEMENTATION_GUIDE.md`, has no first-class HEAL module. The guide identifies HEAL as missing and does not establish a working Huawei Health, Health Connect or wearable integration. Do not treat a proposal, dependency, asset, permission declaration or generic sensor utility as a completed HEAL workflow.

### 17.1 Reuse

- Shared Pokémon collection, Pokédex, Bag, currency, reward ledger and acquisition interfaces, after the shared ecosystem phase has established stable contracts.
- Shared app shell, module registry, navigation, local data lifecycle, permission coordinator, export/backup/encryption foundations and integration interfaces when they meet the target architecture.
- Shared design-system components and chart primitives only after review against `FEATURES/DESIGN_SYSTEM.md`.
- Shared reminder scheduler and delivery through the HEAL source adapter contract.
- Existing generic unit-conversion utilities from GEAR where appropriate; HEAL remains responsible for health-specific semantics and presentation.

### 17.2 Build

Create HEAL as an independent domain with its own models, repositories, local persistence, application/domain rules, import coordinator, provider interfaces, Field Research progression state and tests. Android/provider APIs belong in platform adapters; common HEAL domain logic must not depend on Compose or Android framework classes.

Conceptual package grouping may include `activity`, `fitness`, `nutrition`, `sleep`, `measurements`, `healthrecords`, `goals`, `history`, `imports`, `research`, `repositories` and `tests`. These are logical boundaries, not a requirement for one Gradle module per folder.

### 17.3 Replace or avoid

- Do not add health fields to PLAN task models or CARE persistence.
- Do not place all health data in a miscellaneous global save object.
- Do not let UI screens call Huawei/Health Connect APIs directly.
- Do not couple common domain models to Android Health Connect types.
- Do not create a second Pokémon collection, Bag, currency, egg system or reward ledger.
- Do not preserve cloud sync or Supabase as a hidden HEAL dependency.
- Do not claim an integration works until tested on the actual device, firmware, region and app version.
- Do not preserve legacy architecture merely to avoid designing a proper HEAL domain; preserve user data and proven shared business rules.

## 18. Implementation phases and exit gates

HEAL follows the dependency-first roadmap in the Implementation Guide. Do not start a later phase while the preceding foundation or privacy gate is failing.

### Phase 0 — HEAL domain foundation

- Establish HEAL ownership and module boundary.
- Define common record metadata, units, source/provenance semantics and repositories.
- Integrate with the approved local database, encryption, backup/export and migration architecture.
- Define provider-neutral interfaces and Field Research event boundary.

**Exit gate:** HEAL has one authoritative owner for every record type; no dependency on PLAN/CARE persistence; source and unit semantics are testable.

### Phase 1 — Manual activity and fitness

- Today dashboard shell and quick actions.
- Manual activity logging and history.
- Exercise library, workout sessions, sets and templates.
- Basic health goals and body measurements.

**Exit gate:** activity and fitness workflows work offline, recover interruptions, preserve timestamps and survive process death.

### Phase 2 — Nutrition and sleep

- Food/serving model and saved foods.
- Meal logging and nutrient provenance.
- Hydration.
- Sleep and supported recovery records.
- History, summaries and charts.

**Exit gate:** nutrition and sleep histories can be maintained manually; unknown values remain unknown; no mandatory online lookup.

### Phase 3 — Wearable/provider feasibility and imports

- Test Huawei Watch Fit 4 Pro against actual phone, region, app version and firmware.
- Evaluate official Huawei Health interfaces, Health Connect compatibility, legitimate exports and maintained free bridges.
- Implement only verified provider adapters.
- Add permission, freshness, import preview, reconciliation, deduplication and disconnect/delete behavior.

**Exit gate:** each advertised metric has verified provenance and history behavior; repeated imports are idempotent; manual data is not silently overwritten; unsupported paths are clearly marked blocked.

### Phase 4 — Personal health records and reminders

- Medication, symptom, appointment and health-note records.
- Optional HEAL reminder source adapter for medication and appointments, plus optional hydration/goal reminders.
- Secure export, selected deletion and provider data controls.

**Exit gate:** private records remain local and are never passed to progression; reminder delivery uses the shared contract; no clinical dosage logic exists.

### Phase 5 — Field Research integration

- HEAL regional identity and acquisition configuration.
- Eligible event policy, opt-in controls, deduplication and payment history.
- Encounter/recruitment integration with shared Pokémon services.
- Reward generosity and anti-farming simulations.

**Exit gate:** no sensitive measurement or record is exposed; events are idempotent; historical imports do not reward; acquisition is generous and comparable with other modules.

### Phase 6 — Polish and acceptance

- Refine configurable Today dashboard, trends and history.
- Accessibility, responsive layouts, dark mode and reduced motion.
- Performance, large-history, import recovery and migration testing.
- Real-device verification of supported integrations.

**Exit gate:** all core workflows pass functional, privacy, offline, accessibility, performance and real-device acceptance; unresolved provider limitations are documented without overstated support.

## 19. Acceptance criteria

### Product utility
- HEAL is useful without a wearable, internet, account or Pokémon reward.
- Activity, workout, nutrition, hydration, sleep, measurements and health records have clear owners and independent workflows.
- Quick logging does not require unnecessary fields.
- Dashboard metrics are configurable and missing data is not displayed as zero.
- Goals can be paused; missed days do not cause loss or shame.

### Data quality and integrations
- Every imported record has source, timestamp, unit and provenance.
- Manual, imported, derived and estimated values are distinguishable.
- Repeated imports do not duplicate records or rewards.
- Uncertain duplicates are preserved for review.
- Manual records are not silently overwritten.
- Provider permissions can be denied/revoked without disabling manual HEAL use.
- Huawei/Health Connect capabilities are advertised only after actual device/firmware verification.

### Privacy and rewards
- Sensitive health records remain within HEAL and protected local storage.
- No actual measurement, meal, sleep, medication, symptom or appointment data reaches WORLD, launcher or shared reminder storage.
- Field Research uses minimal opaque eligibility events and is opt-in.
- No rewards derive from weight, calories burned, biomarkers, sleep, symptoms or medication adherence.
- Historical backfill, duplicate import, edit, deletion and migration cannot replay rewards.
- Deleting HEAL data does not delete shared Pokémon, currency or unrelated module records.

### Reliability and accessibility
- Manual workflows function offline.
- Interrupted workouts and imports recover safely.
- Process death, reboot, time-zone changes, permission revocation and app update are tested.
- TalkBack, dynamic type, contrast, touch targets, reduced motion and chart text alternatives pass review.
- Large histories remain responsive; no background sensor polling or animation runs unnecessarily.
- Shared PMD dialogue-box language is consistent without excessive framing.

## 20. Explicit non-goals

Not in scope unless separately approved:
- Medical diagnosis, treatment, prescribing, dose calculation or clinical decision support.
- Mandatory wearable, account, subscription, cloud sync or paid integration.
- Unverified direct watch access, rooting, credential scraping or fragile automation.
- Compulsory calorie counting, weight loss, body measurement, daily check-ins or streaks.
- Competitive fitness, social leaderboards, public health profiles or sharing health data with friends.
- Automatic access to CARE mood/journal data or MINT financial records.
- Rewarding weight change, calorie burn, biomarkers, sleep quality, medication adherence or clinical outcomes.
- Complex coaching plans, advanced sports analytics or medical-grade reporting without a demonstrated need.
- A separate HEAL Pokémon collection, currency, Bag, egg system or reward engine.

## 21. Documentation alignment

| Document | Authoritative responsibility |
|---|---|
| `../PRODUCT_BIBLE.md` | Product-wide module map, ownership, privacy and shared Pokémon rules |
| `../IMPLEMENTATION_GUIDE.md` | Baseline implementation status, dependency order and delivery roadmap |
| `../MODULES/PLAN.md` | Planning tasks and commitments; explicit links to HEAL records only |
| `../MODULES/CARE.md` | Emotional self-care, routines and private reflection; no HEAL record ownership |
| `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture` | Shared reminder scheduling, policy, delivery and recovery |
| `../FEATURES/DESIGN_SYSTEM.md` | Shared PMD dialogue-box visual language and reusable components |
| Shared Pokémon/economy specification | Collection, Bag, reward ledger, regional acquisition contracts and economy balance |
| Future `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md` | Cross-platform provider contracts and sensitive-data architecture when created |
| `../ARCHITECTURE/DATA_AND_MIGRATIONS.md` | Shared persistence, transaction, export, backup, restore, migration and recovery contracts |

This document is the single detailed requirements source for HEAL. Shared scheduling, visual rules, Pokémon ownership and technical foundations remain in their respective canonical documents; do not duplicate their implementation rules here.

## 22. As built (0.136.0, REP-014): the manual slice

*Built and tested on the JVM, compiled for Android; **not run on a device**. Nothing here is a provider integration: there is no Health Connect, no watch and no import, and the Settings screen says so.*

**What exists.** Section 2's Today card and menu; **activity** (type, minutes, when it ended, optional distance and note; edit, delete), **hydration** (quick amounts, any amount, the day's total, an optional target the player sets, history), **sleep** (a finished night, never a stage or a score), **body measurements** (weight, body fat, waist, hip, other; kept in the unit entered; kept off Today unless asked; a trend sentence only from three comparable entries, with no word that judges), **optional goals** (weekly active minutes, weekly active days, a daily drink total; pause, put away, delete; "so far", never "failed"), and a Settings page (Field Research, hydration target and reminders, weight unit, export and erase by kind). Not built, and not offered: the exercise library, workout sessions and templates (section 4), meals and nutrients (section 5), personal health records (section 8), Today customisation, imports (section 10), a consolidated History, and charts.

**Field Research (section 12), as built** *(numbers are *(tune)* starting values; `RewardRules.VERSION` 17, `docs/economy.md`)*:
- **Off until the player turns it on** (Settings). Turning it off keeps every record and loses nothing.
- Only two things can pay, both only for a record **created now** (never edited or backfilled): an **activity** of at least 10 minutes that ended no more than 12 hours ago and not in the future (3 a day), and the **day's first drink** (1 a day). Sleep, measurements, goals, notes, calories and everything else never pay. Imported and derived records never pay (none exist yet).
- A payment is **4 Rare Candies, a berry and a little friendship for Heal's lead** (an activity) or **1 Rare Candy** (the drink), a point of trainer rank, and points towards a **Field Egg** (2 and 1; 4 fill the meter), which hatches into Heal's party from the **Heal Region** (`tools/species/regions/heal.txt`, about 66 hatchable lines of sporty, outdoor and energetic Pokémon, ending with its patrons Raikou, Entei and Suicune).
- The event the shared world receives is exactly `HealEvent`: a stable `id` (`heal:<kind>:<record id>`), `module` ("heal"), an opaque `ref` (the record's random key), the broad `kind` (`activity_logged` or `participation`) and the time. No duration, distance, type of activity, volume or note exists in it. The game remembers the paid ids (`AppData.healPaid`, the newest 1000), so a replay (an edit, a delete, a restore of an older backup, a migration) never pays twice; the record also keeps `paid`.
- Heal's party (a lead and five) is a place like any other (POKEMON_ECOSYSTEM.md section 2.2): joining takes a Pokémon from where it was. Its perks (Friendship gain: Fighting, Normal, Fire, Electric, Flying, Dragon; Berry finder: Water, Grass, Ground, Ice, Bug, Fairy; capped at +25% a bucket) change only what Field Research pays, never a record or a total. Heal works fully with an empty party.
- **First-run tutorial** (POKEMON_ECOSYSTEM.md section 10.2.1): a 5-second walk in Heal gives one Common Heal Region Pokémon into Heal's party, leading it, once per save (`Tutorials.HEAL_WALK`); it creates no record, event, meter point, streak or cap use, and a player who has already logged an activity is not offered it.

**Data (section 14).** `heal_*` tables from the start (schema 7): `heal_activity`, `heal_hydration`, `heal_sleep`, `heal_measurement`, `heal_goal` (one family per kind, each row `key, position, day, json`) and `heal_state` (the settings). `HealTables` can read, export (`readKind`) or erase (`erase`) one family alone and `clear` all of HEAL, touching nothing outside `heal_*`; the app's own Export and Erase in Heal's Settings do the same through the store. Heal is in the daily backup and the backup bundle (`BackupBundle.KNOWN`); restoring a backup made before Heal existed leaves Heal's records alone. **Not done:** encryption of these tables and of the backup files (section 14: "use the shared encryption and backup architecture"; MINT's encryption session is where the choice is made, and until then HEAL's records are protected only by the app's private storage), and per-kind export of a *backup*.

**Reminders (section 13), as built.** Hydration only, off until the player adds a time (up to three a day), through the shared scheduler (`HealReminderAdapter`): the request holds only an opaque slot (`hyd-1000`), the words are the same for everyone ("A moment for a drink?"), the notification is private on the lock screen, the only button is "In 30 min" (there is no "done": a reminder is not a record and pays nothing), it follows the global quiet hours and focus deferral, and it stays silent once the player's own target for the day is met. Medication, appointment, goal check-in and the other sources of the table in section 13 are not built.

## Shared Pokémon ecosystem contract

HEAL's acquisition identity is **Field Research**. HEAL owns eligibility for user-completed activities and approved participation events; the shared Pokémon ecosystem owns reward settlement, canonical Egg/owned-Pokémon records, Pokédex, inventory and progression. HEAL has an independent party referencing canonical owned Pokémon IDs. Only minimal, non-sensitive event metadata may be submitted; measurements, biometrics, health outcomes and medical records are excluded. See `../FEATURES/POKEMON_ECOSYSTEM.md`.

---

## Testing reference

HEAL health-data provenance, permission denial/revocation, import deduplication, offline history, privacy isolation and provider failure must follow [`../ARCHITECTURE/TEST_STRATEGY.md`](../ARCHITECTURE/TEST_STRATEGY.md). Use synthetic health fixtures only.
