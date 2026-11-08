# PokéLife Implementation Guide

**Version:** 2.0  
**Status:** Baseline evidence, implementation status, dependency plan  
**Baseline audited:** `pokeplan-reboot-main (5).zip`, version `0.123.0`; re-checked against the repository (`main`, commit `2b22975d`) on 6 November 2026  
**Documentation reconciliation:** 6 November 2026; status re-aligned with the code and the Product Bible at version `0.143.0` on 8 November 2026 (section 3.2)

> This guide distinguishes approved specifications from implementation evidence. The inspected baseline is a source archive, not a verified current working checkout. No successful full build/test run or target-device acceptance run was established by the baseline audit. Re-audit the actual implementation branch before treating any status as current.

## 1. Executive summary

PokéLife has an established Kotlin Multiplatform/Compose foundation and substantial existing PLAN, CARE, WORLD, GEAR, Pokémon, asset, and launcher implementation. The 0.123.0 baseline is not the complete product defined by the Product Bible. **Since then (0.124.0 to 0.143.0) the data foundation, the shared Pokémon and economy contracts, and the HEAL, MINT and HOME modules have been built on the JVM and compiled for Android; LINK is the only life module not started, and nothing has been verified on a device by this guide.** Section 3.2 is the current status; section 3's table is kept as the 0.123.0 baseline.

The primary engineering risk is not a lack of feature specifications. It is the gap between approved modular ownership and the older JSON/store/cloud pathways. The implementation sequence must establish the local data architecture, migration safety, and shared services before broad module expansion.

No feature is marked device-verified by this guide. The one device observation recorded is the owner's confirmation on the phone that the database cutover kept all data (REP-012). Build and test evidence is in section 3.2.

## 2. Evidence and status vocabulary

### 2.1 Evidence boundary

The baseline audit inspected the supplied `0.123.0` source archive, representative domain/UI/application source, repository documentation, and existing test inventory. It did not establish:
- a successful full build;
- execution of the full test suite;
- end-to-end acceptance of every workflow;
- live Huawei Health, bank/e-wallet, or third-party provider integrations;
- visual quality of every screen from a complete device review;
- current state of a later working branch.

A source file or test file is evidence of code/test presence only. It is not evidence that tests passed or that a complete workflow works.

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

A capability may be built but untested, or tested but not device-verified. Never collapse these states into “implemented”.

## 3. Baseline implementation status: source archive 0.123.0

| Area | Baseline evidence | Status against target | Not established |
|---|---|---|---|
| PLAN | Tasks/planner, focus, habits, goals/lists, calendar-related models and reminders | Partial; substantial foundation to reuse/refactor | Full target workflows, migration, device acceptance |
| CARE | Routines, calm breaks, Diary Card, skills/DBT, journal/trends, reminders, visitors, Care Eggs/rewards | Partial; since 0.132.0 on its own tables and the visitor rules of `MODULES/CARE.md` section 6 (JVM-tested, `evidence/REP-014-care-tables.md`) | Device acceptance; reunions, Care Play, Meadow and egg inheritance not built |
| WORLD | Expeditions, teams, dungeons, missions, rank/skills, rewards, story scenes and settlement | Partial; functioning foundation with substantial content/model work remaining | Full regional coverage, final balance, end-to-end/device acceptance |
| GEAR | Calculator, currency, units, date/time, sensors, QR, network, sound, randomizer, flashlight capabilities | Partial; existing tools need dedicated-app quality and lifecycle review | Every tool's full capability and device acceptance |
| Android launcher | Optional launcher with town/scene rendering, pages, app list, widgets, folders, weather/settings | Partial; meaningful prototype | Full living-world behavior, robust lifecycle, actual Samsung device acceptance |
| Shared Pokémon | Pokédex, collection, party, PC, Bag, buddy, evolution, items, stats, perks, forms, rewards | Partial; substantial foundation requiring shared ownership/economy reconciliation | Canonical target schema, migration, complete module contracts, tested settlement |
| HEAL | No first-class module in the audited baseline | Planned / specified | Manual workflows, imports, provider integration |
| MINT | No first-class module in the audited baseline | Planned / specified | Finance workflows, PHP provider feasibility, reconciliation |
| HOME | No first-class module in the audited baseline | Planned / specified | Household workflows, reminders, attachment lifecycle |
| LINK | No first-class module in the audited baseline | Planned / specified | Contact/relationship workflows, import and reminder integration |
| Data architecture | Legacy JSON/document persistence, `GameStore`, separate CARE store | Target architecture specified; baseline architecture misaligned | SQLDelight feasibility, migration fixtures, cutover |
| Integrations/privacy | Existing calendar read path, sensor/location/microphone/scanner abstractions, reminder receivers/reboot recovery; Supabase/cloud paths also present | Target policy specified; baseline requires audit/refactor/retirement | Provider approvals, current permission manifest audit, security verification |
| Tests | Unit/domain and selected UI/navigation/launcher test sources exist | Test assets present; test execution not evidenced by this audit | Passing full suite, integration suite, device suite |
| Design system/assets | Existing design and asset foundations | Partial; target shared specifications now define authority | Full screen audit, asset-rights clearance, device rendering review |

**Important:** The table describes the archived 0.123.0 baseline only. It is not a claim about changes made after that archive. Re-inspect the actual branch and record commit/build identifiers before implementation begins.


## 3.1 Engineering operations findings from archived workflow review (2026-11-06)

The archived `0.123.0` repository contains three workflows under `.github/workflows/`:

| Workflow | Observed configuration | Follow-up |
|---|---|---|
| `ci.yml` | `gradle/actions/setup-gradle@v4`; uploads an artifact with `actions/upload-artifact@v4`; `retention-days: 3`; `cancel-in-progress: true` | Keep the short retention unless the artifact has a documented longer-lived purpose; inspect the live cache and storage breakdown |
| `release-apk.yml` | `gradle/actions/setup-gradle@v4`; `cancel-in-progress: true`; publishes APKs through GitHub Releases, including a moving `latest` release and versioned releases | Treat GitHub Release assets separately from Actions artifact storage; review release lifecycle and Gradle cache reuse |
| `deploy-reward.yml` | `gradle/actions/setup-gradle@v4`; `cancel-in-progress: false` | Review whether queued deployment runs should remain serialized and whether cache usage is appropriate |

CI and release workflows comment that they share a Gradle cache key. The inspected workflow files contain no standalone `actions/cache` step; `gradle/actions/setup-gradle` manages Gradle caching. This does not establish the size, number, age, or billable storage of live cache entries. Do not indiscriminately delete caches or release assets: inspect the live GitHub Actions storage breakdown first, preserve required release deliverables, then apply targeted cleanup and retention changes. The user's 6 November 2026 notification reported 0.45 GB of 0.5 GB included Actions storage used (90%); this is a point-in-time account alert, not a current measurement.

### Release signing security follow-up

The archived repository README flags moving signing credentials into protected secrets before public release. Treat this as an audit finding, not proof that a production signing key was exposed. Inspect tracked keystores, Gradle signing configuration, ignored files, and Git history. Determine whether each credential is test-only or sensitive. If a real release key has been committed, assess exposure and plan rotation without breaking application update continuity. See backlog item REP-005.

### Repository re-check (6 November 2026)

The docs were imported into the repository at `docs/pokelife/`, and the real checkout was inspected:

- `main` is `0.123.0` at commit `2b22975d`; no pull request is open.
- The "Latest APK" release workflow succeeded on the last five merges (runs 202 to 206, 3 November 2026), so the app packaged on CI at that point. That is build evidence only, not test or device evidence.
- The checkout has about 90 `*Test*.kt` files outside `legacy-android/` (the archive audit counted 89).
- `ci.yml` keeps its artifact for 3 days; no workflow has a standalone `actions/cache` step (Gradle caching comes from `setup-gradle`).
- The signing key is `androidApp/pokeplan-debug.keystore`, committed on purpose and shared by every build; `CLAUDE.md` already notes that it must move to a secret before any public release (REP-005).
- **Baseline test run (REP-001):** **passed.** `./gradlew jvmTest compileKotlinWasmJs --console=plain` at commit `2b22975d` (the `main` baseline; this branch only adds documents) in the cloud sandbox (JDK 21): `BUILD SUCCESSFUL in 13m 12s`. The JVM test reports show 816 tests, 0 failures, 0 errors, 0 skipped (pokemon-core 167, plan-core 134, care-core 8, world-core 90, app-core 200, composeApp 217). Not covered: the wasm tests (compile only), any Android build or instrumented test, and any device run. The screenshot tests in this count only check that something was drawn (`evidence/REP-002-test-inventory.md`). Status: Tested (JVM), not Device-verified.

### Build/test execution limitation (archive audit)

The archive audit could not resolve `services.gradle.org` to download Gradle 9.8.1. That was an environment failure, not a source failure, and it is superseded by the repository run above.

## 3.2 Current status at 0.143.0 (8 November 2026)

This section supersedes the status column of section 3, which stays as the 0.123.0 baseline. Status words follow section 2.2. Evidence is the backlog row (`IMPLEMENTATION_BACKLOG.md`), the evidence file, the module's "As built" section and the tests named there; nothing below is device-verified.

### Modules

| Area | Status at 0.143.0 | Evidence | Not established |
|---|---|---|---|
| PLAN | Partial. Own `plan_*` tables since 0.133.0; Habit merged into recurring tasks; one reminder scheduler | REP-014, `evidence/REP-014-plan-tables.md`, REP-011 | Streak reminder and weekly recap adapters; device acceptance; target workflows against `PLAN.md` not re-audited |
| CARE | Partial. Own `care_*` tables since 0.132.0; visitor rules of `CARE.md` section 6; reunions, Care Play, Meadow and egg inheritance built (0.133.0) | `evidence/REP-014-care-tables.md`, `REP-014-plan-tables.md` | Device acceptance; the rest of `CARE.md` not re-audited |
| HEAL | **Built, manual first** (0.136.0): activity, hydration, sleep, measurements, goals, Field Research (rules version 17) | `MODULES/HEAL.md` section 22, `evidence/REP-014-heal.md`, `HealStoreTest`, `HealTablesTest` | Exercise library and workouts, nutrition, health records, imports (Health Connect, Huawei), history and charts, medication reminders, encryption; **device acceptance; wearable claims withheld** |
| MINT | **Built, manual first** (0.141.0): accounts, activity, budgets, bills, review and reconciliation, exact integer money, Ledger Research (version 18); rows and backups sealed with an Android Keystore key; follow-ups (0.142.0): a passphrase-sealed backup that opens on any phone, bill reminders through the shared scheduler (off until turned on), the optional screen-lock access gate, full account edit | `MODULES/MINT.md` section 22, `evidence/REP-014-mint.md`, `MintStoreTest`, `MintTablesTest`, `PortableBackupTest`, `MintRemindersTest` | Providers, statement import, splits, rollover, savings targets, foreign currency; **device acceptance, including the Keystore path, the access gate and the reminders**
| HOME | **Built, manual first** (0.143.0): chores with deterministic schedules and no backlog, supplies with exact quantities, belongings, maintenance, warranties, reference notes, search, Overview, Habitat Discovery (version 19) | `MODULES/HOME.md` section 16, `evidence/REP-014-home.md`, `HomeRulesTest`, `HomeStoreTest`, `HomeTablesTest` | Reminders, attachments, PLAN links, widgets, imports, encryption (no owner decision); device acceptance |
| LINK | Planned / specified; placeholder card, colour and region reserved. Not started | `MODULES/LINK.md` | Everything |
| WORLD | Partial. Foundation as in section 3; Rescue Team maps and tilesets added (0.140.0); Poké is the shared balance and World holds none (0.145.0); every expedition member gets the full per-floor Pokémon XP (0.145.0) | CHANGELOG, REP-010 | Full regional coverage, balance, shared-ownership completion, device acceptance; not re-audited since 0.123.0 beyond those |
| GEAR | Partial. Not re-audited since 0.123.0 | Section 3 | Every tool's capability and device acceptance |
| Launcher | Partial. Not re-audited since 0.123.0 | Section 3 | Lifecycle, Samsung hardware acceptance |
| Shared Pokémon and economy | Partial, advanced: four roles with one place per Pokémon (0.124.0, 0.133.0), one currency Poké (0.125.0, version 15), shared Pokémon, Bag and ledger tables as the live authority (0.135.0), per-module parties and leads (Plan, Care, Heal, Mint, Home), module regions curated (0.139.0), an arrival scene per acquisition (0.141.0, 0.143.0). Held items, natures and Mints, abilities and capsules, evolution and trade items, Bottle Caps and Tera Shards exist | REP-009, REP-010, `evidence/REP-014-shared-tables.md`, `docs/economy.md` (rules version 19) | Moves and mastery, Ribbons and Marks, Poké Ball identity, Mega/Z/Dynamax/Tera systems as gameplay, Jobs, contests, Pokéathlon, berries and camps, raids: **not found in the source by a name search; unverified, treat as not built**. A party for LINK; the place label on the party and PC screens |
| Data architecture | **Live on Android since 0.129.0**: SQLDelight/SQLite (`:data`), schema 9, every released schema committed with verified migrations, idempotent append-only ledger, daily file backup of the documents with move-aside restore, one authority (no dual writes). Plan, Care, Heal, Mint and Home and the player's Pokémon, Bag and Poké are in their own tables; legacy JSON kept as the rollback copy | REP-006, REP-007, REP-008, REP-012, `evidence/REP-014-*`; the owner confirmed on the phone that the cutover kept all data | A real kill and a real low-storage run on a device, the snapshot archive with attachments, archive encryption, the typed repositories per module (`DATA_AND_MIGRATIONS.md` section 7.3.0; the app still reads one `AppData` through the split) |
| Integrations and privacy | Cloud, accounts, sync, friends board and the server reward function **retired** (0.130.0). Reminders: Plan and Care on one scheduler with global quiet hours and focus deferral (0.131.0), Heal's hydration reminders, Mint's bill reminders (0.142.0). Private module content never reaches the shared save, ledger or reward log (tested for Care, Heal, Mint, Home) | REP-011, REP-012, module tests | Reminders for Home and Link; the streak reminder and weekly recap; the permission manifest audit on a device; provider approvals |
| Tests | `./gradlew jvmTest compileKotlinWasmJs` at the commit that built HOME: `BUILD SUCCESSFUL`, 1,151 JVM tests, 0 failures (pokemon-core 192, plan-core 134, care-core 8, heal-core 8, mint-core 23, home-core 58, world-core 90, app-core 285, data 105, composeApp 248). The wasm target is compile-only; there are no Android unit or instrumented tests (`:androidApp:compileDebugKotlin` compiles) | `evidence/REP-002-test-inventory.md`; the screenshot tests only check that something was drawn | A device suite; an integration suite; screenshots are looked at by hand, not compared |
| Design system and assets | Design rules of 8 November 2026 applied (dialogue boxes, pixel-drawn controls, module accents, fewer words; 0.137.0, 0.138.0); real game assets only | `FEATURES/DESIGN_SYSTEM.md`, CHANGELOG | Rendering on target Android versions, TalkBack and dark mode on device; REP-015 asset registry and rights audit (planned); asset distribution rights |

### Sequence and gates

| Sequence | Status |
|---|---|
| 0 Baseline | Done (REP-001 to REP-005). Signing remains test-grade and must move to a secret before any public release |
| 1 Architecture and test strategy | Done (REP-007, Gate A passed; `TEST_STRATEGY.md` approved) |
| 2 Local data foundation | Built and live on Android (REP-008, schema 9); **Gate B is partial**: fixtures migrate and the owner confirmed the cutover on the phone, but kill, low-storage and corruption recovery on a device are not run |
| 3 Shared Pokémon, economy and reminders | Built for the contracts above; reminders are shared for Plan, Care, Heal's hydration and Mint's bills only. **Gate C is partial**: ownership, one Bag and one Poké balance and privacy-safe events hold in tests; shared reminders are not yet used by every module |
| 3B Expanded Pokémon foundations | Partial (see the shared row above) |
| 4 Design system and app shell | Partial; tokens and components applied, device rendering not reviewed |
| 5 PLAN and CARE refactor | Largely done on the data side (own tables, one owner per record); the reminder adapters for the streak reminder and weekly recap remain |
| 6 HEAL | Manual slice built; imports, nutrition and exercise library not built |
| 7 MINT | Manual slice built; import and providers not built |
| 8 HOME and LINK | **HOME manual slice built (0.143.0); LINK not started** |
| 9 GEAR, 10 WORLD, 11 Launcher | Not advanced by this series beyond the shared items above |
| 12 Release hardening | Not started |
| Gate D (module acceptance) | No module passes in full: each lacks device, accessibility and process-death evidence |
| Gate E (device and release acceptance) | Not started |

### Alignment with the Product Bible

| Bible rule | Status in the code |
|---|---|
| Module map (section 4): eight modules, no Grow, no Explore | Holds. PLAN, CARE, HEAL, MINT, HOME and WORLD have screens; GEAR is a Pokégear entry; LINK is a placeholder card |
| One owner, one source of truth (principle 2) | Holds for the built modules: each has its own tables and store and pays only through a minimal event. Open: the app's single `AppData` view over the split tables |
| Utility first; every module works without rewards (principle 1) | Holds: Field Research, Ledger Research and Habitat Discovery can be turned off in Settings without losing a record (tested) |
| Forgiving, no punishment (principles 4 and 9) | Holds by design in Home (no backlog, no streak, no shaming words); not re-audited for the older Plan and Care streak features |
| Local-first, no account, no cloud, no telemetry (principle 6, section 3) | Holds since 0.130.0 |
| Android only (section 3) | Holds; desktop and web stay compile-only |
| Cross-module links (section 4): HOME owns a chore a PLAN task is linked to; LINK owns a follow-up until converted; MINT owns bill state | **Partly built (0.144.0).** HOME chore to PLAN task is built (`TaskLink`, `HomeLinkOwner`: HOME completes and pays once, PLAN pays nothing, tested) and PLAN's half of a LINK follow-up conversion (`convertFollowUp`, one task per follow-up, PLAN pays once); LINK itself and MINT bill links are not built |
| Roles (section 5): global buddy, module party of six, WORLD teams, GEAR helpers; one Pokémon in one place | Holds (0.124.0, 0.133.0); LINK's party does not exist yet |
| One Bag and one currency Poké | Holds (0.125.0, 0.135.0); World's working copy is gone (0.145.0) |
| Held items persistent and non-consumable | Implemented for the existing held items; not re-verified against the full invariant |
| Private content never enters WORLD or reward presentation (sections 3 and 8) | Holds and is tested for Care, Heal, Mint and Home (the event JSON, the shared save, the reward log and the shared tables are searched for private words) |
| Permissions just in time; honest integrations (section 8) | Holds for what is built; no provider integration exists and Settings says so |
| Definition of done (section 9) | **Not met by any new module**: each is built, tested on the JVM and compiled for Android, but has no device verification, accessibility review or process-death evidence |

### Pending items carried over from earlier sessions

1. **Device acceptance for everything since 0.129.0**: the database cutover recovery cases, Mint's Keystore key, access gate, bill reminders and portable backup, the module tables on a device, the new arrival scenes, reminders, and a visual review of every new screen in both palettes and at 130% text.
2. **LINK** (sequence 8): all eight phases of `MODULES/LINK.md`, its party, and its Postcard Trail event.
3. **Cross-module links**: HOME chore to PLAN task and PLAN's half of the LINK follow-up conversion are built (0.144.0); still open: the LINK module itself, MINT bill state, a PLAN shopping task for a supply.
4. **Reminders through the shared scheduler**: Home's (chores, maintenance, low stock, warranty) are built (0.144.0, not run on a device); still open: Link (dates and follow-ups), the Plan streak reminder and weekly recap.
5. **Typed repositories per module** and the removal of the single-`AppData` view; (REP-010's World rules taking the balance as an input is done, 0.145.0.) Started (0.144.0): HEAL, MINT and HOME rewards settle their real event ids in the ledger (`DATA_AND_MIGRATIONS.md` 7.3.0 step 1); the rest is open.
6. **Open module scope**: HEAL (workouts, nutrition, imports, history), MINT (import, providers, splits, rollover, savings targets), HOME (attachments, widgets, imports).
7. **GEAR completion, WORLD completion and the launcher** (sequences 9 to 11), not re-audited since 0.123.0.
8. **Release hardening** (sequence 12): the asset registry and rights audit (REP-015), the sound clean-ups (REP-016, REP-017), the protected release key (REP-005), and a refreshed test baseline that includes an Android build and a device suite.
9. **Shared Pokémon expansion** (P2): the items marked unverified above.

## 4. Architecture dependencies and blocking work

### P0 — Must precede broad feature expansion

1. **Freeze and inventory the real source baseline.** Identify the actual branch/commit, all persisted save shapes, cloud pathways, current permissions, tests, and module repositories. Create representative 0.123.0 save fixtures.
2. **Complete architecture decisions.** Resolve database technology feasibility (including the SQLDelight spike), module repository boundaries, ID policy, transaction coordination, outbox/idempotency, attachment lifecycle, and rollback strategy.
3. **Apply and maintain the existing test strategy.** `ARCHITECTURE/TEST_STRATEGY.md` is present and approved. Validate that its coverage and commands match the current source branch; do not treat specification as evidence that tests passed.
4. **Implement the local data foundation.** Database lifecycle, schema versioning, repositories, transaction boundaries, backup/restore, migration runner, recovery, and data-retention policy.
5. **Implement shared domain foundations.** Canonical Pokémon catalog/owned instances, Pokédex, parties, Bag, Poké, reward ledger, and shared reminder persistence/scheduler contracts.
6. **Retire cloud/account/sync pathways only after migration and rollback gates pass.** The approved product has no mandatory account or PokéLife cloud synchronization.

### P1 — Core product workflows

- Design-system components and app shell, in parallel with data-foundation work once module boundaries and tokens are stable.
- PLAN and CARE refactor/migration using their existing domain behavior and approved ownership.
- Shared reminders, notification delivery, permission timing, and recovery.
- HEAL manual-first workflows; provider integration feasibility is a separate gated workstream.
- MINT manual/import-first finance workflows; provider access remains feasibility-gated.
- HOME chores, inventory, maintenance and household reference.
- LINK people, notes, important dates and follow-ups.
- GEAR tool correctness, lifecycle, accessibility and permission audit.
- WORLD shared ownership/economy migration, expedition model validation, authored story and regional content.
- Launcher completion after shared Pokémon presentation and platform lifecycle are stable.

### P2 — Depth and content

- Expand shared Pokémon mechanics only after the shared ownership, Bag, transaction, migration and test gates pass: persistent held items and item acquisition, abilities/Hidden Abilities, natures/Mints, moves/mastery, evolution items, bounded training, Poké Balls, Ribbons/Marks and research.
- Add WORLD gameplay in staged vertical slices: Mega/Z/Dynamax/Tera transformations, jobs, shops/crafting, Berry Garden/cooking/camps, contests/Pokéathlon, fossils, raids/outbreaks and Ranger field interactions.
- Held items are explicitly persistent, non-consumable equipment. Item-effect triggers never consume or destroy them; runtime state is separate from inventory.

- Additional provider integrations only after official API/terms/region/approval validation.
- Expanded WORLD regional campaigns, Ranger content, dungeons, story and postgame.
- Additional tool depth and justified optional refinements.
- Launcher environmental options, population controls, widgets and polish.
- Additional asset coverage only after provenance, licensing, and performance checks.

## 5. Recommended implementation sequence and release gates

Dependencies are more important than the phase number. Parallel work is allowed only when it does not create competing data or platform authorities.

| Sequence | Workstream | Depends on | Exit gate |
|---|---|---|---|
| 0 | Source freeze, inventory, save fixtures, branch/status baseline | Current source checkout | Reproducible baseline; all persisted data and cloud/permission paths inventoried |
| 1 | Architecture decisions and test strategy | Sequence 0 | Database choice and ownership contracts reviewed; test strategy exists; no unresolved critical ownership ambiguity |
| 2 | Local database, repositories, migrations, transactions, backup/restore, recovery | Sequence 1 | Migration fixtures pass; interrupted writes/recovery tested; no dual-write authority |
| 3 | Shared Pokémon/economy and reminder foundations | Sequences 1–2 | Canonical owned IDs, party references, one Bag/Poké/reward ledger; idempotency and reminder recovery tests pass |
| 3B | Expanded shared Pokémon foundations | Sequence 3; inventory/economy contracts stable | Persistent held items, item sources, centralized ability/nature/move/evolution rules and migration tests pass |
| 4 | Design system and app shell | Sequence 1; may run alongside 2–3 | Shared components/tokens/accessibility baseline approved and rendered on target Android versions |
| 5 | PLAN and CARE migration/refactor | Sequences 2–4 | Existing data preserved; one owner per record/action; target acceptance tests pass |
| 6 | HEAL | Sequences 2–4; shared reminders/rewards for those integrations | Manual workflows work offline; source/provenance and privacy tests pass; wearable claims withheld until device proof |
| 7 | MINT | Sequences 2–4; shared reminders/rewards | Manual/import/reconciliation workflows work; financial privacy tests pass; live integrations separately approved and verified |
| 8 | HOME and LINK | Sequences 2–4; shared reminders/rewards | Domain ownership, cross-links, privacy-safe events, offline workflows, export/restore and opt-out tests pass |
| 9 | GEAR completion | Sequences 2–4 | Each shipped tool passes its own correctness, lifecycle, permission and accessibility checklist |
| 10 | WORLD completion | Sequence 3 plus stable app/data foundations | Shared ownership is canonical; deterministic expedition/reward tests pass; authored story/content validation passes |
| 11 | Launcher completion | Sequences 3–4; stable module navigation and Pokémon projections | Launcher lifecycle, widget behavior, gestures, fallback, performance and accessibility verified on physical Samsung hardware |
| 12 | Release hardening | All intended release workstreams | Full regression/migration suite, privacy/security, asset rights, accessibility, performance, backup/restore and device acceptance pass |

### Release gates

**Gate A — Architecture approval**
- All module records have one owner.
- Shared Pokémon, reminders, integration registry, and database services each have one authority.
- Test strategy, migration design, transaction/idempotency model, backup/restore, and rollback are documented.
- No module implementation starts on a competing store or shadow entity model.

**Gate B — Data migration**
- Representative real save fixtures migrate without loss or duplicate rewards.
- Legacy JSON remains untouched until import verification and rollback retention requirements pass.
- Process death, interrupted migration, low storage, corruption, and restore failure have tested recovery paths.
- Cutover establishes one authoritative database; indefinite dual writes are prohibited.

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
- Asset licensing/provenance is cleared for the intended distribution. The inspected baseline contains game-derived/private-use material; do not assume public redistribution rights.
- No cloud dependency, telemetry, data loss, unresolved P0/P1 defect, or unverified integration claim remains.

## 6. Workstream reuse policy

| Existing area | Direction |
|---|---|
| Pokémon Dex and curated data | Reuse after catalog/identity audit |
| Pokémon progression/reward logic | Refactor to canonical shared ownership and transactional settlement |
| PLAN domain | Reuse/refactor; preserve tested rules, rebuild boundaries/UI where needed |
| CARE domain/content | Refactor; preserve routines, reflection, visitor scenes and authored content while fixing persistence/ownership |
| WORLD expedition simulation | Reuse/refactor; validate balance and shared battle identity |
| WORLD story engine | Refactor and extend; preserve proven branch execution and separate campaign families |
| Compose components | Selective reuse against `FEATURES/DESIGN_SYSTEM.md` |
| Launcher | Re-architect/refactor around stable scene state and Android lifecycle |
| GEAR tools | Selective reuse and complete per-tool correctness |
| Supabase/cloud/social pathways | Retire from product after data migration/rollback gates |
| Legacy Android app | Reference only |

## 7. Engineering workflow

For each implementation change, record: user problem; authoritative owner/spec; current behavior and evidence; target acceptance criteria; data/migration impact; reward impact; permissions/privacy; UI/accessibility; tests/performance; and documentation updates.

Required sequence:
1. Inspect current implementation and data before editing.
2. Write or update domain and migration tests.
3. Implement domain rules before UI.
4. Use approved shared components and platform adapters.
5. Run targeted tests, full relevant tests, and build; record exact commands/results.
6. Inspect screenshots and real-device behavior where required.
7. Update the authoritative specification only when an approved requirement changes; update this guide when implementation evidence/status changes.
8. Review for duplicate owners, schedulers, reward systems, currency, Bag, shadow records, dead code, and hidden cloud dependencies.

A phase cannot close as “mostly done”. List unmet acceptance criteria and evidence.

## 8. Definitive documentation inventory

Canonical specification root: `docs/pokelife/` in the repository (originally `/PokéLife/docs/`). The active implementation backlog is `docs/pokelife/IMPLEMENTATION_BACKLOG.md`, a separate delivery artifact. `README.md` in the same folder is orientation only.

| Path | Authority | Status |
|---|---|---|
| `PRODUCT_BIBLE.md` | Product-wide principles, module boundaries and canonical document map | Canonical |
| `IMPLEMENTATION_GUIDE.md` | Baseline evidence, implementation status, dependencies, roadmap and release gates | Canonical |
| `MODULES/PLAN.md` | Planning, tasks, recurrence, focus and planning events | Canonical |
| `MODULES/CARE.md` | Self-care, reflection, routines, Care Play and visitors | Canonical |
| `MODULES/HEAL.md` | Physical health, activity, nutrition, sleep and health records | Canonical |
| `MODULES/MINT.md` | Finance records, budgets, bills, privacy and financial workflows | Canonical |
| `MODULES/HOME.md` | Household chores, inventory, assets, maintenance and reference | Canonical |
| `MODULES/LINK.md` | Contacts, relationship notes, dates and follow-ups | Canonical |
| `MODULES/GEAR.md` | Practical utilities and device tools | Canonical |
| `MODULES/WORLD.md` | Pokémon adventure, expeditions, campaigns and settlement | Canonical |
| `FEATURES/LAUNCHER.md` | Living home environment and optional Android launcher | Canonical |
| `FEATURES/POKEMON_ECOSYSTEM.md` | Shared Pokémon domain plus integrated expansion catalog, item acquisition, persistent held items, abilities, moves, gimmicks and WORLD activities | Canonical; expansion merged |
| `FEATURES/ASSETS_AND_AUDIO.md` | Asset pipeline, metadata, provenance, licensing, rendering and audio | Canonical |
| `FEATURES/DESIGN_SYSTEM.md` | Shared visual language, tokens, components and accessibility | Canonical |
| `ARCHITECTURE/DATA_AND_MIGRATIONS.md` | Persistence, IDs, transactions, backup/restore, migrations and recovery | Canonical |
| `ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md` | Android/provider integrations, permissions, privacy and shared reminder architecture | Canonical; reminders merged |
| `ARCHITECTURE/TEST_STRATEGY.md` | Cross-layer testing strategy | Canonical |

The 17 documents above are the complete canonical specification set. The former standalone `POKEMON_ECOSYSTEM_EXPANSION.md` is merged into `FEATURES/POKEMON_ECOSYSTEM.md`; the former `REMINDERS.md` is merged into Section 9 of `ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md`. Neither remains a parallel specification. `IMPLEMENTATION_BACKLOG.md` remains separate because it tracks executable work items, dependencies and delivery status rather than product rules.

### Reconciliation and archival policy

- The canonical `docs/` tree is the only current specification authority.
- Historical source copies and reconciliation artifacts are not current authorities; retain them only as archive evidence, not as editable specifications.
- Update the owning canonical document when a requirement changes, then update dependent cross-references and backlog items in the same change.
- Never infer implementation status from specification presence.

## 9. Owner decisions of 6 November 2026

These are recorded in their authoritative documents; they are listed here only so the plan can be read in one place.

1. **These docs are the source of truth** over the repository's older `docs/design.md`, `docs/ecosystem.md` and `docs/economy.md` (see `README.md`).
2. **Pokémon roles:** global buddy (main companion, cosmetic); a party of one lead plus five per module whose bonuses apply to that module only; WORLD expedition teams; GEAR helpers (visual only). `FEATURES/POKEMON_ECOSYSTEM.md` section 2.2. This replaces the old "a lead per region, no global buddy" decision. Backlog REP-009.
3. **Module map:** PLAN, CARE, HEAL, MINT, HOME, LINK, GEAR, WORLD. No Grow and no Explore module. `PRODUCT_BIBLE.md` section 4.
4. **Local-only has priority:** no new work on Supabase, accounts, sync or the server reward function; retire them after Gate B. `PRODUCT_BIBLE.md` section 3. Backlog REP-012.
5. **Android priority:** the only product platform; desktop and web are development conveniences. `PRODUCT_BIBLE.md` section 3; the database spike judges Android only. Backlog REP-006, REP-013.
6. **One currency, Poké:** PLAN coins and WORLD Poké merge 1:1. Backlog REP-010 (a `RewardRules.VERSION` bump and a re-run of both economy simulations).

## 10. Current unresolved issues

1. **Test execution evidence:** `ARCHITECTURE/TEST_STRATEGY.md` exists and is approved. Its stated baseline includes 89 Kotlin test source files, but this audit did not execute them or establish a passing test run. Refresh the baseline against the current branch.
2. **Architecture decision maturity (updated at 0.143.0: the database is live on Android, see section 3.2):** `DATA_AND_MIGRATIONS.md` is an approved target architecture. The SQLDelight feasibility spike (REP-006) passed on the JVM and compiles for Android, and five 0.123.0 save fixtures exist (REP-003); the `:data` module and the JSON importer now exist and are tested on the JVM (REP-008, `evidence/REP-008-data-foundation.md`); device checks, activation and cutover are still to do. No database architecture is used by the app yet. Gate A (ownership, transactions and idempotency, rollback) was reviewed in `evidence/REP-007-gate-a-review.md`; the device checks are deferred to a release by the owner, so the database stays off the app's live path until they run.
3. **Integration feasibility:** Huawei Watch Fit 4 Pro/Health Connect and Philippine bank/e-wallet integrations remain provider/device-dependent. No live integration is verified by the baseline audit.
4. **Asset distribution rights:** the baseline includes game-derived/private-use material. Public distribution requires an asset-by-asset rights review and replacement/permission where necessary.
5. **Expanded Pokémon implementation evidence:** the integrated expansion catalogue is approved target scope, not evidence of implementation. Verify actual Kotlin source areas, schemas and tests before assigning implementation status or exact file paths.
6. **Current branch status:** section 3.2 is current for version `0.143.0` on the branch that builds HOME (8 November 2026); refresh it against a named commit whenever work lands. The 6 November 2026 re-check above covers `2b22975d` only.
7. **Legacy duplicates:** root-level refresh and historical files remain outside the canonical project folder. They should be clearly archived or labelled historical after dependency/reference review.

## 11. Documentation change control

- Product decisions belong in the Product Bible or the relevant authoritative module/feature document.
- Shared technical contracts belong in the relevant Architecture document.
- Implementation evidence and roadmap belong here.
- Do not create a global decision register or duplicate feature specifications.
- Do not mark a requirement built, tested, or device-verified based solely on its specification.
