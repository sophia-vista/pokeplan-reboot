# PokéLife implementation backlog

**Status:** Active delivery plan. It tracks work, not product rules; behaviour belongs in the specifications.  
**Created:** 6 November 2026, to hold the items `IMPLEMENTATION_GUIDE.md` and the Product Bible refer to.  
**Baseline:** repository `main`, `0.123.0`.

Status uses the guide's vocabulary (Planned, Partial, Blocked, Done with evidence). Order follows the guide's sequence and release gates.

## Sequence 0: baseline

| ID | Item | Depends on | Status |
|---|---|---|---|
| REP-001 | Record the baseline build: commit, command (`./gradlew jvmTest compileKotlinWasmJs`), environment, result | none | Done: JVM tests and the wasm compile pass (816 tests, 0 failures); see the guide, section 3.1 |
| REP-002 | Map the existing test files to the module contracts they protect; find which screenshot tests compare images and which only assert drawing | REP-001 | Done as an inventory (`evidence/REP-002-test-inventory.md`): the screenshot test only asserts that something was drawn; `androidApp` has no tests |
| REP-003 | Inventory every persisted save shape (`AppData`, `CareStore`, widget access to `save.json`), cloud path and manifest permission; commit representative save fixtures | none | Partial: inventory done (`evidence/REP-003-baseline-inventory.md`); fixtures and device permission check still to do |
| REP-004 | Inspect live Actions storage (caches, artifacts, release assets) before any cleanup; the 3-day artifact retention in `ci.yml` is already short | none | Partial: the latest `main` run (206) has no artifacts, and the repository has about 30 versioned releases plus the rolling `latest`, each with an APK (release assets do not count toward Actions storage). Cache sizes cannot be read with the available tools: check Settings > Actions > Caches on GitHub before deleting anything |
| REP-005 | Signing: `androidApp/pokeplan-debug.keystore` is committed and shared by every build (documented in `CLAUDE.md`). Decide whether it is test-only; plan a protected release key and update continuity before any public release | none | Planned |

## Sequence 1 to 2: architecture and data

| ID | Item | Depends on | Status |
|---|---|---|---|
| REP-006 | SQLDelight/SQLite feasibility spike, **Android driver only** (desktop and web may keep a development store); migration runner and backup snapshot included | REP-003 | Planned |
| REP-007 | Gate A review: ownership contracts, transaction and idempotency model, rollback strategy | REP-006 | Planned |
| REP-008 | Local data foundation: repositories, migrations, backup/restore, recovery; one authority, no dual writes | REP-007 | Planned |

## Sequence 3: shared Pokémon and economy

| ID | Item | Depends on | Status |
|---|---|---|---|
| REP-009 | Pokémon roles: global buddy; module party of six (lead in slot 1); WORLD teams; GEAR helpers as visual only. Migrate PLAN's buddy and CARE's `careLeadId`; scope party perks to their own module | REP-008 | Planned |
| REP-010 | One currency, Poké: merge PLAN coins and WORLD Poké 1:1 with audit metadata; bump `RewardRules.VERSION`; update `docs/economy.md`; re-run `:plan-core:simulateEconomy` and `:world-core:simulateWorldEconomy` | REP-008 | Planned |
| REP-011 | Shared reminder scheduler and persistence (Integrations section 9) | REP-008 | Planned |

## Later (guide sequence 4 onward)

| ID | Item | Depends on | Status |
|---|---|---|---|
| REP-012 | Retire Supabase, accounts, sync, friends board and the server reward function (`supabase/`, `app-core/cloud`, `tools/server`, `deploy-reward.yml`) | Gate B: migration and rollback verified | Planned |
| REP-013 | Decide how long the desktop and web builds stay in CI (compile only, or drop) once REP-006 lands | REP-006 | Planned |
| REP-014 | Module map in the app: PLAN and CARE refactor first, then HEAL (manual first), MINT, HOME and LINK, GEAR completion, WORLD completion, launcher | Gates A to C | Planned |
