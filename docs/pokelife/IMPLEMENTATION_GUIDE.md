# PokéLife Implementation Guide

**Version:** 2.0  
**Status:** Baseline evidence, implementation status, dependency plan  
**Baseline audited:** `pokeplan-reboot-main (5).zip`, version `0.123.0`; re-checked against the repository (`main`, commit `2b22975d`) on 6 November 2026  
**Documentation reconciliation:** 6 November 2026; status re-audited against the code, module by module, at version `0.145.0` on 8 November 2026 (section 3.2)

> This guide distinguishes approved specifications from implementation evidence. The inspected baseline is a source archive, not a verified current working checkout. No successful full build/test run or target-device acceptance run was established by the baseline audit. Re-audit the actual implementation branch before treating any status as current.

## 1. Executive summary

PokéLife has an established Kotlin Multiplatform/Compose foundation and substantial existing PLAN, CARE, WORLD, GEAR, Pokémon, asset, and launcher implementation. The 0.123.0 baseline is not the complete product defined by the Product Bible. **Since then (0.124.0 to 0.145.0) the data foundation, the shared Pokémon and economy contracts, and the HEAL, MINT and HOME modules have been built on the JVM and compiled for Android; all eight modules now have a built slice (LINK manual-first, 0.145.0), and nothing has been verified on a device by this guide.** Section 3.2 is the current status; section 3's table is kept as the 0.123.0 baseline.

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

## 3.2 Current status at 0.148.0 (8 November 2026)

This section supersedes the status column of section 3, which stays as the 0.123.0 baseline. Status words follow section 2.2. It was rebuilt on 8 November 2026 from a **static audit of every module and feature against its specification** (seven read-only audits comparing each spec with the code; nothing was run on a device, and a few "unverified" cells say so). Evidence is the backlog row (`IMPLEMENTATION_BACKLOG.md`), the evidence file, the module's "As built" section and the tests named there. Nothing below is device-verified.

### Modules

| Area | Status at 0.148.0 | Evidence | Gaps (audit, 8 November 2026) |
|---|---|---|---|
| PLAN | **Largely built.** Own `plan_*` tables (0.133.0); Today, Inbox, Upcoming, calendar (read-only device events), lists, events, subtasks, calendar recurrence and frequency targets (Habit merged), focus sessions with Task and Focus eggs, goals with milestones, weekly review, per-task reminders, the streak reminder and the weekly recap on the shared scheduler (0.149.0), widgets (Today, Focus), HOME and LINK links | REP-014, `evidence/REP-014-plan-tables.md`, REP-011, `TaskLinksTest` | **Projects: missing.** Task priority (None/Low/Med/High, separate from the E-A rank) and an Archived state: missing. Recurrence scopes "this and future": missing (whole-definition edits only). Event reminders and a default timing: missing. Streak reminder and weekly recap still on `AppAlerts`, not the shared scheduler. Goal completion is inferred from milestones (spec: deliberate); goal status (on hold, archived) and description missing. Targets and CARE agenda entries are not in Today. Strict focus (leaving the app for 10 seconds fails the session) and the Super Candy per milestone were **decided by the owner on 8 November 2026 to stay**, and PLAN.md 6.3 and 9 now say so. Device acceptance (focus process death, reminders) not run |
| CARE | **Largely built.** Own `care_*` tables (0.132.0); routines, calm breathing, Diary Card, journal with prompts and search, trends, visitors (meadow of four, queue of six, persistent warmth), reunions, Care Play, fixed Meadow, egg inheritance with an origin note, routine reminders, widget | `evidence/REP-014-care-tables.md`, `CareStoreTest`, `CareExtrasTest`, `CareRemindersTest`, `CareTablesTest` | Routine Skip, the optional once-daily Diary reminder and quick calm/diary widget actions: missing. Journal edit has a store function but no screen; per-entry export and the "Reflect" and "History" destinations: missing. Care eggs hatch at once (owner decision, 8 November 2026) and now play a scene where the visitor leaves the egg first. A visitor's liked spot and berry are per species, not per individual (6.2); direct-recruit odds are not tied to visitor rarity (6.5). Only one calm tool (breathing). No Care-only privacy test; visitor process-death and clock tests not evident; Meadow animation does not stop when not visible. Device acceptance not run |
| HEAL | **Built, manual first** (0.136.0): activity, hydration, sleep, body measurements, optional goals, Field Research (on by default; rules version 19 now), hydration reminders, Heal's party, `heal_*` tables, **rows, backups and kept-aside copies sealed with a Keystore key (0.154.0)** | `MODULES/HEAL.md` section 22, `evidence/REP-014-heal.md`, `evidence/REP-014-heal-sealing.md`, `HealStoreTest`, `HealTablesTest`, `HealSealingTest` | Exercise library, workouts and templates; meals and nutrition; personal health records (medication, symptoms, appointments); imports (Health Connect, Huawei); History with charts and a configurable Today; measurement edit; medication, appointment and other reminders; selected-source deletion; device and accessibility acceptance; wearable claims withheld |
| MINT | **Built, manual first** (0.141.0, 0.142.0): accounts, transactions, budgets, bills, manual reconciliation, review, exact integer money, net position and available money, Ledger Research, rows and backups sealed with a Keystore key, passphrase-sealed portable backup, bill reminders (off until turned on), screen-lock gate, hide amounts | `MODULES/MINT.md` section 22, `evidence/REP-014-mint.md`, `MintStoreTest`, `MintTablesTest`, `PortableBackupTest`, `MintRemindersTest` | Splits, rollover, savings targets, foreign currency and rates, balance snapshots and freshness, statement and CSV import with duplicate detection, reconciliation sessions, merchant entity and reports (date range, cash flow, comparison, charts), provider integrations, bill-to-PLAN-task link, attachments, per-bill reminder timing, a Mint widget, the `budget_review` and `bill_review` event kinds. **Device acceptance of the Keystore path, the gate, the reminders and the portable backup** |
| HOME | **Built, manual first** (0.143.0) **plus links and reminders** (0.144.0): chores with deterministic schedules and no backlog, areas, supplies with exact quantities and the restocking list, belongings, maintenance, warranties, reference notes, search, Overview, Habitat Discovery, Home's party, `home_*` tables, reminders (off by default, per record), chore-to-PLAN-task link | `MODULES/HOME.md` section 16, `evidence/REP-014-home.md`, `evidence/REP-014-links-reminders.md`, `HomeRulesTest`, `HomeStoreTest`, `HomeTablesTest`, `HomeRemindersTest`, `TaskLinksTest` | Search filters (category, status, low stock, due) and the 8-result cap; attachments and photos; maintenance planned/cancelled state; service-provider link (needs LINK); PLAN shopping task for a supply; upkeep-milestone event; widgets and shortcuts; imports; encryption (no owner decision); the s14 acceptance boxes are all unticked although most are built; device acceptance |
| LINK | **Built, manual first** (0.145.0, by another session on `main`): people with notes and a directory, important dates (29 February observed on the 28th), follow-ups, search and duplicate suggestions; Postcard Trail on by default, paying only a finished follow-up (`RewardRules.VERSION` 20); Link Region released, Link's party, postcard tutorial; `link_*` tables (schema 10), in the backup; not sealed; not run on a device | `MODULES/LINK.md` section 15, `evidence/REP-014-link.md`, `TaskLinksTest` | Reminders, device-contact import, a UI for handing a follow-up to Plan (`convertFollowUp` exists; the 8 November audit found nothing calling it before this module), widgets; device acceptance |
| WORLD | **Partial; a functioning foundation with large content gaps.** Teams, presets, dungeons in six layers, deterministic offline settlement, finds and recruitment, rank and skills, 57 job definitions, facilities, Café, Shop, Swap Shop, Dojo, floor events (7 field moves, 4 hazards) and forks, 21 authored story episodes (the Mew opening and 5 each for Kanto, Johto, Hoenn, Sinnoh), the Guardian, widget and job alert. **0.145.0:** every member gets the full per-floor Pokémon XP, World holds no Poké balance (`pokeEarned`), "Send my strongest" picks the best team by type | CHANGELOG, REP-010, `docs/world.md` (status table rebuilt 8 November), `WorldStoreTest`, `WorldTest` | **Story: Rescue Team and Explorers of Sky have chapter title cards only, no scenes; Johto, Hoenn and Sinnoh have no branching; the other Mythical episodes, Unova to Paldea, the Ranger regions, other PMD settings, Orre and Hisui are not written; no mainline postgame.** The five destinations (Base, Atlas, Expeditions, Guild, Journal) are not the screen layout; (0.156.0: the screen settles on entry and at the next due floor, not by polling, and each expedition, job, research step, choice and treasure box settles under its own ledger id); Exploration XP and rank are still World-owned; unopened treasure is a World count; no battle-identity model (power is levels plus a 20% type bonus; **0.156.0: floors are cleared, a setback or a safe retreat by that power, and the Investigation, Challenge and Story job kinds exist**); notifications only for jobs; the widget settles when loaded (not read-only); no World migration fixtures (`worldVersion` 1). The expansion systems of POKEMON_ECOSYSTEM.md 5.5 (jobs as assignments, contests, Berry Garden, camps, raids, fossils) are not built. Device acceptance not run |
| GEAR | **Partial; broad.** All 18 tools are in the catalog and routed (Calculator, Everyday, Currency, Units, Timer, Stopwatch, World clock, Flashlight with SOS and screen light, Picker, Coin and dice, Compass, Level, Barometer, Device check, Waypoints, Sound meter, QR, Network); helpers are visual only and never auto-picked | `MODULES/GEAR.md`, `GearTest`, `docs/pokegear-audit.md` (0.105.0, stale) | **Fixed on 8 November 2026: the Network check is now "Check the connection" with a note that it is a reachability and delay check; a scanned QR code is named (web link, Wi-Fi, contact, ...) and warned about (not encrypted, an @ in the address, look-alike letters, a number address, a link shortener, a very long link, an open Wi-Fi, a text-message code) and never opened; loans are retired (owner decision): the Ranger request, `lendGearHelper`, `gearLoans` and the loaner helper are removed. Still open: timers and stopwatch use wall-clock time, not a monotonic source; no privacy leak test for gear data; QR payload creation for Wi-Fi and contacts.** Also: Currency history charts and presets, units (torque, density), compass true north and calibration, waypoint note and manual entry, Everyday gaps, Picker features unconfirmed, Device check lacks battery and storage. No device, TalkBack or font-scale evidence |
| Launcher | **Partial; a meaningful prototype.** Opt-in HOME role, a four-column widget grid with pages, folders, app drawer with categories, hide and pin, local-launch suggestions, living town with the buddy first, weather and forecast, Android widget hosting | `FEATURES/LAUNCHER.md`, `LauncherModelTest`, `WidgetGridTest` | **0.152.0 (not run on a device):** provider sizes and resize modes honoured on the grid, real dp in list mode (the pixel/dp mix-up fixed), a removed provider shows a placeholder, a leaked picker id is released, and search lists PokéLife's modules and places. Still open: provider configuration is only launched, not re-opened from edit mode; no cleanup of ids the host holds that no layout names (beyond the picker's); no swipe-up drawer or quick surface; no landmark navigation to modules; population is not by favourites or rotation and defaults to 24; positions are not persisted; no built-in module widgets in the launcher (Focus, Quick Tasks, World, Gear, ...); portrait only; no onboarding, reset or accessibility navigation mode; `QUERY_ALL_PACKAGES` policy not decided; launcher preferences are outside the backup; no androidApp lifecycle tests; Samsung acceptance not run |
| Shared Pokémon and economy | **Partial, advanced.** One place per Pokémon, module parties with leads and per-module perks, one currency Poké, shared Pokémon, Bag and ledger tables, module regions, arrival scenes, held items (one each, non-consumable, swap returns the item), Mints, Ability Capsule and Patch, Bottle Caps, vitamins, Tera Shards and `teraType`, evolution and trade items, form dex | REP-009, REP-010, `evidence/REP-014-shared-tables.md`, `docs/economy.md` (rules version 19) | **Moves and mastery: missing (no moveset on the owned record). Poké Ball identity, Ribbons and Marks, research and per-Pokémon history, original nature after a Mint, originModule/originEventId: missing.** Held-item catalog is 7 perk items plus stones (spec: 30 to 40 effects with source and rarity); 25 abilities have an effect and none apply in World. **Mega is half-built (a Mega Stone held by the buddy boosts Plan, but nothing shows a Mega form and no stone can be obtained); the Dynamax Band is never read; Z-moves missing; Tera has no Orb.** Jobs, contests, Pokéathlon, berries and camps, fossils, outbreaks, raids, Secret Bases: not built (name search and audit agree). **The place label on the detail page is fixed (8 November 2026: `Places.label` names every place, tested)**; party slots and PC cards still show no place. PC lacks origin, form and module filters; protection of shinies and unique forms is partial. |
| Data architecture | **Live on Android since 0.129.0**: SQLDelight/SQLite (`:data`), schema 10, every released schema committed (`databases/1..9.db`) with verified migrations, idempotent append-only ledger and outbox, daily file backup with move-aside restore, one authority (no dual writes). Plan, Care, Heal, Mint, Home, Link and the player's Pokémon, Bag and Poké are in their own tables; WORLD, GEAR and Launcher stay in the `save` row | REP-006, REP-007, REP-008, `evidence/REP-014-*`; the owner confirmed on the phone that the cutover kept all data | Typed repositories per module (only Pokémon, Economy and Reminder exist; the app reads one `AppData` through the split); the outbox is written and nothing consumes it; `pokemon_history` missing; the integrity check does not run on every open; **backup is a one-file JSON bundle with a checksum, not the specified archive** (no SQLite snapshot, attachments, per-file checksums, post-restore reconciliation or reminder rebuild); only Mint is encrypted in it; corruption recovery shows no explanation or salvage list; real kill and low-storage runs on a device not done. **Full-device erase fixed on 8 November 2026** (it now clears Heal, Mint and Home with the others, then the daily backups, the kept-aside copies, held reminders and the old `save.json` and `care.json`; the confirm text lists them; restoring a backup now reloads Home too). Not erased: the Keystore key (it holds no data), already scheduled alarms until the next reconcile, and a copy the player saved as a file |
| Integrations and privacy | Cloud, accounts, sync, friends board and the server reward function **retired** (0.130.0). Shared reminders: Plan (tasks, streak reminder, weekly recap), Care, Heal (hydration), Mint (bills), Home, Link (dates and follow-ups, 0.149.0), with a nightly refresh alarm, quiet hours, focus deferral, snooze and repeat in `reminder_hold`; read-only calendar; widgets for Today, Buddy, Focus, Care, World. Private module content never reaches the shared save, ledger or reward log (tested for Care, Heal, Mint, Home) | REP-011, REP-012, module tests | **Manifest audit not done: `allowBackup="true"` with no extraction rules (IP 10.1 and 4.3 forbid it); location, microphone, calendar, `QUERY_ALL_PACKAGES`, `REQUEST_DELETE_PACKAGES` and exact-alarm permissions unjustified in writing; legacy receivers still declared.** No `FLAG_SECURE` or recents protection. Notification privacy checked only for hydration and bill reminders. Streak reminder, weekly recap and Link reminders (Link is built; its reminders are not). Health Connect, Huawei, Mint provider and statement imports, LINK contacts: none exist. No widgets for Heal, Mint or Home and no widget lock-screen policy |
| Tests and CI | `./gradlew jvmTest compileKotlinWasmJs` passes at 0.145.0 (8 November 2026); about 1,200 `@Test` methods in about 120 test files (counted, not the runner's total); no Android unit or instrumented tests (`:androidApp:compileDebugKotlin` compiles) | `evidence/REP-002-test-inventory.md` (0.123.0 baseline) | **Screenshot tests assert that something was drawn, not that it matches; no lint, static analysis, dependency or security scan; the release workflow builds a debug APK signed with the committed key; no accessibility, journey, process-death or device suites** |
| Design system and assets | Design rules of 8 November 2026 applied (dialogue boxes, pixel-drawn controls, module accents match the spec table, fewer words; 0.137.0, 0.138.0); real game assets only | `FEATURES/DESIGN_SYSTEM.md`, CHANGELOG | Colour tokens do not carry the spec's names (`textTertiary`, `focus`, `*Container`, `disabled*` absent); rounded shapes remain in the launcher, `AppBackdrop`, folders and `PixelTheme` (`Spacing.CardRadius` 16 dp; `PixelSlider` wraps Material); nine files still use literal colours (mostly art). **REP-015: no asset registry, no rights status or release gate, no app-wide credits screen (only per-Pokémon artist credits); audio has effect and cry sliders but no music; REP-016 and REP-017 not auditioned** |

### Sequence and gates

| Sequence | Status |
|---|---|
| 0 Baseline | Done (REP-001 to REP-005). Signing remains test-grade and must move to a secret before any public release |
| 1 Architecture and test strategy | Done (REP-007, Gate A passed; `TEST_STRATEGY.md` approved) |
| 2 Local data foundation | Built and live on Android (REP-008, schema 10); **Gate B is partial**: fixtures migrate and the owner confirmed the cutover on the phone, but kill, low-storage and corruption recovery on a device are not run, and the backup archive and full-device erase are incomplete (see the data row) |
| 3 Shared Pokémon, economy and reminders | Built for the contracts above; shared reminders are used by Plan (tasks, streak reminder and weekly recap), Care, Heal, Mint, Home and Link (0.149.0). **Gate C is partial**: ownership, one Bag and one Poké balance and privacy-safe events hold in tests; the reminders have not run on a device |
| 3B Expanded Pokémon foundations | Partial: held items, Mints, capsules, training items, Tera and trade items exist; moves, Balls, Ribbons, history and the WORLD expansion systems do not |
| 4 Design system and app shell | Partial; tokens and components applied, device rendering not reviewed; rounded shapes remain in the launcher |
| 5 PLAN and CARE refactor | Done on the data side (own tables, one owner per record). Open on behaviour: the PLAN and CARE gaps in the table, including two spec conflicts for the owner |
| 6 HEAL | Manual slice built; workouts, nutrition, records, imports and History not built |
| 7 MINT | Manual slice built; import, providers, splits, rollover, savings and foreign currency not built |
| 8 HOME and LINK | **HOME built (0.143.0, links and reminders 0.144.0); LINK manual slice built (0.145.0)** |
| 9 GEAR | Broad tool set present; spec violations (Network label, QR safety, monotonic time, loans) and unaudited UX; not accepted |
| 10 WORLD | Foundation and 21 story episodes present; story and content, the five destinations, shared Exploration XP and per-reward ledger ids, and the battle model remain (see the World row) |
| 11 Launcher | Prototype; host robustness, search, population and widgets remain |
| 12 Release hardening | Not started (asset registry, manifest and backup policy, release key, CI hardening, device suites) |
| Gate D (module acceptance) | No module passes in full: each lacks device, accessibility and process-death evidence |
| Gate E (device and release acceptance) | Not started |

### Alignment with the Product Bible

| Bible rule | Status in the code |
|---|---|
| Module map (section 4): eight modules, no Grow, no Explore | Holds. PLAN, CARE, HEAL, MINT, HOME, LINK, WORLD and GEAR have screens |
| One owner, one source of truth (principle 2) | Holds for the built modules: each has its own tables and store and pays only through a minimal event. World's Poké copy is gone (0.145.0). Open: Exploration XP and unopened treasure in World; the app's single `AppData` view over the split tables |
| Utility first; every module works without rewards (principle 1) | Holds: Field Research, Ledger Research and Habitat Discovery can be turned off in Settings without losing a record (tested) |
| Forgiving, no punishment (principles 4 and 9) | Holds by design in Home (no backlog, no streak, no shaming words) and Care (no streaks). Focus is strict by the owner's decision (8 November 2026, PLAN.md 6.3), an accepted exception to this principle; the Plan streak text has not been checked against the no-pressure rules |
| Local-first, no account, no cloud, no telemetry (principle 6, section 3) | Holds since 0.130.0, except that `allowBackup="true"` leaves the app data open to the system's cloud backup until the manifest is fixed |
| Android only (section 3) | Holds; desktop and web stay compile-only |
| Cross-module links (section 4): HOME owns a chore a PLAN task is linked to; LINK owns a follow-up until converted; MINT owns bill state | **Partly built (0.144.0).** HOME chore to PLAN task is built (HOME completes and pays once, PLAN pays nothing, tested) and PLAN's half of a LINK follow-up conversion; LINK itself and MINT bill links are not built |
| Roles (section 5): global buddy, module party of six, WORLD teams, GEAR helpers; one Pokémon in one place | Holds (0.124.0, 0.133.0); every module's party exists; the detail page names every place (8 November 2026) |
| One Bag and one currency Poké | Holds (0.125.0, 0.135.0, 0.145.0) |
| Held items persistent and non-consumable | Holds for the existing held items (one each, swap returns the old item, nothing consumed) |
| Shiny and egg luck and party bonuses (POKEMON_ECOSYSTEM.md 2.2.1) | Owner decision 8 November 2026: the Shiny hunter and Egg luck buckets and the Mega boost stay (capped, shown); the spec now says so |
| Private content never enters WORLD or reward presentation (sections 3 and 8) | Holds and is tested for Care, Heal, Mint and Home; not tested for Gear data |
| Permissions just in time; honest integrations (section 8) | Holds for what is built; no provider integration exists and Settings says so; the manifest still declares permissions nobody has justified |
| Definition of done (section 9) | **Not met by any module**: each is built, tested on the JVM and compiled for Android, but has no device verification, accessibility review or process-death evidence |

### Owner decisions of 8 November 2026 (on the audit's conflicts)

| # | Question | Decision | Effect |
|---|---|---|---|
| 1 | Focus interruptions | **Strict stays** | PLAN.md 6.3 amended; no gentle mode |
| 2 | Milestone Super Candy | **Stays as it is** | PLAN.md 9 amended; goal completion is still inferred, to be made deliberate |
| 3 | Shiny and egg luck perks, EV and IV model | **Perks stay** | POKEMON_ECOSYSTEM.md 2.2.1 amended; the EV and IV model was not raised again and stays as built |
| 4 | Care eggs hatch at once, or enter the shared Egg collection | **Hatch at once, with a scene where the visitor leaves the egg** | Built 8 November 2026 (`VisitorLeavesEggScene`); CARE.md 6.6 amended |
| 5 | Mega and Dynamax | **Integrate them into PokéLife** | Roadmap item 9: a source for Mega Stones, the Mega form shown, the Dynamax Band given a use |
| 6 | HEAL encryption | **Put in the roadmap** | **Built in 0.154.0** (`evidence/REP-014-heal-sealing.md`): `heal_*` rows, Heal's backup copies and kept-aside copies are sealed with Heal's own Keystore key; a passphrase backup seals it with Mint. Not run on a device |
| 7 | Gear loans | **Retire** | Done 8 November 2026 |
| 8 | Manifest (`allowBackup`, permissions) | **OK for now** | Left as it is; recorded in pending item 10 so it is revisited before any public release |

### Pending items (ordered by value)

1. **Quick fixes: done on 8 November 2026** (the place label, the complete full-device erase and the Home reload after a restore, the Network button, the QR safety reading, Gear loans retired). The rest of the stale docs listed under "Documentation drift" below remain.
2. **Device acceptance for everything since 0.129.0**: the database cutover recovery cases, Mint's Keystore key, access gate, bill reminders and portable backup, the module tables, arrival scenes, reminders, and a visual review of every new screen in both palettes and at 130% text. Closes Gates B and C and starts D.
3. **WORLD content and structure**: scenes for Rescue Team and Explorers of Sky (separate authored campaigns), branching in Johto, Hoenn and Sinnoh, the other Mythical episodes, then Unova onward and the Ranger regions; (settle-on-entry, the ledger id per reward and the five destinations are done, 0.156.0); Exploration XP and rank as the shared record; the battle-identity model (setbacks, retreats and the three job kinds are done, 0.156.0).
4. **PLAN completion**: Projects, priority, Archived state, recurrence scopes, event reminders, deliberate goal completion, targets in Today (the streak reminder and weekly recap are on the shared scheduler since 0.149.0).
5. **LINK** (sequence 8): phases 1 to 5 and 7 of `MODULES/LINK.md` are built (0.145.0); reminders (phase 5's second half), device-contact import (phase 6) and a UI for handing a follow-up to Plan remain; it also unblocks Home's service-provider link.
6. **CARE completion**: routine Skip, Diary reminder, journal edit and export, Reflect destination, per-visitor individuality, the egg decision, privacy and process-death tests.
7. **Typed repositories per module**, the outbox consumer and the removal of the single-`AppData` view; the backup archive (DATA_AND_MIGRATIONS.md 10.2 to 10.4) with whole-bundle encryption and post-restore reconciliation. Started (0.144.0): HEAL, MINT and HOME rewards settle their real event ids in the ledger.
8. **Open module scope**: HEAL (**sealing `heal_*` rows and backups: done in 0.154.0, owner decision 6**; workouts, nutrition, records, imports, History), MINT (import, providers, splits, rollover, savings, foreign currency, reports), HOME (search filters, attachments, widgets, imports), GEAR (see its row), Launcher (see its row).
9. **Shared Pokémon expansion** (P2): **Mega and Dynamax integrated into PokéLife (owner decision 5)**: a source for Mega Stones (a shop or quest, to be designed), the Mega form shown for a focus session, the Dynamax Band given a use; then the owned-record provenance layer (original nature, Ball, moveset, Ribbons and Marks, history) with a migration, then moves and mastery, a larger held-item catalog, then the WORLD systems that depend on them (jobs, contests, Berry Garden, camps, raids).
10. **Release hardening** (sequence 12): the manifest (`allowBackup` and the declared permissions were left as they are for now, owner decision 8), the asset registry, rights status and credits screen (REP-015), sound clean-ups (REP-016, REP-017), the protected release key (REP-005), real screenshot comparison, an androidApp test source set, lint and static analysis, a release-variant compile, and a device suite.

### Documentation drift found by the audit

Fixed on 8 November 2026 in this change: `docs/world.md` (status table), `WORLD.md` 10.2, `Story.kt`, this section, and two lines of `CLAUDE.md` (plan-core and the `data/` "Next"). Still stale and listed so they are not mistaken for current: `CLAUDE.md` (habits, the reminder list omits Heal, Mint and Home, widgets described as Glance, the Care screens file list); `docs/design.md` (habits as Today items, strict-mode and milestone text, retired server and egg-ticket paragraphs, Mega/Dynamax claims at 176); `docs/ecosystem.md` (Care cloud sync, superseded visitor and buddy sections); `HOME.md` 16 status line; `HEAL.md` 322 and 496 against the on-by-default rule at 532; `evidence/REP-014-heal.md` (Field Research "opt-in"), `REP-014-mint.md` (gate and portable backup listed as later), `REP-014-care-tables.md` (Care items "not built"), `REP-014-home.md` (reminders and PLAN link "not started"); `DATA_AND_MIGRATIONS.md` 10.5.1 (documents list), `INTEGRATIONS_AND_PRIVACY.md` manifest sections (an open gap, not a done state); `TEST_STRATEGY.md` and `REP-002` file counts (0.123.0 baseline); `docs/pokegear-audit.md` (0.105.0); `GEAR.md` (Choose and Measure grouping, Pokégear name); the `MODULES/*.md` acceptance boxes, which are all unticked and carry no built/partial status.

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
2. **Architecture decision maturity (updated at 0.145.0: the database is live on Android, see section 3.2):** `DATA_AND_MIGRATIONS.md` is an approved target architecture. The SQLDelight feasibility spike (REP-006) passed on the JVM and compiles for Android, and five 0.123.0 save fixtures exist (REP-003); the `:data` module and the JSON importer now exist and are tested on the JVM (REP-008, `evidence/REP-008-data-foundation.md`); device checks, activation and cutover are still to do. No database architecture is used by the app yet. Gate A (ownership, transactions and idempotency, rollback) was reviewed in `evidence/REP-007-gate-a-review.md`; the device checks are deferred to a release by the owner, so the database stays off the app's live path until they run.
3. **Integration feasibility:** Huawei Watch Fit 4 Pro/Health Connect and Philippine bank/e-wallet integrations remain provider/device-dependent. No live integration is verified by the baseline audit.
4. **Asset distribution rights:** the baseline includes game-derived/private-use material. Public distribution requires an asset-by-asset rights review and replacement/permission where necessary.
5. **Expanded Pokémon implementation evidence:** the integrated expansion catalogue is approved target scope, not evidence of implementation. Verify actual Kotlin source areas, schemas and tests before assigning implementation status or exact file paths.
6. **Current branch status:** section 3.2 is current for version `0.148.0` on the branch `ccr-52919690-38g1t1` (merged with `main` at 0.147.0) (8 November 2026, audited module by module); refresh it against a named commit whenever work lands. The 6 November 2026 re-check above covers `2b22975d` only.
7. **Legacy duplicates:** root-level refresh and historical files remain outside the canonical project folder. They should be clearly archived or labelled historical after dependency/reference review.

## 11. Documentation change control

- Product decisions belong in the Product Bible or the relevant authoritative module/feature document.
- Shared technical contracts belong in the relevant Architecture document.
- Implementation evidence and roadmap belong here.
- Do not create a global decision register or duplicate feature specifications.
- Do not mark a requirement built, tested, or device-verified based solely on its specification.
