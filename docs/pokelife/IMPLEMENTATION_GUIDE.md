# PokéLife Implementation Guide

**Status:** Implementation status, dependency plan and release gates. Product rules live in the Product Bible and the module and feature specifications, not here.
**Status last audited:** 8 November 2026, module by module against the code (section 3.2); refresh it against a named commit whenever work lands.

> This guide separates approved specifications from implementation evidence. Nothing in it is device-verified except the owner's confirmation on the phone that the database cutover kept all data (REP-012).

## 1. Executive summary

PokéLife has a Kotlin Multiplatform/Compose foundation. All eight modules have a built slice (HEAL, MINT, HOME and LINK manual-first), the data foundation is live on Android, and the shared Pokémon and economy contracts hold in tests. Everything is tested on the JVM and compiled for Android; **nothing is device-verified**, so no module meets the Definition of Done (Bible section 9). Section 3.2 is the current status.

The main engineering risk is not a lack of specifications. It is the gap between approved modular ownership and the remaining single-`AppData` view over the split tables, plus the missing device evidence.

## 2. Evidence and status vocabulary

### 2.1 Evidence boundary

A source file or test file is evidence of code and test presence only, not that tests passed or that a workflow works. Not established anywhere: end-to-end acceptance of every workflow, live Huawei Health, bank or e-wallet integrations, a complete device review of visual quality, and the wasm tests (compile only). The JVM suite passes (`./gradlew jvmTest compileKotlinWasmJs`; about 1,200 `@Test` methods in about 120 files at 0.145.0).

### 2.2 Required status labels

| Status | Meaning | Minimum evidence |
|---|---|---|
| Specified / approved | Requirements and acceptance criteria are recorded in the authoritative specification | Approved document |
| Built | Relevant implementation exists in the inspected branch | Source inspection; scope stated |
| Tested | Relevant automated or manual tests were executed and passed | Test command/run, scope, result |
| Device-verified | End-to-end behavior was checked on named physical hardware/OS/firmware | Device, build, scenario, result |
| Partial | Some capability exists but required workflow/edge cases remain | Specific built scope and gaps |
| Planned | Approved requirement with no implementation evidence | Specification only |
| Blocked / feasibility | Depends on provider/platform feasibility not yet demonstrated | Explicit dependency and validation plan |
| Unknown | Evidence is insufficient | Must not infer completion |
| Retire after migration | Existing path conflicts with approved architecture and must be removed only after safe migration | Migration and rollback gate |

A capability may be built but untested, or tested but not device-verified. Never collapse these states into "implemented".

## 3. Baseline implementation status

The 0.123.0 baseline (before the data foundation and the HEAL, MINT, HOME and LINK modules existed) is superseded by section 3.2. Its test run (816 JVM tests, 0 failures; screenshot tests assert only that something was drawn) and the baseline inventories are recorded in the backlog rows REP-001 to REP-003.

## 3.1 Engineering operations

- `ci.yml` keeps its artifact for 3 days and cancels superseded runs; `release-apk.yml` publishes APKs through GitHub Releases (a moving `latest` plus versioned releases). Release assets do not count toward Actions storage. Gradle caching comes from `gradle/actions/setup-gradle`; there is no standalone `actions/cache` step. Inspect the live Actions storage breakdown before deleting any cache or release asset (REP-004).
- **Signing:** `androidApp/pokeplan-debug.keystore` is committed on purpose and shared by every build. It is test-grade: fine while the repository is private, and it must be replaced by a protected release key (continuity with the installed app planned) before any public release (REP-005).

## 3.2 Current status at 0.148.0 (8 November 2026)

Status words follow section 2.2. Built from a static audit of every module and feature against its specification; nothing was run on a device. Evidence is the backlog row, the module's "As built" section and the tests named there.

### Modules

| Area | Status at 0.148.0 | Gaps (audit, 8 November 2026) |
|---|---|---|
| PLAN | **Largely built.** Own `plan_*` tables; Today, Inbox, Upcoming, calendar (read-only device events), lists, events, subtasks, recurrence and frequency targets (Habit merged), focus with Task and Focus eggs, goals with milestones, weekly review, per-task reminders, the streak reminder and weekly recap on the shared scheduler, widgets, HOME and LINK links | **Projects, task priority (None/Low/Med/High, separate from the E-A rank) and an Archived state: missing.** Recurrence scope "this and future": missing. Event reminders and a default timing: missing. Goal status (on hold, archived) and description missing; goal completion is inferred from milestones. Targets and CARE agenda entries are not in Today. Device acceptance (focus process death, reminders) not run. Later review items: REP-030 |
| CARE | **Largely built.** Own `care_*` tables; routines, calm breathing, Diary Card, journal with prompts and search, trends, visitors (meadow of four, queue of six, persistent warmth), reunions, Care Play, Meadow, egg inheritance with an origin note, routine reminders, widget | Routine Skip, the optional once-daily Diary reminder and quick calm/diary widget actions: missing. Per-entry export and the "Reflect" and "History" destinations: missing. A visitor's liked spot and berry are per species, not per individual (CARE.md 6.2); direct-recruit odds are not tied to visitor rarity (6.5). Only one calm tool (breathing). No Care-only privacy test; visitor process-death and clock tests not evident; the Meadow animation does not stop when not visible. Device acceptance not run |
| HEAL | **Built, manual first:** activity, hydration, sleep, body measurements, optional goals, Field Research (on by default), hydration reminders, Heal's party, `heal_*` tables with rows, backups and kept-aside copies sealed with a Keystore key | Exercise library, workouts and templates; meals and nutrition; personal health records; imports (Health Connect is built read-only on request; Huawei is not); History with charts and a configurable Today; medication, appointment and other reminders; selected-source deletion; device and accessibility acceptance; wearable claims withheld |
| MINT | **Built, manual first:** accounts, transactions, budgets, bills, manual reconciliation, review, exact integer money, net position and available money, Ledger Research, sealed rows and backups, passphrase-sealed portable backup, bill reminders (off until turned on), screen-lock gate, hide amounts, statement CSV import | Splits, rollover, savings targets, foreign currency and rates, balance snapshots and freshness, reconciliation sessions, merchant entity and reports (date range, cash flow, comparison, charts), provider integrations, bill-to-PLAN-task link, attachments, per-bill reminder timing, a Mint widget, the `budget_review` and `bill_review` event kinds. **Device acceptance of the Keystore path, the gate, the reminders and the portable backup** |
| HOME | **Built, manual first, with links and reminders:** chores with deterministic schedules and no backlog, areas, supplies with exact quantities and the restocking list, belongings, maintenance, warranties, reference notes, search, Overview, Habitat Discovery, Home's party, `home_*` tables, reminders (off by default, per record), chore-to-PLAN-task link | Search filters (category, status, low stock, due) and the 8-result cap; attachments and photos; maintenance planned/cancelled state; service-provider link to LINK; PLAN shopping task for a supply; upkeep-milestone event; widgets and shortcuts; imports; encryption (no owner decision); the acceptance boxes in BASE.md section 14 are unticked although most are built; device acceptance |
| LINK | **Built, manual first:** people with notes and a directory, important dates (29 February observed on the 28th), follow-ups, search and duplicate suggestions, reminders (0.149.0), Postcard Trail, Link's party; `link_*` tables, in the backup, not sealed | Device-contact import, a UI for handing a follow-up to Plan (`convertFollowUp` exists), widgets; device acceptance |
| WORLD | **Partial; a functioning foundation with large content gaps.** Teams, presets, dungeons in six layers, deterministic offline settlement that settles on entry and at the next due floor, finds and recruitment, rank and skills, job definitions, facilities, Café, Shop, Swap Shop, Dojo, floor events and forks, setbacks and safe retreats, Investigation, Challenge and Story jobs, the five destinations (Base, Atlas, Expeditions, Guild, Journal), battle identity, 21 authored story episodes plus Rescue Team and Explorers of Sky scenes, the Guardian, widget and job alert; every team member gets the full per-floor XP, World holds no Poké balance | **Story:** Johto, Hoenn and Sinnoh have no branching; the other Mythical episodes, Unova to Paldea, the Ranger regions, other PMD settings, Orre and Hisui are not written; no mainline postgame. Exploration XP and rank are still World-owned (shared record needs a migration slice); unopened treasure is a World count; notifications only for jobs; the widget settles when loaded; no World migration fixtures (`worldVersion` 1). The expansion systems of POKEMON_ECOSYSTEM.md 5.5 (jobs as assignments, contests, Berry Garden, camps, raids, fossils) are not built. Device acceptance not run |
| GEAR | **Partial; broad.** All 18 tools in the catalog and routed (Calculator, Everyday, Currency, Units, Timer, Stopwatch, World clock, Flashlight with SOS and screen light, Picker, Coin and dice, Compass, Level, Barometer, Device check, Waypoints, Sound meter, QR, Network); helpers visual only; loans retired; QR payloads are read and warned about, never opened | Timers and stopwatch use wall-clock time, not a monotonic source; no privacy leak test for gear data; QR payload creation for Wi-Fi and contacts; Currency history charts and presets; compass true north and calibration; Device check lacks battery and storage; document scanner with OCR not built. No device, TalkBack or font-scale evidence. Later audit items: REP-028 |
| Launcher | **Partial; a meaningful prototype.** Opt-in HOME role, a four-column widget grid with pages, provider sizes and resize modes honoured, folders, app drawer with categories, hide and pin, living town with the buddy first, weather, widget hosting, search that lists PokéLife's own places | Provider configuration only launched, not re-opened from edit mode; no cleanup of host-held widget ids no layout names; no swipe-up drawer or quick surface; no landmark navigation to modules; population not by favourites or rotation (defaults to 24); positions not persisted; no built-in module widgets (Focus, Quick Tasks, World, Gear, ...); portrait only; no onboarding, reset or accessibility navigation mode; `QUERY_ALL_PACKAGES` policy not decided; launcher preferences are outside the backup; no androidApp lifecycle tests; Samsung acceptance not run |
| Shared Pokémon and economy | **Partial, advanced.** One place per Pokémon, module parties with leads and per-module perks, one currency Poké, shared Pokémon, Bag and ledger tables, module regions, arrival scenes, held items (one each, non-consumable, swap returns the item), Mints, Ability Capsule and Patch, Bottle Caps, vitamins, Tera Shards and `teraType`, evolution and trade items, form dex; the place is named on the detail page | **Moves and mastery: missing (no moveset on the owned record). Poké Ball identity, Ribbons and Marks, research and per-Pokémon history, original nature after a Mint, originModule/originEventId: missing.** Held-item catalog is 7 perk items plus stones (spec: 30 to 40 effects with source and rarity); 25 abilities have an effect and none apply in World. **Mega is half-built** (a Mega Stone held by the buddy boosts Plan, but no Mega form is shown and no stone can be obtained); the Dynamax Band is never read; Z-moves missing; Tera has no Orb. Jobs, contests, Pokéathlon, berries and camps, fossils, outbreaks, raids, Secret Bases: not built. Party slots and PC cards show no place; PC lacks origin, form and module filters; protection of shinies and unique forms is partial |
| Data architecture | **Live on Android**: SQLDelight/SQLite (`:data`), every released schema committed with verified migrations, idempotent append-only ledger and outbox, daily file backup with move-aside restore, one authority (no dual writes). Plan, Care, Heal, Mint, Home, Link and the player's Pokémon, Bag and Poké are in their own tables; WORLD, GEAR and Launcher stay in the `save` row | Typed repositories per module (only Pokémon, Economy and Reminder exist; the app reads one `AppData` through the split); the outbox is written and nothing consumes it; `pokemon_history` missing; the integrity check does not run on every open; **backup is a one-file JSON bundle with a checksum, not the specified archive** (no SQLite snapshot, attachments, per-file checksums, post-restore reconciliation or reminder rebuild); only Mint (and Heal) is encrypted in it; corruption recovery shows no explanation or salvage list; real kill and low-storage runs on a device not done. Full-device erase clears every module, the daily backups, kept-aside copies, held reminders and the old files; not erased: the Keystore key, already scheduled alarms until the next reconcile, and a copy the player saved as a file |
| Integrations and privacy | Cloud, accounts, sync, friends board and the server reward function **retired**. Shared reminders: Plan, Care, Heal (hydration), Mint (bills), Home, Link, with a nightly refresh alarm, quiet hours, focus deferral, snooze and repeat in `reminder_hold`; read-only calendar; widgets for Today, Buddy, Focus, Care, World. System backup and device transfer are closed (`allowBackup="false"`, `data_extraction_rules.xml`). Private module content never reaches the shared save, ledger or reward log (tested for Care, Heal, Mint, Home) | **Manifest audit not done:** location, microphone, calendar, `QUERY_ALL_PACKAGES`, `REQUEST_DELETE_PACKAGES` and exact-alarm permissions are unjustified in writing (owner left them for now); legacy receivers still declared. No `FLAG_SECURE` or recents protection. Notification privacy checked only for hydration and bill reminders. Health Connect and statement import exist; Huawei, Mint providers and LINK contacts do not. No widgets for Heal, Mint or Home and no widget lock-screen policy |
| Tests and CI | `./gradlew jvmTest compileKotlinWasmJs` passes; no Android unit or instrumented tests (`:androidApp:compileDebugKotlin` compiles) | **Screenshot tests assert that something was drawn, not that it matches; no lint, static analysis, dependency or security scan; the release workflow builds a debug APK signed with the committed key; no accessibility, journey, process-death or device suites** |
| Design system and assets | Design rules of 8 November 2026 applied (dialogue boxes, pixel-drawn controls, module accents match the spec table, fewer words); real game assets only | Colour tokens do not carry the spec's names (`textTertiary`, `focus`, `*Container`, `disabled*` absent); rounded shapes remain in the launcher, `AppBackdrop`, folders and `PixelTheme` (`Spacing.CardRadius` 16 dp; `PixelSlider` wraps Material); nine files still use literal colours (mostly art). **REP-015: no asset registry, no rights status or release gate, no app-wide credits screen (only per-Pokémon artist credits); audio has effect and cry sliders but no music; REP-016 and REP-017 not auditioned** |

### Sequence and gates

| Sequence | Status |
|---|---|
| 0 Baseline, 1 Architecture and test strategy | Done (REP-001 to REP-007; Gate A passed). Signing stays test-grade until a public release |
| 2 Local data foundation | Built and live on Android (REP-008). **Gate B is partial**: fixtures migrate and the owner confirmed the cutover on the phone, but kill, low-storage and corruption recovery on a device are not run, and the backup archive is incomplete |
| 3 Shared Pokémon, economy and reminders | Built. **Gate C is partial**: ownership, one Bag and one Poké balance and privacy-safe events hold in tests; reminders have not run on a device |
| 3B Expanded Pokémon foundations | Partial: held items, Mints, capsules, training items, Tera and trade items exist; moves, Balls, Ribbons, history and the WORLD expansion systems do not |
| 4 Design system and app shell | Partial; tokens and components applied, device rendering not reviewed |
| 5 PLAN and CARE refactor | Done on the data side; open on behaviour (the PLAN and CARE gaps above) |
| 6 HEAL, 7 MINT, 8 HOME and LINK | Manual slices built; the gaps above remain |
| 9 GEAR | Broad tool set; unaudited UX; not accepted |
| 10 WORLD | Foundation present; story and content, shared Exploration XP and the remaining expansion systems remain |
| 11 Launcher | Prototype; host robustness, search, population and widgets remain |
| 12 Release hardening | Not started (asset registry, manifest and backup policy, release key, CI hardening, device suites) |
| Gate D (module acceptance), Gate E (device and release acceptance) | D: no module passes in full (each lacks device, accessibility and process-death evidence). E: not started |

### Alignment with the Product Bible

| Bible rule | Status in the code |
|---|---|
| Module map (section 4) | Holds: eight modules, no Grow, no Explore |
| One owner, one source of truth | Holds for the built modules (own tables and store, a minimal event). Open: Exploration XP and unopened treasure in World; the single `AppData` view over the split tables |
| Utility first (principle 1) | Holds: Field Research, Ledger Research and Habitat Discovery can be turned off without losing a record (tested) |
| Forgiving, no punishment (principles 4 and 9) | Holds in Home and Care. Focus is strict by the owner's decision (PLAN.md 6.3), an accepted exception; the Plan streak text has not been checked against the no-pressure rules |
| Local-first, no account, cloud or telemetry | Holds |
| Android only | Holds; desktop and web stay compile-only |
| Cross-module links | Partly built: HOME chore to PLAN task (HOME pays once, PLAN pays nothing, tested) and PLAN's half of a LINK follow-up conversion; MINT bill links are not built |
| Roles; one Bag and one currency; held items persistent and non-consumable | Hold |
| Private content never enters WORLD or reward presentation | Holds and is tested for Care, Heal, Mint and Home; not tested for Gear data |
| Permissions just in time; honest integrations | Holds for what is built; the manifest still declares permissions nobody has justified |
| Definition of done (section 9) | **Not met by any module** (no device verification, accessibility review or process-death evidence) |

### Owner decisions of 8 November 2026 (on the audit's conflicts)

| # | Question | Decision | Effect |
|---|---|---|---|
| 1 | Focus interruptions | **Strict stays** | PLAN.md 6.3 amended; no gentle mode (11 November 2026: add an allow list and an ongoing notification, Bible section 5) |
| 2 | Milestone Super Candy | **Stays as it is** | PLAN.md 9 amended; goal completion is still inferred, to be made deliberate |
| 3 | Shiny and egg luck perks, EV and IV model | **Perks stay** | POKEMON_ECOSYSTEM.md 2.2.1 amended; the EV and IV model stays as built |
| 4 | Care eggs hatch at once, or enter the shared Egg collection | **Hatch at once, with a scene where the visitor leaves the egg** | Built; CARE.md 6.6 amended |
| 5 | Mega and Dynamax | **Integrate them into PokéLife** | Pending item 8 |
| 6 | HEAL encryption | **Put in the roadmap** | Built (Heal's own Keystore key; a passphrase backup seals it with Mint) |
| 7 | Gear loans | **Retire** | Done |
| 8 | Manifest | **Backup closed; permissions OK for now** | Declared permissions stay in pending item 9 |

### Pending items (ordered by value)

1. **Device acceptance for everything since the database cutover**: recovery cases, Mint's Keystore key, access gate, bill reminders and portable backup, the module tables, arrival scenes, reminders, and a visual review of every new screen in both palettes and at 130% text. Closes Gates B and C and starts D.
2. **WORLD content and structure**: branching in Johto, Hoenn and Sinnoh, the other Mythical episodes, then Unova onward and the Ranger regions; Exploration XP and rank as the shared record.
3. **PLAN completion**: Projects, priority, Archived state, recurrence scopes, event reminders, deliberate goal completion, targets in Today (REP-030 holds the review items).
4. **LINK**: device-contact import and a UI for handing a follow-up to Plan; this also unblocks Home's service-provider link.
5. **CARE completion**: routine Skip, Diary reminder, Reflect destination, per-visitor individuality, privacy and process-death tests (REP-030).
6. **Typed repositories per module**, the outbox consumer and the removal of the single-`AppData` view; the backup archive (DATA_AND_MIGRATIONS.md 10.2 to 10.4) with whole-bundle encryption and post-restore reconciliation.
7. **Open module scope**: HEAL (workouts, nutrition, records, Huawei, History), MINT (providers, splits, rollover, savings, foreign currency, reports), HOME (search filters, attachments, widgets, imports), GEAR (REP-028), Launcher (its row).
8. **Shared Pokémon expansion** (P2): **Mega and Dynamax integrated (owner decision 5)**: a source for Mega Stones (to be designed), the Mega form shown for a focus session, the Dynamax Band given a use; then the owned-record provenance layer (original nature, Ball, moveset, Ribbons and Marks, history) with a migration, then moves and mastery, a larger held-item catalog, then the WORLD systems that depend on them.
9. **Release hardening** (sequence 12): the manifest, the asset registry, rights status and credits screen (REP-015), sound clean-ups (REP-016, REP-017), the protected release key (REP-005), real screenshot comparison, an androidApp test source set, lint and static analysis, a release-variant compile, and a device suite.

## 4. Architecture dependencies and blocking work

### P0 — Must precede broad feature expansion

Done or live: the source baseline and save fixtures, the architecture decisions and test strategy, the local data foundation, and the shared Pokémon, Bag, Poké, ledger and reminder foundations. Cloud, account and sync paths are retired. What remains of P0 is the device evidence in pending item 1 and the typed repositories in item 6.

### P1 — Core product workflows

- Design-system components and app shell.
- Completing PLAN and CARE behaviour, and the HEAL, MINT, HOME and LINK scope still open.
- GEAR tool correctness, lifecycle, accessibility and permission audit.
- WORLD shared ownership, expedition model validation, authored story and regional content.
- Launcher completion after shared Pokémon presentation and platform lifecycle are stable.

### P2 — Depth and content

- Expand shared Pokémon mechanics only on the stable ownership, Bag, transaction, migration and test foundations: persistent held items and item acquisition, abilities/Hidden Abilities, natures/Mints, moves/mastery, evolution items, bounded training, Poké Balls, Ribbons/Marks and research.
- Add WORLD gameplay in staged vertical slices: Mega/Z/Dynamax/Tera transformations, jobs, shops/crafting, Berry Garden/cooking/camps, contests/Pokéathlon, fossils, raids/outbreaks and Ranger field interactions. Held items stay persistent, non-consumable equipment; runtime state is separate from inventory.
- Additional provider integrations only after official API/terms/region/approval validation.
- Expanded WORLD regional campaigns, Ranger content, dungeons, story and postgame.
- Launcher environmental options, population controls, widgets and polish.
- Additional asset coverage only after provenance, licensing and performance checks.

## 5. Recommended implementation sequence and release gates

Dependencies matter more than the phase number. Parallel work is allowed only when it does not create competing data or platform authorities.

| Sequence | Workstream | Depends on | Exit gate |
|---|---|---|---|
| 0 | Source freeze, inventory, save fixtures | Current checkout | Reproducible baseline |
| 1 | Architecture decisions and test strategy | 0 | Ownership contracts reviewed; no unresolved critical ambiguity |
| 2 | Local database, repositories, migrations, backup/restore, recovery | 1 | Migration fixtures pass; interrupted writes/recovery tested; no dual-write authority |
| 3 | Shared Pokémon/economy and reminder foundations | 1–2 | Canonical owned IDs, one Bag/Poké/reward ledger; idempotency and reminder recovery tests pass |
| 3B | Expanded shared Pokémon foundations | 3 | Persistent held items, item sources, central ability/nature/move/evolution rules and migration tests pass |
| 4 | Design system and app shell | 1 | Shared components/tokens/accessibility baseline rendered on target Android versions |
| 5 | PLAN and CARE migration/refactor | 2–4 | Existing data preserved; one owner per record/action; acceptance tests pass |
| 6 | HEAL | 2–4 | Manual workflows work offline; source/provenance and privacy tests pass; wearable claims withheld until device proof |
| 7 | MINT | 2–4 | Manual/import/reconciliation workflows work; financial privacy tests pass; live integrations separately approved and verified |
| 8 | HOME and LINK | 2–4 | Domain ownership, cross-links, privacy-safe events, offline workflows, export/restore and opt-out tests pass |
| 9 | GEAR completion | 2–4 | Each shipped tool passes its own correctness, lifecycle, permission and accessibility checklist |
| 10 | WORLD completion | 3 plus stable foundations | Shared ownership canonical; deterministic expedition/reward tests pass; authored story/content validation passes |
| 11 | Launcher completion | 3–4 | Lifecycle, widget behavior, gestures, fallback, performance and accessibility verified on physical Samsung hardware |
| 12 | Release hardening | All intended workstreams | Full regression/migration suite, privacy/security, asset rights, accessibility, performance, backup/restore and device acceptance pass |

### Release gates

Gate A (architecture approval) has passed.

**Gate B — Data migration**
- Representative real save fixtures migrate without loss or duplicate rewards.
- Legacy JSON stays untouched until import verification and rollback retention requirements pass.
- Process death, interrupted migration, low storage, corruption, and restore failure have tested recovery paths.
- One authoritative database; indefinite dual writes are prohibited.

**Gate C — Shared systems**
- Pokémon ownership and progression are canonical and idempotent.
- Bag and Poké have one balance/inventory authority.
- Shared reminder scheduling and notification delivery are used by all modules.
- Privacy-safe event contracts prevent sensitive source content from reaching WORLD.

**Gate D — Module acceptance**
- Module-specific acceptance criteria pass.
- Offline, empty/error, permission-denied, process-death, time-zone, and migration cases pass as applicable.
- Cross-module links never duplicate records or rewards.

**Gate E — Device and release acceptance**
- Physical-device checks cover target Android hardware, permission behavior, notification delivery, launcher lifecycle, accessibility, dark mode, performance and battery.
- Asset licensing/provenance is cleared for the intended distribution; the repository contains game-derived and private-use material, so do not assume public redistribution rights.
- No cloud dependency, telemetry, data loss, unresolved P0/P1 defect, or unverified integration claim remains.

## 6. Workstream reuse policy

| Existing area | Direction |
|---|---|
| Pokémon Dex and curated data | Reuse after catalog/identity audit |
| Pokémon progression/reward logic | Canonical shared ownership and transactional settlement |
| PLAN, CARE domains | Reuse/refactor; preserve tested rules and authored content |
| WORLD expedition simulation and story engine | Reuse/refactor; validate balance and shared battle identity; keep campaign families separate |
| Compose components | Selective reuse against `FEATURES/DESIGN_SYSTEM.md` |
| Launcher | Re-architect around stable scene state and Android lifecycle |
| GEAR tools | Selective reuse and complete per-tool correctness |
| Legacy Android app | Reference only |

## 7. Engineering workflow

For each implementation change, record: user problem; authoritative owner/spec; current behavior and evidence; target acceptance criteria; data/migration impact; reward impact; permissions/privacy; UI/accessibility; tests/performance; and documentation updates.

1. Inspect current implementation and data before editing.
2. Write or update domain and migration tests.
3. Implement domain rules before UI.
4. Use approved shared components and platform adapters.
5. Run targeted tests, full relevant tests, and build; record exact commands/results.
6. Inspect screenshots and real-device behavior where required.
7. Update the authoritative specification only when an approved requirement changes; update this guide when implementation evidence/status changes.
8. Review for duplicate owners, schedulers, reward systems, currency, Bag, shadow records, dead code, and hidden cloud dependencies.

A phase cannot close as "mostly done". List unmet acceptance criteria and evidence.

## 8. Documentation inventory

The canonical set is the 17 documents listed in `PRODUCT_BIBLE.md` section 11; `IMPLEMENTATION_BACKLOG.md` is the separate delivery artifact. `README.md` is orientation only.

- The `docs/pokelife/` tree is the only current specification authority.
- Update the owning canonical document when a requirement changes, then dependent cross-references and backlog items in the same change.
- Never infer implementation status from specification presence.
- Still stale and not to be mistaken for current: `CLAUDE.md` details (habits, the reminder list, widgets described as Glance), `docs/design.md` (habits as Today items, strict-mode and milestone text, Mega/Dynamax claims), `BASE.md` 16 status line, `HEAL.md` 322 and 496 against the on-by-default rule at 532, `DATA_AND_MIGRATIONS.md` 10.5.1 (documents list), `INTEGRATIONS_AND_PRIVACY.md` manifest sections (an open gap, not a done state), `TEST_STRATEGY.md` file counts (0.123.0 baseline), `GEAR.md` (Choose and Measure grouping, Pokégear name), and the `MODULES/*.md` acceptance boxes, which are all unticked and carry no built/partial status.

## 9. Owner decisions of 6 November 2026

Recorded in their authoritative documents; listed here so the plan reads in one place.

1. These docs are the source of truth over the older `docs/design.md` and `docs/economy.md` (see `README.md`).
2. Pokémon roles: global buddy (cosmetic); a party of one lead plus five per module, bonuses for that module only; WORLD expedition teams; GEAR helpers (visual only). `FEATURES/POKEMON_ECOSYSTEM.md` 2.2. REP-009.
3. Module map: PLAN, CARE, HEAL, MINT, HOME, LINK, GEAR, WORLD; no Grow and no Explore. Bible section 4.
4. Local-only has priority: no work on Supabase, accounts, sync or the server reward function. Bible section 3. REP-012.
5. Android is the only product platform; desktop and web are development conveniences. Bible section 3. REP-006, REP-013.
6. One currency, Poké: PLAN coins and WORLD Poké merge 1:1. REP-010.

## 10. Current unresolved issues

1. **Integration feasibility:** Huawei Watch Fit 4 Pro/Health Connect and Philippine bank/e-wallet integrations remain provider/device-dependent; no live integration is verified.
2. **Asset distribution rights:** the repository includes game-derived/private-use material; public distribution requires an asset-by-asset rights review and replacement or permission where necessary.
3. **Expanded Pokémon scope:** the integrated expansion catalogue is approved target scope, not evidence of implementation; verify the Kotlin source areas, schemas and tests before assigning status.
4. **Device evidence:** the database cutover, reminders, Keystore paths and every module's acceptance still need a physical-device pass (pending item 1).

## 11. Documentation change control

- Product decisions belong in the Product Bible or the relevant module/feature document.
- Shared technical contracts belong in the relevant Architecture document.
- Implementation evidence and roadmap belong here.
- Do not create a global decision register or duplicate feature specifications.
- Do not mark a requirement built, tested, or device-verified based solely on its specification.
