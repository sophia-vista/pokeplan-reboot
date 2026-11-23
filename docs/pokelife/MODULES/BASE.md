# PokéLife — BASE Module Specification

**Version:** 1.0  
**Status:** Approved product specification  
**Module:** BASE (stored names keep the old working name: the `home_*` tables, event ids and save keys use "home")  
**Scope:** Personal household management  
**Shown as:** **Base**: the app shows this module as "Base" (menu card, header, party, perks and region, "Base Region"); "Home" is only the first tab. Code uses `Base*` names (`base-core`, `app.pokeplan.base`); stored ids, tables and saves keep HOME (Product Bible section 3), except the enum constants `Module.BASE`, `Place.BASE`, `Region.BASE` (saved as `"HOUSEHOLD"`) and `ReminderModule.BASE` (a stored `"HOME"` is read as BASE).

> This document is the single source of truth for BASE-specific product behavior, data ownership, workflows, progression, privacy adaptations, implementation direction and acceptance criteria. Product-wide rules remain in the Product Bible; shared reminder behavior, visual components, data lifecycle and Pokémon/economy services remain in their respective shared specifications. Do not duplicate those systems here.

## 1. Purpose and boundaries

BASE is PokéLife's practical household operating hub. It reduces the mental effort of remembering what needs cleaning, replacing, repairing, checking or maintaining, and provides one local place for useful household records.

BASE should answer five questions:

1. What household responsibilities need attention?
2. What supplies do I have, and what is running low?
3. What needs maintenance, and when?
4. Where is important household information stored?
5. What household work have I already completed?

BASE owns household records and their business rules. It is not a general task manager, finance tracker, note-taking application, property-management suite or smart-home controller.

### 1.1 Core capabilities

- One-time and recurring household chores.
- Household areas/rooms for optional organization.
- Consumable supplies and durable household inventory.
- Manual quantity tracking and optional low-stock thresholds.
- Household assets, maintenance history and schedules.
- Warranty information.
- Household-specific notes and reference material.
- Optional links to PLAN tasks without duplicate ownership.
- Fast capture, low-maintenance workflows and local search.
- Optional reminders through the shared reminder feature.
- BASE's own Pokémon acquisition identity: **Habitat Discovery**.

### 1.2 Explicit non-goals

Initial BASE does not include:

- Multiple properties or a property portfolio.
- Landlord/tenant portals, rent collection or property accounting.
- Household collaboration, shared accounts or multi-user permissions.
- Smart-home automation, device control or IoT dashboards.
- Automatic monitoring of the physical home.
- Full shopping, procurement or price-comparison workflows.
- Financial accounting, payments or transaction ownership.
- General project management or a second task manager. (A **household project** (4.4A) is one lightweight record for a planned purchase or improvement, not project management.)
- A general-purpose notes/wiki application.
- Barcode-driven automation as a release dependency.

The initial scope is one personal household and one local user. Areas are optional; a record can be created without assigning a room.

## 2. Ownership and module boundaries

| Capability / record | Canonical owner | BASE relationship |
|---|---|---|
| Household chores and chore schedules | BASE | Owns definitions, occurrences and completion history |
| Household maintenance and warranties | BASE | Owns assets, schedules and service history |
| Household supplies and inventory | BASE | Owns item identity, quantities and low-stock state |
| Household reference notes | BASE | Owns household-specific content and attachments |
| General tasks, projects and focus | PLAN | Optional linked task; never a duplicate chore |
| Shared reminder scheduling/delivery | `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture` | BASE supplies source-aware requests |
| Purchases, bills and transactions | MINT | BASE may link only through explicit references; no financial copy |
| Household projects (a planned purchase or improvement, 4.4A) | BASE | Owns the project record and its rough budget as the player's own note; the purchase itself is a MINT transaction, linked by reference only |
| Contact records and relationship context | LINK | Optional service-provider reference through LINK ownership |
| Generic calculators/converters | GEAR | May be reused; BASE owns household meaning and labels |
| Pokémon, Bag, currency and reward ledger | Shared Pokémon/economy services | BASE emits eligible activity events only |
| Health, reflection and relationship-sensitive records | HEAL / CARE / LINK | No access unless an explicit, approved link is introduced |

### 2.1 BASE versus PLAN

A recurring household chore is a BASE record, not a PLAN recurring task. BASE owns the canonical chore definition, schedule, occurrences and completion history.

A user may explicitly create a linked PLAN task when they want the responsibility visible in PLAN. PLAN displays a linked representation referencing the BASE record. Completing it from PLAN calls BASE's completion operation. It must not create an independent PLAN completion or a second reward.

The link is optional and one-way in ownership: BASE remains canonical. Unlinking removes the projection/reference, not the BASE chore. If the user explicitly converts a responsibility into a PLAN-owned task, the UI must explain that it is a new PLAN record with PLAN rules; do not silently transfer or duplicate ownership.

### 2.2 BASE versus MINT

BASE tracks what is owned and its household state. MINT owns financial facts. An optional link to a MINT transaction may be provided only through a user-selected stable reference. BASE must not copy transaction values, account data or payment credentials into its own records. A purchase/restock action does not automatically create a MINT transaction.

## 3. Information architecture

Use a compact navigation structure with five primary destinations, plus a focused overview:

1. **Overview** — actionable household status and quick capture.
2. **Chores** — household responsibilities, schedules and history.
3. **Inventory** — supplies, durable items, quantities and restocking.
4. **Maintenance** — assets, maintenance records, schedules and warranties.
5. **Reference** — searchable household notes and practical information.
6. **Room** — the Secret Base room, a view of Base's party among earned decorations (section 9.5; a menu card, not a tab).

**Shown as:** the app labels the Inventory destination **Belongings** (three tabs: **Stock**, **Shopping**, **Items**) and Reference **Notes**, so consumables, the shopping list and durable items are one destination rather than items living under Maintenance (the as-built list in 9.7 uses the shipped names).

Search is available from the BASE shell and covers all BASE-owned record types. Settings remain a secondary destination, not a primary tab.

### 3.1 Overview

Overview is an attention surface, not an analytics dashboard. Show only useful current information, in this priority order:

- Chores due today or overdue.
- Upcoming or overdue maintenance.
- Supplies below their configured thresholds.
- Recently updated records only when useful.
- Quick-add actions for chore, inventory item, asset/maintenance and reference note.

Each summary opens its canonical destination. Users can collapse or hide optional sections. Do not list every inventory item, historical chore or completed maintenance record on the overview. A compact status summary may show counts for chores requiring attention, low-stock items and upcoming maintenance.

### 3.2 Chores

Provide practical views such as Due, Upcoming and History. Avoid a separate complex planner. Filters may include area, category and status. Quick completion must be available from list rows.

### 3.3 Inventory

Separate consumables from durable household items in presentation and editing, while keeping a coherent inventory domain. Provide All, Low stock and Restocking views. Quantity and unit must be visible without opening every record.

### 3.4 Maintenance

Show upcoming, overdue and history views. Asset detail is the canonical place for identification, warranty details, associated maintenance schedules and completed service records.

### 3.5 Reference

Provide a searchable list of household-specific notes and documents, with optional categories and links to an asset or area. Do not evolve this into a general wiki or knowledge-management product.

## 4. Features and behavior

### 4.1 Household areas

`BaseArea` is an optional organizational label, not a required hierarchy. Suggested initial examples: Bedroom, Kitchen, Bathroom, Living area, Balcony, Storage and General household.

Users can create, rename, reorder and archive areas. Archiving an area must not delete its records; records become unassigned or retain a clearly marked archived-area reference according to the data model. Avoid enforcing a room tree or floor plan. Records can exist without an area.

### 4.2 Chores

A chore is a household responsibility with an optional schedule. The user should be able to create one with only a title.

**Chore definition fields**

| Field | Requirement / behavior |
|---|---|
| Title | Required |
| Description / instructions | Optional |
| Area | Optional `BaseArea` reference |
| Category | Optional; e.g. cleaning, laundry, kitchen, general, outdoor |
| Schedule | One-time or recurring; optional for unscheduled chores |
| Frequency | Supported recurrence described below |
| Estimated duration | Optional informational value |
| Reminder preference | Optional BASE-owned preference using shared reminders |
| Status | Active, paused or archived |
| Created / updated | System timestamps |

**Scheduling**

Support one-time due dates and recurring schedules for daily, selected weekdays, weekly, monthly and yearly use cases where deterministic. A custom interval may be added only where it can be represented, previewed and tested reliably. Do not expose arbitrary cron-like expressions.

Use a canonical `Chore` definition and distinct `ChoreOccurrence` instances or an equivalent model. Occurrences must be deterministic and idempotent. Completing one occurrence does not complete future occurrences. Editing recurrence must not rewrite completed history. Rescheduling one occurrence must not silently shift the entire recurrence. Where safe, recurrence editing can offer this occurrence, this and future occurrences, or the schedule definition; if not all scopes can be implemented safely, expose only the supported subset and document it.

**Occurrence behavior**

- Complete an occurrence with one immediate action.
- Record a completion timestamp and optional note.
- Allow undo of an accidental completion, subject to reward-ledger reversal/idempotency rules.
- Allow skip or defer without destroying future occurrences.
- Missed occurrences do not accumulate an unlimited backlog; the module should present relevant overdue work without generating every missed instance indefinitely.
- Pause preserves the definition and history.
- Archive removes the chore from active views but preserves history.
- Editing title, area or category does not mutate historical completion facts.
- No penalty, lost progress, punitive streak or shame-oriented message for missed chores.

**History**

Show a lightweight chronological history of completed, skipped and deferred occurrences. Completion notes are optional. Do not make streaks or performance analytics a primary feature. The purpose is to remember what was done, not judge household performance.

### 4.3 Consumable inventory and supplies

Consumables include pantry goods, cleaning products, laundry detergent, toiletries and disposable household supplies.

**Consumable fields**

| Field | Requirement / behavior |
|---|---|
| Name | Required |
| Category | Optional |
| Quantity | Numeric or explicitly unknown |
| Unit | User-selectable unit such as pieces, packs, bottles, kg or L |
| Low-stock threshold | Optional, compatible with the item's unit |
| Storage location / area | Optional |
| Notes | Optional |
| Last updated | Automatic |

**Quantity operations**

- Increase quantity when restocking.
- Decrease quantity when consuming or removing stock.
- Set quantity directly when exact consumption tracking is unnecessary.
- Allow unknown quantity without treating it as zero.
- Prevent negative stock by default; if a correction would result in a negative value, ask the user to correct the entry rather than silently clamp it.
- Preserve update time and optional adjustment reason.
- A complete consumption ledger is not required. `StockAdjustment` history is lightweight and may be bounded/compacted under the shared data-retention policy.

**Low-stock behavior**

An item is low stock when its known quantity is less than or equal to its configured threshold. Unknown quantity is not automatically low stock. If no threshold is configured, do not infer one. Low-stock items appear in Overview and the Low stock filter. Users may dismiss the current low-stock notice or mark the item as restocked; dismissal does not change quantity or threshold.

**Restocking list**

Provide a lightweight list derived from low-stock items plus manually added entries. Users can mark an item purchased/restocked, adjust its quantity, remove it from the list or add a manual item. A restocking list is not a MINT transaction or a PLAN task. If the user explicitly wants a PLAN shopping task, create a linked PLAN task by user action; BASE remains the source of the supply record.

### 4.4 Durable household inventory and assets

Durable items include appliances, furniture, electronics, tools and household equipment. They may also be registered directly as maintenance assets without a general inventory entry; avoid duplicate asset identity.

An asset may have:
- Name and optional category.
- Brand and model.
- Serial number (optional; treat as sensitive household reference data).
- Purchase date (optional).
- Area/location (optional).
- Notes.
- Optional photos/documents.
- Optional warranty record(s).
- Zero or more maintenance schedules and maintenance records.

Asset identity must use stable IDs. A user can edit, archive or delete an asset. Deleting an asset must clearly explain the effect on associated records and attachments; prefer archive where history should be retained. Do not require purchase price or seller details.

### 4.4A Household projects (backlog REP-061)

Irregular household jobs, such as buying an air conditioner for a room, are neither chores, maintenance nor supplies. This is the one addition; it does not make BASE a project manager.

A household project is one record: a name (the only required field), an optional area, an optional rough budget (a figure the player types, never a MINT balance or a copy of a transaction), an optional target date, a status (Thinking, Planned, Done, Dropped) and a private note. It may carry the same optional reference to a MINT transaction that an asset has (2.2), set by the player and never copied.

- **Becoming an asset.** Marking a project Done offers "Add as an asset" once, prefilled with the name, area and purchase date; declining leaves the project as a record. An asset created this way may offer a maintenance schedule (4.5) in the same step, optional.
- **No sub-structure.** No milestones, dependencies, sub-projects or percent complete. A project that needs steps is a PLAN list or task, linked as in 2.1.
- **Areas, not properties.** A project may name an optional area (4.1) and never needs one. It does not add a second property (1.2 stands): an occasional job at the owner's other home is a project with a note saying where.
- **Privacy and rewards.** Names, budgets and notes follow section 8. A project emits no reward event of its own; finishing the linked maintenance or chore does, as before.

### 4.5 Maintenance

Maintenance records track work performed or a maintenance action that is planned. Keep scheduled maintenance distinct from completed history.

**Maintenance record fields**

- Title (required).
- Associated asset (optional for general household maintenance).
- Description/instructions (optional).
- Performed date or scheduled date as appropriate.
- Status (planned, completed, cancelled/archived as appropriate).
- Next maintenance date or schedule reference (optional).
- Service provider/contact reference (optional, LINK-owned if linked).
- Cost (optional, informational only; not an accounting record).
- Notes and attachments (optional).

**Schedules**

Support one-time and recurring schedules, including annual checks and user-defined intervals where they can be reliably calculated. A schedule generates upcoming maintenance occurrences deterministically. Completing maintenance records the event and calculates the next due date from the schedule's defined recurrence policy. Do not reset warranty data or rewrite purchase information when maintenance is completed. An asset may exist without any maintenance schedule.

**History**

Asset detail displays a chronological maintenance history. Preserve the completed record's original date, notes and attachment references when a future schedule is edited. Cost is informational and must not be treated as a MINT expense.

### 4.6 Warranties

A warranty record may include provider/manufacturer, coverage description, start date, expiry date, reference number, document/photo attachment and optional notes. Warranty expiry is informational; users may opt into an expiry reminder. Do not infer warranty coverage or legal rights from a model number or document. Do not silently delete expired warranty history.

### 4.7 Household reference

Reference records store practical household-specific information such as:

- Appliance manuals, model numbers and instructions.
- Measurements and dimensions.
- Warranty and service references.
- Service-provider details or links to LINK contacts.
- Household-specific network/router reference information.
- Storage/location notes.
- Other practical household notes.

Each reference has a title, optional category, text content, optional area/asset link, optional attachments, and timestamps. Search must cover title and text content locally. Reference content remains local and private. Do not implement general-purpose folders, backlinks, a block editor, collaborative editing or an independent wiki engine.

### 4.8 Search, filters and settings

One BASE search covers chores, inventory, assets, maintenance, warranties and reference records. Results identify their record type and open the canonical record. Search is local and available offline.

Useful filters: area, category, active/paused/archived, low stock, due/overdue maintenance and record type. Keep filter controls compact.

BASE settings are limited to defaults such as quantity units, optional default categories, low-stock presentation, chore preferences and maintenance preferences. Global theme, accessibility, notification permission, quiet hours and data lifecycle remain shared settings/services.

## 5. Data model and persistence

The following are logical domain concepts. They do not require one physical database table per concept; follow the approved architecture and normalization strategy.

| Entity | Responsibility |
|---|---|
| `BaseArea` | Optional household zone/room |
| `Chore` | Canonical chore definition and schedule |
| `ChoreOccurrence` | Generated one-time/recurring occurrence and state |
| `ChoreCompletion` | Immutable completion fact/event, linked to occurrence |
| `InventoryItem` | Consumable or durable household item |
| `StockAdjustment` | Optional quantity change history |
| `MaintenanceAsset` | Appliance, equipment or other maintained asset |
| `MaintenanceRecord` | Planned or completed maintenance activity |
| `MaintenanceSchedule` | Recurrence definition for future maintenance |
| `WarrantyRecord` | Warranty dates and reference/attachment metadata |
| `BaseReference` | Household note or reference record |
| `BaseSettings` | BASE-specific preferences |
| `BaseResearchState` | BASE-owned Habitat Discovery progress/configuration state |
| `BaseRewardEvent` | Minimal idempotent eligibility event and shared reward payment reference |

Relationships:

- `BaseArea` may organize chores, inventory items, assets and references.
- `Chore` generates `ChoreOccurrence` records; an occurrence may have one completion event.
- `InventoryItem` may have zero or more stock adjustments.
- `MaintenanceAsset` may have maintenance records, schedules and warranties.
- `BaseReference` may optionally link to an area or asset.
- A linked PLAN task references a BASE record using a stable opaque ID; it is a projection/association, not a duplicate canonical record.
- Reminder requests reference BASE source IDs opaquely; shared reminder storage is not a BASE database.

### 5.1 Data rules

- Stable IDs for all records and events.
- Explicit relationships and referential integrity.
- Created/updated timestamps; event timestamps for completions and maintenance.
- Local database is authoritative.
- Soft deletion/archive where history or linked records should remain recoverable.
- Explicit deletion behavior for attachments and linked records.
- Versioned migrations and deterministic occurrence generation.
- Backup/restore and export/import through the shared data lifecycle; no BASE-specific save file.
- Do not persist derived statuses (overdue, low-stock) when they can be computed reliably from canonical data. If cached, reconcile on load.
- Do not store sensitive household detail in analytics, logs, reward events or shared scheduler records.

### 5.2 Backup, export and deletion

BASE records and attachments participate in shared local backup/restore and data export. Preserve IDs and relationships on restore. Exports should be usable and document supported formats; avoid embedding private attachment contents in logs or diagnostics. Users can export selected BASE categories or all BASE data and delete individual records or all BASE data without deleting Pokémon, Bag contents, PLAN records, MINT transactions or other modules' data. Backup encryption and device-level protection follow shared architecture/security specifications.

## 6. Reminders and notifications

Shared scheduling, notification delivery, global quiet hours, focus deferral, permissions, recovery, notification identity, action routing and scheduler privacy are defined exclusively in `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`.

BASE owns only:

- Whether a reminder is enabled for a specific BASE record.
- BASE-specific default reminder choices, if any.
- The source record's meaning and canonical state.
- Validation and execution of BASE-specific notification actions.
- Creation/update/cancellation requests through the shared reminder adapter.

Potential BASE reminder sources:

| Source | Default | Behavior |
|---|---|---|
| Chore occurrence | Off unless user enables it | Remind for a selected occurrence or configured schedule |
| Maintenance schedule | Off unless user enables it | Upcoming maintenance reminder |
| Low-stock item | Off unless user enables it | Optional notice when quantity reaches threshold |
| Warranty expiry | Off unless user enables it | Optional notice before user-entered expiry date |

Rules:

- Reminders are optional and non-nagging.
- No default reminder for every chore or every low-stock item.
- A source edit, completion, pause, archive or deletion updates/cancels its reminder request.
- Stale reminders cannot open or mutate deleted/archived/ineligible records.
- Notification actions route to BASE's adapter and validate current state.
- Reminder delivery, snooze or dismissal is not a reward-bearing activity.
- Do not copy chore titles, inventory names, asset serial numbers, maintenance notes or reference content into shared scheduler storage. Resolve concise notification text as late as practical and follow lock-screen privacy policy.

BASE does not implement its own scheduler, notification channels, quiet-hours engine, focus queue, Android delivery or recovery logic.

## 7. Integrations and permissions

BASE is manual-first and does not require network access or external accounts.

### 7.1 Android capabilities

- Use Android document/photo pickers for user-selected attachments.
- Optional camera capture for asset documentation, requested contextually.
- Android share/import intents may accept user-selected household reference material where safe and supported.
- Local notification delivery uses the shared reminder feature.
- No location permission is required.

Permission denial must not impair core BASE functionality. Do not request camera, photo or file permissions at startup. Explain each permission at the point of use and support supported system pickers without broad storage access.

### 7.2 Optional future integrations

Barcode-assisted inventory capture and user-selected external document sources may be considered after core workflows. They are not release dependencies. Any future connector must use documented, user-authorized interfaces, explicit scope and clear disconnect/deletion behavior. No smart-home connection or background household surveillance is in initial scope.

## 8. Privacy and security

Household information can reveal routines, possessions, property details, room layouts, location context, serial numbers and private documents. Treat all BASE content as private personal information.

- Store BASE records locally under the product-wide local-first/no-mandatory-account direction.
- No PokéLife cloud sync, advertising, profiling or telemetry containing BASE content.
- Do not transmit addresses, room names, serial numbers, inventory, chore descriptions, maintenance notes, warranty documents, photos or reference text to WORLD, launcher or reward services.
- Do not use household content to choose Pokémon species, rarity or reward size.
- No location permission or automatic location inference.
- Protect local storage and backups according to shared data-security policy.
- Do not log raw attachment contents or private household text.
- Support per-record edit/archive/delete and clear attachment deletion behavior.
- Lock-screen notification content follows shared privacy controls.
- External provider transmission, if ever added, requires explicit user action and scope disclosure.

The progression event contract in Section 9 is the only permitted BASE-to-shared-reward boundary. It contains no human-readable household content.

## 9. Pokémon progression — Habitat Discovery

**Habitat Discovery is BASE's approved Pokémon acquisition identity.** It is distinct from HEAL's Field Research and from PLAN's planning/focus rewards. BASE frames eligible household upkeep as caring for a habitat: meaningful activity contributes to discoveries in BASE's regional pool.

Habitat Discovery is an acquisition/progression presentation, not a reason to create chores, increase household workload or expose private records. Core BASE remains fully useful with rewards disabled or unavailable.

### 9.1 Core loop

1. The user completes a qualifying household activity.
2. BASE evaluates the event under a versioned BASE eligibility policy.
3. BASE records one minimal, idempotent progression event.
4. Accumulated Habitat Discovery progress makes a discovery/encounter opportunity available.
5. Recruitment resolves through the shared Pokémon acquisition and collection services.

BASE Research progress is BASE-owned progression state, not a separate currency. The shared Pokémon/economy services own reward delivery, Bag/currency, Pokémon instance registration and the global collection. BASE creates no separate collection, Bag, Poké balance, egg implementation or reward ledger.

### 9.2 Qualifying activities

| Activity | Eligible | Notes |
|---|---|---|
| Complete a chore occurrence | Yes | One event per valid completion; undo/re-completion follows idempotent reversal policy |
| Complete a meaningful maintenance activity | Yes | Only when marked completed as real work, not merely created or scheduled |
| Complete an explicitly defined household upkeep milestone | Yes, if approved in the versioned policy | Must represent meaningful completed work and resist trivial splitting |
| Create/edit/archive a chore | No | Administrative operation |
| Open BASE or view a record | No | Passive use |
| Add/edit inventory or change quantity | No | Administrative bookkeeping; stock adjustment is not proof of household upkeep |
| Add a maintenance schedule or warranty | No | Administrative operation |
| View or edit reference notes | No | Administrative operation |
| Add a purchase/restocking list entry | No | Does not prove a purchase or household task completion |

Do not award for repeated toggling, rapid undo/re-complete loops, duplicate linked PLAN actions or migration replay. If one activity is visible from PLAN and BASE, BASE's canonical completion event is paid once. A PLAN task that is merely linked to a BASE chore must not independently pay a PLAN reward for the same real-world completion.

### 9.3 Reward behavior and balance

- Use the shared economy's generous progression principles and idempotent ledger.
- Provide frequent, predictable positive progression during ordinary household upkeep.
- Meaningful milestones may provide additional progress/reward only under the versioned BASE policy.
- Rare Pokémon remain discoverable without excessive grinding.
- Duplicates remain separate owned Pokémon instances.
- Rare Candy, Super Candy and other shared items follow the shared economy specification.
- No BASE-only currency, Bag, Pokémon ownership or parallel reward ledger.
- No attendance streak, daily quota, loss of accumulated progress, missed-day penalty or expiring discovery.
- The user can disable Habitat Discovery rewards without disabling BASE functionality.
- Reward thresholds, caps, rarity weights and quantities are calibrated centrally with other modules in shared economy simulations; do not hard-code an independently generous or stingy BASE economy.
- Historical import/migration never pays retroactive BASE rewards.

The regional acquisition pool should primarily use species appropriate to BASE's approved habitat/region identity. Maintain meaningful type and rarity representation. Exact roster composition, regional dex membership and encounter weights belong to the shared Pokémon ecosystem specification and the BASE content configuration, not household records.

### 9.4 Privacy-safe event contract

The shared reward boundary may receive only minimum metadata such as:

- Stable opaque event ID.
- Source module identifier (stored as `"home"`, `BaseEvent.MODULE`).
- Opaque source-record reference.
- Broad eligible event category.
- Event timestamp.
- Eligibility-policy version.

Do not send chore titles, room/area names, inventory names or quantities, asset identities, serial numbers, purchase costs, maintenance notes, warranty data, reference text, filenames or attachment contents to WORLD or the reward presentation layer. Prefer evaluation inside BASE/reward boundary and expose only a generic reward result to WORLD. Reward events must be idempotent; edits, deletions, undo/redo and migration must not replay paid events.

### 9.5 The Secret Base room *(REP-053; every number is a *(tune)* starting value)*

Art, scale and how decorations are earned: `../FEATURES/POKEMON_PLACES_IDEAS.md` section 6; this section is the rule. It adds one card, **Room**, to Base's menu (a sixth destination beside the five in section 3, opening on the menu of cards, not a tab).

**9.5.1 What the room is.** A Hoenn Secret Base room (Pokémon Emerald art, `assets/hoenn/`) that shows **Base's party** standing among the decorations the player has placed. It is a view, never a place: the room has no slots and no capacity, assigns nothing and moves nothing, so a Pokémon is in Base's party exactly as before and "one place only" (`../FEATURES/POKEMON_ECOSYSTEM.md` 2.2) is untouched. An empty party is an empty furnished room. The room has **no upkeep**: nothing wilts, gathers dust, gets hungry or asks for a visit, and a room left alone for a year looks as it did. It works fully with Habitat Discovery off (the player can still place what they own; only earning stops). Pokémon are the PMD sprites (their Idle animation, a still frame under Reduce motion) at the same pixel size as the room. The room is drawn at 5x on a phone and 4x under about 1000 px, flush with the dialogue frame.

**9.5.2 A decoration.** A cosmetic object with a stable id (its Emerald file name, e.g. `snorlax_doll`), a name and a kind, a 24 px sprite (`assets/hoenn/decorations/`, palette colour 0 see-through). It has **no effect**: no bonus, no stat, no sale value, no use anywhere else; it is neither money nor a Bag item. It takes **one cell** (the sprite is drawn centred on the cell with its feet on the cell's bottom edge); a **mat** lies on the floor under whatever stands on it, every other decoration stands. The catalogue is the 42 sprites (`put_away_cursor.png` is a menu cursor, not a decoration):

| Kind | Decorations |
|---|---|
| Dolls (10) | Blastoise, Charizard, Lapras, Regice, Regirock, Registeel, Rhydon, Snorlax, Venusaur, Wailmer |
| Mats (7) | Attract, Fire Blast, Fissure, Powder Snow, Spikes, Surf, Thunder |
| Desks (7) | Brick, Camp, Comfort, Hard, Heavy, Pretty, Ragged |
| Plants (6) | Big, Colorful, Gorgeous, Red, Tropical, Pretty Flowers |
| Tents (2) | Blue, Red |
| Bricks (3) | Blue, Red, Yellow |
| Ornaments (2) | Glass, Sand |
| Others (5) | Breakable Door, Slide, Solid Board, Stand, Tire |

**9.5.3 The room: grid and looks.**
- **Grid.** The cells every size-1 Hoenn layout can walk on (the `SecretBase_*1` maps, 11 x 9 metatiles of 16 px; a cell is walkable where its collision bits in `map.bin` are 0 in all six): **44 cells**, rows of 3, 5, 8, 8, 8, 7 and 5 starting at row 1 (hand-derived from the six size-1 layouts; `RoomTest` checks the 44 cells and three of them in detail). Decorations sit on grid cells, at most **one per cell**, with no limit besides the cells. Because the grid is common to every look, a change of look never moves or loses a decoration.
- **Looks (three room types, cave in four colours).** The look is derived from the **primary type of Base's lead** and is not stored (there is no choosing a look). With no lead the room is Shrub.

| Look | Types |
|---|---|
| Tree | Grass, Bug, Flying |
| Shrub | Normal, Fairy, Psychic |
| Red cave | Fire, Fighting, Dragon |
| Brown cave | Rock, Ground, Steel |
| Blue cave | Water, Ice, Ghost |
| Yellow cave | Electric, Dark, Poison |

- **Size.** Only the size-1 layout of each look is used (11 x 9); the three larger layouts of each look (`layouts.tsv`) stay unused (the room does not grow).
- **Where the Pokémon stand.** Base's party, in party order, on cells that hold no standing decoration: each of the first six has its own spot spread over the floor (back left, back right, middle, front left and right), or the free cell nearest to it when a decoration stands there, so the scene looks the same each time it opens. A mat does not block a cell. Their places are never stored.

**9.5.4 Earning (BASE upkeep).** A **Decoration meter** fills from the payments Habitat Discovery already makes, under the same switch, the same daily caps and the same idempotent event id: a chore done adds **1 point**, a piece of maintenance done **2**. At **5 points** *(tune; the Habitat Egg meter's size)* the meter pays **one decoration** and carries the rest over. Which one: **a random decoration the player does not own yet**, drawn from the event id so a replay, restore or undo-and-redo gives the same one and pays once (one of each: a decoration is owned at most once). Once all 42 are owned the meter stops filling. Nothing is taken back by an undo, as for every Habitat Discovery payment. Creating, editing, skipping, quantities and the rest still pay nothing (9.2). A decoration reaches the player with a short line on the payment's result, never a dialogue to get past. The other two ways of getting one (bought at the Department Store, Mom's gifts, chunks 6 and A6) put a decoration into the same owned set and are specified in REP-053 chunk 6 (no MINT.md section exists yet); the order of the three never matters. A new save starts with **three** decorations (a doll, a mat and a plant: `snorlax_doll`, `thunder_mat`, `big_plant`), granted once and never again.

**9.5.5 Placing and removing.** *Arrange* is a mode of the Room screen. A tray lists the decorations the player owns and has not placed ("in the box"); tapping one and then an empty cell places it; tapping a placed one picks it up (into the box) or moves it to another empty cell. Placing, moving and removing cost nothing, can be repeated without limit, are one undo-able step each, and never lose or sell a decoration; a decoration can be placed only if it is owned and unplaced, on an empty grid cell (rules, not screens, refuse the rest). There is one room per save. Changing the party or the lead never changes what is placed.

**9.5.6 Data and privacy.** *Owned decorations and the meter* are shared Pokémon-side state like the Bag and the Egg meters (three sources write to the owned set; the Bag's rules for a reward apply): `docs/economy.md` lists the reward and its meter. *Placement* is Base's own record: `home_room` rows (decoration id, cell), in Base's export and erase (`BaseKinds`), in the daily backup. A decoration id is public game content, so the reward event stays what 9.4 says (no household word), and nothing about a chore, an asset or an area reaches the room. Not sealed (the room is not private).

**9.5.7 Not in this section.** Berry plots (a BASE.md section still to be written), badges (PLAN.md 9.1), Department Store and Mom (REP-053 chunk 6; no MINT.md section yet), the other places, Mail and Gimmighoul coins, rooms of the larger sizes, and any second room.

**Acceptance.** The room shows exactly Base's party and nothing assigns or moves a Pokémon; the look follows the lead's type and every one of the 44 cells is placeable in every look; place, move and remove are rules in `base-core` with tests (owned and unplaced, empty cell, a cell outside the grid, a repeat); the meter pays once per event id however often an occurrence is undone and redone, honours the daily caps and the Habitat Discovery switch, and never pays a decoration already owned; a save from before this section opens with an empty room and the starter decorations; screenshots in both palettes for an empty room, a furnished room, the tray and a look of each type.

## 10. Offline behavior and lifecycle

Core BASE is fully functional offline.

| Capability | Offline behavior |
|---|---|
| Chores, schedules and history | Full local functionality |
| Inventory and quantity changes | Full local functionality |
| Low-stock state and restocking list | Full local functionality |
| Assets, maintenance and warranties | Full local functionality |
| Household reference and search | Full local functionality |
| Local attachments | Full functionality for files stored locally |
| Shared local reminders | Supported subject to Android permission/platform behavior |
| Export/backup | Local destination supported |
| Optional future external integrations | Unavailable until provider access returns; never required |

Handle process death, app restart, device reboot, time-zone/system-clock changes, permission revocation, low storage, interrupted attachment operations and app updates. Recurrence generation and reminder reconciliation must be idempotent. A temporary platform failure must not lose a completion or quantity change after it has been committed locally.

## 11. Visual presentation

BASE follows `../FEATURES/DESIGN_SYSTEM.md` and its **compact utility presentation**. The shared design system owns PMD dialogue-box geometry, borders, typography, tokens, component anatomy, states, accessibility and interaction behavior. This section defines only BASE-specific composition.

- Overview uses a small number of adaptive PMD dialogue-box panels: household status, due chores, low-stock summary and upcoming maintenance.
- Do not place every chore, inventory item or paragraph in a separate dialogue box. Use efficient list rows, timelines and forms with selective framing.
- Chore rows prioritize completion control and title, then relevant schedule/area metadata.
- Inventory rows show name, quantity/unit and low-stock state clearly; do not rely on color alone.
- Maintenance asset detail prioritizes identification, warranty state and next maintenance.
- Reference uses readable native-feeling text and document layouts, not game dialogue as the reading surface.
- Pokémon portraits/sprites may appear in Habitat Discovery, discovery results and restrained empty-state feedback. Do not decorate every record or obstruct the primary real-world action.
- Pokémon feedback must be optional or brief, must not delay completion, and must not require navigating dialogue to perform a household action.
- Use shared light/dark tokens, scalable system typography, TalkBack semantics, minimum 48 dp targets, reduced-motion behavior and responsive layouts.
- Keep pixel art crisp and subordinate to utility content. Do not invent a separate BASE design language or independent component variants where shared components suffice.

## 12. Reuse policy

BASE reuses the shared foundations (local data layer, migrations, backup/restore, Pokémon collection and reward services, reminder scheduler through a BASE source adapter, PMD design-system components, GEAR calculators where useful) and owns its domain: models, repositories, occurrence/schedule rules, quantity semantics, maintenance/warranty relationships, search, attachment references, the Habitat Discovery policy adapter and tests. Domain logic stays independent of Compose and Android APIs; document/photo/camera/notification integrations belong in platform adapters.

### 12.3 Avoid

- Do not repurpose PLAN's task table as the canonical chore database.
- Do not represent inventory quantities as PLAN tasks or MINT transactions.
- Do not create a BASE-specific reminder scheduler.
- Do not create a parallel BASE Pokémon collection or reward ledger.

### 12.4 Migration

Shared database ownership, transaction boundaries, backup/restore, schema migration and recovery follow `../ARCHITECTURE/DATA_AND_MIGRATIONS.md`. BASE owns household records and must not create a separate persistence or reward authority.

Cross-module links, shared scheduler metadata and shared reward events use stable IDs and versioned contracts. Migration never replays historical BASE activity as reward events. If household data is ever found in a legacy generic notes/task area, define an explicit, opt-in mapping before import; never silently reinterpret unrelated records as BASE data.

## 13. Remaining work

Section 16 lists what exists. Still to do, in order:

- **Phase 2 rest:** attachments and document/photo handling, explicit PLAN shopping task for a supply, LINK service-provider reference, backup/export/restore validation of attachments. **Exit gate:** household records are searchable, linked appropriately, exportable and recoverable without exposing private details across modules.
- **Phase 3 (refinement):** barcode-assisted inventory entry if justified and technically safe, import/export quality, attachment previews and quick actions, additional BASE regional discovery content, widgets and shortcuts only after core flows are stable. **Exit gate:** refinements do not increase maintenance burden, create permission creep or compromise offline-first behavior.

Do not start feature expansion while architecture, local data lifecycle, reminder contracts or shared Pokémon/economy foundations are failing.

## 14. Acceptance criteria

> Boxes checked against the code in 0.236.0 (23 November 2026): a tick means built and found in the code; an unticked box says what is missing.

### Chores

- [x] Create a chore with title only.
- [x] Assign optional area/category/instructions/duration.
- [x] Support one-time and deterministic recurring schedules.
- [x] Generate occurrences idempotently across restart and time-zone changes.
- [x] Complete, undo, skip and defer an occurrence.
- [x] Preserve completed history when recurrence or metadata is edited.
- [x] Pause, resume and archive without deleting history.
- [x] Avoid unlimited missed-occurrence backlogs and punitive streaks.
- [x] A linked PLAN action invokes BASE completion and cannot double-pay.

### Inventory

- [x] Add consumable and durable items.
- [x] Support numeric or unknown quantity and configurable units.
- [x] Increase, decrease and set quantity directly.
- [x] Prevent accidental negative stock.
- [x] Configure optional low-stock thresholds.
- [x] Unknown quantity is not treated as zero/low stock.
- [x] Restocking list supports low-stock-derived and manual entries.
- [x] Administrative inventory edits do not generate progression rewards or MINT transactions.

### Assets and maintenance

- [x] Register and edit assets with optional area, brand/model and serial number.
- [x] Associate maintenance records/schedules and warranty records.
- [x] Support one-time and recurring maintenance.
- [x] Preserve completed maintenance history after schedule edits.
- [x] Correctly derive upcoming/overdue state.
- [x] Asset may exist without maintenance schedule.
- [ ] Optional attachments survive backup/restore and are deleted according to explicit policy. — not built: attachments are not built (REP-041)
- [x] Optional costs remain informational and do not become MINT transactions.

### Reference and search

- [x] Create household-specific reference records with title-only quick capture.
- [ ] Link optional areas/assets and attachments. — partial: areas and assets link; attachments are not built (REP-041)
- [x] Search all BASE record types locally and offline.
- [x] Search results identify record type and open canonical record.
- [x] BASE remains a household reference tool, not a general wiki.

### Reminders and integrations

- [x] BASE uses only `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture` shared scheduling/delivery.
- [x] Chore, maintenance, low-stock and warranty reminders are individually optional.
- [x] Source edits/completion/archive/delete reconcile or cancel reminders.
- [x] Stale notification actions cannot mutate missing/ineligible records.
- [x] Permission denial does not impair BASE; permissions are requested contextually.
- [x] No location permission is required.

### Pokémon progression and privacy

- [x] Habitat Discovery is the only BASE acquisition identity.
- [x] Eligible chore/maintenance completion events use versioned BASE policy.
- [x] Creation, viewing, inventory edits, quantity changes, warranty/reference edits and reminder delivery do not grant rewards.
- [x] Repeated toggling, duplicate PLAN projections and migration cannot double-pay.
- [x] BASE rewards use shared collection, Bag, currency and reward ledger.
- [x] Pokémon acquisition appears in the global collection as an individual owned instance.
- [x] Household names, areas, inventory, assets, serial numbers, costs, notes and attachments never enter WORLD/reward payloads.
- [x] Rewards can be disabled without disabling BASE.
- [x] No missed-day penalty, decay, attendance quota or expiring discovery.

### General quality

- [x] Core BASE workflows are fully offline.
- [x] Data survives process death, app restart and device reboot.
- [x] Backup/restore and schema migration preserve IDs and relationships.
- [ ] Attachments are safely stored, restored and deleted. — not built: attachments are not built (REP-041)
- [x] Local search, empty/error states and low-storage failures are handled.
- [ ] Light/dark themes, enlarged text, TalkBack and reduced motion are usable. — partial: themes and reduced motion ship; the Secret Base room cells are not labelled for screen readers and no TalkBack or font-scale pass is recorded (REP-048)
- [ ] Real-device UI review passes on target Android hardware and smaller supported widths. — not verified: no phone or smaller-width pass is recorded (REP-033)
- [x] No duplicated data ownership, scheduler, currency, Bag, collection or reward ledger.

## 15. Cross-reference authority

- Product-wide module map, privacy/local-first direction and regional acquisition principles: `../PRODUCT_BIBLE.md`.
- Shared PMD visual language and reusable components: `../FEATURES/DESIGN_SYSTEM.md`.
- Shared scheduling, quiet hours, focus deferral, notification delivery, recovery and reminder privacy: `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`.
- Shared Pokémon collection, regional dex, reward ledger, Bag, currency and acquisition balance: shared Pokémon/economy specification.
- Data lifecycle, schema migrations, backup/restore and encryption: shared architecture specifications.
- PLAN task ownership and explicit linked-task behavior: `PLAN.md`.
- MINT transaction ownership: `MINT.md`.
- LINK contact ownership: `LINK.md`.

This module document owns BASE-specific rules only. Shared feature documents remain authoritative for shared behavior.

## 16. As built: the manual slice

*Compiled for Android; not yet run on a device. No attachment, camera or import exists.*

Sections 3 and 4 are built, with the differences below and the open items under "Not built" and in REP-061, REP-041 and REP-029 (Overview, Chores, Supplies, Belongings, Maintenance, Warranties, Notes, Settings, search). What differs from, or is decided beyond, the spec:
- **Search** leaves out what no list shows (archived chores, supplies and items, finished one-time schedules, warranties of an archived item).
- **Chores:** a repeating chore shows only its **latest** occurrence up to today, so a month away leaves one overdue chore, never a backlog; an occurrence done early counts; editing a chore never touches its entries, which are kept for a bounded time (`BaseRules.ENTRY_KEEP_DAYS`). The recurrence scope "this and future occurrences" is not offered: only the schedule definition changes, from a first date, and past entries stay.
- **Supplies:** quantity is exact (thousandths of a unit, no floating point); a supply on the restocking list because it is low leaves it only with a new quantity; the adjustment history is bounded (`BaseRules.ADJUSTMENTS_KEPT`).
- **Belongings:** durable things are assets; one identity serves inventory and maintenance.
- **Maintenance:** a schedule runs on the calendar or counted from when it was done; its start date moves only when its next date or kind of repeat is edited, never on a rename; a cost is plain text, never a transaction; undoing a tick removes the record and returns the schedule to its date (same event id, nothing pays twice).

**Menus, stock and shopping (0.237.0).**
- **Sections.** Six destinations: **Chores**, **Belongings** (tabs **Stock**, **Shopping**, **Items**), **Maintenance**, **Notes**, **Room** and **Settings**. Belongings is its own destination; durable items are no longer a tab under Maintenance. The three "hide a section" switches are gone (they could only fold away a list that already shows only what needs attention), and every overview row opens the section it describes. The overview's quick actions capture a chore, a supply, a shopping line or a note from one title (`BaseQuickActions`), instead of keys that only re-open the sections.
- **Stock changes have a screen and a history.** A supply's **Adjust stock** form sets, adds or removes an exact quantity with an optional reason (Counted, Used, Bought, Correction); each change is one `StockAdjustment` and the supply shows the last five, newest first (`BaseStore.recentAdjustments`, `BaseRules.ADJUSTMENT_HISTORY`); negative stock is refused.
- **Shopping, end to end.** A hand-written restocking line can point at a supply (`BaseStore.addRestock(name, supplyId)`); the list is grouped by household area with "Other" last (`BaseRules.restockGroups`); marking a line **Bought** updates that supply's stock through the same stock adjustment (one adjustment; Undo restores the stock and the line), and a line whose supply has an unknown quantity asks for the quantity bought. One thing to buy is listed once even when a low supply and a linked hand-written line are the same (`BaseRules.restockList`). None of it pays a reward or creates a MINT transaction.

**Habitat Discovery (section 9)** *(amounts, caps, meter sizes and perks are *(tune)* values owned by `docs/economy.md`, the "Base: ..." rows)*:
- **On by default**, off in Settings without touching a record. Only a chore done and maintenance done pay; everything in 9.2 marked No never pays.
- **Idempotent by occurrence.** The event id names the occurrence (`home:chore:<chore id>:<due day>`, `home:maintenance:<schedule id>:<due date>`) and the game remembers it (`AppData.basePaid`, saved as `homePaid`), so undo then done again, a restore or a replay pays once. Nothing is clawed back by an undo. A migration never pays.
- **What leaves BASE** is exactly `BaseEvent`: id, module ("home"), an opaque `ref`, the broad kind (`chore_completed`, `maintenance_completed`) and the time.
- A Habitat Egg hatches into Base's party from the **Base Region** (`tools/species/regions/base.txt`). Base's party perks change only what Habitat Discovery pays. A new Pokémon settles into the habitat from above (`Acquisition.HABITAT`). First-run tutorial: `../FEATURES/POKEMON_ECOSYSTEM.md` 10.2.1.

**Secret Base room (section 9.5).** A **Room** card in Base's menu, drawn flush in the dialogue frame at a whole-number scale (5x on a phone, 4x narrower, 1x at least). **Arrange** shows a dot on every empty cell of the 44 and a box of the owned decorations not placed; each step has an undo. Rules and tests are in `base-core` (`Room.kt`, `RoomTest`), the store side in `BaseStore` and `GameStore` (`grantDecoration`, `ownedDecorations`); the meter is `Counters.HOME_DECOR_METER`. A decoration paid shows as a line in the payment's reward text. *Not built:* choosing a cell without touching the picture for a screen reader (the box and the buttons are labelled; the cells are not), the Department Store and Mom's gifts (REP-053 chunk 6, which add through `GameStore.grantDecoration`).

**Data (section 5).** `home_*` tables: `home_area`, `home_chore`, `home_entry`, `home_supply`, `home_adjustment`, `home_restock`, `home_asset`, `home_schedule`, `home_maintenance`, `home_warranty`, `home_reference`, `home_room` (the room's placements) and `home_state` (settings), each row `key, position, day, json`; `BaseTables` reads, exports or erases one family alone. Home is in the daily backup and the backup bundle. Not sealed: household content follows the shared data-security policy.

**Linked PLAN tasks (2.1).** Chore → "Show in Plan" makes one PLAN task per chore, a projection of its current occurrence (`TaskLink(HOME_CHORE, chore id, occurrence)`): the title and day follow the chore, a repeating chore's task moves on to the next occurrence, and a deleted chore takes its projection with it (restorable). Completing, reopening or "won't do" in PLAN run `BaseStore.complete`, `undo` and `skip` (`BaseLinkOwner`), so BASE records the one completion and pays once; PLAN pays nothing for a task another module owns, and a linked task cannot repeat by itself. "Remove from Plan" deletes only the projection. A store that does not know Home (a widget) leaves a linked task untouched. There is no "convert to a PLAN-owned task" action.

**Reminders (6).** Base → Settings → Reminders is **off**; "Remind me" is always offered on a chore and the first one turns the setting on, while a maintenance schedule, a supply (with a threshold) and a warranty (with an end date) offer theirs once it is on. One reminder each, never repeated: a chore on the day of its next unsettled occurrence (nothing for an overdue one, none for a once-only chore that is done), maintenance a chosen number of days before its next date, a warranty before its end date, a low supply once. The request holds the record's opaque id, the kind and the day; the lock screen says only the kind, and the name shows after unlocking only if the player allowed it. A chore's notification offers "Done" (the chore's own completion), every kind offers "Tomorrow"; a reminder never records maintenance, restocks or edits a warranty, and delivering, snoozing or dismissing pays nothing.

Open questions are REP-029 in `../IMPLEMENTATION_BACKLOG.md`.

**Not built, not offered:** attachments, photos and the camera (7.1), a PLAN shopping task for a supply (4.3), LINK service-provider references, widgets and shortcuts, barcode capture, imports, the "upkeep milestone" event, recurrence scopes other than the schedule definition, and a durable item that is not also an asset.

## Shared Pokémon ecosystem contract

BASE's acquisition identity is **Habitat Discovery**. BASE owns eligibility for qualifying chore completions, maintenance and household-upkeep milestones. If a BASE chore is linked to a PLAN task, BASE owns the canonical completion event and reward; it must not pay twice. The shared Pokémon ecosystem owns canonical reward settlement, Eggs/owned instances, Pokédex and inventory. BASE has an independent party referencing shared owned Pokémon IDs. Household names, inventory contents, notes and property identifiers are excluded from reward events. See `../FEATURES/POKEMON_ECOSYSTEM.md`.

---

## Testing reference

BASE integration tests must verify that BASE references and initiates supported workflows without taking ownership of PLAN tasks or generating duplicate completion/reward events. See [`../ARCHITECTURE/TEST_STRATEGY.md`](../ARCHITECTURE/TEST_STRATEGY.md).
