# PokéLife — HOME Module Specification

**Version:** 1.0  
**Status:** Approved product specification  
**Module:** HOME  
**Scope:** Personal household management  
**Baseline audit:** PokéLife source `0.123.0`, as documented in `IMPLEMENTATION_GUIDE.md`  
**Last updated:** 5 November 2026

> This document is the single source of truth for HOME-specific product behavior, data ownership, workflows, progression, privacy adaptations, implementation direction and acceptance criteria. Product-wide rules remain in the Product Bible; shared reminder behavior, visual components, data lifecycle and Pokémon/economy services remain in their respective shared specifications. Do not duplicate those systems here.

## 1. Purpose and boundaries

HOME is PokéLife's practical household operating hub. It reduces the mental effort of remembering what needs cleaning, replacing, repairing, checking or maintaining, and provides one local place for useful household records.

HOME should answer five questions:

1. What household responsibilities need attention?
2. What supplies do I have, and what is running low?
3. What needs maintenance, and when?
4. Where is important household information stored?
5. What household work have I already completed?

HOME owns household records and their business rules. It is not a general task manager, finance tracker, note-taking application, property-management suite or smart-home controller.

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
- HOME's own Pokémon acquisition identity: **Habitat Discovery**.

### 1.2 Explicit non-goals

Initial HOME does not include:

- Multiple properties or a property portfolio.
- Landlord/tenant portals, rent collection or property accounting.
- Household collaboration, shared accounts or multi-user permissions.
- Smart-home automation, device control or IoT dashboards.
- Automatic monitoring of the physical home.
- Full shopping, procurement or price-comparison workflows.
- Financial accounting, payments or transaction ownership.
- General project management or a second task manager.
- A general-purpose notes/wiki application.
- Barcode-driven automation as a release dependency.

The initial scope is one personal household and one local user. Areas are optional; a record can be created without assigning a room.

## 2. Ownership and module boundaries

| Capability / record | Canonical owner | HOME relationship |
|---|---|---|
| Household chores and chore schedules | HOME | Owns definitions, occurrences and completion history |
| Household maintenance and warranties | HOME | Owns assets, schedules and service history |
| Household supplies and inventory | HOME | Owns item identity, quantities and low-stock state |
| Household reference notes | HOME | Owns household-specific content and attachments |
| General tasks, projects and focus | PLAN | Optional linked task; never a duplicate chore |
| Shared reminder scheduling/delivery | `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture` | HOME supplies source-aware requests |
| Purchases, bills and transactions | MINT | HOME may link only through explicit references; no financial copy |
| Contact records and relationship context | LINK | Optional service-provider reference through LINK ownership |
| Generic calculators/converters | GEAR | May be reused; HOME owns household meaning and labels |
| Pokémon, Bag, currency and reward ledger | Shared Pokémon/economy services | HOME emits eligible activity events only |
| Health, reflection and relationship-sensitive records | HEAL / CARE / LINK | No access unless an explicit, approved link is introduced |

### 2.1 HOME versus PLAN

A recurring household chore is a HOME record, not a PLAN recurring task. HOME owns the canonical chore definition, schedule, occurrences and completion history.

A user may explicitly create a linked PLAN task when they want the responsibility visible in PLAN. PLAN displays a linked representation referencing the HOME record. Completing it from PLAN calls HOME's completion operation. It must not create an independent PLAN completion or a second reward.

The link is optional and one-way in ownership: HOME remains canonical. Unlinking removes the projection/reference, not the HOME chore. If the user explicitly converts a responsibility into a PLAN-owned task, the UI must explain that it is a new PLAN record with PLAN rules; do not silently transfer or duplicate ownership.

### 2.2 HOME versus MINT

HOME tracks what is owned and its household state. MINT owns financial facts. An optional link to a MINT transaction may be provided only through a user-selected stable reference. HOME must not copy transaction values, account data or payment credentials into its own records. A purchase/restock action does not automatically create a MINT transaction.

## 3. Information architecture

Use a compact navigation structure with five primary destinations, plus a focused overview:

1. **Overview** — actionable household status and quick capture.
2. **Chores** — household responsibilities, schedules and history.
3. **Inventory** — supplies, durable items, quantities and restocking.
4. **Maintenance** — assets, maintenance records, schedules and warranties.
5. **Reference** — searchable household notes and practical information.

Search is available from the HOME shell and covers all HOME-owned record types. Settings remain a secondary destination, not a primary tab.

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

`HomeArea` is an optional organizational label, not a required hierarchy. Suggested initial examples: Bedroom, Kitchen, Bathroom, Living area, Balcony, Storage and General household.

Users can create, rename, reorder and archive areas. Archiving an area must not delete its records; records become unassigned or retain a clearly marked archived-area reference according to the data model. Avoid enforcing a room tree or floor plan. Records can exist without an area.

### 4.2 Chores

A chore is a household responsibility with an optional schedule. The user should be able to create one with only a title.

**Chore definition fields**

| Field | Requirement / behavior |
|---|---|
| Title | Required |
| Description / instructions | Optional |
| Area | Optional `HomeArea` reference |
| Category | Optional; e.g. cleaning, laundry, kitchen, general, outdoor |
| Schedule | One-time or recurring; optional for unscheduled chores |
| Frequency | Supported recurrence described below |
| Estimated duration | Optional informational value |
| Reminder preference | Optional HOME-owned preference using shared reminders |
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

Provide a lightweight list derived from low-stock items plus manually added entries. Users can mark an item purchased/restocked, adjust its quantity, remove it from the list or add a manual item. A restocking list is not a MINT transaction or a PLAN task. If the user explicitly wants a PLAN shopping task, create a linked PLAN task by user action; HOME remains the source of the supply record.

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

One HOME search covers chores, inventory, assets, maintenance, warranties and reference records. Results identify their record type and open the canonical record. Search is local and available offline.

Useful filters: area, category, active/paused/archived, low stock, due/overdue maintenance and record type. Keep filter controls compact.

HOME settings are limited to defaults such as quantity units, optional default categories, low-stock presentation, chore preferences and maintenance preferences. Global theme, accessibility, notification permission, quiet hours and data lifecycle remain shared settings/services.

## 5. Data model and persistence

The following are logical domain concepts. They do not require one physical database table per concept; follow the approved architecture and normalization strategy.

| Entity | Responsibility |
|---|---|
| `HomeArea` | Optional household zone/room |
| `Chore` | Canonical chore definition and schedule |
| `ChoreOccurrence` | Generated one-time/recurring occurrence and state |
| `ChoreCompletion` | Immutable completion fact/event, linked to occurrence |
| `InventoryItem` | Consumable or durable household item |
| `StockAdjustment` | Optional quantity change history |
| `MaintenanceAsset` | Appliance, equipment or other maintained asset |
| `MaintenanceRecord` | Planned or completed maintenance activity |
| `MaintenanceSchedule` | Recurrence definition for future maintenance |
| `WarrantyRecord` | Warranty dates and reference/attachment metadata |
| `HomeReference` | Household note or reference record |
| `HomeSettings` | HOME-specific preferences |
| `HomeResearchState` | HOME-owned Habitat Discovery progress/configuration state |
| `HomeRewardEvent` | Minimal idempotent eligibility event and shared reward payment reference |

Relationships:

- `HomeArea` may organize chores, inventory items, assets and references.
- `Chore` generates `ChoreOccurrence` records; an occurrence may have one completion event.
- `InventoryItem` may have zero or more stock adjustments.
- `MaintenanceAsset` may have maintenance records, schedules and warranties.
- `HomeReference` may optionally link to an area or asset.
- A linked PLAN task references a HOME record using a stable opaque ID; it is a projection/association, not a duplicate canonical record.
- Reminder requests reference HOME source IDs opaquely; shared reminder storage is not a HOME database.

### 5.1 Data rules

- Stable IDs for all records and events.
- Explicit relationships and referential integrity.
- Created/updated timestamps; event timestamps for completions and maintenance.
- Local database is authoritative.
- Soft deletion/archive where history or linked records should remain recoverable.
- Explicit deletion behavior for attachments and linked records.
- Versioned migrations and deterministic occurrence generation.
- Backup/restore and export/import through the shared data lifecycle; no HOME-specific save file.
- Do not persist derived statuses (overdue, low-stock) when they can be computed reliably from canonical data. If cached, reconcile on load.
- Do not store sensitive household detail in analytics, logs, reward events or shared scheduler records.

### 5.2 Backup, export and deletion

HOME records and attachments participate in shared local backup/restore and data export. Preserve IDs and relationships on restore. Exports should be usable and document supported formats; avoid embedding private attachment contents in logs or diagnostics. Users can export selected HOME categories or all HOME data and delete individual records or all HOME data without deleting Pokémon, Bag contents, PLAN records, MINT transactions or other modules' data. Backup encryption and device-level protection follow shared architecture/security specifications.

## 6. Reminders and notifications

Shared scheduling, notification delivery, global quiet hours, focus deferral, permissions, recovery, notification identity, action routing and scheduler privacy are defined exclusively in `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`.

HOME owns only:

- Whether a reminder is enabled for a specific HOME record.
- HOME-specific default reminder choices, if any.
- The source record's meaning and canonical state.
- Validation and execution of HOME-specific notification actions.
- Creation/update/cancellation requests through the shared reminder adapter.

Potential HOME reminder sources:

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
- Notification actions route to HOME's adapter and validate current state.
- Reminder delivery, snooze or dismissal is not a reward-bearing activity.
- Do not copy chore titles, inventory names, asset serial numbers, maintenance notes or reference content into shared scheduler storage. Resolve concise notification text as late as practical and follow lock-screen privacy policy.

HOME does not implement its own scheduler, notification channels, quiet-hours engine, focus queue, Android delivery or recovery logic.

## 7. Integrations and permissions

HOME is manual-first and does not require network access or external accounts.

### 7.1 Android capabilities

- Use Android document/photo pickers for user-selected attachments.
- Optional camera capture for asset documentation, requested contextually.
- Android share/import intents may accept user-selected household reference material where safe and supported.
- Local notification delivery uses the shared reminder feature.
- No location permission is required.

Permission denial must not impair core HOME functionality. Do not request camera, photo or file permissions at startup. Explain each permission at the point of use and support supported system pickers without broad storage access.

### 7.2 Optional future integrations

Barcode-assisted inventory capture and user-selected external document sources may be considered after core workflows. They are not release dependencies. Any future connector must use documented, user-authorized interfaces, explicit scope and clear disconnect/deletion behavior. No smart-home connection or background household surveillance is in initial scope.

## 8. Privacy and security

Household information can reveal routines, possessions, property details, room layouts, location context, serial numbers and private documents. Treat all HOME content as private personal information.

- Store HOME records locally under the product-wide local-first/no-mandatory-account direction.
- No PokéLife cloud sync, advertising, profiling or telemetry containing HOME content.
- Do not transmit addresses, room names, serial numbers, inventory, chore descriptions, maintenance notes, warranty documents, photos or reference text to WORLD, launcher or reward services.
- Do not use household content to choose Pokémon species, rarity or reward size.
- No location permission or automatic location inference.
- Protect local storage and backups according to shared data-security policy.
- Do not log raw attachment contents or private household text.
- Support per-record edit/archive/delete and clear attachment deletion behavior.
- Lock-screen notification content follows shared privacy controls.
- External provider transmission, if ever added, requires explicit user action and scope disclosure.

The progression event contract in Section 9 is the only permitted HOME-to-shared-reward boundary. It contains no human-readable household content.

## 9. Pokémon progression — Habitat Discovery

**Habitat Discovery is HOME's approved Pokémon acquisition identity.** It is distinct from HEAL's Field Research and from PLAN's planning/focus rewards. HOME frames eligible household upkeep as caring for a habitat: meaningful activity contributes to discoveries in HOME's regional pool.

Habitat Discovery is an acquisition/progression presentation, not a reason to create chores, increase household workload or expose private records. Core HOME remains fully useful with rewards disabled or unavailable.

### 9.1 Core loop

1. The user completes a qualifying household activity.
2. HOME evaluates the event under a versioned HOME eligibility policy.
3. HOME records one minimal, idempotent progression event.
4. Accumulated Habitat Discovery progress makes a discovery/encounter opportunity available.
5. Recruitment resolves through the shared Pokémon acquisition and collection services.

HOME Research progress is HOME-owned progression state, not a separate currency. The shared Pokémon/economy services own reward delivery, Bag/currency, Pokémon instance registration and the global collection. HOME creates no separate collection, Bag, Poké balance, egg implementation or reward ledger.

### 9.2 Qualifying activities

| Activity | Eligible | Notes |
|---|---|---|
| Complete a chore occurrence | Yes | One event per valid completion; undo/re-completion follows idempotent reversal policy |
| Complete a meaningful maintenance activity | Yes | Only when marked completed as real work, not merely created or scheduled |
| Complete an explicitly defined household upkeep milestone | Yes, if approved in the versioned policy | Must represent meaningful completed work and resist trivial splitting |
| Create/edit/archive a chore | No | Administrative operation |
| Open HOME or view a record | No | Passive use |
| Add/edit inventory or change quantity | No | Administrative bookkeeping; stock adjustment is not proof of household upkeep |
| Add a maintenance schedule or warranty | No | Administrative operation |
| View or edit reference notes | No | Administrative operation |
| Add a purchase/restocking list entry | No | Does not prove a purchase or household task completion |

Do not award for repeated toggling, rapid undo/re-complete loops, duplicate linked PLAN actions or migration replay. If one activity is visible from PLAN and HOME, HOME's canonical completion event is paid once. A PLAN task that is merely linked to a HOME chore must not independently pay a PLAN reward for the same real-world completion.

### 9.3 Reward behavior and balance

- Use the shared economy's generous progression principles and idempotent ledger.
- Provide frequent, predictable positive progression during ordinary household upkeep.
- Meaningful milestones may provide additional progress/reward only under the versioned HOME policy.
- Rare Pokémon remain discoverable without excessive grinding.
- Duplicates remain separate owned Pokémon instances.
- Rare Candy, Super Candy and other shared items follow the shared economy specification.
- No HOME-only currency, Bag, Pokémon ownership or parallel reward ledger.
- No attendance streak, daily quota, loss of accumulated progress, missed-day penalty or expiring discovery.
- The user can disable Habitat Discovery rewards without disabling HOME functionality.
- Reward thresholds, caps, rarity weights and quantities are calibrated centrally with other modules in shared economy simulations; do not hard-code an independently generous or stingy HOME economy.
- Historical import/migration never pays retroactive HOME rewards.

The regional acquisition pool should primarily use species appropriate to HOME's approved habitat/region identity. Maintain meaningful type and rarity representation. Exact roster composition, regional dex membership and encounter weights belong to the shared Pokémon ecosystem specification and the HOME content configuration, not household records.

### 9.4 Privacy-safe event contract

The shared reward boundary may receive only minimum metadata such as:

- Stable opaque event ID.
- Source module identifier (`HOME`).
- Opaque source-record reference.
- Broad eligible event category.
- Event timestamp.
- Eligibility-policy version.

Do not send chore titles, room/area names, inventory names or quantities, asset identities, serial numbers, purchase costs, maintenance notes, warranty data, reference text, filenames or attachment contents to WORLD or the reward presentation layer. Prefer evaluation inside HOME/reward boundary and expose only a generic reward result to WORLD. Reward events must be idempotent; edits, deletions, undo/redo and migration must not replay paid events.

## 10. Offline behavior and lifecycle

Core HOME is fully functional offline.

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

HOME follows `../FEATURES/DESIGN_SYSTEM.md` and its **compact utility presentation**. The shared design system owns PMD dialogue-box geometry, borders, typography, tokens, component anatomy, states, accessibility and interaction behavior. This section defines only HOME-specific composition.

- Overview uses a small number of adaptive PMD dialogue-box panels: household status, due chores, low-stock summary and upcoming maintenance.
- Do not place every chore, inventory item or paragraph in a separate dialogue box. Use efficient list rows, timelines and forms with selective framing.
- Chore rows prioritize completion control and title, then relevant schedule/area metadata.
- Inventory rows show name, quantity/unit and low-stock state clearly; do not rely on color alone.
- Maintenance asset detail prioritizes identification, warranty state and next maintenance.
- Reference uses readable native-feeling text and document layouts, not game dialogue as the reading surface.
- Pokémon portraits/sprites may appear in Habitat Discovery, discovery results and restrained empty-state feedback. Do not decorate every record or obstruct the primary real-world action.
- Pokémon feedback must be optional or brief, must not delay completion, and must not require navigating dialogue to perform a household action.
- Use shared light/dark tokens, scalable system typography, TalkBack semantics, minimum 48 dp targets, reduced-motion behavior and responsive layouts.
- Keep pixel art crisp and subordinate to utility content. Do not invent a separate HOME design language or independent component variants where shared components suffice.

## 12. Existing implementation audit and reuse policy

The Implementation Guide's audit of source version `0.123.0` identifies HOME as absent as a first-class life module. The inspected architecture has useful foundations, but the current data/store boundaries are not the target modular local-first architecture. No HOME end-to-end workflow is established by the baseline audit.

### 12.1 Reuse after explicit review

- Kotlin Multiplatform and Compose foundations.
- Android application shell, navigation and module registry where compatible with the target architecture.
- Shared local data layer, repository conventions, migrations, backup/restore and attachment management once finalized.
- Shared Pokémon collection, regional dex, reward ledger, Bag, currency and acquisition interfaces.
- Shared reminder scheduler and delivery through a HOME source adapter.
- Shared PMD design-system components.
- Generic GEAR calculators/converters where useful.

### 12.2 Build as HOME-owned domain behavior

Create HOME models, repositories, occurrence/schedule rules, quantity semantics, maintenance/warranty relationships, search, local attachment references, Habitat Discovery policy adapter and tests. Keep domain logic independent of Compose and Android APIs. Android document/photo/camera/notification integrations belong in platform adapters.

### 12.3 Improve or replace rather than extend blindly

- Define clean domain ownership before adding HOME to legacy global save structures.
- Do not repurpose PLAN's task table as the canonical chore database.
- Do not represent inventory quantities as PLAN tasks or MINT transactions.
- Do not create a HOME-specific reminder scheduler.
- Do not create a parallel HOME Pokémon collection or reward ledger.
- Do not assume code presence proves end-to-end readiness; verify tests, persistence, migration, lifecycle and device UI before reuse.
- Preserve user data and proven business rules, not structurally misaligned legacy implementation details.

### 12.4 Migration

Shared database ownership, transaction boundaries, backup/restore, schema migration and recovery follow `../ARCHITECTURE/DATA_AND_MIGRATIONS.md`. HOME owns household records and must not create a separate persistence or reward authority.

HOME is new in the audited baseline, so there is no legacy HOME dataset to migrate from `0.123.0`. However, cross-module links, shared scheduler metadata and shared reward events must use stable IDs and versioned contracts. Migration must never replay historical HOME activity as reward events. If later source inventory finds household data stored in a legacy generic notes/task area, inventory it and define an explicit, opt-in mapping before import; do not silently reinterpret unrelated records as HOME data.

## 13. Implementation phases and exit gates

HOME follows the dependency-first roadmap in `IMPLEMENTATION_GUIDE.md`. Do not start feature expansion while architecture, local data lifecycle, reminder contracts or shared Pokémon/economy foundations are failing their exit gates.

### Phase 0 — Domain and data foundation

- Establish HOME domain boundary and repository interfaces.
- Define stable IDs, relationships and schema/migration strategy.
- Define recurrence and occurrence-generation semantics.
- Define quantity/unit semantics and low-stock derivation.
- Define asset, maintenance, schedule and warranty relationships.
- Define attachment storage/deletion behavior.
- Define optional PLAN linking and source-aware reminder contracts.
- Define privacy-safe Habitat Discovery event schema and idempotency.

**Exit gate:** no duplicated canonical records, no competing sources of truth, no household content in shared reminder/reward payloads, and all core domain rules have unit tests.

### Phase 1 — Core household experience

- HOME navigation and Overview.
- Quick-add chore, chore schedule and occurrence handling.
- Completion, undo, skip/defer, pause/archive and history.
- Optional household areas.
- Consumable inventory, units and quantity adjustment.
- Low-stock state and simple restocking list.
- Basic asset registration and maintenance records/schedules.
- Optional HOME reminders through the shared feature.
- Habitat Discovery event integration through shared Pokémon/economy services.
- PMD utility presentation and accessibility.

**Exit gate:** user can manage ordinary chores, household supplies and essential maintenance fully offline; no duplicate rewards or scheduler implementation.

### Phase 2 — Household records depth

- Durable inventory and asset relationships.
- Warranty management and expiry reminders.
- Household reference library and local search.
- Attachments and document/photo handling.
- Cross-HOME search and filters.
- Richer maintenance schedules/history.
- Explicit optional PLAN task links.
- Backup/export/restore validation for HOME records and attachments.

**Exit gate:** household records are searchable, linked appropriately, exportable and recoverable without exposing private details across modules.

### Phase 3 — Refinement

- Barcode-assisted inventory entry if justified and technically safe.
- Optional import/export quality improvements.
- Attachment previews and quick actions.
- Additional HOME regional discovery content.
- Optional widgets/shortcuts only after core flows are stable.
- Further accessibility, performance and interaction refinements.

**Exit gate:** refinements do not increase maintenance burden, create permission creep or compromise offline-first behavior.

## 14. Acceptance criteria

### Chores

- [ ] Create a chore with title only.
- [ ] Assign optional area/category/instructions/duration.
- [ ] Support one-time and deterministic recurring schedules.
- [ ] Generate occurrences idempotently across restart and time-zone changes.
- [ ] Complete, undo, skip and defer an occurrence.
- [ ] Preserve completed history when recurrence or metadata is edited.
- [ ] Pause, resume and archive without deleting history.
- [ ] Avoid unlimited missed-occurrence backlogs and punitive streaks.
- [ ] A linked PLAN action invokes HOME completion and cannot double-pay.

### Inventory

- [ ] Add consumable and durable items.
- [ ] Support numeric or unknown quantity and configurable units.
- [ ] Increase, decrease and set quantity directly.
- [ ] Prevent accidental negative stock.
- [ ] Configure optional low-stock thresholds.
- [ ] Unknown quantity is not treated as zero/low stock.
- [ ] Restocking list supports low-stock-derived and manual entries.
- [ ] Administrative inventory edits do not generate progression rewards or MINT transactions.

### Assets and maintenance

- [ ] Register and edit assets with optional area, brand/model and serial number.
- [ ] Associate maintenance records/schedules and warranty records.
- [ ] Support one-time and recurring maintenance.
- [ ] Preserve completed maintenance history after schedule edits.
- [ ] Correctly derive upcoming/overdue state.
- [ ] Asset may exist without maintenance schedule.
- [ ] Optional attachments survive backup/restore and are deleted according to explicit policy.
- [ ] Optional costs remain informational and do not become MINT transactions.

### Reference and search

- [ ] Create household-specific reference records with title-only quick capture.
- [ ] Link optional areas/assets and attachments.
- [ ] Search all HOME record types locally and offline.
- [ ] Search results identify record type and open canonical record.
- [ ] HOME remains a household reference tool, not a general wiki.

### Reminders and integrations

- [ ] HOME uses only `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture` shared scheduling/delivery.
- [ ] Chore, maintenance, low-stock and warranty reminders are individually optional.
- [ ] Source edits/completion/archive/delete reconcile or cancel reminders.
- [ ] Stale notification actions cannot mutate missing/ineligible records.
- [ ] Permission denial does not impair HOME; permissions are requested contextually.
- [ ] No location permission is required.

### Pokémon progression and privacy

- [ ] Habitat Discovery is the only HOME acquisition identity.
- [ ] Eligible chore/maintenance completion events use versioned HOME policy.
- [ ] Creation, viewing, inventory edits, quantity changes, warranty/reference edits and reminder delivery do not grant rewards.
- [ ] Repeated toggling, duplicate PLAN projections and migration cannot double-pay.
- [ ] HOME rewards use shared collection, Bag, currency and reward ledger.
- [ ] Pokémon acquisition appears in the global collection as an individual owned instance.
- [ ] Household names, areas, inventory, assets, serial numbers, costs, notes and attachments never enter WORLD/reward payloads.
- [ ] Rewards can be disabled without disabling HOME.
- [ ] No missed-day penalty, decay, attendance quota or expiring discovery.

### General quality

- [ ] Core HOME workflows are fully offline.
- [ ] Data survives process death, app restart and device reboot.
- [ ] Backup/restore and schema migration preserve IDs and relationships.
- [ ] Attachments are safely stored, restored and deleted.
- [ ] Local search, empty/error states and low-storage failures are handled.
- [ ] Light/dark themes, enlarged text, TalkBack and reduced motion are usable.
- [ ] Real-device UI review passes on target Android hardware and smaller supported widths.
- [ ] No duplicated data ownership, scheduler, currency, Bag, collection or reward ledger.

## 15. Cross-reference authority

- Product-wide module map, privacy/local-first direction and regional acquisition principles: `../PRODUCT_BIBLE.md`.
- Shared PMD visual language and reusable components: `../FEATURES/DESIGN_SYSTEM.md`.
- Shared scheduling, quiet hours, focus deferral, notification delivery, recovery and reminder privacy: `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`.
- Shared Pokémon collection, regional dex, reward ledger, Bag, currency and acquisition balance: shared Pokémon/economy specification.
- Data lifecycle, schema migrations, backup/restore and encryption: shared architecture specifications.
- PLAN task ownership and explicit linked-task behavior: `PLAN.md`.
- MINT transaction ownership: MINT module specification when finalized.
- LINK contact ownership: LINK module specification when finalized.

This module document owns HOME-specific rules only. Shared feature documents remain authoritative for shared behavior.

## 16. As built (0.143.0, REP-014): the manual slice

*Built and tested on the JVM, compiled for Android; **not run on a device**. No reminder, attachment, camera, PLAN link or import was started.*

**What exists.** A menu with the Overview on top and five destinations. **Overview** (3.1): only what needs attention (chores due or overdue with a tick, maintenance due or soon, low supplies, warranties ending in 30 days), quick-add, and a search over every kind; sections can be folded away in Settings. **Chores** (4.2, 3.2): Due, Upcoming and History; title-only quick add; one time, daily, weekly (chosen weekdays), monthly and yearly, every N; complete, skip, put off, undo; pause, resume, archive; a repeating chore shows only its **latest** occurrence up to today, so a month away leaves one overdue chore, never a backlog; an occurrence done early counts; editing a chore never touches its entries (history). Not offered: "this and future occurrences" scopes (only the schedule definition changes, from a first date, and past entries stay). **Supplies** (4.3): quantity in thousandths of a unit (exact, no floating point), unknown is not zero and never low, optional threshold, +1/-1, set directly, a negative result is refused rather than clamped, bounded adjustment history (300), dismiss the low notice (cleared by the next change), and the restocking list (low supplies plus entries written by hand; not a purchase, task or transaction). **Belongings** (4.4): durable things are assets, with brand, model, serial, purchase date and area; one identity for inventory and maintenance. **Maintenance** (4.5): schedules (one time or repeating, on the calendar or counted from when it was done), done records with notes and a cost that is plain text, never a transaction, history per item, one-off work logged without a schedule. **Warranties** (4.6): typed dates and reference, ending-soon and expired states, never deleted silently. **Notes** (4.7): title, text, category, area, item. **Settings:** Habitat Discovery on or off, areas (add, rename, reorder, archive, delete), units, folded sections, export and erase by kind.

**Habitat Discovery (section 9), as built** *(numbers are *(tune)* starting values; `RewardRules.VERSION` 19, `docs/economy.md`)*:
- **On by default** (every acquisition method is), off in Settings without touching a record. Only two things pay: a **chore done** (2 Rare Candies; at most 5 paid a day) and **maintenance done** (4; at most 2 a day). Creating, editing, archiving, quantities, the restocking list, warranties, notes, schedules, skipping and putting off never pay.
- **Idempotent by occurrence.** The event id names the occurrence (`home:chore:<chore id>:<due day>`, `home:maintenance:<schedule id>:<due date>`), the game remembers it (`AppData.homePaid`, newest 1000), so undo then done again, a restore or a replay pays once. Nothing is clawed back by an undo. A migration never pays.
- **What leaves HOME** is exactly `HomeEvent`: id, module ("home"), an opaque `ref`, the broad kind (`chore_completed`, `maintenance_completed`) and the time. `HomeStoreTest` and `HomeTablesTest` check that no title, area, supply, asset, serial, cost or note reaches the event, the shared save, the reward log or the shared tables.
- A payment is candies, a little friendship for Home's lead, a trainer point and points towards a **Habitat Egg** (chore 1, maintenance 2; 5 fill the meter), hatched into Home's party from the **Home Region** (`tools/species/regions/household.txt`). Home's party perks (Candy finder: Grass, Normal, Bug, Ground, Fire, Water; Friendship gain: Fairy, Flying, Ice, Electric, Psychic, Ghost; capped at +25% a bucket) change only what Habitat Discovery pays. A new Pokémon settles into the habitat from above (`Acquisition.HABITAT`, the town scene).
- **First-run tutorial** (POKEMON_ECOSYSTEM.md 10.2.1): a 5-second tidy gives one Home Region Pokémon into Home's party, leading it, once per save.

**Data (section 5).** `home_*` tables from the start (schema 9): `home_area`, `home_chore`, `home_entry`, `home_supply`, `home_adjustment`, `home_restock`, `home_asset`, `home_schedule`, `home_maintenance`, `home_warranty`, `home_reference` (each row `key, position, day, json`) and `home_state` (settings); `HomeTables` reads, exports or erases one family alone. Home is in the daily backup and the backup bundle. Not sealed: no encryption decision exists for Home (Mint's was the owner's); household content follows the shared data-security policy and can be sealed the same way later.

**Not built, not offered:** reminders (6; the shared scheduler has a `HOME` module slot and nothing feeds it), attachments, photos and the camera (7.1), linked PLAN tasks (2.1), LINK service-provider references, widgets and shortcuts, barcode capture, imports, the "upkeep milestone" event, recurrence scopes other than the schedule definition, and a durable item that is not also an asset.

## Shared Pokémon ecosystem contract

HOME's acquisition identity is **Habitat Discovery**. HOME owns eligibility for qualifying chore completions, maintenance and household-upkeep milestones. If a HOME chore is linked to a PLAN task, HOME owns the canonical completion event and reward; it must not pay twice. The shared Pokémon ecosystem owns canonical reward settlement, Eggs/owned instances, Pokédex and inventory. HOME has an independent party referencing shared owned Pokémon IDs. Household names, inventory contents, notes and property identifiers are excluded from reward events. See `../FEATURES/POKEMON_ECOSYSTEM.md`.

---

## Testing reference

HOME integration tests must verify that HOME references and initiates supported workflows without taking ownership of PLAN tasks or generating duplicate completion/reward events. See [`../ARCHITECTURE/TEST_STRATEGY.md`](../ARCHITECTURE/TEST_STRATEGY.md).
