# PokéLife Implementation Guide

**Status:** Implementation status, dependency plan and release gates. Product rules live in the Product Bible and the module and feature specifications, not here.

> This guide separates approved specifications from implementation evidence. Nothing in it is device-verified except the owner's confirmation on the phone that the database cutover kept all data.

## 1. Executive summary

PokéLife has a Kotlin Multiplatform/Compose foundation. All eight modules have a built slice (HEAL, MINT, BASE and LINK manual-first), the data foundation is live on Android, and the shared Pokémon and economy contracts hold in tests. Everything is tested on the JVM and compiled for Android; **nothing is device-verified**, so no module meets the Definition of Done (Bible section 9). Section 3.2 is the current status.

The main engineering risk is not a lack of specifications. It is the gap between approved modular ownership and the remaining single-`AppData` view over the split tables, plus the missing device evidence.

## 2. Evidence and status vocabulary

### 2.1 Evidence boundary

A source file or test file is evidence of code and test presence only, not that tests passed or that a workflow works. Not established anywhere: end-to-end acceptance of every workflow, live Huawei Health, bank or e-wallet integrations, a complete device review of visual quality, and the wasm tests (compile only). The JVM suite is the merge gate (`./gradlew jvmTest compileKotlinWasmJs`).

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

## 3. Implementation status

### 3.1 Engineering operations

- `ci.yml` keeps its artifact for 3 days and cancels superseded runs; `release-apk.yml` publishes APKs through GitHub Releases (a moving `latest` plus versioned releases). Release assets do not count toward Actions storage. Gradle caching comes from `gradle/actions/setup-gradle`; there is no standalone `actions/cache` step. Inspect the live Actions storage breakdown before deleting any cache or release asset (REP-004).
- **Signing:** `androidApp/pokeplan-debug.keystore` is committed on purpose and shared by every build. It is test-grade: fine for personal use while the repository is private (REP-047 drops the release-key work).

### 3.2 Current status

Status words follow section 2.2. Nothing was run on a device; the only Android-side evidence is the Robolectric suite (REP-033). The reward numbers are `RewardRules.VERSION` and `docs/economy.md`; the schema is the highest `databases/N.db`. Where a row and a module file differ, the module file and the code win.

| Area | Built | Gaps (backlog) |
|---|---|---|
| PLAN | **Largely built.** Own `plan_*` tables. A page with no menu (lead, today's open tasks, quick actions, party); Tasks, Calendar, Focus, Goals and Settings under the header's Sections key. One Tasks workspace (Today, Upcoming, All, Done); lists of three kinds (Tasks, Checklist with a reset, Tracker), no categories; a reminder with no date; day keys with a date picker, typed times, reminder offsets, repeat every N weeks, on weekdays, on a monthly day or yearly; a task's reward focus (Exp, Egg or Items); event companions that earn exp; focus with Task and Focus eggs; goals and milestones with a Gym badge each and a Hall of Fame; streak reminder; widgets; BASE and LINK links | **REP-030, REP-035, REP-057, REP-058:** manual order and bulk actions, the Archived task state, "this and future" for repeating tasks, event reminders, goal status, projects, task statistics, tags and saved recurrence presets, a logged history for unticked checklist items; focus allow list, ongoing notification, break cycle |
| CARE | **Largely built.** Own `care_*` tables. Four places from the Sections key: Today (routines with Skip, "2 of 5 today", a Yesterday tab; the mood key; quick actions), Practice (the routines, and the calm break with the DBT skills as guided walk-throughs, under one pill), Reflect (the Diary Card and the journal, both editable), Dream (a dusk scene, Care Play, the visiting dreamers, reunions); trends, egg inheritance, routine reminders, widget. Care Play fills no Egg meter | **REP-030, REP-036:** the once-daily Diary reminder, a readable journal export, a quick mood widget, a visitor's notebook, a body scan and grounding tool. Journal and diary rows are not sealed (REP-021) |
| HEAL | **Built, import first, with manual entry behind it:** Activity (exercise and sleep), Wellness (water, body measurements, readings) and Records (the medication and supplement log with an optional dose log and refill reminders) as grouped places, goals, Field Research, hydration reminders, Heal's party, `heal_*` tables with sealed rows and backups. Health Connect read-only; **Huawei Health reader** (screen reading for that app only, off after each read; step notification) saves workouts, weight, body fat, sleep, steps, heart rate, SpO2, skin temperature, stress and sleep score after a preview; nightly or weekly read reminder | **REP-040:** exercise library, workouts and templates, history charts, symptoms and appointments, nutrition, the Records timeline and illnesses. Huawei: the Week tab is not parsed, resting heart rate awaits a capture, watch actions unchecked. Wearable claims withheld |
| MINT | **Built, manual first, past the first slice:** accounts (groups, arrange, hide from total, card limit and days, card colour, open the wallet's app, a receive-money QR image), transactions (splits, one receipt, transfer fee), budgets and savings targets, bills and recurring income (paydays and holidays, estimated income) under one Plans place, pay-later and credit accounts with installment plans and one monthly bill per account, statement records and an estimated-interest line, interest earned, the credit card flow, reconciliation, review, exact integer money, Ledger Research, a flat reward for logging and outbreaks, IOUs as income someone owes back (a flagged income that lowers spending in its category), Overview counts settings, two Glance widgets, sealed rows and backups, passphrase-sealed portable backup, bill reminders (off until turned on), screen-lock gate, hide amounts, statement CSV import | **REP-039:** rollover, refunds, charts, more attachments and several receipts, custom recurrence, spotting subscriptions, a quick-add widget, foreign currency, the open pairs of MINT.md section 22; IOUs stay one expense at a time: no who-owes-whom balance, no splitting by share between several people and no settle-up (REP-073); provider integrations. **Device acceptance of the Keystore path, the gate, the reminders, the widgets and the portable backup** |
| BASE | **Built, manual first, with links, reminders and the Secret Base room:** chores with deterministic schedules, areas (rename, archive, move), supplies and the restocking list, belongings, maintenance (a name is enough), warranties, reference notes, search, Overview, Habitat Discovery, Base's party, `home_*` tables, per-record reminders (off by default; "Remind me" always offered), chore-to-PLAN-task link, example chips on first use. **Room** (`home_room`): Base's party in a Hoenn room of the lead's type, 42 decorations on 44 squares, earned through a Decoration meter | **REP-029, REP-041:** a freshness line, starter chores, expiry dates, photos and attachments, search filters and the 8-result cap, a PLAN shopping task for a supply, a service-provider link to LINK, widgets, imports. Berry plots are next (REP-053). No encryption decision. |
| LINK | **Built, manual first; the archive direction is specified (owner, 25 November 2026): primary places People, Memories, Reminders.** Built: people with notes, a main category and any number of groups, and a directory (search by name, phone, email, address or slambook word; sort by name, recent, My order or by group), important dates with a reminder chosen in the same step, follow-ups handed to Plan and back, duplicate suggestions, reminders, Postcard Trail, Link's party; contacts import (Android; `READ_CONTACTS` asked only after an explanation); a slambook profile and a "Me" card with a Western natal chart, BaZi, Kua and Life Path computed on the phone; `link_*` tables, in the backup, not sealed | **REP-081 to REP-089 (specified, not built):** Memories and Eras with photo-first entries and timeline/gallery/chapters browsing; local photo galleries; OneDrive photo source; person connections and a profile reorganisation; slambook question sets per category; per-person Pokémon teams (needs the ecosystem and economy spec); one Reminders destination with last meetup and widgets; PLAN event–memory links; unified search. **REP-043:** a chooser for changed fields on re-import, upcoming dates on the launcher (REP-022), sealing, D8; widgets. **REP-072:** Friend Safari is built (0.225.0; `SafariRules`, `GameStore.safari`); the owner is to confirm the 5-a-day total cap |
| WORLD | **Partial; a functioning foundation, content broad.** Teams, presets, a recommended team for the chosen dungeon, deterministic offline settlement, finds and recruitment, rank and skills, 75 jobs, facilities, Café, Kecleon Shop, Swap Shop, Dojo, floor events and forks, setbacks and retreats, Investigation, Challenge and Story jobs, the four destinations (Explore with the Atlas and the Guild, Expeditions, Town, Journal), battle identity (moves count), the Guardian, widget and job alert; two Mystery Dungeon arcs (Rescue Team, Explorers of Sky) and ten region layers (Kanto to Hisui) with over a hundred dungeons and over seventy authored story episodes (counted in the source); Evolution Trials | **REP-037:** branching in Johto, Hoenn and Sinnoh; the other Mythical episodes; Orre and the Ranger regions; a mainline postgame; Exploration XP and rank as a shared record; Atlas settlements and routes; Journal records; Storage and Duskull Bank; story and find notifications; a read-only widget; music. The six newest layers have no map picture (REP-051). The expansion systems of POKEMON_ECOSYSTEM.md 20.3 (contests, camps, raids, fossils) are not built |
| GEAR | **Partial; broad.** All 18 tools in the catalog and routed; helpers visual only and reacting to real actions only; QR payloads are read and warned about, never opened. "As built" in GEAR.md | **REP-028, REP-042:** decide which tools to drop or merge; Everyday sums are half of GEAR.md 5.2; Timer presets and a World clock with search and 12-hour time; timers use wall-clock time; every `changeGear` writes the whole save; no document scanner. No device, TalkBack or font-scale evidence |
| Launcher | **Partial; a meaningful prototype.** Opt-in HOME role, widget grid with pages and resize modes, folders, app drawer, search that lists PokéLife's own places, weather. The town is a Mystery Dungeon floor, a new one each day, with Pokémon walking paths, three daily finds, hidden cosmetic traps, a Guardian on the stairs, species quirks, gesture settings and a confirmed reset | **REP-045, REP-052:** built-in module widgets, landmark navigation, saved positions, provider reconfiguration and orphan id cleanup, launcher settings in the backup, onboarding; Red Rescue Team full tile sets; walking cost with 24 Pokémon unchecked on a device; Link dates on the home page (REP-022) |
| Shared Pokémon and economy | **Partial, advanced.** One place per Pokémon (the PC lists only what rests in it), module parties with per-module perks, one currency Poké, shared Pokémon, Bag and ledger tables, held items, Mints, capsules, vitamins, Tera Shards, trade and evolution items, a moveset of up to four moves (read by WORLD), a way into every special evolution, a Poké Mart, themed item drops from every life module, outbreaks for MINT, a Mega Stone source and the Mega form in the form dex | **REP-044:** Poké Ball identity, Ribbons and Marks, per-Pokémon history, original nature after a Mint, origin module and event; the Dynamax Band never read, Z-moves, no Tera Orb. **REP-068, REP-069:** the other modules' everyday actions are not yet levelled with a Plan focus egg, and outbreaks exist for MINT only. The economy simulation lacks module drops, the shop and the Egg and Items reward focus (REP-051). Berry plots next (REP-053); contests, camps, fossils and raids not built |
| Data architecture | **Live on Android:** SQLDelight/SQLite (`:data`), every released schema committed with verified migrations, idempotent append-only ledger and outbox, daily file backup with move-aside restore, an automatic passphrase-sealed backup into a folder the player picks, one authority. A corrupt database with no backup asks "Use the older data" or "Start fresh". Plan, Care, Heal, Mint, Base, Link and the player's Pokémon, Bag and Poké are in their own tables; WORLD, GEAR and the launcher stay in the `save` row | **REP-020:** typed repositories per module (the app reads one `AppData` through the split) and retiring the JSON documents. **REP-018:** `GameStore` is still about 2,000 lines. The outbox is written and nothing consumes it; `pokemon_history` missing. **REP-034:** the off-phone backup is not run on a device and does not yet seal Care, Base and Link. Backup is a one-file bundle with a checksum, not the archive of DATA_AND_MIGRATIONS.md 10.2; real kill and low-storage runs on a device not done |
| Integrations and privacy | Shared reminders for Plan, Care, Heal, Mint, Base and Link, with a nightly refresh alarm, quiet hours, focus deferral, snooze and repeat; a reminder due while the phone was off is shown; read-only calendar; Health Connect, Huawei Health, statement import and the phone's contacts; widgets for Today, Buddy, Focus, Care, World and Mint. System backup and device transfer closed. Private module content never reaches the shared save, ledger or reward log (tested for Care, Heal, Mint, Base). One screen-lock switch for Care, Heal, Base, Link and Mint; one reminders switch per module; Reset clears every module's settings and cancels alarms | The manifest declares `QUERY_ALL_PACKAGES`, `REQUEST_DELETE_PACKAGES`, exact-alarm permissions, `EXPAND_STATUS_BAR` (pull-down-notifications gesture) and `WRITE_SECURE_SETTINGS` (granted over adb, for the Huawei reader's one-read switch), some without a written reason (store-only items dropped, REP-047). The Huawei listener's battery cost is unconfirmed on the phone (REP-055). No `FLAG_SECURE` or recents protection; no `BiometricPrompt` in front of the private modules (REP-022). Notification privacy checked only for hydration and bill reminders. No widgets for Heal or Base |
| Tests and CI | `./gradlew jvmTest compileKotlinWasmJs` is the merge gate. **`androidApp` has a Robolectric suite (REP-033), run by CI's Android step:** `LiveStore` (fixture cutover, process death, corrupt and newer databases, sealing, fail closed), the shared reminders and their receivers, the Today widget, focus across process death | **Screenshot tests still assert that something was drawn, not that it matches.** Open in REP-033: the other modules' reminder notifications, the widgets' Glance rendering, a kill during a write, stored-PNG comparison in both palettes, a TalkBack semantics check. No lint, static analysis or dependency scan; the release workflow builds a debug APK signed with the committed key. No instrumented, journey or accessibility suites |
| Design system and assets | Design rules applied (one accent per module, one `ModulePage`, one date, time and amount field, the form kit, dropdowns, a Sections key and one-row header, one order on every module's main screen); Hoenn room art and Gym badges bundled; real game assets only; Settings → About → Credits lists the art, sound and font sources | **REP-015: no asset registry, rights status or release gate, no per-asset credits** (only per-Pokémon artist credits and the by-source list). Audio has effect and cry sliders and no music; 52 sound clips committed, none auditioned (REP-017), REP-016 planned. About 290 one-off dp values remain (REP-023). The 12-hour clock and date format reach only the launcher (REP-073) |
| Me and Settings | **Built, not run on a device.** Me: trainer card, Stats (opening Plan progress and Care trends), Settings and Help. Settings is a menu of pages: General, Privacy and access, Notifications, Backups (complete backup, module exports, Reset), About | **REP-073:** the 12-hour clock and date format across the app; a reminders switch for Heal |


### Sequence and gate status

The workstreams and gates are defined in section 5.

| Sequence | Status |
|---|---|
| 0 to 1 | Done; Gate A passed |
| 2 Local data foundation | Built and live on Android. **Gate B is partial**: fixtures migrate and the owner confirmed the cutover on the phone; Robolectric covers process death and corruption recovery, but kill, low-storage and recovery on a device are not run, and the backup archive is incomplete |
| 3 Shared Pokémon, economy and reminders | Built. **Gate C is partial**: ownership, one Bag and one Poké balance and privacy-safe events hold in tests; Plan reminders are covered by Robolectric, the other modules' notifications and a device run are not |
| 3B Expanded Pokémon foundations | Partial: held items, Mints, capsules, training items, Tera, trade items, the moveset, Mega Stones and the Mega form in the form dex exist; Balls, Ribbons, history, the Dynamax Band and the WORLD expansion systems do not |
| 4 Design system and app shell | Partial; tokens and components applied, device rendering not reviewed |
| 5 PLAN and CARE refactor | Done on the data side; open on behaviour (REP-030, REP-035, REP-036) |
| 6 HEAL, 7 MINT, 8 BASE and LINK | Manual slices built, with imports for Heal, Mint and Link; the gaps above remain |
| 9 GEAR | Broad tool set; UX decisions open (REP-028); not accepted |
| 10 WORLD | Foundation and broad content present; branching, shared Exploration XP and the remaining expansion systems remain |
| 11 Launcher | Prototype with a dungeon-floor town; host robustness, module widgets and navigation remain |
| 12 Release hardening | Not started (asset registry, manifest and backup policy, CI hardening, device suites) |
| Gate D, Gate E | D: no module passes in full (each lacks device and accessibility evidence). E: not started |

### Alignment with the Product Bible

| Bible rule | Status in the code |
|---|---|
| Module map (section 4) | Holds: eight modules (PLAN, CARE, HEAL, MINT, BASE, LINK, GEAR, WORLD) |
| One owner, one source of truth | Holds for the built modules (own tables and store, a minimal event). Open: Exploration XP and unopened treasure in World; the single `AppData` view over the split tables |
| Utility first (principle 1) | Holds: Field Research, Ledger Research and Habitat Discovery can be turned off without losing a record (tested) |
| Forgiving, no punishment (principles 4 and 9) | Holds in Base and Care. Focus is strict by the owner's decision (PLAN.md 6.3), an accepted exception; the Plan streak text has not been checked against the no-pressure rules |
| Local-first, no account, cloud or telemetry | Holds |
| Android only | Holds; desktop and web stay compile-only |
| Cross-module links | Partly built: BASE chore to PLAN task (BASE pays once, PLAN pays nothing, tested) and a LINK follow-up handed to Plan and back; MINT bill links are not built |
| Roles; one Bag and one currency; held items persistent and non-consumable | Hold |
| Private content never enters WORLD or reward presentation | Holds and is tested for Care, Heal, Mint and Base; not tested for Gear data |
| Permissions just in time; honest integrations | Holds for what is built (the Huawei reader is declared for one app and turns itself off after a read); the manifest still declares permissions nobody has justified in writing |
| Definition of done (section 9) | **Not met by any module** (automated Android tests exist; no device verification, TalkBack or font-scale evidence) |

## 4. Architecture dependencies and blocking work

### P0 — Must precede broad feature expansion

What remains of P0 is the device evidence (REP-033 and the device checks in the backlog's Tier 5) and the typed repositories (REP-020). The source baseline, save fixtures, local data foundation and the shared Pokémon, Bag, Poké, ledger and reminder foundations are live.

### P1 — Core product workflows

- Design-system components and app shell.
- Completing PLAN and CARE behaviour, and the HEAL, MINT, BASE and LINK scope still open.
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
| 8 | BASE and LINK | 2–4 | Domain ownership, cross-links, privacy-safe events, offline workflows, export/restore and opt-out tests pass |
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

The canonical set is the 17 documents listed in `PRODUCT_BIBLE.md` section 11; `IMPLEMENTATION_BACKLOG.md` is the separate delivery artifact. `README.md` is orientation only; `evidence/` and `FEATURES/POKEMON_PLACES_IDEAS.md` (proposals) are outside the set.

- The `docs/pokelife/` tree is the only current specification authority.
- Update the owning canonical document when a requirement changes, then dependent cross-references and backlog items in the same change.
- Never infer implementation status from specification presence.
- Still behind the code and not to be mistaken for current: `docs/design.md` (older than the code in places), the manifest sections of `INTEGRATIONS_AND_PRIVACY.md` (an open gap, not a done state), and `DATA_AND_MIGRATIONS.md` 10.5.1 (the documents list).

## 9. Current unresolved issues

1. **Integration feasibility:** Huawei Watch Fit 4 Pro/Health Connect and Philippine bank/e-wallet integrations remain provider/device-dependent; no live integration is verified.
2. **Asset distribution rights:** the repository includes game-derived/private-use material; public distribution requires an asset-by-asset rights review and replacement or permission where necessary.
3. **Expanded Pokémon scope:** the integrated expansion catalogue is approved target scope, not evidence of implementation; verify the Kotlin source areas, schemas and tests before assigning status.
4. **Device evidence:** the database cutover, reminders, Keystore paths and every module's acceptance still need a physical-device pass (backlog Tier 5 and REP-033).

## 10. Documentation change control

- Product decisions belong in the Product Bible or the relevant module/feature document.
- Shared technical contracts belong in the relevant Architecture document.
- Implementation evidence and roadmap belong here.
- Do not create a global decision register or duplicate feature specifications.
- Do not mark a requirement built, tested, or device-verified based solely on its specification.
