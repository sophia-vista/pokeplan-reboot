# PokéLife — Data Architecture and Migrations

**Status:** The live data architecture (reviewed 20 November 2026): a local SQLite database through SQLDelight on Android; desktop and web keep JSON development stores.

> This document owns the local-first data architecture: persistence contracts, transactions, backup and restore, schema migration and recovery. Product behavior remains owned by the Product Bible and the module and feature specifications.

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

## 2. Source precedence

Use the latest approved files under `docs/pokelife/` as authorities. `docs/design.md` and `docs/code-map.md` (what the code does today) and dated reviews are evidence, not competing authorities. The shared Pokémon domain is owned by `FEATURES/POKEMON_ECOSYSTEM.md`; this document owns physical persistence, repository boundaries, transactions, backup/restore, migration execution and recovery.

## 3. Target application architecture

Use a modular, layered architecture. Top to bottom: the application shell (navigation, module registry); the platform layer (Android lifecycle, notifications, files, permissions, database driver); the shared design system; `data` (database and schema, repositories and the transaction coordinator, migrations, backup/restore/export, attachments); shared domain services (Pokémon, economy and reward ledger, reminders, event contracts and outbox, integration contracts); and the eight product domains (PLAN, CARE, HEAL, MINT, BASE, LINK, GEAR, WORLD). The real Gradle layout is in `docs/code-map.md`; the names here are boundaries, and adapting them must not weaken ownership.

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

**Decided:** SQLite with SQLDelight. Android is the only product platform (`PRODUCT_BIBLE.md` section 3), so only the Android driver is judged; desktop and web builds keep an in-memory or JSON development store. SQLDelight gives typed SQL and generated Kotlin interfaces over a mature engine, the driver sits in the platform layer while domain contracts stay common Kotlin, and schema migrations are versioned and tested independently. Do not implement two competing database stacks; Room is not used.

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

### 4.5 Database rules

SQLDelight 2.4.1 on SQLite with the Android driver. The module is `:data` (Android and JVM targets; desktop and web keep JSON stores and never open the database).

- **Foreign keys are off by default in SQLite.** The Android driver sets them in `onConfigure`, `JvmDatabase` sets them, and `DataStore.open` reads `PRAGMA foreign_keys` back and refuses to run without it. A test proves the ownership rules fail without them.
- **Rules in the schema:** one owner per Pokémon (section 7.3), `CHECK (slot BETWEEN 1 AND 6)` for a party, nonnegative balances and stacks, IV/EV/level/friendship `CHECK`s. Rules that depend on other rows (module eligibility) stay in the domain layer.
- **Versioning:** the schema version is `PRAGMA user_version`. A new file is created at the current version; an older one is migrated step by step in one transaction; a newer one is refused with `NewerSchemaException` and not touched; a file that has tables but no version is refused.
- **Migrations are checked by the build:** `schemaOutputDirectory` is set, every released schema is committed as `data/src/commonMain/sqldelight/databases/N.db`, `verifyMigrations` is on and `jvmTest` depends on `verifyCommonMainDataDbMigration`. A schema edit without a numbered `.sqm` migration fails the build.
- **SQLite on Android 8 is 3.18:** no upsert, and `VACUUM INTO` (3.27) is not used in common code. The Android backup is a WAL checkpoint, then a copy, under the single writer. A manual `BEGIN` over a file-backed JDBC driver is unsound (the driver may give the next statement another connection), so the store always uses the driver's own transaction.
- **Process:** widgets, reminder receivers and notification actions run in the app's single process (no `android:process` in the manifest) and use the same repositories through one process-wide handle, so section 4.3 needs no multi-process SQLite. Moving any of them to another process reopens that decision.
- **Ownership in code:** shared tables (`owned_pokemon`, `pokemon_place`, `dex_history`, `bag_stack`, `currency_balance`, `reward_event`, `reward_ledger_entry`, `outbox_event`, `processed_event`, `migration_history`, `app_meta`) are written only by the shared Pokémon and economy service, module tables only by that module's repository; repositories call the transaction coordinator, never `SqlDriver`; generated queries stay `internal` to `:data`.
- **IDs:** every legacy ID is kept as the `TEXT` primary key; module ids in tables are upper case (`PLAN`, `CARE`, ...), the importer maps the legacy `plan` and `care`; legacy local dates stay ISO text.
- **Not proven on a device:** low storage, a process kill during a write, the 600,000-round passphrase timing and Keystore key creation on a phone without a hardware keystore.


## 5. Domain ownership and authoritative records

Every canonical record has exactly one owner. Cross-module screens use read-only projections or explicit links.

| Domain / capability | Canonical owner | Authoritative records and responsibilities |
|---|---|---|
| Application profile and global settings | Application foundation | Local profile, theme/accessibility/global preferences, data lifecycle state |
| PLAN | PLAN | Tasks, task occurrences, lists/projects, recurrence definitions, goals, milestones, focus sessions and planning history |
| CARE | CARE | Self-care routines, routine history, diary cards, journal entries, notes, mood check-ins, skill practice and CARE settings |
| HEAL | HEAL | Activity, fitness, nutrition, sleep, body measurements, health observations, source/import history and HEAL settings |
| MINT | MINT | Budgets, manual transactions, accounts/cards/e-wallet references, recurring bills, payment status and finance settings; provider-reported facts retain source provenance |
| BASE (its tables and document keep the name `home`) | BASE | Household chores/occurrences, inventory, areas, assets, maintenance, warranties, household reference records and attachments |
| LINK | LINK | Contacts, relationship notes, important dates, relationship follow-ups and LINK settings |
| GEAR | GEAR | Utility preferences and locally retained utility history where specified; calculations and transient tool state need not be persisted |
| WORLD | WORLD | Campaign/story progress, expeditions, missions, settlement state, dungeon progress and WORLD-specific records |
| Pokémon reference catalog | Shared Pokémon service / packaged reference data | Canonical species, forms, moves, types, abilities, evolution references and catalog version |
| Pokémon collection | Shared Pokémon service | Individual owned specimens, acquisition/evolution history, Pokédex/form/shiny history, party/PC membership and buddy selection |
| Economy | Shared economy service | Poké balance, Bag/inventory, reward ledger, rank/progression balances and reward policy versions |
| Reminders | Shared reminders feature | Delivery requests, schedule metadata, notification identities and recovery state; never source business records |
| External provider facts | External provider remains source authority | PokéLife stores permitted local snapshots/imports with source, timestamp, external identity and provenance |

### 5.1 Module ownership rules

- A BASE chore remains a BASE record even if PLAN displays a linked task or reminder.
- A HEAL workout or measurement remains in HEAL even if another module displays a safe summary.
- A MINT bill/payment remains in MINT; reminder scheduling does not own payment status.
- CARE journal, mood, and diary content stays in CARE. It is not exposed to WORLD or reward presentation.
- WORLD owns adventure progress but not global Pokémon ownership, Bag, currency, or reward ledger.
- GEAR tools do not own module progression and do not generate rewards merely from being opened or used. Helper Pokémon are visual helpers, not a separate collection or acquisition path.
- Imported calendar events remain provider-owned read-only projections unless a module's specification explicitly defines a user-created local record.
- External bank/e-wallet/health provider data must preserve whether it was imported, manually entered, or observed through an authorized integration.

### 5.2 Read models and projections

A dashboard or cross-module view may combine data from several repositories into a derived read model. A projection is rebuildable and is never a second editable canonical record; store one only when performance requires it, mark it derived, and define invalidation/rebuild. A linked record stores the target module and an opaque target ID, not a copy of the target.

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
| `pokemon_place` | **One place per specimen**: the specimen is the primary key, so a second row cannot exist; kind `buddy` (exactly one row), `party` (the module's party id and a slot, the lead in slot 1, unique per party) or `guardian` (a slot); no row means the PC. A WORLD team and a GEAR helper are the other two places and stay in their modules' records; the live write refuses a save that names one Pokémon in two |
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

### 7.3.0 Decision: the app keeps reading one `AppData` through the split

The shared Pokémon, Bag and ledger tables are the live authority, and the app reaches them **the way it reaches PLAN's tables**: `LiveDocuments` splits every save into PLAN's part, the player's part and the rest, writes the first two as rows and puts the save back together on a read, so `GameStore`, the reward engine and every screen still read and write one `AppData` and the rules stay pure and tested. The typed repositories (`PokemonRepository`, `EconomyRepository`) are the tables' own API and are used by the split, not directly by the app; `app-core` also targets web, which has no SQLite, so a typed repository port must live behind an interface there.


**Still needed before the app can move onto the typed repositories** (per module, as each is refactored): a `GameStore` that asks them for one Pokémon, one party or one balance instead of holding the whole list; the WORLD team and GEAR helper places moved into `pokemon_place` (they are inside the `save` row, and the write refuses a Pokémon named there and in the table); `pokemon_history` (acquisition and evolution facts) and held-item references; and World's rules taking the balance as an input so the in-memory `WorldData.poke` can go (it is `@Transient`, never stored).

**Ledger events.** `AppData.ledgerNotes` holds one `LedgerNote` per paid event: the event's own id, source module, opaque reference, broad kind and what it changed in the Poké balance and the Bag. `PokemonTables.settleNotes` settles each through `EconomyRepository.settle` under that id (an id already in the ledger is skipped) **before** the net change, which still brings the tables to the document, so a note that does not match the save (or would go below zero) is corrected, never trusted. Ids: HEAL, MINT, BASE and LINK `<module>:<kind>:<record id>` (BASE keeps the prefix `home:`, `home:chore:<id>:<day>`; LINK `link:followup:<id>`); PLAN `plan:task:<task>:complete`, `plan:series:<series>:<epochDay>`, `plan:step:<task>:<step>`, `plan:milestone:<goal>:<milestone>`, `plan:review:<weekStartEpochDay>`, `plan:quest:<epochDay>:<quest>`, `plan:welcome:<lastActiveEpochDay>`, `plan:focus:<new Pokémon id>`; CARE `care:<kind>:<epochDay>:<count paid that day>` and `care:befriend:<new Pokémon id>`; the Shop `app:shop:<millis>`. `CareEvent` is unchanged (the daily cap guarantees a count never repeats). A note holds no title, only an opaque id and the amounts.

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

Every module's records are in its own tables, one family per kind of record so a private kind can be read, exported or erased on its own. Detailed fields remain in the module specifications (families a module has not built yet are listed there); 7.4.1 gives the layout all of them share.

- **PLAN:** `plan_task`, `plan_list`, `plan_event`, `plan_series`, `plan_goal`, `plan_review`, `plan_focus_record`, `plan_day` (rest days and active days) and `plan_state` (the running focus session and how it ended, completions waiting out their undo, the pick, emergency pauses, the legacy habits); `MODULES/PLAN.md` section 11.2.
- **CARE:** `care_routine`, `care_routine_completion`, `care_diary_card`, `care_journal_entry`, `care_calm_break`, `care_skill_practice`, `care_visitor`, `care_dismissed_visitor` and `care_state`; the two private record types (diary card, journal) are separate tables.
- **HEAL:** `heal_activity`, `heal_hydration`, `heal_sleep`, `heal_measurement`, `heal_goal`, `heal_steps`, `heal_vital` and `heal_state`, created with no document row (`HealTables`, `MODULES/HEAL.md` section 22); **every row sealed** (section 10.4.1).
- **MINT:** `mint_account`, `mint_category`, `mint_transaction`, `mint_budget`, `mint_bill`, `mint_bill_payment`, `mint_reconciliation`, `mint_review` and `mint_state`, created with no document row (`MintTables`, `MODULES/MINT.md` section 22); every amount an integer in minor units inside the record's JSON; **every row, backup and kept-aside copy sealed** (section 10.4.1).
- **BASE:** `home_area`, `home_chore`, `home_entry`, `home_supply`, `home_adjustment`, `home_restock`, `home_asset`, `home_schedule`, `home_maintenance`, `home_warranty`, `home_reference`, `home_room` (the Secret Base room's placements) and `home_state`, created with no document row, not sealed (`BaseTables`; the tables keep the name `home_`).
- **LINK:** `link_person`, `link_note`, `link_date`, `link_followup` and `link_state`, created with no document row, not sealed (`LinkTables`).
- **GEAR:** tool preferences and intentionally retained history only; ephemeral conversions and calculations stay transient.
- **WORLD:** campaign and story state, expeditions and teams, missions, dungeon progress and settlement state, which live inside the `save` row.

### 7.4.1 Record tables

**Layout.** A record table has the columns the app asks by (`key`, `position`, `day`) and the record itself as JSON in `json`. A `*_state` table holds the single-value fields (settings, drafts, anything a newer build adds). The typed model (`CareData`, `HealData`, ...) stays the one definition of a record, so there is no second field mapping to drift, `:data` stays independent of the `*-core` modules, and a field this build does not know is kept, not dropped. The cost is that SQL cannot filter on a field inside `json`; when a screen needs such a query, the column is added with a migration. The `*Tables` classes take the JSON apart on save and put it back on load under unchanged stores; `RecordRows` diffs, so a save writes only the rows that changed (tests count them with triggers) and a deleted record rewrites the later positions.

**Shared Pokémon tables.** `owned_pokemon` (typed columns, `position` for the order they were owned in, `origin_json` exactly as the save has it with `origin_how` and `origin_region` as lookup columns, `extra_json` for unknown fields), `pokemon_place`, `dex_history`, `player_progress` (Egg meter, trainer points), `bag_stack`, `currency_balance`, `reward_event`, `reward_ledger_entry`, and `pokemon_state` for the single values with no table (party leads, the roles version, anything a newer build adds to the player, the favourites and origins of Pokémon that are gone). `pokemon_place` has `owned_id` as primary key, `UNIQUE (kind, ref, slot)` (exactly one buddy, one Pokémon per slot) and a `CHECK` tying each kind to its shape; no row is the PC. The save row keeps only what is neither PLAN's nor the player's (settings, World, GEAR, quests, the reward log, counters).

**One place per Pokémon.** In the table: the primary key, the unique slot and the single buddy; `setParty`, `setBuddy` and `setGuardians` each take the Pokémon from its old place and close up the slots left behind. Across the tables and the `save` row (WORLD teams and GEAR helpers are checked, not constrained): a write naming a Pokémon in two places (buddy and party, Guardian and buddy, a World team and the table, a helper and the table) is refused whole before any row is written. A Pokémon World gives (a Guardian included) arrives with no place, that is in the PC.

**Backup and restore through the split.** The daily backup assembles `save.json`, `care.json` and the module documents from all their parts; `replaceAll` passes a restored save through the same check and split, and a save the tables cannot represent is refused and the current save kept. A backup without a module's document leaves that module alone and never wipes it. A build older than the schema refuses the file (`NewerSchemaException`).

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

**The ledger is the only writer of the balance and the Bag.** `currency_balance` and `bag_stack` change only inside `EconomyRepository.settle`, in the same transaction as their ledger entries. The save does not store `coins` or `inventory`: a live write compares the document with the tables and turns the difference into **one ledger event** (`save:<n>`, source `APP`, kind `net-change`, one entry per asset that moved), and a read gives the balances back from the tables, so a balance can always be rebuilt from the ledger (`EconomyRepository.reconcile()`). A write that changes nothing writes nothing, and a save that would take a balance or a stack below zero is refused whole, the other changes in it rolled back too. Net-change events say *how much*, not *why*; the real per-source events are in section 7.3.0.

### 8.5 Transactional outbox

Insert outbox events in the same transaction as the source change and reward. A worker/consumer later dispatches them. Each consumer stores processed event IDs and is safe to retry. The outbox payload is versioned and privacy-minimized. A failed consumer must not roll back the already committed user action; it remains pending for retry or diagnosis.

### 8.6 Cross-domain workflows

Avoid distributed transactions because all participating data is local. Coordinate through the single database transaction when an operation truly needs atomic cross-domain state. Otherwise use outbox events and eventual projections. Do not let a generic event bus become a second source of truth.

### 8.7 Settlement rules

- **Event identity.** `reward_event.event_id` is the primary key and equals the idempotency key: `<sourceModule>:<sourceRecordId>:<eventKind>`, plus `:<occurrenceId>` where the product allows a new reward for a new occurrence (a recurring task's date, a routine's day). Never a timestamp, a title or a random id.
- **A retry** is detected by the existing `event_id` before any side effect; the repository returns the recorded settlement and changes nothing.
- **Policy version.** `reward_event.policy_version` stores `RewardRules.VERSION`; the opening-balance event never re-runs the engine.
- **Ledger and balances.** The repository exposes no update or delete of a ledger entry (triggers refuse it even for raw SQL); a reversal is a new entry whose `reverses_entry_id` points at the original. `currency_balance` and `bag_stack.quantity` change in the ledger entry's transaction with `CHECK (>= 0)`; a spend that would go below zero fails the whole transaction; `reconcile()` recomputes both from the ledger.
- **Undo.** Undoing a real-life action before settlement removes it without a reward. After settlement it appends a compensating ledger event; it never reuses the original key and never lets the repeated action pay twice (`POKEMON_ECOSYSTEM.md` section 12.5). PLAN's 10-second undo window for a completed task is a PLAN rule, stored with the task.

## 9. Offline-first behavior

All core records and local game progression work offline. A network connection may refresh external information, but does not gate creating, viewing, editing, completing, or exporting locally owned records.

- Local database reads/writes work without network access.
- Imported/provider-sourced data shows source and last-updated provenance where meaningful.
- A failed provider refresh leaves the last valid local snapshot intact and visibly stale where relevant.
- Manual data entry remains available when integrations are disconnected or permission is denied.
- Reminders use local platform scheduling and reconcile from canonical module records; Android permission/platform limits are communicated without losing source records.
- Search, summaries, and projections are local and rebuildable.
- There are no cloud writes, accounts or sync, and none is planned.

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

The manifest records backup format version, app version, database schema version, creation time, database snapshot identity, included modules, attachment count, and encryption metadata. Checksums cover every payload file. Do not include credentials or other secrets.

### 10.3 Backup consistency

Create a consistent SQLite snapshot using the supported SQLite backup mechanism or a documented checkpoint/close procedure. Do not copy a live WAL-mode database file in isolation and assume the result is complete. Include attachments and manifest in a staged archive, verify checksums, then publish the completed archive to the user-selected destination. A failed backup must leave any previous backup untouched.

### 10.4 Encryption and destinations

Use app-private storage for working data. Support a user-selected document destination through Android's document picker. Offer optional archive encryption with a user-controlled password/recovery credential and a modern authenticated encryption format; do not invent or store a recoverable password on the server. Clearly warn that losing the only encryption credential can make the archive unrecoverable. Backup destination access is explicit and revocable.

### 10.4.1 Sealing of Mint and Heal

**Decision.** MINT's rows are encrypted at rest with an Android Keystore key and every copy of Mint that leaves its tables is sealed ; HEAL is sealed the same way (`IMPLEMENTATION_GUIDE.md` section 3.2). Reasons: sealed rows make any copy of the files useless to anyone but this phone; a Keystore key needs no password and no prompt, so the modules open like every other (Mint's access gate is a separate feature); SQLCipher would add a native library, a new driver and a migration of the live file, none verifiable here, and would lock the game's own data to a key the player cannot move. BASE and LINK are not sealed (no owner decision); they can be sealed the same way. CARE is not sealed either.

**Cipher and keys.** `RowCipher` seals and opens a text: `m1.` plus the URL-safe Base64 of the IV and ciphertext, AES-256-GCM (`AesGcmCipher`, provider-chosen random IV, a context string as associated data so a text sealed for one use never opens as another). Mint's key: alias `pokelife.mint.v1`, associated data `pokelife-mint`. Heal's own key: alias `pokelife.heal.v1`, associated data `pokelife-heal`, so one module's key can be lost without the other. Both are made inside the Keystore, non-exportable, with no screen-lock prompt. Rows are compared by what they say, not by ciphertext (`RecordRows.Codec`), so a save still writes only the rows that changed.

**What is sealed.**

| Where | Sealed form |
|---|---|
| Rows of `mint_*` and `heal_*` (records and settings) | `m1.` text, the `day` column empty: a table shows no figure, name, currency, measurement, time, note or date |
| `snapshot()` of the live documents (the daily backup in `filesDir/backups/` and "Save a copy") | Mint and Heal sealed whole with the phone's key. Every file of a daily backup is in addition sealed with a third Keystore key (`pokelife.backup.v1`, associated data `pokelife-backup`, `BK1:` prefix; no backup is taken without it), so Care's journal and the other modules are not in clear there either |
| Documents kept aside (`aside:mint:*`, `aside:heal:*`: a migration, a restore, an unreadable document) | sealed |
| A passphrase backup | Mint and Heal sealed under the passphrase (below) |

`BackupBundle` accepts a sealed Mint or Heal document. **Still in clear in the tables:** the other modules (a copy of the database file carries Plan, Care, Base and Link in clear, and Mint and Heal only as text this phone's key opens); settings and notification text (the hydration reminder says the same words for everyone and holds an opaque slot id; a snoozed bill reminder's `reminder_hold` row holds an opaque bill id and a due day); the Pokémon, Bag, Poké balance and ledger, which hold no health or money figure (a `HealEvent` or `MintEvent` carries only `{id, kind, time, ref}`). Sealing hides records from a copy of the files, not from the running app, a rooted phone while it is open, a screenshot, or an unlocked screen; nothing here is an access gate. `scrub` cannot reach flash blocks the phone's storage keeps after a delete.

**Failure modes.**

| Case | Result |
|---|---|
| No key on this run, **Mint** | Refuses to start (fail closed) |
| No key, Heal **sealed** | Fail closed: `healStorage` is memory only for the run, nothing is read or written (`HealLockedException`), the rows open again at the next start with the key |
| No key, Heal **not yet sealed** (Keystore failed on an old install) | Heal keeps working in clear for that run and is sealed at the first start with the key. The one difference from Mint: Heal already existed in clear, and locking the player out of their records would be worse |
| Database copied to another phone (the key opens none of it) | Reads as empty; the first write sets the old rows aside, still sealed, as `aside:<module>:locked:<hash>`, then starts empty |
| A backup sealed with another phone's key is restored | Changes nothing; kept as `aside:<module>:locked:<millis>`, sealed. The same for a sealed document when this run has no key |
| A backup from an older build (module in clear) is restored | Written and sealed by the table's own write |
| Recovery from a damaged database | `LiveStore.openOrRecover` gives the new database both keys **before** restoring the newest backup, so a sealed Mint or Heal in it is restored, not kept aside as locked |
| A passphrase backup on a phone that cannot open Mint or Heal | Refused with a note; a module that is not sealed is sealed under the passphrase as it is |

**Sealing an install whose Heal is in clear.** `HealTables.seal(nowMillis)` runs at every start and does nothing once done: (1) in one transaction it rewrites every row of the six tables as sealed text (day emptied), seals every plaintext `aside:heal:*` copy, sets the marker `heal_sealed` in `app_meta`, reads the whole document back with the key and compares it, and checks that no row is left in clear; any difference rolls everything back and Heal stays as it was, tried again at the next start; (2) `scrub()` runs `VACUUM` and a truncating WAL checkpoint (best effort, outside any transaction) so free pages and the log do not keep the old clear rows; (3) `LiveBackup.refreshAfterSealing` takes a fresh sealed backup and deletes older daily copies that hold Heal in clear. A fresh install is sealed from its first row.


**Portable (passphrase) backup.** A *separate, opt-in* file format, so the daily backups and the tables keep needing no passphrase. `PassphraseSealer` (`p1.` texts, `Pbkdf2Sealer`): the passphrase is stretched with PBKDF2-HMAC-SHA256 (600,000 rounds, OWASP 2023; random salt; the iteration count is stored in each text so it can be raised later, at most 5,000,000 accepted) into the key of AES-256-GCM, with a context string as associated data. Minimum 10 characters. The passphrase is never stored and a lost one cannot be recovered; the screen says so before it is used. `open` returns null for a wrong passphrase or a damaged text and never throws. One passphrase seals Mint and Heal in the same file. `BackupBundle.stage` accepts `p1.` texts as a Mint or Heal document; restore converts a portable document to plain JSON in memory only and lets `LiveDocuments.replaceAll` seal it with the phone's key as it writes. Only a fresh copy or an automatic backup can be made portable, not a file already saved. The 600,000-round timing is not measured on a device (it runs on a worker thread). The way to move Mint or Heal between phones without a passphrase is the module's own Export, a readable file.

**Tests that must keep existing:** a clear install is sealed once and shows nothing in any stored text; kept-aside copies are sealed; no key leaves Heal in clear and working, a sealed module with no key reads as locked and writes nothing; another phone's key sets rows aside sealed before any write; snapshots and backup files hold no Mint or Heal in clear and restore on the same phone; a backup from another key changes nothing; an older build's clear backup is sealed on restore; a passphrase backup opens elsewhere only with the passphrase; a module that cannot be opened here cannot be made portable; a sealing that cannot finish leaves the rows in clear.

### 10.5 Export formats

Provide a complete restorable backup separately from readable exports. Human-readable JSON/CSV exports may be offered per module or for selected records, with stable IDs, schema/format version, timestamps, provenance, and relationship references. A partial CSV export is not a full backup. JSON export must have an explicit schema and must not silently omit data necessary for restoration.

### 10.5.1 What exists

The live-documents half of the archive above exists as **one JSON file** (`BackupBundle`, `pokelife-backup` format version 1): the documents `save` and `care` exactly as the app serialises them (plus the module documents, section 10.4.1), the database schema they were taken at, the time and a SHA-256 of the documents. The app keeps daily backups as folders (`LiveBackup`, seven daily and four weekly copies, a `DONE` marker last) and can write any of them, or a fresh one, as a bundle through Android's document picker (`CreateDocument`); a bundle is chosen with `OpenDocument`. There is no `database.sqlite` payload, attachments or whole-archive encryption yet (no module has attachments).

**Restore:** the chosen file is read whole and **staged**: `BackupBundle.stage` refuses a file that is not a backup, is cut short or damaged, fails its checksum, holds a document this build does not know, or comes from a newer format or schema, and changes nothing. A bundle that passes is previewed (its date and contents) and only on confirmation swapped in by `LiveDocuments.replaceAll`: one transaction, in which each replaced document is first kept as `aside:<name>:<millis>`, so a restore can be undone and a failure part way rolls the first write back too. The tables follow the documents, because the swap uses the same write path as every save. The app reloads its stores after a restore. A backup is skipped, never half written, when the phone lacks room for twice its size plus 5 MB (`LiveBackup.lowStorage`); a save that fails for lack of storage rolls back whole, is reported in Settings → Backups, and the next save that fits writes everything again.

A write interrupted before its commit leaves every table at the last committed state (`BackupRestoreTest`, simulated by dropping the connection inside the transaction; a real kill on a device is unproven).

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

### 10.7 Automatic backup off the phone (REP-034)

**Why.** The daily backups are in the app's private folder and system backup is off, so a lost, wiped or replaced phone loses all data that was not exported by hand. The Keystore keys do not survive the phone either, so a copy that leaves it cannot rely on them.

**Setup, once (Settings → Backups → "Back up off this phone").** The player types a passphrase twice (at least 10 characters, the same rule and warning as "Export a backup": it is the only thing that opens Mint and Heal in a copy elsewhere, and a lost one cannot be recovered), then picks a folder with the phone's folder picker (`OpenDocumentTree`), typically one a cloud app such as Drive syncs. The app keeps the permission to that folder (`takePersistableUriPermission`) until the player changes or stops it, and makes the first copy at once. A passphrase is required: without it Mint and Heal could only be sealed to this phone, which is useless off it.

**What a copy is.** One `BackupBundle` file (section 10.5.1) per copy, named `pokelife-backup-<yyyy-MM-dd>-<millis>.json`, taken from `LiveDocuments.snapshot()` in one transaction. Mint and Heal are sealed under the passphrase (`BackupBundle.portable`, section 10.4.1); a copy is not made, and the player is told why, when either cannot be opened on this phone. Plan, Care, Base, Link, the Pokémon and the Bag travel as the other backups carry them (section 10.4.1 lists what is in clear). Restoring a copy is the existing restore: the file is staged and checked whole, previewed, asked for the passphrase, swapped in with the replaced documents kept aside. "Restore copy" in the card stages the newest copy of the chosen folder; on another phone "Restore from a file" opens the same file from the folder.

**When.** A daily WorkManager job (`OffPhone.Worker`, storage not low) makes a copy when the newest in the folder is more than 20 hours old, like the phone's own daily backup; "Copy now" makes one at once. The job does nothing until the backup is set up. It never retries a folder that is gone or a passphrase that cannot be read (retrying would not mend them); only a store that is not open yet is retried.

**Safety.** A copy is a new file, so a failed copy never touches an earlier one (section 10.3). After writing, the file is read back and must equal what was written and pass `BackupBundle.stage`; one that does not is deleted and reported. Only after a copy is verified are the oldest beyond the newest 7 removed, and only files with this app's exact name pattern are ever listed or deleted: anything else in the folder is never touched.

**The passphrase on this phone.** The daily job must seal without asking, so the passphrase is kept as `m1.` text sealed with its own Keystore key (`pokelife.offphone.v1`, context `pokelife-offphone`) in the app's private preferences, and nowhere else. If the key cannot seal it, nothing is stored and the backup is not set up. A wiped or lost phone loses that key; the copies then open with the passphrase the player remembers.

**Status.** The card shows the folder's name and the time of the last verified copy. When the last copy is more than 3 days old it says so quietly, in one line; the last problem (a folder that is gone, a copy that could not be checked) is shown until the next good copy. Nothing else in the app interrupts the player about it, and no notification is sent.

**Stopping.** "Stop" forgets the folder, releases its permission and deletes the sealed passphrase from the phone. The copies already in the folder stay: they belong to the player.

**Not built.** The database snapshot with attachments and whole-archive encryption of section 10.2 (REP-008 remainder: no module has attachments); an automatic copy without a passphrase; sealing Care, Base and Link; a notification about an old copy; merging a copy into existing data. Not run on a device: the folder picker, the Storage Access Framework calls (`SafBackupFolder`) and the 600,000-round passphrase stretching on a worker thread are untested there.

**Tests that must keep existing** (`OffPhoneBackupTest`, `OffPhoneTest`): a copy holds no Mint or Heal in clear and opens on another phone only with the passphrase; the newest 7 stay and nothing foreign is touched; a refused, cut-short or throwing write leaves earlier copies untouched and removes the bad file; no copy when Mint cannot be opened here or the passphrase is short; the passphrase is not in the preferences file and nothing is stored when the Keystore cannot seal it; a folder that is gone is reported and nothing is written.

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

## 12. Legacy JSON saves and the cutover

### 12.1 Legacy files

The two JSON documents are Android's main save `filesDir/save.json` (`AppData`: player, settings, PLAN data, reward log, WORLD data, GEAR state, origins, Dex history, device-specific values) and the CARE save `filesDir/care.json` (`CareData`). The database holds them as the `live_document` rows `save` and `care` (section 12.6); desktop and web still use them as their stores. Compatibility is protected by `SaveCompatibilityTest` (default-field loading, serialized enum-name stability) and by five saves of the first JSON-only version committed in `app-core/src/jvmTest/resources/fixtures/0.123.0/` (`SaveFixtureTest`).

### 12.2 Fixtures

When `AppData`, `PlayerState`, `CareData`, `WorldData`, `Settings` or `GearData` change, keep the fixtures current: every serialized property, all enum names and legacy aliases, every persisted identifier and reference, and values that no longer appear in current enums or catalogs. They must cover small and large collections, evolved and shiny specimens, duplicates, independent module parties, WORLD team assignments and the PC, a full reward history, active expeditions, CARE private content and pending focus/reminder state. They are generated by the code; add an anonymized copy of a real phone save when a test needs real-world shape.

### 12.3 Mapping rules

Anything that brings an older save in (a restore of an older backup) follows these:

- Dex, seen and shiny history are independent of current ownership; every owned specimen ID and its individual attributes (party membership, buddy, favourites, held items, candy progress, IVs/EVs, nature, ability, gender, friendship, level, origin, acquisition timestamps) are preserved.
- The reward log is kept for display and history only; it is never turned into reward events or credited again. Persisted balances and inventory are mapped once; where a ledger cannot be proven equivalent the balance becomes an opening-balance entry with provenance.
- Source timestamps and date/time semantics are preserved; local dates are not reinterpreted as UTC instants. User-authored text is preserved. Legacy enum names map through explicit tables, never by ordinal.
- Account credentials, refresh tokens and cloud or server-only metadata are not carried in.

### 12.4 IDs and cross-file reconciliation

Original IDs are preserved; an old-to-new map is created only when a legacy ID is invalid or collides. A missing optional reference is a warning, never a fabricated record. Where old data has no stable occurrence identity, derive a deterministic key from the source record ID plus its canonical date, record the rule and test collisions; never use the current time or random values.

### 12.5 Reward replay protection

The ledger starts with an opening-balance entry (`live:save:opening-balance`, source `MIGRATION`). Legacy paid markers and counters carry over as eligibility state only where their semantics are clear. Historical PLAN, CARE, WORLD or other activity is never replayed as fresh rewards, and newly introduced module rewards are not granted for old activity. After any such move compare the Poké balance, item quantities, candies, rank, owned Pokémon count and known reward totals against the source.


### 12.6 Cutover and retirement

The database is the live store. Rules:

- **Two documents, one table.** `live_document` holds the documents `save` and `care` exactly as the app serialises them; each save is one transaction. The module tables of section 7.4.1 take documents over one by one.
- **Activation** (`LiveDocuments.activate`, on every start, a no-op once active): in one transaction copy `save.json` and `care.json` in, read each back and require it byte-equal, set the marker `live_store = database`. Any failure rolls everything back and the files stay the live store. A fresh install activates with no documents. **No dual writes:** the legacy files are never written after activation, and are the rollback copy; the daily backup (`LiveBackup`, `filesDir/backups/<millis>/`, seven daily and four weekly copies kept by `BackupRetention`, a `DONE` marker last, an unfinished one never used) holds the same JSON, each file sealed with the phone's Keystore key (section 10.4.1), so only this build on this phone opens it.
- **Corruption:** if the open or `integrity_check`/`foreign_key_check` fails with a corrupt-file error (`AndroidDatabase` overrides androidx's `onCorruption`, whose default deletes the file and opens an empty one), the whole database (and `-wal`, `-shm`) is renamed `*.corrupt-<millis>`, never deleted, and the newest backup is restored (the marker set from it). **With no backup the player is asked (REP-026 c):** the legacy files are only as new as the cutover, so importing them silently would undo everything since. `LiveStore` writes the one-word file `recovery.choice` (`pending`), gives no store (`get` returns null, so widgets, reminders and the backup job wait and the storage refuses), and `MainActivity` shows `RecoveryChoice` before anything opens: "Use the older data" (`older`: the files are imported as a first start would) or "Start fresh" (`fresh`: an empty database, the files ignored). The answer is saved in the same file first and survives a restart; the flag is deleted once the database has opened with it. The damaged file stays aside either way and the old files are never deleted by the choice. With no old files there is nothing to choose and the database starts empty as before; a database that was never live (flag file `live.active` absent) still uses the files without asking. A database from a newer build is left untouched. Once the database has been live (flag file `live.active`), a failed open does **not** fall back to the stale JSON files: the storage refuses, so nothing is overwritten.
- **Restore:** `LiveBackup.restoreNewest` keeps the documents it replaces as `aside:<name>:<millis>` in the same transaction, so a restore can be undone.
- **Retirement:** desktop and web keep their JSON stores. Retire the JSON code and files only after release acceptance and the backup retention requirements.

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

Unreadable saves are kept aside, never overwritten; use structured recovery artifacts and a user-visible explanation.

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

Data lifecycle UI (backup/restore status, migration warnings, import summaries, destructive confirmations, corruption recovery, permission explanations) follows `FEATURES/DESIGN_SYSTEM.md` and its shared dialogue-box components, light/dark, large text, TalkBack, 48 dp targets and reduced motion.

- Keep messages concise and factual: what happened, whether the original data is safe, what action is available, what happens next.
- Destructive restore/erase operations say whether current data will be replaced and require deliberate confirmation.
- Migration progress must not imply success until verification completes; an interrupted migration states that the original save remains preserved when that is true.
- Show backup date, archive version, schema compatibility, included modules and record counts in a readable list or compact table, not one dialogue box per item.
- Do not display raw JSON, database paths, SQL errors, tokens or sensitive payloads by default.

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
- [ ] CARE/HEAL private content and MINT/BASE/LINK sensitive content are absent from generic WORLD/reward/outbox payloads.
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

### 16.6 Legacy saves and migration

- [ ] Fixtures cover the old and current JSON shapes, including `save.json` and `care.json`.
- [ ] Every persisted field is mapped, intentionally excluded, or preserved, with a documented reason; legacy enum names and IDs map deterministically.
- [ ] Pokémon count, specimen IDs, forms, shiny state, individual attributes, party membership and acquisition history match the source.
- [ ] Poké, inventory, candies, rank and reward history reconcile without duplicate credit; no historical activity creates a new reward.
- [ ] Interrupted migration at every checkpoint can resume safely or restore the verified original.
- [ ] A newer schema is never silently downgraded by an older app.

### 16.7 Recovery and deletion

- [ ] Corrupt database is preserved and does not trigger silent blank initialization.
- [ ] Integrity check/recovery operates on a copy where appropriate.
- [ ] Failed migration/restore leaves the prior known-good data accessible.
- [ ] Deletion removes required attachments, search indexes, thumbnails, and derived caches.
- [ ] Historical reward/completion records retain required integrity without retaining unnecessary private content.
- [ ] Full local erase has explicit scope and confirmation.

### 16.8 UI and release

- [ ] Data lifecycle screens follow the shared design system (section 15); light/dark, large text, TalkBack, keyboard/focus and reduced motion pass.
- [ ] Migration, backup, restore, corruption, permission, empty, loading and failure states are tested on device, and performance and storage use are profiled on target Android hardware.
- [ ] No cloud account or synchronization path is required for local data integrity.

## 17. Implementation sequence and gates

Still to do, in order:

1. **Domain migration:** the typed repositories per module (section 7.3.0), keeping one database and clear module ownership.
2. **Backup archive and recovery:** the full archive of section 10.2 (database snapshot, attachments, whole-archive encryption; the JSON bundle with its checksum, the restore preview and the user-chosen destinations are built, sections 10.5.1 and 10.7), per-kind export of a backup, interrupted-restore, corruption and low-storage drills on a device.
3. **Release verification:** full tests, migration fixtures, device tests, UI/accessibility review and recovery drills.

Do not broaden module implementation while the data foundation or migration gate is failing. The exact feature roadmap remains in `IMPLEMENTATION_GUIDE.md`.

## 18. Cross-reference authority

- Product-wide local-first/no-account direction, module map, privacy principles, and decision precedence: `../PRODUCT_BIBLE.md`.
- Implementation status and delivery sequence: `../IMPLEMENTATION_GUIDE.md`.
- Module-specific canonical records and workflows: `../MODULES/PLAN.md`, `CARE.md`, `HEAL.md`, `MINT.md`, `BASE.md`, `LINK.md`, `GEAR.md`, and `WORLD.md`.
- Shared Pokémon identity, acquisition, collection, evolution, Bag, and reward semantics: `../FEATURES/POKEMON_ECOSYSTEM.md`.
- Shared reminder scheduling, policy, delivery, action routing, and recovery: Section 9 of `INTEGRATIONS_AND_PRIVACY.md`.
- PMD dialogue-box geometry, components, accessibility, and visual states: `../FEATURES/DESIGN_SYSTEM.md`.
- Provider capabilities, permissions, provenance, and privacy: `INTEGRATIONS_AND_PRIVACY.md` when finalized.
- Data-specific test catalogue and release gates: `TEST_STRATEGY.md` when finalized.

This document owns shared data architecture, persistence, transactions, backup/restore, migration, and recovery. Module and feature documents own their domain-specific behavior and must link here rather than duplicate the shared data lifecycle.

---

## Testing reference

Repository contracts, transaction integrity, migration fixtures, backup/restore, crash recovery and data reconciliation must follow [`TEST_STRATEGY.md`](TEST_STRATEGY.md). Migration and recovery tests are P0 release gates; supported legacy fixtures and retry/failure paths must be exercised against the real persistence implementation.
