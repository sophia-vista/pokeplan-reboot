# PokéLife Implementation Guide

**Status:** Implementation status, dependency plan and release gates. Product rules live in the Product Bible and the module and feature specifications, not here.
**Status last audited:** 15 November 2026, at 0.197.0, from the changelog, the backlog and the modules' "As built" sections (section 3.2); refresh it against a named commit whenever work lands.

> This guide separates approved specifications from implementation evidence. Nothing in it is device-verified except the owner's confirmation on the phone that the database cutover kept all data (REP-012).

## 1. Executive summary

PokéLife has a Kotlin Multiplatform/Compose foundation. All eight modules have a built slice (HEAL, MINT, BASE and LINK manual-first), the data foundation is live on Android, and the shared Pokémon and economy contracts hold in tests. Everything is tested on the JVM and compiled for Android; **nothing is device-verified**, so no module meets the Definition of Done (Bible section 9). Section 3.2 is the current status.

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

## 3.2 Current status at 0.224.0 (20 November 2026)

Status words follow section 2.2. Refreshed from the changelog (0.197.0 to 0.224.0) and the backlog register; the rows were first rebuilt at 0.197.0 from the changelog (0.149.0 to 0.197.0) and the modules' "As built" sections (REP-032). The module files' "As built" sections were not re-read line by line in this refresh, so where a row and a module file differ, the module file and the code win. Nothing was run on a device; the only Android-side evidence is the Robolectric suite (REP-033). Rules version 32, database schema 12.

### Modules

| Area | Status at 0.224.0 | Gaps (backlog, 20 November 2026) |
|---|---|---|
| PLAN | **Largely built.** Own `plan_*` tables. Modules → Plan opens a page with no menu (0.216.0): the lead, today's open tasks with check keys, the action items (Inbox quick add, the task to do next with Focus on this and Pick for me), the party last; Tasks, Calendar, Focus, Goals and Settings are in the header's Sections key. One Tasks workspace that opens on Today (Today, Upcoming, All, Done); lists of three kinds (Tasks, Checklist with a reset, Tracker); no categories (0.224.0: a list carries the colour and icon); a reminder with no date (a nudge); one set of day keys with a date picker, typed times, reminder offsets, and repeat every N weeks, on weekdays, on a monthly day or yearly; editor tabs Schedule, Details, More; a task's reward focus (Exp, Egg or Items) and event companions that earn exp (0.218.0, rules version 31); focus with Task and Focus eggs, any open task, a session summary and Recent; goals and milestones (editable), a Gym badge per milestone and a Hall of Fame; streak reminder and recap on the shared scheduler; widgets; BASE and LINK links. **Scrapped (0.216.0):** the weekly review and the frequency targets (saved targets became recurring tasks); progress and trends moved to Me → Stats | **Depth (REP-030, REP-035):** manual order and bulk actions, the Archived task state, "this and future" for repeating tasks, event reminders, goal status and deliberate completion, projects, task statistics; priority (0.215.0 records that the owner dropped "Rank versus priority"; whether a priority field is still wanted is unconfirmed); tags and saved recurrence presets (a spec first); unticked checklist items at a reset are not yet logged; no external calendar import beyond the device-calendar picker. **Focus:** allow list, ongoing notification, break cycle. Device acceptance (reminders on a phone) not run |
| CARE | **Largely built.** Own `care_*` tables. Five places reached from the Sections key (0.220.0): Today (routines with Skip, "2 of 5 today" and a Yesterday tab; the mood key; Quick actions), Routines, Calm (the breathing break and the DBT skills as guided walk-throughs), Reflect (the Diary Card for any earlier day and the journal with prompts and search, both editable), Dream (a dusk scene, Care Play in place, the dreamers who visit, reunions and sending away); trends (now under Me → Stats), egg inheritance, routine reminders, widget. Care Play fills no Egg meter (0.198.0, rules version 29) | **REP-030, REP-036:** the once-daily Diary reminder, a readable journal export, a quick mood widget, a visitor's notebook, calm tools beyond the skills library (a body scan, a grounding tool: not found in the code), the rest of REP-030's Care list. Journal and diary rows are not sealed (REP-021). Device acceptance not run |
| HEAL | **Built, manual first, with imports:** activity, hydration, sleep, body measurements (all editable), goals, Field Research, hydration reminders, Heal's party, `heal_*` tables with sealed rows, backups and kept-aside copies. Health Connect read-only; **Huawei Health reader** (screen reading for that app only, off after each read; step notification) saves workouts, weight and body fat and sleep after a preview, and daily steps, heart rate, SpO2, skin temperature, stress and sleep score (schema 11); steps and reads pay (rules version 25); nightly or weekly read reminder. The screen lock for Heal is set in Settings → Privacy and access (0.224.0) | **REP-040:** medication and supplement log first, then exercise library, workouts and templates, history charts, symptoms and appointments, nutrition. Huawei: the Week tab is captured and not parsed, resting heart rate awaits a capture, watch actions for reminders unchecked. Wearable claims withheld; device acceptance not run |
| MINT | **Built, manual first, well past the first slice:** accounts (groups, arrange, hide from total, card limit and days, a card colour, the wallet's app opened from the account, a receive-money QR image kept whole), transactions (category splits, one receipt, a transfer fee), budgets, savings targets, bills and recurring income (twice-a-month paydays with holidays, estimated income; "Paid, record it"), pay-later and credit accounts with installment plans folded into one monthly bill per account (0.214.0), statement records and an estimated-interest line, interest earned on deposit accounts, the credit card flow, manual reconciliation, review, exact integer money, Ledger Research, a flat reward for logging and outbreaks (0.219.0, rules version 32), Shared bills as a display-only ledger (0.224.0), Overview counts settings and two Glance widgets (0.210.0), sealed rows and backups, passphrase-sealed portable backup, bill reminders (off until turned on), screen-lock gate, hide amounts, statement CSV import | **REP-039:** rollover, refunds, charts (trend, cash flow, by category), more attachment types and several receipts, custom recurrence, spotting subscriptions, a quick-add widget, foreign currency, the open pairs of MINT.md section 22; Shared bills do not yet split by share or post settlements (REP-073); provider integrations. **Device acceptance of the Keystore path, the gate, the reminders, the widgets and the portable backup** |
| BASE (was HOME, renamed 13 November) | **Built, manual first, with links, reminders and the Secret Base room:** chores with deterministic schedules, areas (rename, archive, move), supplies and the restocking list, belongings, maintenance (a name is enough; records editable), warranties (editable), reference notes, search, Overview, Habitat Discovery, Base's party, `home_*` tables, per-record reminders (off by default; "Remind me" is always offered), chore-to-PLAN-task link, example chips on first use (0.202.0). **Room** (0.196.0, schema 12 `home_room`): Base's party in a Hoenn room of the lead's type, 42 decorations on 44 squares, earned through a Decoration meter | **REP-029, REP-041:** a freshness line, starter chores, expiry dates, photos and attachments, search filters and the 8-result cap, a PLAN shopping task for a supply, a service-provider link to LINK, widgets, imports; the Chores tab label "Upcoming" is clipped at 320 dp. Berry plots are next (REP-053 chunk 4). No encryption decision. The acceptance boxes in BASE.md section 14 are unticked; device acceptance not run |
| LINK | **Built, manual first** (audited 0.171.0): people with notes and a directory (search by name, phone, email, address or slambook word), important dates with a reminder chosen in the same step (0.201.0), follow-ups handed to Plan and back, duplicate suggestions, reminders, Postcard Trail, Link's party; **contacts import** (Android; `READ_CONTACTS` asked only after an explanation; 0.201.0); **slambook profile and a "Me" card** with a Western natal chart, BaZi, Kua and Life Path computed on the phone (0.223.0); `link_*` tables, in the backup, not sealed | **REP-043:** a chooser for changed fields on re-import, upcoming dates on the launcher (REP-022), sealing, D8; widgets. **REP-072:** Friend Safari is specified (LINK.md 9.5) and not built. Device acceptance not run (the contacts query has no Robolectric test) |
| WORLD | **Partial; a functioning foundation, content now broad.** Teams, presets, **a recommended team for the chosen dungeon and a one-action replace (0.202.0)**, deterministic offline settlement, finds and recruitment, rank and skills, 75 jobs, facilities, Café, Kecleon Shop, Swap Shop, Dojo, floor events and forks, setbacks and retreats, Investigation, Challenge and Story jobs, the five destinations, battle identity (moves count), the Guardian, widget and job alert. **Two Mystery Dungeon arcs (Rescue Team, Explorers of Sky, with their optional episodes) and ten region layers (Kanto to Hisui): over a hundred dungeons and over seventy authored story episodes (counted in the source at 0.197.0, not run)**; Evolution Trials; Explorers of Sky pictures, portraits and jingles in the shops and the job board | **REP-037:** branching in Johto, Hoenn and Sinnoh; the other Mythical episodes; Orre and the Ranger regions; a mainline postgame; Exploration XP and rank as a shared record (migration slice, World fixtures); Atlas settlements and routes; Journal records; Storage and Duskull Bank; story and find notifications; a read-only widget; music. The six newest layers have no map picture (REP-051). The expansion systems of POKEMON_ECOSYSTEM.md 5.5 (contests, camps, raids, fossils) are not built. Device acceptance not run |
| GEAR | **Partial; broad.** All 18 tools in the catalog and routed (audited 0.171.2; timer length typed); helpers visual only and reacting to real actions only (0.198.0); the dice history switch is saved; QR payloads are read and warned about, never opened. "As built" in GEAR.md | **REP-028, REP-042:** decide which tools to drop or merge; Everyday sums are half of GEAR.md 5.2; Timer presets and a World clock with search and 12-hour time; timers use wall-clock time, not a monotonic source; every `changeGear` writes the whole save; document scanner with OCR not built. No device, TalkBack or font-scale evidence |
| Launcher | **Partial; a meaningful prototype.** Opt-in HOME role, widget grid with pages and resize modes, folders, app drawer, search that lists PokéLife's own places, weather. **The town is a Mystery Dungeon floor** (0.188.0, a new one each day): rooms and corridors from the game's tiles, Pokémon walking paths and picking up three daily finds, hidden cosmetic traps, a Guardian on the stairs; a "how lively" setting. **Launcher pass (0.221.0):** species quirks (sleepers, rooted, disguised, slow, fast, erratic, timid), ghosts that rarely cross walls, more gestures (two-finger swipes, press and hold, pull down notifications) and jobs, explicit choice lists in the launcher settings, a confirmed "Reset launcher settings", 12-hour clock and date format now in General settings (used by the launcher only) | **REP-045, REP-052:** built-in module widgets, landmark navigation to the modules, saved positions, provider reconfiguration and orphan id cleanup, launcher settings in the backup, onboarding; Red Rescue Team full tile sets; walking cost with 24 Pokémon unchecked on a device (and the 0.224.0 fix for tile-by-tile walking); the open layout refreshes after "Erase this device" only on a restart (REP-073); Link dates on the home page (REP-022). `QUERY_ALL_PACKAGES` policy dropped with public-release work (REP-047) |
| Shared Pokémon and economy | **Partial, advanced.** One place per Pokémon (REP-009 done: the PC lists only what rests in it, a Show all switch names every place), module parties with per-module perks, one currency Poké, shared Pokémon, Bag and ledger tables, held items, Mints, capsules, vitamins, Tera Shards, trade and evolution items; **a moveset** of up to four moves (0.159.0, read by WORLD); every special evolution has a way in (0.182.0); a Poké Mart; themed item drops from every life module; **a kinder economy** (0.187.0, rules 26). **Rules version 32:** 29 Care Play fills no Egg meter, 30 the weekly review and targets scrapped, 31 a task's reward focus and event companions (a new exp reward), 32 Mint's flat logging reward and outbreaks (a pick of three caught directly) | **REP-044:** Poké Ball identity, Ribbons and Marks, per-Pokémon history, original nature after a Mint, origin module and event; Mega (no stone source, no form shown), the Dynamax Band never read, Z-moves, no Tera Orb. **REP-068, REP-069:** the other modules' everyday actions are not yet levelled with a Plan focus egg, and outbreaks exist for MINT only. The economy simulation lacks module drops, the shop and the Egg and Items reward focus (REP-051, 0.218.0). Berry plots next (REP-053); jobs as assignments, contests, camps, fossils, outbreaks (WORLD) and raids not built |
| Data architecture | **Live on Android**: SQLDelight/SQLite (`:data`), every released schema (to 12) committed with verified migrations, idempotent append-only ledger and outbox, daily file backup with move-aside restore, **an automatic backup into a folder the player picks, sealed under a passphrase (0.199.0)**, one authority. A corrupt database with no backup asks "Use the older data" or "Start fresh" instead of importing old files silently (0.199.0). Plan, Care, Heal, Mint, Base, Link and the player's Pokémon, Bag and Poké are in their own tables; WORLD, GEAR and the launcher stay in the `save` row | **REP-020:** typed repositories per module (the app reads one `AppData` through the split) and retiring the JSON documents. **REP-018:** `GameStore` is still 2,067 lines (counted 20 November 2026; it was 1,964 at 0.197.0). The outbox is written and nothing consumes it; `pokemon_history` missing. **REP-034:** the off-phone backup is built and not run on a device (the folder picker, the worker, the passphrase key derivation); a notification about an old copy, sealing Care, Base and Link in it, and the archive format of DATA_AND_MIGRATIONS.md 10.2 are left. Backup is a one-file bundle with a checksum, not the specified archive; real kill and low-storage runs on a device not done |
| Integrations and privacy | Cloud, accounts, sync and the server reward function **retired**. Shared reminders: Plan, Care, Heal, Mint, Base, Link, with a nightly refresh alarm, quiet hours, focus deferral, snooze and repeat; a reminder due while the phone was off is shown (0.173.1); read-only calendar; Health Connect, Huawei Health and statement import; the phone's contacts (0.201.0); widgets for Today, Buddy, Focus, Care, World and two for Mint. System backup and device transfer closed. Private module content never reaches the shared save, ledger or reward log (tested for Care, Heal, Mint, Base). Settings → Privacy and access holds one screen-lock switch for Care, Heal, Base, Link and Mint, Notifications holds one reminders switch per module, and Reset clears every module's settings and cancels alarms (0.224.0) | The manifest still declares `QUERY_ALL_PACKAGES`, `REQUEST_DELETE_PACKAGES` and exact-alarm permissions without a written reason (store-only items dropped, REP-047); it also declares `EXPAND_STATUS_BAR` (0.221.0, used only for the pull-down-notifications gesture) and `WRITE_SECURE_SETTINGS` (granted over adb, for the Huawei reader's one-read switch). The Huawei listener unbinds after each read and both Huawei services start disabled (0.202.0); the Background figure after a day is still to confirm on the phone (REP-055). No `FLAG_SECURE` or recents protection; no `BiometricPrompt` in front of the private modules (REP-022). Notification privacy checked only for hydration and bill reminders. No widgets for Heal or Base; Mint providers do not exist |
| Tests and CI | `./gradlew jvmTest compileKotlinWasmJs` is the merge gate. **`androidApp` has a Robolectric suite (REP-033, 0.175.1), run by CI's Android step:** `LiveStore` (fixture cutover, process death, corrupt and newer databases, sealing, fail closed), the shared reminders and their receivers, the Today widget, focus across process death. A lean-suite pass removed tests of constants and copy (REP-050) | **Screenshot tests still assert that something was drawn, not that it matches.** Open in REP-033: Care, Heal, Mint, Base and Link reminder notifications, the widgets' Glance rendering, a kill during a write, stored-PNG comparison in both palettes, a TalkBack semantics check. No lint, static analysis or dependency scan; the release workflow builds a debug APK signed with the committed key (fine for personal use, REP-047). No instrumented, journey or accessibility suites |
| Design system and assets | Design rules of 8 November 2026 applied; the UI/UX review of 0.174.0 (`CopyBudgetTest`, one accent per module, one `ModulePage`, one date, time and amount field); asset audit of 0.169.0; Hoenn room art and Gym badges bundled; real game assets only. Since then: Sections key and a one-row module header (0.203.0, 0.204.1), the form kit and dropdowns (0.211.0, 0.212.0), one order on every module's main screen (0.216.0, 0.220.1), a shared baseline for mixed-size text and a foldable party-perks `Accordion` (0.224.0); Settings → About → Credits lists the art, sound and font sources (0.198.0) | **REP-015: no asset registry, rights status or release gate, no app-wide per-asset credits** (only per-Pokémon artist credits and the by-source Credits list). Audio has effect and cry sliders and no music; 52 sound clips committed, none auditioned (REP-017), REP-016 planned. About 290 one-off dp values remain (REP-023, counted at 0.165.0). The 12-hour clock and date format reach only the launcher; the rest of the app builds time text in about 22 places (REP-073) |
| Me and Settings | **Built (0.224.0), not run on a device.** Me: trainer card (name, buddy, rank), Stats (totals, opening Plan progress and Care trends), Settings and Help. Settings is a menu of pages: General, Privacy and access, Notifications, Backups (complete backup, a Module exports card, Reset), About | **REP-073:** the 12-hour clock and date format across the app; a reminders switch for Heal |

### Sequence and gates

| Sequence | Status |
|---|---|
| 0 Baseline, 1 Architecture and test strategy | Done (REP-001 to REP-007; Gate A passed). Signing stays test-grade; a release key is not needed for personal use (REP-047) |
| 2 Local data foundation | Built and live on Android (REP-008). **Gate B is partial**: fixtures migrate and the owner confirmed the cutover on the phone; Robolectric covers process death and corruption recovery, but kill, low-storage and recovery on a device are not run, and the backup archive is incomplete |
| 3 Shared Pokémon, economy and reminders | Built. **Gate C is partial**: ownership, one Bag and one Poké balance and privacy-safe events hold in tests; Plan reminders are covered by Robolectric, the other modules' notifications and a device run are not |
| 3B Expanded Pokémon foundations | Partial: held items, Mints, capsules, training items, Tera, trade items and the moveset exist; Balls, Ribbons, history, Mega and Dynamax and the WORLD expansion systems do not |
| 4 Design system and app shell | Partial; tokens and components applied, device rendering not reviewed |
| 5 PLAN and CARE refactor | Done on the data side; open on behaviour (REP-030, REP-035, REP-036) |
| 6 HEAL, 7 MINT, 8 BASE and LINK | Manual slices built, with imports for Heal and Mint and the Secret Base room; the gaps above remain |
| 9 GEAR | Broad tool set, audited once; UX decisions open (REP-028); not accepted |
| 10 WORLD | Foundation and broad content present; branching, shared Exploration XP and the remaining expansion systems remain |
| 11 Launcher | Prototype with a dungeon-floor town; host robustness, module widgets and navigation remain |
| 12 Release hardening | Not started (asset registry, manifest and backup policy, CI hardening, device suites); store-only items dropped |
| Gate D (module acceptance), Gate E (device and release acceptance) | D: no module passes in full (each lacks device and accessibility evidence). E: not started |

### Alignment with the Product Bible

| Bible rule | Status in the code |
|---|---|
| Module map (section 4) | Holds: eight modules (PLAN, CARE, HEAL, MINT, BASE, LINK, GEAR, WORLD), no Grow, no Explore |
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

### Owner decisions of 8 November 2026 (on the audit's conflicts)

| # | Question | Decision | Effect |
|---|---|---|---|
| 1 | Focus interruptions | **Strict stays** | PLAN.md 6.3 amended; no gentle mode (11 November 2026: add an allow list and an ongoing notification, Bible section 5) |
| 2 | Milestone Super Candy | **Stays as it is** | PLAN.md 9 amended; goal completion is still inferred, to be made deliberate |
| 3 | Shiny and egg luck perks, EV and IV model | **Perks stay** | POKEMON_ECOSYSTEM.md 2.2.1 amended; the EV and IV model stays as built |
| 4 | Care eggs hatch at once, or enter the shared Egg collection | **Hatch at once, with a scene where the visitor leaves the egg** | Built; CARE.md 6.6 amended |
| 5 | Mega and Dynamax | **Integrate them into PokéLife** | Open: REP-044 |
| 6 | HEAL encryption | **Put in the roadmap** | Built (Heal's own Keystore key; a passphrase backup seals it with Mint) |
| 7 | Gear loans | **Retire** | Done |
| 8 | Manifest | **Backup closed; permissions OK for now** | Declared permissions stay in pending item 9 |

### Pending items

Retired as a separate list on 15 November 2026: it duplicated the backlog and had fallen behind the code (priority, the Plan structure, the Secret Base room and the module-package split had all moved on). The order of work, with value and effort for each item, is the **Priority board** at the top of `IMPLEMENTATION_BACKLOG.md`. Device acceptance (the old item 1) is now REP-033 (automated) plus the device checks in the board's last tier.

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
