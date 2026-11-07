# PokéLife — Data Architecture and Migrations

**Version:** 1.1  
**Status:** Approved target architecture (owner, 6 November 2026; implementation details such as the database choice stay subject to their stated feasibility spike)  
**Baseline inspected:** PokéLife source archive `pokeplan-reboot-main (5).zip`, version `0.123.0`  
**Review date:** 6 November 2026

> This document defines the target local-first data architecture, persistence contracts, backup lifecycle, and migration requirements. It distinguishes verified behavior in 0.123.0 from recommendations. Product behavior remains owned by the Product Bible and module/feature specifications; this document owns the technical data foundation. A source file's presence is evidence of implementation, not proof of end-to-end correctness or release readiness.

## 1. Purpose and architectural principles

PokéLife must have one dependable local data lifecycle across its eight modules and shared Pokémon ecosystem. It must remain useful without an account, network connection, cloud service, or third-party integration.

The architecture follows these principles:

1. **Local-first authority.** The on-device database is authoritative for PokéLife-owned records. Network services are optional providers, never required for core use.
2. **One database, clear ownership.** One local database provides atomic cross-domain transactions. Each module owns its records, validation, and business rules through its repository/service boundary.
3. **No duplicate canonical records.** Other modules use stable references and read-only projections rather than copying another module's editable data.
4. **Shared systems stay shared.** Pokémon ownership, Pokédex history, Bag, currency, rank, reward ledger, and reminder infrastructure have one authority each.
5. **Domain logic is platform-independent.** Domain models and policies do not depend on Compose, Android framework APIs, or storage implementation details.
6. **Transactions before notifications.** A user action is committed before UI state, notifications, or cross-module projections report success.
7. **Preserve history.** Completion, payment, progression, evolution, acquisition, and imported-source history must survive edits and migration where the source data permits.
8. **Recovery is designed in.** Backup, restore, schema migration, interrupted writes, corruption, and low-storage behavior are foundational requirements.
9. **Privacy by ownership and minimization.** Sensitive module content does not cross module boundaries unless an explicit user-authorized contract requires it.
10. **No destructive migration without a verified backup.** The original save remains recoverable until the new database has passed validation and acceptance.

## 2. Evidence and status from version 0.123.0

The following are confirmed by inspection of the supplied source archive. They are not the target architecture.

| Area | Confirmed implementation | Assessment |
|---|---|---|
| Language and UI | Kotlin Multiplatform and Compose Multiplatform | Reusable foundation; Android is the product priority |
| Core modules | `pokemon-core`, `plan-core`, `care-core`, `world-core`, `app-core`, `composeApp`, `androidApp` | Some domain separation exists; it does not yet represent the final eight-module product |
| Main persistence | `androidApp/.../SaveFile.kt` creates `save.json`; `GameStore` persists serialized `AppData` through `SavedDocument` | Single JSON document for much of the app, including PLAN, shared Pokémon, GEAR, and WORLD state |
| CARE persistence | `careStorage()` creates a separate `care.json`; `CareStore` persists `CareData` independently | Separate save and separate commit boundary |
| Storage abstraction | `Storage` exposes `load`, `save`, and `keepAside`; `MemoryStorage` supports tests | Simple document storage contract, not a relational repository abstraction |
| File storage | `FileStorage` writes a temporary file and attempts rename; on failure it falls back to writing the destination directly | Better than a direct-only write, but the fallback is not an atomic replacement guarantee |
| Save decoding | `SavedDocument` decodes JSON, applies `tidy`, and preserves unreadable input with `keepAside` | Valuable corruption-preservation behavior to retain and strengthen |
| Save schema | `AppData.SCHEMA` is currently `1`; many fields have defaults | Default-field compatibility exists; this is not a sequential schema migration framework |
| IDs | `newId(nowMillis, rng)` creates time-plus-random string IDs | Existing IDs must be preserved; new IDs should not depend on wall-clock uniqueness |
| Pokémon | `OwnedPokemon` is an individual entity with an ID, species/form, nature, ability, IVs/EVs, level, friendship, shiny state, held item, and party metadata | Valuable individual identity and progression model to preserve and normalize |
| Rewards | `RewardEngine` applies activity rules and returns state/reward outcomes; stores call into shared World/reward interfaces | Reusable domain logic; reward persistence and source mutation are not yet one universal relational transaction |
| Compatibility tests | `SaveCompatibilityTest` checks missing default-valued fields, stored enum names, and old save behavior | Useful regression tests; expand to real fixtures and explicit migration tests |
| Cloud pathways | Account/session fields, cloud document methods, Supabase schema/functions, and optional CARE sync exist | Conflict with the approved no-account/no-cloud-sync product direction; retire after local data is secured |

### 2.1 Important existing failure windows

- `SavedDocument.write()` assigns its in-memory `value` before calling `storage.save()`. A failed save can therefore leave the process observing a value that was not durably persisted.
- `FileStorage.save()` normally writes a temporary file and renames it, but its direct-write fallback can expose a partial destination if the fallback is interrupted.
- CARE's completion and its call into the shared reward/World interface cross two separately persisted stores. A process failure between those writes can leave a completion and its reward out of sync.
- JSON compatibility through default values does not explicitly transform changed meanings, split/merged records, IDs, or relationships.

The target must close these windows with database transactions, a single authority, explicit migration steps, and an idempotent event/reward pipeline.

### 2.2 Source material and precedence

Use the latest approved files under `/PokéLife/` as product and module authorities. The source archive's `docs/ecosystem.md`, historical Pokémon progression documents, and the earlier `02_DATA_MODEL_AND_PRIVACY.md` are useful evidence, not competing current authorities. The canonical shared Pokémon domain specification is `FEATURES/POKEMON_ECOSYSTEM.md`; this document owns physical persistence, repository boundaries, transactions, backup/restore, migration execution and recovery, while the Pokémon Ecosystem document owns shared Pokémon domain semantics.

## 3. Target application architecture

Use a modular, layered architecture. The package/module names below are conceptual boundaries; adapt naming to the repository's Gradle conventions without weakening ownership.

```text
application shell
├── app / navigation / module registry
├── platform
│   ├── Android lifecycle, notifications, files, permissions
│   ├── sensors, intents, document picker, backup destination
│   └── platform-specific database driver
├── design-system
│   └── shared PMD-inspired components and tokens
├── data
│   ├── database and schema
│   ├── repositories / transaction coordinator
│   ├── migrations and legacy JSON importer
│   ├── backup / restore / import / export
│   └── attachment and local search infrastructure
├── shared domain services
│   ├── pokemon
│   ├── economy / reward ledger
│   ├── reminders
│   ├── event contracts / outbox
│   └── integration provider contracts
└── product domains
    ├── plan
    ├── care
    ├── heal
    ├── mint
    ├── home
    ├── link
    ├── gear
    └── world
```

### 3.1 Layer responsibilities

**Presentation.** Compose screens render immutable state and emit user intent. They do not write database tables, calculate rewards, generate recurrence, or own persistence.

**Application/use-case layer.** Coordinates user actions across domain services. It defines transaction boundaries, invokes authoritative repositories, and returns a committed result or a recoverable error.

**Domain layer.** Owns business rules, validation, domain entities, policies, and platform-neutral interfaces. It must not depend on Compose, Android framework classes, SQLDelight generated implementation classes, or file APIs.

**Data layer.** Implements repositories, SQL queries, transaction coordination, migrations, import/export, backup/restore, and local indexes. It enforces database constraints in addition to domain validation.

**Platform layer.** Implements Android-specific scheduling/delivery, permissions, document picker, file access, lifecycle hooks, sensors, and database driver setup. Platform failure must not invalidate already committed domain records.

### 3.2 Dependency rules

- Presentation depends on application/domain APIs, not concrete database tables.
- A module may access its own repository interfaces and approved shared service interfaces.
- A module must not import another module's persistence implementation or mutate its tables directly.
- Shared services must not depend on module UI or module implementation details.
- Cross-module interaction uses stable event/request contracts and opaque record references.
- Database foreign keys may enforce referential integrity, but do not turn cross-module SQL access into a public module API.
- `data` may depend on domain contracts to implement repositories; domain code must not depend on `data`.
- Static canonical reference data (species, forms, moves, item definitions, assets metadata) is versioned separately from user-owned records.

## 4. Persistence technology and database lifecycle

### 4.1 Recommendation

Use **SQLite with SQLDelight** as the target relational persistence technology, subject to a small Phase 1 feasibility spike against the current Gradle/Kotlin Multiplatform versions and Android deployment target. Because Android is the only product platform (`PRODUCT_BIBLE.md` section 3), the spike judges the Android driver only; the repository's desktop and web builds do not need the database and may keep an in-memory or JSON development store.

Reasons:

- The supplied project is Kotlin Multiplatform/Compose, so SQLDelight offers typed SQL and generated Kotlin interfaces while retaining SQLite as the durable engine.
- SQLite supplies transactions, foreign keys, uniqueness constraints, indexes, and mature local-file behavior.
- The Android driver can be hosted in the Android platform layer while domain contracts remain common Kotlin.
- SQL schema migrations can be versioned and tested independently from the legacy JSON importer.

Do not introduce Room solely by habit: it is a reasonable Android-only alternative, but the current codebase is KMP. If the feasibility spike demonstrates that SQLDelight materially harms maintainability or Android reliability, compare Room plus an explicit Android-only data boundary before changing this recommendation. Do not implement two competing database stacks.

### 4.2 Database file and connection policy

- One primary application database in app-private storage.
- One database connection manager and transaction coordinator per process.
- Enable and verify SQLite foreign-key enforcement on every connection.
- Use WAL mode where supported and validated; define checkpoint behavior for backup.
- Choose a synchronous/durability policy appropriate to the data-loss tolerance and benchmark on target devices. Do not weaken durability for cosmetic performance.
- Use prepared statements/typed queries; never build SQL from untrusted text.
- Keep attachments as files in app-private storage, with database metadata and stable references. Do not store large media blobs in ordinary record columns unless a specific need justifies it.
- Store timestamps in UTC epoch milliseconds or a consistent UTC representation; store the original time-zone/local-time context when the domain meaning depends on it.
- Keep database backups and export archives outside the live database directory.

### 4.3 Single-writer and process coordination

The application and Android entry points such as widgets or notification actions must not independently load and rewrite whole snapshots. They call the same repository/use-case boundary and database transaction layer. If multiple Android processes remain necessary, explicitly configure SQLite multi-process access, transaction serialization, and invalidation; otherwise prefer one process and IPC/intent routing into it. Never rely on stale in-memory `AppData` snapshots to overwrite newer committed changes.

### 4.4 Database version metadata

Maintain a schema version in SQLite's migration mechanism and a small application metadata table. Record the installed app version separately from the database schema version. App version changes do not automatically imply a schema migration, and a schema migration does not imply a product-content reset.

## 5. Domain ownership and authoritative records

Every canonical record has exactly one owner. Cross-module screens use read-only projections or explicit links.

| Domain / capability | Canonical owner | Authoritative records and responsibilities |
|---|---|---|
| Application profile and global settings | Application foundation | Local profile, theme/accessibility/global preferences, data lifecycle state |
| PLAN | PLAN | Tasks, task occurrences, lists/projects, recurrence definitions, goals, milestones, focus sessions and planning history |
| CARE | CARE | Self-care routines, routine history, diary cards, journal entries, notes, mood check-ins, skill practice and CARE settings |
| HEAL | HEAL | Activity, fitness, nutrition, sleep, body measurements, health observations, source/import history and HEAL settings |
| MINT | MINT | Budgets, manual transactions, accounts/cards/e-wallet references, recurring bills, payment status and finance settings; provider-reported facts retain source provenance |
| HOME | HOME | Household chores/occurrences, inventory, areas, assets, maintenance, warranties, household reference records and attachments |
| LINK | LINK | Contacts, relationship notes, important dates, relationship follow-ups and LINK settings |
| GEAR | GEAR | Utility preferences and locally retained utility history where specified; calculations and transient tool state need not be persisted |
| WORLD | WORLD | Campaign/story progress, expeditions, missions, settlement state, dungeon progress and WORLD-specific records |
| Pokémon reference catalog | Shared Pokémon service / packaged reference data | Canonical species, forms, moves, types, abilities, evolution references and catalog version |
| Pokémon collection | Shared Pokémon service | Individual owned specimens, acquisition/evolution history, Pokédex/form/shiny history, party/PC membership and buddy selection |
| Economy | Shared economy service | Poké balance, Bag/inventory, reward ledger, rank/progression balances and reward policy versions |
| Reminders | Shared reminders feature | Delivery requests, schedule metadata, notification identities and recovery state; never source business records |
| External provider facts | External provider remains source authority | PokéLife stores permitted local snapshots/imports with source, timestamp, external identity and provenance |

### 5.1 Module ownership rules

- A HOME chore remains a HOME record even if PLAN displays a linked task or reminder.
- A HEAL workout or measurement remains in HEAL even if another module displays a safe summary.
- A MINT bill/payment remains in MINT; reminder scheduling does not own payment status.
- CARE journal, mood, and diary content stays in CARE. It is not exposed to WORLD or reward presentation.
- WORLD owns adventure progress but not global Pokémon ownership, Bag, currency, or reward ledger.
- GEAR tools do not own module progression and do not generate rewards merely from being opened or used. Helper Pokémon are visual helpers, not a separate collection or acquisition path.
- Imported calendar events remain provider-owned read-only projections unless a module's specification explicitly defines a user-created local record.
- External bank/e-wallet/health provider data must preserve whether it was imported, manually entered, or observed through an authorized integration.

### 5.2 Read models and projections

A dashboard or cross-module view may combine data from multiple repositories into a derived read model. A projection is rebuildable and is never a second editable canonical record. Store a projection only when performance requires it, mark it as derived, and define invalidation/rebuild behavior. A linked record stores the target module and opaque target ID, not a serialized copy of the target record.

## 6. Shared contracts and services

### 6.1 Repository contract

Each domain exposes operations such as create, update, complete, archive, restore, and query through its own repository/use-case API. The API accepts domain intent and returns committed results. It does not expose arbitrary table access or mutable global state.

### 6.2 Transaction coordinator

Use one transaction boundary for any operation that must update multiple records/services atomically. A transaction callback receives only the required repository/DAO operations. Nested domain services participate in the existing transaction; they must not open independent transactions that commit early.

### 6.3 Shared Pokémon contract

Modules and WORLD request eligible acquisition/progression through the shared Pokémon/economy contract. They do not insert `owned_pokemon`, update Poké balance, or grant items directly. The shared Pokémon specification at `../FEATURES/POKEMON_ECOSYSTEM.md` remains authoritative for species/forms, individual specimen identity, independent module parties, separate WORLD expedition teams, PC/storage, evolution, acquisition pools, buddy, shared Bag/Poké and progression semantics. Physical tables and transaction boundaries are owned here.

### 6.4 Reminder contract

Section 9 of `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md` is authoritative for scheduling, quiet hours, focus deferral, Android delivery, action routing, reboot recovery and reminder privacy. Each module owns its source record and source adapter. Shared reminder tables hold only stable reminder IDs, module identifiers, opaque source references, timing metadata, action keys, local-time/time-zone interpretation, minimal policy flags, stable notification identities and delivery/reconciliation state. The source module remains authoritative; scheduler rows are rebuildable metadata, not business records.

### 6.5 Integration contract

Each provider adapter declares capabilities, permissions, provenance, refresh behavior, error states, and deletion/disconnect behavior. Provider access is optional. Local manual entry and cached permitted snapshots remain usable offline. Provider credentials belong in platform secure storage, not ordinary domain tables or exports.

## 7. Entity identity, relationships, and constraints

### 7.1 IDs

- Use UUIDv4/UUIDv7 or another collision-resistant opaque identifier generated by a well-tested common ID provider. Choose one format and document it; do not mix formats without a migration need.
- IDs are immutable and never reused, including after deletion.
- IDs must not encode names, sensitive data, species, module content, or precise timestamps.
- Preserve valid legacy IDs during migration. If a legacy identifier is invalid or collides, create an explicit old-to-new ID mapping and retain it in the migration audit.
- External provider IDs are stored separately with provider namespace and account/source scope; they are not used as PokéLife primary keys.
- Human-readable slugs and display names are not identity keys.

### 7.2 Common metadata

Persisted domain entities generally include:

- `id` (stable primary key)
- `created_at` (UTC)
- `updated_at` (UTC)
- optional `archived_at` or `deleted_at` only where the domain needs recoverable deletion/history
- optional `revision` or optimistic concurrency token for edits that may originate from multiple entry points

Do not add generic audit metadata to transient UI state or every derived row. Domain-specific historical events should be explicit records.

### 7.3 Core shared logical entities

The following is a logical catalogue, not a mandate to create one table for every noun. Normalize entities where they have independent identity, lifecycle, query needs, or relationships.

| Entity | Key relationships / constraints |
|---|---|
| `app_profile` | One local profile; no account identity requirement |
| `app_settings` / `module_settings` | One row per settings scope/module; global settings are not duplicated |
| `pokemon_species` / `pokemon_form` | Packaged/versioned canonical catalog; form belongs to species |
| `owned_pokemon` | Unique specimen ID; references canonical species and form; mutable current attributes |
| `pokemon_history` | Immutable acquisition, evolution, transfer, release, and milestone facts referencing specimen ID |
| `pokemon_dex_history` | Species/form/shiny discovery facts survive transfer or deletion of a specimen |
| `pokemon_place` (replaces `pokemon_party`, `pokemon_party_slot` and `pokemon_buddy_selection`, schema 6) | **One place per specimen** (owner decision, 7 November 2026): the specimen is the primary key, so a second row cannot exist; kind `buddy` (exactly one row), `party` (the module's party id and a slot, the lead in slot 1, unique per party) or `guardian` (a slot); no row means the PC. A WORLD team and a GEAR helper are the other two places and stay in their modules' records; the live write refuses a save that names one Pokémon in two |
| `item_definition` | Canonical item metadata/version |
| `inventory_stack` | Unique stack identity; item reference; quantity constraints; optional stack metadata/provenance |
| `reward_event` | Unique idempotency key; source module/reference; event type; policy version; outcome and timestamp |
| `reward_ledger_entry` | Append-only delta for currency/item/other ledgered reward; references reward event; reversal uses compensating entry |
| `reward_balance` | Materialized projection maintained in the same transaction as ledger entries; rebuildable from ledger |
| `domain_outbox_event` | Stable event ID, contract version, source, minimal payload, creation/processing state |
| `processed_event` | Unique consumer + event ID to prevent duplicate handling |
| `reminder_request` | Stable ID, source module/reference, action key, due definition, delivery state; no copied business content |
| `external_source` / `import_batch` | Provider identity, import time, source schema/version, status, and provenance |
| `attachment` | Stable attachment ID, private relative path, media type, size, checksum, owner module/reference; deletion state |
| `migration_history` | Applied migration version, checksum, start/completion state, and outcome |

### 7.3.0 Decision: the app keeps reading one `AppData` through the split *(owner delegated, 7 November 2026, REP-014)*

The shared Pokémon, Bag and ledger tables are the live authority since schema 6 (0.135.0), and the app reaches them **the way it reaches PLAN's tables**: `LiveDocuments` splits every save into PLAN's part, the player's part and the rest, writes the first two as rows and puts the save back together on a read, so `GameStore`, the reward engine and every screen still read and write one `AppData` and the rules stay pure and tested. The typed repositories (`PokemonRepository`, `EconomyRepository`) are the tables' own API and are used by the split and the importer, not yet by the app.

What the typed repositories still need before the app can move onto them (a later REP-014 step, per module as each is refactored): a `GameStore` that asks them for one Pokémon, one party or one balance instead of holding the whole list; a reward-bearing action that calls `EconomyRepository.settle` with its **real** event (`<module>:<source record>:<kind>`) instead of the live write's net change of one save (section 8.4 "As built"); the WORLD team and GEAR helper places moved into `pokemon_place` (their records are still inside the `save` row); `pokemon_history` (acquisition and evolution facts) and held-item references; and World's rules taking the balance as an input so `WorldData.poke` can go.

**Step 1 as built (0.144.0, REP-014): module rewards settle their real events.** `AppData.ledgerNotes` holds one `LedgerNote` per paid HEAL, MINT or HOME event (`GameStore.heal/mint/home` through `noted`): the event's own id (`home:chore:<id>:<day>`), source module, opaque reference, broad kind and what it changed in the Poké balance and the Bag. `PokemonTables.settleNotes` settles each note through `EconomyRepository.settle` under that id (idempotent: an id already in the ledger is skipped) **before** the net change, which still brings the tables to the document, so a note that does not match the save (or one that would go below zero) is corrected, never trusted. The ledger now says *why* for these three modules. Still open from the list above: PLAN's task rewards and CARE's moments (CareEvent has no id yet) still arrive as the net change; the other items (repositories asked by `GameStore`, WORLD and GEAR places in `pokemon_place`, `pokemon_history`, World taking the balance as an input) are unchanged.

### 7.3.1 Expanded Pokémon item and progression records

The logical model must support the expanded shared Pokémon contract without creating parallel module inventories:

| Entity | Key relationships / constraints |
|---|---|
| `item_definition` | Stable canonical item ID, category, stack/equipment/consumable classification, compatibility, acquisition-source metadata and definition version |
| `inventory_stack` / `item_instance` | Shared Bag ownership, positive quantity or unique instance, provenance and stable identity |
| `held_item_assignment` | Owned Pokémon ID plus item instance/stack reference; unique active assignment per item; one primary held item per Pokémon in the initial model |
| `item_ledger_entry` | Grant, spend, purchase, craft, transfer, equip, unequip, correction or reversal; stable source event and idempotency key |
| `item_effect_runtime` | Scoped trigger/cooldown/duration state keyed to assignment and encounter/floor/expedition; never changes item quantity |
| `pokemon_ability_state` | Selected canonical ability, eligible slot, Hidden Ability eligibility and change history |
| `pokemon_nature_state` | Original nature, optional mint-adjusted effective modifier and history |
| `pokemon_move_state` | Learned moves, active slots, mastery and acquisition source |
| `pokemon_ribbon_mark` / `pokemon_research_state` | Individual milestones and canonical provenance; PokéLife achievements remain separate |
| `pokemon_transformation_state` | Owned Pokémon reference, eligible transformation, temporary encounter scope and resolution; no duplicate owned instance |
| `item_recipe` / `crafting_event` | Versioned recipe, declared ingredients, output and atomic settlement |
| `pokemon_job` | Assigned owned IDs, compatible job, time range, settlement state and output event |

**Held-item persistence invariant:** a held item is persistent, reusable, non-consumable equipment and never lifetime single-use. Equipping, triggering, activating, unequipping, swapping, battle/expedition completion, evolution, process death and restart do not decrement quantity or destroy the item. A per-encounter trigger limit resets for later eligible encounters; it does not consume the item. Runtime trigger state is separate from the item instance. A held item cannot be consumed as a crafting ingredient while assigned. The database must enforce one active assignment per item instance and the approved one-held-item-per-Pokémon rule. Canonical consumables such as evolution stones and vitamins use separate explicit consumption operations.

Item definitions must declare acquisition source families. Item grants, purchases, crafting, transfer and any consumable use participate in the shared transaction coordinator and item ledger. Legacy Bag/held-item reconciliation must preserve total item ownership without multiplying duplicate views; if a legacy save only records an equipped item, migration may recover exactly one item instance with provenance and must not fabricate extras.

### 7.4 Module-owned entity families

**As built:** CARE's records are in `care_*` (schema 4, 0.132.0) and PLAN's in `plan_*` (schema 5, 0.133.0), each as `key, position, day, json` tables plus a `*_state` table for single values (and `plan_day` for the rest and active days). PLAN shares the `AppData` document with the player, so `LiveDocuments` splits every save into PLAN's part (tables) and the rest (the `save` row) and puts them together on a read; a Habit record no longer exists (merged into recurring tasks, `MODULES/PLAN.md` section 11.2).

**As built, the shared tables (schema 6, 0.135.0):** `owned_pokemon` (typed columns, the order they were owned in `position`, the origin exactly as the save has it, any field a newer build adds in `extra_json`), `pokemon_place`, `dex_history`, `player_progress` (Egg meter, trainer points), `bag_stack`, `currency_balance`, `reward_event` and `reward_ledger_entry`, and `pokemon_state` for the few single values with no table (the party leads, the roles version, anything a newer build adds to the player, the favourites and origins of Pokémon that are gone). `PokemonTables` takes that part of the save apart and puts it back; the `save` row keeps only what is neither PLAN's nor the player's (settings, World, GEAR, quests, the reward log, counters). `evidence/REP-014-shared-tables.md`.

Detailed fields remain in module specifications. The data layer should accommodate at least these families:

- **PLAN:** `task`, `task_occurrence`, `task_series`/recurrence, `subtask`, `task_list`, `project`, `goal`, `milestone`, `focus_session`, `focus_history`, and any explicitly approved calendar cache.
- **CARE:** `self_care_routine`, `routine_completion`, `diary_card_entry`, `journal_entry`, `note`, `mood_check_in`, `skill_practice`, `entry_tag`, and `entry_link`. *Built first (0.132.0, schema 4):* `care_routine`, `care_routine_completion`, `care_diary_card`, `care_journal_entry`, `care_calm_break`, `care_skill_practice`, `care_visitor`, `care_dismissed_visitor` and `care_state`, each record table holding its key, position, day and the record as JSON (`evidence/REP-014-care-tables.md`).
- **HEAL (as built, 0.136.0, schema 7):** `heal_activity`, `heal_hydration`, `heal_sleep`, `heal_measurement`, `heal_goal` and `heal_state`, created in tables from the start with no document row (`HealTables`, `MODULES/HEAL.md` section 22); activation sets a marker and imports a `heal` document row if a restore left one. Planned, not built: `health_metric_definition`, `health_observation`, `activity_session`, `workout`, `exercise_set`, `food_entry`, `nutrition_summary`, `sleep_session`, `body_measurement`, `health_source`, and `import_batch`.
- **MINT (as built, 0.141.0, schema 8):** `mint_account`, `mint_category`, `mint_transaction`, `mint_budget`, `mint_bill`, `mint_bill_payment`, `mint_reconciliation`, `mint_review` and `mint_state`, created in tables from the start with no document row (`MintTables`, `MODULES/MINT.md` section 22); every amount an integer in minor units inside the record's JSON, **every row sealed (AES-256-GCM, Android Keystore key) and every backup or kept-aside copy of Mint sealed**: this is the first use of section 10.4's encryption, chosen for Mint only (`evidence/REP-014-mint.md` section 6). Planned, not built (the logical families below): `financial_account`, `account_snapshot`, `transaction`, `budget`, `budget_period`, `bill`, `bill_occurrence`, `payment_record`, `debt`, `savings_goal`, and provider import provenance.
- **HOME:** `household_area`, `chore`, `chore_occurrence`, `inventory_item`, `inventory_adjustment`, `asset`, `maintenance_schedule`, `maintenance_record`, `warranty`, `reference_record`, and attachment references.
- **LINK:** `contact`, `relationship_note`, `important_date`, `follow_up`, `contact_method`, and explicit contact-to-record links.
- **GEAR:** tool preferences and intentionally retained history only; ephemeral conversions/calculations should remain transient.
- **WORLD:** `campaign_progress`, `story_state`, `expedition`, `expedition_team_member`, `mission`, `dungeon_progress`, `settlement_state`, and other WORLD-owned game state.

### 7.5 Constraints and indexes

Enforce where applicable:

- Primary-key uniqueness and foreign-key integrity.
- Nonnegative inventory quantities and valid currency minor-unit precision.
- Valid Pokémon level, IV/EV, friendship, species/form compatibility, and item references.
- Unique party slot position within a party and no duplicate owned specimen in one party.
- Unique reward idempotency key and unique consumer/event processing key.
- Unique external source identity within provider/account scope where the provider supplies a stable ID.
- Unique reminder identity and stable source/action association.
- Valid date/time and recurrence fields at the domain boundary.
- Explicit nullable/unknown states; unknown quantity or missing measurement must not be silently converted to zero.

Index common access paths, not every column:

- Module records by status, due date, created/updated time, and parent/reference ID.
- Occurrences by series/source ID and scheduled time.
- Pokémon by species, form, shiny state, party, and acquisition time/source.
- Reward events by source reference, event type, and timestamp.
- Ledger entries by event ID and timestamp.
- Inventory by item ID and update time.
- Reminders by due time, source reference, and delivery status.
- Health/finance imports by provider, external ID, observed time, and batch.
- Attachments by owner module/reference.

Avoid broad indexes on sensitive free-text content. If local search uses an FTS index, keep it private, rebuildable, and synchronized with deletion/retention rules.

## 8. Transactions and consistency

### 8.1 General rule

The database is authoritative. An operation is successful only after its transaction commits. Update in-memory observable state only from committed repository results or database invalidation; never optimistically publish an uncommitted authoritative snapshot as saved.

### 8.2 Reward-bearing operation

A qualifying module action and its reward must be committed atomically. Conceptual sequence:

1. Load and validate the canonical source record inside the transaction.
2. Verify the action is eligible under the module's current policy.
3. Apply the source state change (e.g. completion).
4. Insert a reward event with a deterministic idempotency key.
5. If the event already exists, return its recorded result; do not pay again.
6. Evaluate the versioned shared reward policy using only permitted event metadata.
7. Append reward ledger entries.
8. Update materialized currency/inventory/progression balances.
9. Register any acquired Pokémon instance and its acquisition history through the shared Pokémon service.
10. Insert minimal outbox events for downstream consumers.
11. Commit, then publish success to UI and schedule follow-up work.

The exact reward payload and acquisition rules belong to the shared Pokémon/economy specification. This document requires atomicity and idempotency, not new reward tuning.

### 8.3 Idempotency keys

Use a stable key derived from canonical source identity and the logical earning occurrence, for example `(source_module, source_record_id, qualifying_occurrence_id, reward_kind, policy_scope)`. Do not use a random ID alone as the deduplication key. Re-completion after undo, a retry after process death, a notification action, and migration replay must resolve to the same logical key when they represent the same earning event.

If the product explicitly permits a new reward after a genuine new occurrence, that occurrence receives a new stable occurrence ID. Never infer a new earning event merely because a row was edited or re-saved.

### 8.4 Ledger and reversals

Reward ledger entries are append-only. Do not rewrite old ledger deltas to make a balance look correct. For a legitimate reversal, append a compensating entry linked to the original event and preserve the reason/policy. A correction must not allow a repeated action to pay twice. Materialized balances are projections that can be reconciled from the ledger.

**As built (0.135.0, REP-014): the ledger is the only writer of the balance and the Bag.** `currency_balance` and `bag_stack` change only inside `EconomyRepository.settle`, in the same transaction as their ledger entries. The save no longer stores `coins` or `inventory` as such: a live write compares the document with the tables and turns the difference into **one ledger event** (`save:<n>`, source `APP`, kind `net-change`, one entry per asset that moved), and a read gives the balances back from the tables, so a balance can always be rebuilt from the ledger (`EconomyRepository.reconcile()`, checked after the move and in the tests). A write that changes nothing writes nothing, and a save that would take the balance or a stack below zero is refused whole. The events are honest about being coarse: they say *how much* changed in one action, not *why*; the per-source events (`PLAN:<task>:complete`, ...) arrive as each module's reward sites are moved onto `settle` (section 7.3.0).

### 8.5 Transactional outbox

Insert outbox events in the same transaction as the source change and reward. A worker/consumer later dispatches them. Each consumer stores processed event IDs and is safe to retry. The outbox payload is versioned and privacy-minimized. A failed consumer must not roll back the already committed user action; it remains pending for retry or diagnosis.

### 8.6 Cross-domain workflows

Avoid distributed transactions because all participating data is local. Coordinate through the single database transaction when an operation truly needs atomic cross-domain state. Otherwise use outbox events and eventual projections. Do not let a generic event bus become a second source of truth.

## 9. Offline-first behavior

All core records and local game progression work offline. A network connection may refresh external information, but does not gate creating, viewing, editing, completing, or exporting locally owned records.

- Local database reads/writes work without network access.
- Imported/provider-sourced data shows source and last-updated provenance where meaningful.
- A failed provider refresh leaves the last valid local snapshot intact and visibly stale where relevant.
- Manual data entry remains available when integrations are disconnected or permission is denied.
- Reminders use local platform scheduling and reconcile from canonical module records; Android permission/platform limits are communicated without losing source records.
- Search, summaries, and projections are local and rebuildable.
- No queue of cloud writes or cloud conflict-resolution layer is required for the approved product.
- Remove Supabase account, cloud save, social, and sync dependencies only after migration/export and local-only acceptance have passed.

## 10. Import, export, backup, and restore

### 10.1 Distinct operations

- **Backup:** a recoverable snapshot of the complete local data set at a point in time.
- **Export:** a portable representation intended for user inspection, transfer, or interoperability.
- **Import:** ingest external or legacy data with validation and provenance.
- **Restore:** replace the current local data set from a verified backup.
- **Merge:** a separate, explicitly supported operation; never the default interpretation of restore.

### 10.2 Backup archive

Recommended logical archive:

```text
pokelife-backup/
├── manifest.json
├── database.sqlite
├── attachments/
├── reference-data-version.json
└── checksums.json
```

The manifest records backup format version, app version, database schema version, creation time, database snapshot identity, included modules, attachment count, and encryption metadata. Checksums cover every payload file. Do not include account/session tokens or secrets from retired cloud pathways.

### 10.3 Backup consistency

Create a consistent SQLite snapshot using the supported SQLite backup mechanism or a documented checkpoint/close procedure. Do not copy a live WAL-mode database file in isolation and assume the result is complete. Include attachments and manifest in a staged archive, verify checksums, then publish the completed archive to the user-selected destination. A failed backup must leave any previous backup untouched.

### 10.4 Encryption and destinations

Use app-private storage for working data. Support a user-selected document destination through Android's document picker. Offer optional archive encryption with a user-controlled password/recovery credential and a modern authenticated encryption format; do not invent or store a recoverable password on the server. Clearly warn that losing the only encryption credential can make the archive unrecoverable. Backup destination access is explicit and revocable.

### 10.5 Export formats

Provide a complete restorable backup separately from readable exports. Human-readable JSON/CSV exports may be offered per module or for selected records, with stable IDs, schema/format version, timestamps, provenance, and relationship references. A partial CSV export is not a full backup. JSON export must have an explicit schema and must not silently omit data necessary for restoration.

### 10.5.1 As built (0.133.0, REP-008)

What exists is the live-documents half of the archive above, as **one JSON file** (`BackupBundle`, `pokelife-backup` format version 1): the documents `save` and `care` exactly as the app serialises them, the database schema they were taken at, the time and a SHA-256 of the documents. The app keeps its daily backups as folders (`LiveBackup`, newest three, a `DONE` marker last) and can write any of them, or a fresh one, as a bundle through Android's document picker (`CreateDocument`); a bundle is chosen with `OpenDocument`. There is no `database.sqlite` payload, attachments or encryption yet (no module has attachments; MINT's rows and backups are sealed with a Keystore key, and a backup file can carry Mint sealed under a player's passphrase instead, 0.142.0).

**Restore, as built:** the chosen file is read whole and **staged**: `BackupBundle.stage` refuses a file that is not a backup, is cut short or damaged, fails its checksum, holds a document this build does not know, or comes from a newer format or schema, and changes nothing. A bundle that passes is shown to the player (its date and contents) and only on confirmation is swapped in by `LiveDocuments.replaceAll`: one transaction, in which each replaced document is first kept as `aside:<name>:<millis>`, so a restore can be undone and a failure part way (the write of the second document, say) rolls the first back too. PLAN's and CARE's tables follow the documents, because the swap goes through the same write path as every save. The app reloads both stores after a restore. A backup is skipped, never half written, when the phone lacks room for twice its size plus 5 MB (`LiveBackup.lowStorage`), and a save that fails for lack of storage rolls back whole (SQLite's transaction), is reported in Settings → Backups, and the next save that fits writes everything again.

**Cut short:** a write that is interrupted before its commit (the process killed) leaves every table at the last committed state, across the save row, PLAN's tables and CARE's tables (`BackupRestoreTest`, simulated by dropping the connection inside the transaction; a real kill on a device is still unproven).

### 10.6 Restore workflow

1. Select archive and validate manifest, format version, checksums, encryption, database integrity, and attachment paths.
2. Show a concise preview: backup date, app/schema version, modules, record counts, and compatibility warnings.
3. Create a verified backup of the current database before replacement.
4. Restore into a staging location, never over the live database during validation.
5. Run migrations to the current supported schema and validate foreign keys, ledger/balance reconciliation, owned Pokémon references, and attachments.
6. Close database connections and atomically switch the staged database into service using a recoverable swap procedure.
7. Rebuild derived indexes/projections and reconcile reminders after activation.
8. Keep the pre-restore database until the user confirms the restored app opens and data checks pass.

A restore failure returns to the previous database and leaves the selected archive unchanged. A merge operation must define deterministic conflict rules and preserve both sides where ambiguity exists; it must never silently overwrite canonical records.

## 11. Save versioning and schema migrations

### 11.1 Version model

Track separately:

- Database schema version.
- Legacy JSON save format/schema version.
- Backup archive format version.
- Module-specific policy/configuration versions where reward or interpretation rules change.
- Reference catalog/content version (species/forms/items/assets).

Do not overload app semantic version as a data schema version.

### 11.2 Migration rules

- Migrations are ordered, deterministic, and repeatable only when explicitly designed to be idempotent.
- Each migration has a stable identifier, source/target version, checksum, and tests.
- A migration either commits completely or leaves the prior schema/data usable.
- Use transactions for each migration step where SQLite permits.
- For long-running transformations, use a staged database and checkpoints with durable progress metadata.
- Never silently drop unknown data. Preserve it in a legacy payload/archive or require an explicit documented disposition.
- Preserve stable IDs unless a collision or invalid format requires an auditable mapping.
- Preserve user-visible history and provenance; do not regenerate historical events.
- Rebuild derived tables and indexes from canonical records when possible.
- Keep rollback artifacts until validation and release gates pass.
- A newer database opened by an older app must fail safely with a clear recovery/upgrade message; it must not be downgraded or rewritten by the older app.

### 11.3 Migration execution lifecycle

1. Acquire an exclusive migration lock and stop normal writes.
2. Verify sufficient free storage for a backup, staged database, and temporary data.
3. Create and verify a pre-migration backup.
4. Validate source database/JSON readability and expected format.
5. Apply migrations to a staged database or a transactionally protected copy.
6. Run schema, foreign-key, domain, ledger, and record-count validations.
7. Mark the target schema version only after all steps pass.
8. Atomically activate the new database.
9. Reopen repositories, rebuild projections/search, and reconcile reminders/outbox.
10. Record completion and retain recovery artifacts according to the retention policy.

If any step fails, stop writes, preserve diagnostic information without logging sensitive content, and restore the pre-migration state. Never open a partially migrated database as normal app data.

## 12. Migration from 0.123.0 JSON/save architecture

### 12.1 Legacy files and structures confirmed in source

- Android main save: `filesDir/save.json`, created by `saveStorage()`.
- Android CARE save: `filesDir/care.json`, created by `careStorage()`.
- Main aggregate: `AppData` in `app-core/.../AppData.kt`, with `schema = 1` and serialized fields including `player`, settings, PLAN data, reward log, WORLD data, GEAR state, origins, Dex history, account/cloud state, and device-specific values.
- CARE aggregate: `CareData` in `care-core` and its independent `CareStore`.
- Main store: `GameStore`; persistence through `SavedDocument` and `Storage`.
- CARE store: `CareStore`; separate `SavedDocument` and `Storage`, with reward calls through `World`.
- Android storage adapter: `FileStorage` in `app-core` platform source, using temporary-file replacement with direct-write fallback.
- Save compatibility: `SaveCompatibilityTest`, including default-field loading and serialized enum-name stability.
- Legacy cloud-related structures: account/session and sync-related data in the main and CARE pathways; these are not part of the target product.

### 12.2 Pre-migration inventory

Before implementing the importer, inspect the exact tagged/release source and collect representative real user save fixtures. Inventory:

- Every serialized property in `AppData`, `PlayerState`, `CareData`, `WorldData`, `Settings`, `GearData`, and all nested records.
- All enum serialized names and legacy aliases, including region/party IDs and reward kinds.
- Every persisted identifier and reference, including owned Pokémon, party IDs, buddy, tasks, task series/occurrences, reminders, goals, focus history, CARE routines/logs/journal/diary entries, World teams/expeditions/story state, item IDs, reward history, origins, form/shiny history, and utility settings.
- All old file names, backup/unreadable copies, web storage keys where applicable, and optional CARE cloud copies. Do not import cloud state automatically.
- Any duplicated values that can be derived from canonical records; retain source values until reconciliation confirms safe derivation.
- Legacy values that no longer appear in current enums or reference catalogs.

Build fixtures from anonymized or consented real saves covering small/large collections, evolved and shiny specimens, duplicates, independent module parties, WORLD team assignments and PC, full reward history, active expeditions, completed story arcs, CARE private content, pending focus/reminder state, and older historical schema shapes.

### 12.3 Mapping strategy

Use a dedicated `LegacyJsonImporter` that reads the two source files as immutable inputs and produces typed intermediate records. Do not deserialize into the new domain entities and immediately overwrite the originals.

- Map `AppData.player` into shared Pokémon/economy records, preserving every owned specimen ID and its individual attributes.
- Map species/form/shiny/seen history independently of current ownership so Dex history survives transfers.
- Map party membership, buddy, favorites, held items, candy progress, IVs/EVs, nature, ability, gender, friendship, level, origin, and acquisition timestamps.
- Map `AppData` PLAN, WORLD, GEAR, settings, and device-specific state into their new owning domains.
- Map `CareData` into CARE-owned records and preserve journal, diary card, routine, completion, skill, and trend-relevant history.
- Map existing reward logs for display/history only. Do not turn historical log rows into new reward events or credit balances a second time.
- Map any persisted balances and inventory once, then reconcile them against the imported historical ledger only where the source has enough information to prove equivalence. If not, preserve the source balance as an opening-balance entry with provenance rather than inventing missing ledger detail.
- Preserve source timestamps and date/time semantics. Record the source time zone where available; do not reinterpret local dates as UTC instants.
- Preserve user-authored text and attachments. If a field cannot be represented yet, retain its raw JSON in a migration sidecar keyed by legacy path/record ID until a reviewed mapping exists.
- Map legacy enum names through explicit tables. Never rely on Kotlin enum ordinal order.
- Do not import account credentials, refresh tokens, cloud sessions, or server-only synchronization metadata. Explain retirement of cloud sync without deleting local data.
- Treat external calendar selections, device-specific picker values, exchange-rate caches, and permissions as device-specific preferences; migrate only what remains meaningful and safe.

### 12.4 IDs and cross-file reconciliation

CARE and main-save data may reference shared progression indirectly. The importer must create an old-ID-to-new-ID map only when necessary and preserve original IDs otherwise. Cross-file relationships are resolved after both files have been parsed. A missing optional reference is recorded as an import warning and preserved in the migration sidecar; do not fabricate a target record.

Where old data has no stable occurrence identity, derive a deterministic legacy occurrence key from the source record ID plus its canonical date/occurrence identity. Record the derivation rule and test collisions. Do not use current time or random values during migration.

### 12.5 Reward migration and replay protection

- Import current balances and historical reward log as distinct source facts.
- Create an opening-balance ledger entry where necessary to establish a reconciled ledger without inventing historical transaction details.
- Import legacy paid markers/counters as historical eligibility state only when their semantics are clear.
- Create a reward idempotency registry for known paid source occurrences where possible.
- Do not replay historical PLAN, CARE, WORLD, or other activity as fresh rewards.
- Do not grant newly introduced module rewards for old activity.
- Ensure CARE's independent save and shared World reward state do not produce duplicate or missing credit during consolidation.
- Compare final Poké balance, item quantities, candies, rank/progression, individual owned Pokémon count, and known historical reward totals against the source.

### 12.6 Cutover and retirement

The first database release should support an explicit one-time import from `save.json` and `care.json`. Keep legacy files read-only and untouched throughout import and post-import verification. On success, store a migration completion marker and make the database authoritative. Do not keep dual writes indefinitely; they create divergent sources of truth. If a rollback is required before the retention gate, restore the original JSON-backed app build/data path from the verified backup. Retire old storage code and cloud pathways only after the new path has passed release acceptance and backup retention requirements.

**As built (0.135.0, REP-014): the Pokémon, Bag and ledger tables.** `LiveDocuments.movePokemonToTables` runs after PLAN's move on every start and does nothing once done. In one transaction it (1) passes the whole save through the app's own load check (`LiveDocuments.normalizer`, which `LiveStore` sets to `GameStore.normalizeDocument`: numbers in range, the Poké merge recorded, one place per Pokémon, habits merged), so a save from before those rules is brought up to them exactly as the app would on loading it; (2) keeps the old save whole under `aside:save:pokemon:<millis>`; (3) writes the rows, with the balances as **one opening-balance event** (`live:save:opening-balance`, source `MIGRATION`, the Poké merge's two legacy values kept in its metadata) and nothing else paid, nothing published to the outbox; (4) sets the marker `pokemon_store=tables`; (5) reads the whole save back and compares it with the checked one (`LiveDocuments.canonical`: a set is a set, a Bag entry of zero is no stack) and reconciles the ledger. Any difference, a Pokémon in two places, a buddy or Guardian that is not owned, or tables that already hold something (a legacy import) throws and rolls the whole transaction back, so the `save` row stays the live save and the move is tried again at the next start. The daily backup still assembles `save.json` from all three parts, and a restore (`replaceAll`) passes the backup's save through the same check and the same split. Not measured on a device.

## 13. Interrupted writes, corruption, and recovery

### 13.1 Ordinary database writes

SQLite transactions provide atomic commit/rollback. On disk-full, I/O, constraint, or process failure:

- Return a clear failure; do not publish success or update authoritative in-memory state.
- Keep the previous committed data.
- Retry only when the error is transient and the operation is idempotent.
- Do not retry a non-idempotent reward or import operation without checking its idempotency key.
- Record technical diagnostics without logging journal, health, finance, relationship, or household content.

### 13.2 Corruption

On database open/integrity failure:

1. Stop normal writes and do not initialize a blank database over the damaged file.
2. Preserve the original database, WAL/SHM where applicable, and relevant metadata in a recovery location.
3. Attempt supported SQLite integrity checks and recovery on a copy, never the only original.
4. Offer restore from a verified backup or export of recoverable records.
5. If partial salvage is possible, show what was recovered and what remains unavailable; do not silently discard unrecovered rows.
6. Keep the original corrupt file until the user chooses a recovery path or retention policy permits removal.

Retain the existing 0.123.0 principle of keeping unreadable saves aside, but use structured recovery artifacts and a user-visible explanation.

### 13.3 Failed migration or restore

- Migrations run against a staged database or a protected transaction.
- A failed step never advances the active schema version.
- The original database/JSON remains untouched until activation.
- An activation failure swaps back to the previous known-good database.
- Recovery artifacts identify source schema, target schema, migration ID, failure stage, and checksum; they do not include sensitive payloads in logs.
- Re-running a migration must either resume from a verified checkpoint or restart from the original backup. It must not double-import or double-reward.

### 13.4 App update, reboot, and process death

On startup/recovery, verify database open and migration state, then reconcile pending outbox events and reminders from canonical records. Do not infer that a pending reminder means its source record is still valid. Do not replay user actions based on a UI state that was never committed.

## 14. Data retention, archive, and deletion

Retention must reflect domain semantics and privacy requirements.

- Prefer archive/soft-delete for records whose history is useful and whose module specification requires it. Archived records remain excluded from ordinary active views but remain exportable/searchable through history where appropriate.
- Use hard deletion for records the user explicitly deletes when no legal/product history requirement justifies retention. Remove associated attachments and rebuildable indexes.
- Preserve append-only reward ledger and historical provenance; where a user deletes a source record, remove or anonymize unnecessary source text while retaining the minimum ledger facts needed for balance integrity and fraud/double-pay prevention. Document this behavior in the shared Pokémon/economy specification.
- Deleting a contact, health record, journal entry, finance record, or household attachment must not leave private content in search indexes, thumbnails, caches, or exported temporary files.
- Define foreign-key behavior deliberately: `RESTRICT` where deleting a parent would corrupt history; `CASCADE` only for dependent data that has no independent retention need; `SET NULL` only where an orphaned historical record remains meaningful.
- Never cascade-delete an owned Pokémon history, reward ledger entry, or historical completion merely because a linked display record is removed.
- Local backups are user data too. Explain that deleting data from the active database does not automatically erase copies the user exported to other destinations.
- Provide a clear full-device-data erase operation with confirmation and a verified backup/export option. It must remove local database, attachments, indexes, local backups managed by the app, and credentials in secure storage according to the user's chosen scope.
- Retention duration for provider snapshots and raw import payloads is module-specific; keep only what is needed for provenance, correction, and user history.

## 15. Data-related UI and shared PMD design system

All data lifecycle UI follows `FEATURES/DESIGN_SYSTEM.md`. Use its PMD-inspired dialogue-box geometry and shared components without turning every data row into a speech bubble.

- Backup/restore status, migration warnings, import summaries, destructive confirmations, corruption recovery, and permission explanations use the shared `PrimaryDialogueBox`, `SecondaryDialogueBox`, `DialogueDialog`, `ConfirmationPrompt`, `AlertPrompt`, `EmptyState`, `LoadingState`, `ErrorState`, and `SuccessFeedback` patterns as appropriate.
- Keep messages concise and factual: what happened, whether the original data is safe, what action is available, and what happens next.
- Destructive restore/erase operations explain whether current data will be replaced and require deliberate confirmation.
- Migration progress must not imply success until verification completes. Interrupted migration clearly states that the original save remains preserved when that is true.
- Show backup date, archive version, schema compatibility, included modules, and record counts in readable rows or a compact table.
- Use normal lists/tables for record counts, file inventories, transaction-like histories, and diagnostic details. Do not place every item in a separate dialogue box.
- Do not display raw JSON, database paths, SQL errors, tokens, or sensitive payloads by default. Provide a safe technical detail route only when useful.
- Follow the shared light/dark, typography, spacing, responsive sizing, TalkBack, 48 dp touch-target, focus, and reduced-motion requirements.

## 16. Testing and acceptance criteria

### 16.1 Database and repositories

- [ ] Database opens, closes, and reopens without losing committed data.
- [ ] Foreign keys are enabled and enforced on every connection.
- [ ] Unique constraints and validation rules reject invalid relationships/values.
- [ ] Each module can access its own records through its repository without depending on another module's DAO.
- [ ] Read models/projections rebuild correctly from canonical records.
- [ ] Failed transactions do not publish uncommitted state.
- [ ] Concurrent widget/notification/app actions cannot overwrite newer committed state.

### 16.2 Pokémon and economy

- [ ] Every owned Pokémon has a stable individual ID distinct from species/form IDs.
- [ ] Evolution preserves specimen identity and intended individual attributes/history.
- [ ] All independent module-party, WORLD team, PC and global buddy references remain valid under shared Pokémon rules.
- [ ] Dex/form/shiny history survives specimen transfer/deletion as specified.
- [ ] Reward ledger entries are append-only and balance projections reconcile.
- [ ] Duplicate event delivery, retries, process death, and notification actions cannot pay twice.
- [ ] Reversals use compensating entries and do not erase history.

### 16.3 Domain ownership and privacy

- [ ] Every canonical record has one owner and one repository boundary.
- [ ] Cross-module views use references/projections, not editable copies.
- [ ] CARE/HEAL private content and MINT/HOME/LINK sensitive content are absent from generic WORLD/reward/outbox payloads.
- [ ] External provider values retain source, timestamp, units, and provenance.
- [ ] GEAR utility use does not create Pokémon rewards.

### 16.4 Offline and lifecycle

- [ ] Core module reads/writes/search work with network disabled.
- [ ] Provider failure leaves the last valid local snapshot intact.
- [ ] Permission denial does not block local data entry.
- [ ] Process death and reboot preserve committed records and reconcile reminders/outbox.
- [ ] Low-storage and I/O failures return safe errors without replacing committed data.

### 16.5 Backup, export, and restore

- [ ] Full backup includes database, attachments, manifest, schema/reference versions, and checksums.
- [ ] Backup uses a consistent SQLite snapshot, including WAL state as required by the chosen mechanism.
- [ ] A failed backup never overwrites the previous known-good backup.
- [ ] Archive tampering, missing files, invalid paths, unsupported versions, and wrong encryption credentials are rejected safely.
- [ ] Restore preview reports date, versions, module coverage, and compatibility warnings.
- [ ] Restore validates staging data before activation and can return to the prior database on failure.
- [ ] Export/import round trips stable IDs, references, history, and provenance.
- [ ] Merge is separate from restore and has deterministic conflict handling.

### 16.6 Legacy migration

- [ ] Real 0.123.0 fixtures cover old and current JSON shapes, including `save.json` and `care.json`.
- [ ] Every persisted field is mapped, intentionally excluded, or preserved in a migration sidecar with a documented reason.
- [ ] Legacy enum names and IDs map deterministically.
- [ ] Pokémon count, specimen IDs, forms, shiny state, individual attributes, party membership, and acquisition history match the source.
- [ ] PLAN, CARE, WORLD, GEAR, settings, reminders, and relevant device preferences preserve supported data.
- [ ] CARE journal/diary/routine history is preserved without leaking into other modules.
- [ ] Poké, inventory, candies, rank, and reward history reconcile without duplicate credit.
- [ ] No historical activity creates a new reward.
- [ ] Interrupted migration at every checkpoint can resume safely or restore the verified original.
- [ ] Original JSON files remain untouched until acceptance and retention gates pass.
- [ ] A newer schema is never silently downgraded by an older app.

### 16.7 Recovery and deletion

- [ ] Corrupt database is preserved and does not trigger silent blank initialization.
- [ ] Integrity check/recovery operates on a copy where appropriate.
- [ ] Failed migration/restore leaves the prior known-good data accessible.
- [ ] Deletion removes required attachments, search indexes, thumbnails, and derived caches.
- [ ] Historical reward/completion records retain required integrity without retaining unnecessary private content.
- [ ] Full local erase has explicit scope and confirmation.

### 16.8 UI and release

- [ ] Data lifecycle screens follow the shared PMD dialogue-box design system.
- [ ] Dense inventories and diagnostics use readable lists/tables rather than excessive framed cards.
- [ ] Light/dark themes, large text, TalkBack, keyboard/focus, and reduced motion pass.
- [ ] Migration, backup, restore, corruption, permission, empty, loading, and failure states are tested on device.
- [ ] Performance and storage use are profiled on target Android hardware.
- [ ] No cloud account or synchronization path is required for local data integrity.

## 17. Implementation sequence and gates

1. **Source inventory and fixtures:** freeze the exact 0.123.0 baseline; inventory all serialized data and create representative fixtures.
2. **Database spike:** validate SQLDelight/SQLite with current KMP toolchain, Android lifecycle, backup snapshot, and migration tests.
3. **Core schema and repository contracts:** implement app metadata, module ownership boundaries, shared Pokémon/economy contracts, and transaction coordinator.
4. **Legacy importer:** implement `save.json` and `care.json` parsing, ID mapping, deterministic imports, and validation before any cutover.
5. **Atomic reward/outbox foundation:** make completion, reward ledger, balances, Pokémon grants, and events transactional.
6. **Domain migration:** migrate modules incrementally behind repository APIs while retaining one database and clear module ownership.
7. **Backup/restore and recovery:** validate full archive round trips, interrupted restore, corruption, and low-storage handling.
8. **Cloud-path retirement:** remove Supabase/account/sync dependencies only after local migration, backup, and offline acceptance pass.
9. **Release verification:** run full tests, migration fixtures, device tests, UI/accessibility review, and recovery drills.

Do not broaden module implementation while the data foundation or migration gate is failing. The exact feature roadmap remains in `IMPLEMENTATION_GUIDE.md`.

## 18. Cross-reference authority

- Product-wide local-first/no-account direction, module map, privacy principles, and decision precedence: `../PRODUCT_BIBLE.md`.
- Implementation status, 0.123.0 audit, and delivery sequence: `../IMPLEMENTATION_GUIDE.md`.
- Module-specific canonical records and workflows: `../MODULES/PLAN.md`, `CARE.md`, `HEAL.md`, `MINT.md`, `HOME.md`, `LINK.md`, `GEAR.md`, and `WORLD.md`.
- Shared Pokémon identity, acquisition, collection, evolution, Bag, and reward semantics: `../FEATURES/POKEMON_ECOSYSTEM.md`.
- Shared reminder scheduling, policy, delivery, action routing, and recovery: Section 9 of `INTEGRATIONS_AND_PRIVACY.md`.
- PMD dialogue-box geometry, components, accessibility, and visual states: `../FEATURES/DESIGN_SYSTEM.md`.
- Provider capabilities, permissions, provenance, and privacy: `INTEGRATIONS_AND_PRIVACY.md` when finalized.
- Data-specific test catalogue and release gates: `TEST_STRATEGY.md` when finalized.

This document owns shared data architecture, persistence, transactions, backup/restore, migration, and recovery. Module and feature documents own their domain-specific behavior and must link here rather than duplicate the shared data lifecycle.

---

## Testing reference

Repository contracts, transaction integrity, migration fixtures, backup/restore, crash recovery and data reconciliation must follow [`TEST_STRATEGY.md`](TEST_STRATEGY.md). Migration and recovery tests are P0 release gates; supported legacy fixtures and retry/failure paths must be exercised against the real persistence implementation.
