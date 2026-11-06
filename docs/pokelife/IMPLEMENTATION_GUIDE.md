# PokéLife Implementation Guide

**Version:** 1.9  
**Status:** Baseline evidence, implementation status, dependency plan  
**Baseline audited:** `pokeplan-reboot-main (5).zip`, version `0.123.0`; re-checked against the repository (`main`, commit `2b22975d`) on 6 November 2026  
**Documentation reconciliation:** 6 November 2026

> This guide distinguishes approved specifications from implementation evidence. The inspected baseline is a source archive, not a verified current working checkout. No successful full build/test run or target-device acceptance run was established by the baseline audit. Re-audit the actual implementation branch before treating any status as current.

## 1. Executive summary

PokéLife has an established Kotlin Multiplatform/Compose foundation and substantial existing PLAN, CARE, WORLD, GEAR, Pokémon, asset, and launcher implementation. The 0.123.0 baseline is not the complete product defined by the Product Bible.

The primary engineering risk is not a lack of feature specifications. It is the gap between approved modular ownership and the older JSON/store/cloud pathways. The implementation sequence must establish the local data architecture, migration safety, and shared services before broad module expansion.

No feature is marked device-verified by this guide. No full build or test execution is claimed.

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
| CARE | Routines, calm breaks, Diary Card, skills/DBT, journal/trends, reminders, visitors, Care Eggs/rewards | Partial; substantial foundation to refactor | Target persistence/lifecycle, all acceptance criteria, device acceptance |
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
2. **Architecture decision maturity:** `DATA_AND_MIGRATIONS.md` is labelled a proposed target architecture and still calls for SQLDelight feasibility, source fixtures, and migration acceptance work. It is not evidence of an implemented database architecture.
3. **Integration feasibility:** Huawei Watch Fit 4 Pro/Health Connect and Philippine bank/e-wallet integrations remain provider/device-dependent. No live integration is verified by the baseline audit.
4. **Asset distribution rights:** the baseline includes game-derived/private-use material. Public distribution requires an asset-by-asset rights review and replacement/permission where necessary.
5. **Expanded Pokémon implementation evidence:** the integrated expansion catalogue is approved target scope, not evidence of implementation. Verify actual Kotlin source areas, schemas and tests before assigning implementation status or exact file paths.
6. **Current branch status:** refresh the status table against a named commit whenever work lands; the 6 November 2026 re-check above covers `2b22975d` only.
7. **Legacy duplicates:** root-level refresh and historical files remain outside the canonical project folder. They should be clearly archived or labelled historical after dependency/reference review.

## 11. Documentation change control

- Product decisions belong in the Product Bible or the relevant authoritative module/feature document.
- Shared technical contracts belong in the relevant Architecture document.
- Implementation evidence and roadmap belong here.
- Do not create a global decision register or duplicate feature specifications.
- Do not mark a requirement built, tested, or device-verified based solely on its specification.
