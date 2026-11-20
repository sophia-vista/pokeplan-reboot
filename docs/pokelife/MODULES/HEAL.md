# PokéLife — HEAL Module Specification

**Version:** 1.0  
**Status:** Owner-approved target specification  
**Decision status:** Field Research approved as HEAL's Pokémon acquisition identity  
**Last consolidated:** 5 November 2026

> This document defines the intended HEAL experience and its module-specific contracts. The Product Bible is the product-wide authority; `../IMPLEMENTATION_BACKLOG.md` records delivery status. Shared visual rules: `../FEATURES/DESIGN_SYSTEM.md`; shared reminders: `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`.

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

### 2.4 Plain-language insights (owner, 15 November 2026; backlog REP-064)

*Why.* The owner wants HEAL to give a dashboard, trends, a place to record, goals and "recommendations or insights based on my data". The spec already excludes diagnosis and prescription; this section says what an insight is.

An insight is one factual sentence about the player's own logged data, shown beside the trend it describes, with its period and its source: "Weight is 0.4 kg lower than four weeks ago (7 readings)", "Hydration reached your target on 4 of the last 7 days". It states what the data shows and how much data there is; it never says what the player should do, never grades a day, never names a cause and never appears when the data is too thin to say anything. *(Default applied: the owner asked for "recommendations or insights"; this section gives insights only. A recommendation would need its own decision.)* (unknown and missing days are stated, not counted as zero). Insights are off for a metric the player has hidden (2.2), are computed on the phone and are never sent anywhere (section 14).

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

### 5.2A Fast and accurate nutrition entry (owner, 15 November 2026; backlog REP-060)

*Why.* The owner logs hydration, nutrition and weight only when they remember, wants nutrition to be **comprehensive**, and wants entry that is both **easy and accurate**. Huawei Health has some nutrition entry that the owner finds "limited" (what is limited is not known yet: **open question**, ask on the phone). This supersedes the audit's "nutrition stays unbuilt and unadvertised" and the backlog's "nutrition last" (REP-040) as to priority: nutrition is wanted, and is now built to the standard below. **Default applied** (not the owner's words): it follows the medication log (Tier 2 item 15), because the owner did not say it comes first.

Easy means few taps for the ordinary case; accurate means a value the player can trust and trace. Both are required, and where they pull apart the source label settles it (5.2).

- **Fewest taps.** The first screen of a new entry shows **recent and frequent foods and meals** (5.1), each logged in one tap at its last portion, then a search box, then "Add a food". A portion is a stepper with the unit's usual steps and a typed override; the meal group is pre-chosen from the time of day and changed in one tap. Repeating a previous meal is one action.
- **Accurate by default.** Every nutrient shows its source chip (verified database, provider, estimate, typed by you). A food from a database keeps the per-serving basis it came with; scaling never rounds the stored basis. Unknown nutrients read "unknown", never zero, and a meal with an unknown part says its total is incomplete.
- **Comprehensive.** Calories, protein, carbohydrate, fat, fibre, sugar and sodium first (5.2); further nutrients where a source supplies them; custom foods, saved meals and a recipe made of saved foods. Hydration shares the same quick surface (5.4).
- **Food data source: an owner decision (D12 in the backlog), not made here.** Offline saved foods always work (5.2). Accurate lookup for packaged and local foods needs a data source, which this spec does not name: licensing, regional coverage (the owner's region), attribution, upkeep and what leaves the phone must be checked first. A **barcode lookup sends only the barcode**; a **text search sends what the player is eating** and so needs the explicit, documented, user-authorised purpose rule of the privacy architecture (rule 3 of the privacy architecture's principles, `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md`), off until chosen. Neither may be required for core logging.
- **Layout.** A dialogue box per group (Recent, Search, Today's meals), pixel-drawn controls only, the source chip and the "unknown" state shown on the entry itself and not in a caption, and one next action on the empty state (design rules: `../FEATURES/DESIGN_SYSTEM.md` 5.1 and 12A). Look at a real screenshot in both palettes at 1.3x text with the longest food name before calling it done.
- **No pressure.** The section 5 rules stand: no calorie-restriction default, no grades or red and green, no streak for logging, no mandatory prompts (reminders off by default, section 13).

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

Constraints: prioritize free options; no recurring paid sync service as a prerequisite; no rooting; no forced account/region changes; no credential scraping; no fragile accessibility automation (except reading Huawei Health's own screens and notification on the phone, owner decision 14 November 2026, `INTEGRATIONS_AND_PRIVACY.md` 5.2.2); no security circumvention; no unsupported promise of direct watch access. If a route is unreliable or unsupported, mark it blocked and preserve manual/import alternatives.

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
- Calories burned, heart rate, resting heart rate, HRV, stress, SpO2, skin temperature or other biomarker values. (Steps are the one count that pays, by the amount of steps, because they are movement and not a reading of the body: owner, 14 November 2026, section 22B. A step count is never a target, and a low day is never shown as a miss.)
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

HEAL follows `../FEATURES/DESIGN_SYSTEM.md` and uses its **utility presentation** intensity: a polished PMD-inspired module, not a medical dashboard with Pokémon decorations. HEAL-specific adaptations:

- Shared dialogue-box components for the Today summary, concise prompts, workout completion feedback, Field Research discoveries, connection/import status and primary quick actions. Dense histories, nutrition tables, timelines, sets, forms and charts use efficient layouts with selective framing; do not box every metric or row.
- Restrained line charts for trends, compact bars for activity summaries, clear numbers, textual equivalents and accessible labels. No excessive progress rings, medical-style gauges, composite scores or red/green judgments of normal variation.
- The buddy may appear contextually and react to eligible completion/discovery events; it must not comment unsolicited on weight, food, sleep quality or medical measurements.
- No continuous decorative movement on utility screens.

HEAL may define content-specific chart layouts but cannot fork shared component anatomy or global tokens.

## 17. Reuse policy

HEAL is an independent domain with its own models, repositories, local persistence, domain rules, import coordinator, provider interfaces, Field Research state and tests. Android/provider APIs belong in platform adapters; common HEAL domain logic must not depend on Compose or Android framework classes. Logical groupings (`activity`, `fitness`, `nutrition`, `sleep`, `measurements`, `healthrecords`, `goals`, `history`, `imports`, `research`) are not a requirement for one Gradle module each.

### 17.1 Reuse

Shared Pokémon collection, Pokédex, Bag, currency, reward ledger and acquisition interfaces; the app shell, navigation, local data lifecycle, permission coordinator and export/backup/encryption foundations; shared design-system components; the shared reminder scheduler through the HEAL source adapter; GEAR's generic unit-conversion utilities (HEAL owns health-specific semantics and presentation).

### 17.3 Replace or avoid

- Do not add health fields to PLAN task models or CARE persistence, or put health data in a miscellaneous global save object.
- Do not let UI screens call Huawei/Health Connect APIs directly, or couple common domain models to Health Connect types.
- Do not create a second Pokémon collection, Bag, currency, egg system or reward ledger.
- Do not claim an integration works until tested on the actual device, firmware, region and app version.

## 18. Implementation phases and exit gates

Dependency-first order; do not start a later phase while the preceding foundation or privacy gate fails. Status is in `../IMPLEMENTATION_BACKLOG.md`.

- **Phase 0, domain foundation:** one authoritative owner per record type, no dependency on PLAN/CARE persistence, testable source and unit semantics.
- **Phase 1, manual activity and fitness:** Today shell, activity, exercise library, workout sessions, sets, templates, basic goals and body measurements. Gate: offline, interruption recovery, timestamps preserved, survives process death.
- **Phase 2, nutrition and sleep:** food/serving model, saved foods, meals with nutrient provenance, hydration, sleep and recovery records, history and charts. Gate: unknown values stay unknown; no mandatory online lookup.
- **Phase 3, wearable/provider feasibility and imports:** test the Huawei Watch Fit 4 Pro on the actual phone, region, app version and firmware; implement only verified adapters; permissions, freshness, preview, reconciliation, deduplication, disconnect/delete. Gate: each advertised metric has verified provenance and history behaviour, repeated imports are idempotent, manual data is never silently overwritten, unsupported paths are marked blocked.
- **Phase 4, personal health records and reminders:** medication, symptom, appointment and note records; reminder adapter for medication, appointments, hydration and goals; secure export and selected deletion. Gate: private records stay local and never reach progression; no clinical dosage logic.
- **Phase 5, Field Research integration:** regional identity, eligibility policy, opt-out control, deduplication, payment history, shared encounter integration, generosity and anti-farming simulations. Gate: no sensitive data exposed, events idempotent, historical imports never reward, acquisition comparable with other modules.
- **Phase 6, polish and acceptance:** configurable Today, trends, accessibility, dark mode, reduced motion, performance, large-history, import recovery, migration, and real-device verification. Gate: functional, privacy, offline, accessibility, performance and real-device acceptance pass; provider limits documented without overstated support.

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
- Field Research uses minimal opaque eligibility events and is **on by default** (owner, 8 November 2026: every Pokémon acquisition method starts on; it was opt-in) and can be turned off.
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

Product-wide module map, ownership, privacy and shared Pokémon rules: `../PRODUCT_BIBLE.md`. Delivery status: `../IMPLEMENTATION_BACKLOG.md`. Explicit links only: `PLAN.md`, `CARE.md`. Shared reminders and sensitive-data architecture: `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md`. Visual language: `../FEATURES/DESIGN_SYSTEM.md`. Persistence, export, backup, restore, migration and recovery: `../ARCHITECTURE/DATA_AND_MIGRATIONS.md`. Collection, Bag, reward ledger and economy balance: the shared Pokémon/economy specification. This document is the single detailed requirements source for HEAL; do not duplicate those documents' implementation rules here.

## 22. As built: the manual slice

*Not run on a device.*

**What exists.** Section 2's Today card and menu; **activity** (type, minutes, when it ended, optional distance and note; edit, delete), **hydration** (quick amounts, any amount, the day's total, an optional target the player sets, history), **sleep** (a finished night, never a stage or a score), **body measurements** (weight, body fat, waist, hip, other; kept in the unit entered; kept off Today unless asked; a trend sentence only from three comparable entries, with no word that judges), **optional goals** (weekly active minutes, weekly active days, a daily drink total; pause, put away, delete; "so far", never "failed"), and a Settings page (Field Research, hydration target and reminders, weight unit, export and erase by kind). Later slices add **Connections** (imports, 22A), **Readings** (22B) and the optional screen lock (Settings → Privacy). Not built, and not offered: the exercise library, workout sessions and templates (section 4), meals and nutrients (section 5), personal health records (section 8), Today customisation, a consolidated History, and charts.

**Field Research (section 12), as built** *(numbers are *(tune)* starting values; introduced in `RewardRules.VERSION` 17, steps and readings joined in 24 and 25 (22A, 22B); `docs/economy.md`)*:
- **On by default** (owner, 8 November 2026: every acquisition method starts on), and off in Settings at any time. Turning it off keeps every record and loses nothing. A save that already stored the old default keeps its value.
- In this slice only two things can pay (steps, readings read in and Huawei sessions were added later: 22A and 22B), both only for a record **created now** (never edited or backfilled): an **activity** of at least 10 minutes that ended no more than 12 hours ago and not in the future (3 a day), and the **day's first drink** (1 a day). Sleep, measurements, goals, notes, calories and everything else never pay. Imported and derived records never pay here (Health Connect records never pay; the later exceptions are in 22A and 22B).
- A payment is **4 Rare Candies, a berry and a little friendship for Heal's lead** (an activity) or **1 Rare Candy** (the drink), a point of trainer rank, and points towards a **Field Egg** (2 and 1; 4 fill the meter), which hatches into Heal's party from the **Heal Region** (`tools/species/regions/heal.txt`, about 66 hatchable lines of sporty, outdoor and energetic Pokémon, ending with its patrons Raikou, Entei and Suicune).
- The event the shared world receives is exactly `HealEvent`: a stable `id` (`heal:<kind>:<record id>`), `module` ("heal"), an opaque `ref` (the record's random key), the broad `kind` (`activity_logged`, `participation`, and since rules versions 24 and 25 `steps_reached` and `records_imported`) and the time. No duration, distance, type of activity, volume or note exists in it. The game remembers the paid ids (`AppData.healPaid`, the newest 1000), so a replay (an edit, a delete, a restore of an older backup, a migration) never pays twice; the record also keeps `paid`.
- Heal's party (a lead and five) is a place like any other (POKEMON_ECOSYSTEM.md section 2.2): joining takes a Pokémon from where it was. Its perks (Friendship gain: Fighting, Normal, Fire, Electric, Flying, Dragon; Berry finder: Water, Grass, Ground, Ice, Bug, Fairy; capped at +25% a bucket) change only what Field Research pays, never a record or a total. Heal works fully with an empty party.
- **First-run tutorial** (POKEMON_ECOSYSTEM.md section 10.2.1): a 5-second walk in Heal gives one Common Heal Region Pokémon into Heal's party, leading it, once per save (`Tutorials.HEAL_WALK`); it creates no record, event, meter point, streak or cap use, and a player who has already logged an activity is not offered it.

**Data (section 14).** `heal_*` tables from the start (schema 7): `heal_activity`, `heal_hydration`, `heal_sleep`, `heal_measurement`, `heal_goal` (one family per kind, each row `key, position, day, json`) and `heal_state` (the settings). `HealTables` can read, export (`readKind`) or erase (`erase`) one family alone and `clear` all of HEAL, touching nothing outside `heal_*`; the app's own Export and Erase in Heal's Settings do the same through the store. Heal is in the daily backup and the backup bundle (`BackupBundle.KNOWN`); restoring a backup made before Heal existed leaves Heal's records alone. **Sealed since 0.154.0 (owner decision 6, 8 November 2026; sealing: `../ARCHITECTURE/DATA_AND_MIGRATIONS.md`):** every row is AES-256-GCM text under Heal's own Android Keystore key (`HealTables.seal`, once, read back; an install that had Heal in clear is moved at its first start with the key), the daily backup, the backup file and every kept-aside copy hold Heal sealed, and the passphrase backup seals it with Mint. A phone without the key opens a sealed Heal as empty and keeps the old rows aside, sealed. **Not done:** per-kind export of a *backup*. Heal has the optional screen-lock gate like Mint (Settings → Privacy, `Settings.lockedModules`).

**Reminders (section 13), as built.** Hydration only (besides the optional nightly or weekly reminder to read Huawei Health, 22A), off until the player adds a time (up to three a day), through the shared scheduler (`HealReminderAdapter`): the request holds only an opaque slot (`hyd-1000`), the words are the same for everyone ("A moment for a drink?"), the notification is private on the lock screen, the only button is "In 30 min" (there is no "done": a reminder is not a record and pays nothing), it follows the global quiet hours and focus deferral, and it stays silent once the player's own target for the day is met. Medication, appointment, goal check-in and the other sources of the table in section 13 are not built.

**Audit rules.** A night whose end is still to come is refused (only a finished night is a record, section 2.2); an active-days goal is 1 to 7; every history pages with "Show older" so no record is out of reach; every single delete offers Undo; a paid record is saved in one write. A drink, a night, an activity and a measurement can each be edited (`updateDrink`, `updateSleep`, `updateActivity`, `updateMeasurement`); an edit never pays again.

## 22A. As built: Health Connect, read only

*Health Connect has not been tried on a real phone, and **nothing about the Huawei Watch Fit 4 Pro has been verified**.*

**Classification** (`INTEGRATIONS_AND_PRIVACY.md` section 2): Health Connect is an *Android platform API*; Huawei Health is *unsupported or unverified* and is shown so in Heal → Connections. Section 10.1's feasibility matrix (each metric on the actual watch, phone, region and firmware) is **not done**; until it is, Heal claims no Huawei metric. What the code does allow: whatever Huawei Health writes into Health Connect on the player's phone is read like any other app's, and every imported record shows the app that wrote it, which is itself the evidence the matrix needs.

**What is read** (`HealthKind`, one Android read permission each, chosen by the player): exercise sessions (type, start, length), sleep sessions (start and end, no stages), weight, body fat, drinks (volume, time). Not read from Health Connect, and listed as such on the page (`HealthKind.unsupported`): steps, heart rate, blood oxygen, temperature, stress and sleep score (only Huawei Health's own pages carry those into HEAL, section 22B), heart rate variability, distance on its own and calories burned, sleep stages, nutrition, medication and symptoms. No write, no background and no history permission is requested, so Android limits a read to the 30 days before the first grant.

**Flow** (section 10.3): choose kinds → Android's own permission screen (rechecked on every visit and every read; a revoked permission shows as such) → **Read now** (last 7 or 30 days, on the player's tap only) → a **preview** (per kind: to add; already here; left out because impossible or in the future; flagged) → **Add them**. Reading is paged and bounded (500 a page, 10 pages, 5,000 records); a read that was cut short imports what came and says so, and does not move the last-read time. Manual logging never depends on any of it.

**Records** (section 11.1, `Provenance`): every imported record has `source = IMPORTED` and the provider, the writing app's package, the provider's record id and the import time. Nothing raw is kept; no exception text is logged or shown.

**Deduplication** (section 10.4, `HealImport`): (1) the provider's record id: a record already here is skipped, so a retry or a second read adds nothing; (3) a conservative match against any record already here (activity: start within 2 minutes and length within 2; sleep: start and end within 15 minutes; measurement: same type within 5 minutes and 0.06 of the value after unit conversion; drink: same volume within 2 minutes). A match is **added beside the existing record and flagged**; the player chooses Keep both or Delete the import. A record the player wrote is never changed, replaced or deleted by an import. Matching between two imported records of the same event from different apps is not done.

**Rewards** (section 10.3): Health Connect records never pay and never repay history (since rules version 25 the act of reading a new family of records in pays a small flat amount, 22B; the records themselves still pay nothing). The one exception (owner, 14 November 2026, rules version 24): a session read from Huawei Health's own pages pays like a manual one if it ended within the last 8 days and is not flagged as a repeat (`HealRules.paysLikeManual`, `IMPORT_FRESH_MILLIS`); the store pays it after applying the import, oldest first, under the daily share of the session's own day. Sleep, measurements and drinks never pay from an import.

**Disconnect and delete** (distinct, as `INTEGRATIONS_AND_PRIVACY.md` section 10.3 asks): *Disconnect* clears the chosen kinds, revokes Android's permissions and keeps every imported record; *Delete what an app added* removes the imported records of one writing app (or all) and nothing the player wrote. The Erase buttons in Settings still erase by kind.

**Android:** `androidx.health.connect:connect-client:1.1.0` (androidx, no telemetry, no network of its own); five `READ_*` permissions; the `com.google.android.apps.healthdata` query; `HealthRationaleActivity` (`ACTION_SHOW_PERMISSIONS_RATIONALE` and the Android 14 `VIEW_PERMISSION_USAGE` alias); `AndroidHealthConnect`.

**Owner decision, 14 November 2026 (built in 0.186.0, section 22B):** HEAL keeps daily steps, heart rate and resting heart rate, SpO2, skin temperature, and Huawei's stress number and sleep score as records, history only and never judged; steps pay per amount.

**Huawei Health on the phone (14 November 2026; `INTEGRATIONS_AND_PRIVACY.md` 5.2.2):** an accessibility service declared for `com.huawei.health` only and a notification listener that drops every other app's notifications; on a tap, PokéLife opens Huawei Health, scrolls and captures each page, and shows what it saw (in memory only, copyable with every digit hidden). Since 0.185.0 the workouts (Exercise records), weight and body fat (Weight page) and today's sleep it reads are saved into Heal's existing records after a preview, and a workout pays like a manual one (rules version 24, under Rewards above); 22B adds steps and readings. English labels only.

**Desk research:** Huawei Health is reported not to write into Health Connect itself; Huawei's Health Kit needs a developer registration and a per-app approval that only the owner can start (until then no scopes, no adapter); a third-party bridge the player installs could feed Health Connect and so this import, but is unofficial and unverified, and HEAL never depends on it. **Still to do:** real-device verification of every advertised metric on Android 13 and earlier (the Health Connect app) and 14 and later; the Huawei feasibility matrix (steps, workouts, heart rate, sleep and stages, weight and the rest, with history depth and sync delay); Huawei Health export import; HRV records (steps, heart rate, resting heart rate, SpO2, skin temperature, stress and sleep score exist since 0.186.0, 22B, and only from Huawei Health's own pages); background and historical reads (not requested on purpose); duplicate matching across imported sources; distance from `DistanceRecord`.

## 22B. As built: steps and readings from Huawei Health (0.186.0)

*Owner decision, 14 November 2026. Parsers written from the owner's probe capture of that day (Huawei Health in English); the resting heart rate card is not in that capture and waits for one; nothing has run on a phone beyond it.*

**Records** (schema 11, `heal_steps` and `heal_vital`, sealed like every Heal row; `StepsRecord`, `VitalReading`, `VitalType`). **Steps:** one record per day and provider, the largest count read that day (a day's steps only grow; a smaller later count changes nothing), with how many step amounts Field Research paid. **Readings:** heart rate (one per distinct bpm and day), resting heart rate, SpO2, skin temperature (°C), Huawei's own stress number and Huawei's own sleep score (one per day each; a later read of the same day that shows another value updates it). The Health page gives no time for a reading, so each carries the time PokéLife read it, and the Sleep page's score is stamped on the night it shows. They are history only: never edited (a wrong one is deleted), never called good, bad, high or low, no target, no trend sentence. Huawei's word beside the stress number ("Normal") is not kept. They are in Heal → **Readings**, in export and in erase, one kind at a time.

**Where they come from.** The Health page's summary (steps, the stress number) and cards (Heart: `Heart rate ## bpm`, and `RHR` if the card shows it; Skin temperature: `##.#°C | …`; SpO₂: `##%`), the Health insights line "Your Sleep score is ## points", the Sleep page (`scoring_value` and its date), and the step notification ("#,### steps"). Ranges that make a reading a reading at all: steps 1 to 200,000, heart rate 25 to 250, resting 25 to 150, SpO2 50 to 100, skin temperature 25 to 45 °C, stress 1 to 99, sleep score 1 to 100; anything else is rejected, never repaired. A page that shows "--" or 0 steps is missing, not zero.

**No duplicate data.** Every record has an id made from its own day (and bpm), so the same page read twice, the page and the notification, or the Sleep page and the insights line, are one record; steps and the once-a-day readings update in place; the preview says how many are new, updated and already here. The reader also taps each opened page's **Week** tab when it has one (the Sleep page does) so the week's page is captured for the next probe; the week view is not parsed yet, and a week range is never taken for a night.

**Rewards** (rules version 25, `docs/economy.md`): **steps pay per amount** (one payment per 2,000 steps of today, at most 5 a day: 1 Rare Candy and 1 friendship step to Heal's lead); **reading records in pays** 1 Rare Candy for each family of record a read brought something new and recent in, once a day each, at most 3 a day, for Health Connect and Huawei alike (not for steps, which pay by the amount, nor for a workout from Huawei, which already pays as a session). Neither adds Egg meter points, trainer points, berries or item drops. **No reading, score or sleep value pays for what it says**; a family of readings pays once because it was read in, whatever the numbers were. What was paid is kept as marks in the sealed settings (`HealSettings.paidMarks`, 45 days), so deleting a record and reading it again pays nothing twice. Field Research off pays nothing and records everything.

## Shared Pokémon ecosystem contract

HEAL's acquisition identity is **Field Research**. HEAL owns eligibility for user-completed activities and approved participation events; the shared Pokémon ecosystem owns reward settlement, canonical Egg/owned-Pokémon records, Pokédex, inventory and progression. HEAL has an independent party referencing canonical owned Pokémon IDs. Only minimal, non-sensitive event metadata may be submitted; measurements, biometrics, health outcomes and medical records are excluded. See `../FEATURES/POKEMON_ECOSYSTEM.md`.

---

## Testing reference

HEAL health-data provenance, permission denial/revocation, import deduplication, offline history, privacy isolation and provider failure must follow [`../ARCHITECTURE/TEST_STRATEGY.md`](../ARCHITECTURE/TEST_STRATEGY.md). Use synthetic health fixtures only.
