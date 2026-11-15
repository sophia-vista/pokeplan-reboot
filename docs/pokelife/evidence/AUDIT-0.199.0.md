# PokéLife feature and personal-use audit at 0.199.0

15 November 2026. **Evidence, not authority** (Product Bible section 10): the rules live in the specifications and the work in `IMPLEMENTATION_BACKLOG.md`. Rows REP-054 to REP-056 and decisions D9 and D10 there come from this audit.

**What it is.** An audit of the 0.199.0 APK (SHA-256 `42064dee…6f69`, 24 DEX files, 29,993 entries), the owner's interview about how they use each module, the owner's screenshots, and one battery screen (section 3). The first draft was written from the APK's class metadata and the documents alone. This version reconciles it with the source at 0.199.0 (`main` at `f957b6f1`) by reading the code. **Nothing was built or run, no test was run, and no device was used**; "built" below means the code is there, not that it works.

## 1. Verdict

PokéLife should not get smaller; it should get easier to start. The owner uses what is immediately useful and understandable, and postpones what needs set-up, unfamiliar words or a cluttered screen. WORLD is the exception because its loop is clear and asynchronous. The launcher's PMD-style town is a core reason the owner uses the app and is an acceptance criterion, not polish.

So: keep all eight modules and their depth, make each one useful after one small action, reveal structure later, and fix the one concrete flaw in the best-liked loop (WORLD team choice). Local-only, no accounts, no telemetry and private content staying in its module are unchanged.

## 2. Corrections to the first draft

The draft's evidence labels were honest about not having source. With source, these claims change:

| First draft said | Source says | Where |
|---|---|---|
| The public name is HOME and the implementation uses `base`, a naming drift to review | **BASE is the name** (renamed from HOME on 13 November 2026). Code, labels (`Modules.kt`), the launcher destination and the docs agree; "Home" is only the launcher's first page. No review needed | `CLAUDE.md`, `PartySwitcher.kt:57` |
| The implementation guide's baseline is 0.123.0 and the source is unavailable (P0) | The guide's section 3.2 was rewritten at 0.197.0 (0.198.0); the source tree is this repository; `VERSION` and `CHANGELOG.md` say 0.199.0 | backlog, 0.198.0 |
| Which store is authoritative, SQLite or the JSON stores, is unknown | On Android the SQLite database is authoritative since 0.129.0 (`LiveDocuments` splits the save into per-module tables); desktop and web keep JSON stores as dev conveniences. Retiring the remaining JSON documents and file fallbacks is **REP-020** (Tier 4). Migration, backup, restore and the corrupt-database choice have tests (`LiveStoreTest`) and 0.199.0 added backup off the phone | `DATA_AND_MIGRATIONS.md` 4.5, 7.3.0 |
| HEAL nutrition may exist under other names | **Not built.** `heal-core` has no meal, food or nutrient model; the word appears only in `HealImport.kt`. REP-040 already puts nutrition last | backlog REP-040 |
| Huawei import is an import path through `HealStore` | It is an **on-phone reader**: an accessibility service for `com.huawei.health` (it turns itself off after every walk, because banking apps refuse to open while it is on) and a notification listener that drops every other app's notifications on arrival. Unofficial and fragile by owner decision, 14 November 2026 | `INTEGRATIONS_AND_PRIVACY.md` 5.2.2, `huawei/*.kt` |
| Add a title-only quick capture on Today, Inbox and list screens | **Built:** a quick-add box on Today, the Inbox and each list; "Added to ..." feedback and a kept draft since 0.198.0 | `plan/Planner.kt:162`, `plan/Lists.kt:111` |
| Add Person should ask only for a name | **Built:** one name field and Add, then the profile opens | `link/Link.kt:298` |
| Group and last digits should not be asked in account set-up | **Partly built:** they sit behind a "Group and last digits" key; name, type, currency and opening balance are still on the first screen | `mint/Mint.kt:298-313` |
| Add favourites, recents and search to GEAR | **Built** (`ToolCatalog.search(query, favorites, recents)`, a star on every tool, unit-pair favourites). What is missing is a GEAR launcher widget: the five widgets are Buddy, Care, Focus, Today and World | `gear/Gear.kt:85-156`, `res/xml/widget_*.xml` |
| WORLD needs "Send my strongest" | **Built** (Tiny Woods shortcut, the dungeon page and the Today action), with six presets and a per-team suitability line ("a floor takes about N min; k of n like this place"). Not built: a recommendation that ranks by type, a poor-match mark, a one-action replace; the manual list is simply the **20 strongest idle** Pokémon | `world/World.kt:202,325,331,729` |

Not verifiable from code, still open: that the screenshots the draft cites match 0.199.0, that CARE and LINK feel cluttered or scattered, and anything about timing or feel on the phone.

## 3. New evidence: battery usage of the app

User-observed (Samsung "Battery usage for app", 15 November 2026, 16:33): total usage 7 h 4 m, **screen on 55 m, background 6 h 9 m**; wake-ups 0, wake locks under 1 m, CPU 31 m; mobile data 70 packets, Wi-Fi 1,044 packets; GPS 0, Bluetooth scans 0. "Background usage limits" is off.

What it does and does not say. The wake-up and wake-lock figures are clean, and the traffic is small (the only HTTP clients found in the code are the launcher's weather forecast and Gear's currency rates). The unexplained part is **why the process counts as running for 6 h 9 m with the screen off**, and what the 31 m of CPU is spent on (55 m of an animated town on screen would explain much of it; it may not explain all).

Candidate causes, each checked only in code, none measured:

1. **A bound system service keeps the process alive.** `HuaweiNotificationService` is a notification listener; while the owner has Notification access on, Android keeps it bound, so the app process stays resident all day. The accessibility service turns itself off after every walk, so it should not be bound for long. This is the likeliest reason for the "background" time and is cheap to confirm.
2. **The app is the home app** (`CATEGORY_HOME`), so Android keeps its process warm by design; this is expected and costs little by itself.
3. **A loop that keeps running while stopped.** `Foreground.kt` already gates paint and poll loops on `Lifecycle.State.STARTED`, and `LAUNCHER.md` section 12 requires animation to stop when hidden. Whether every loop (town life, weather, the buddy stage, widgets) obeys that has not been measured.
4. **Alarms, widgets or the backup job** waking more often than expected: exact alarms and boot receivers are declared; the daily backup job (0.199.0) is new.

How to settle it with one phone session, no new code: `adb shell dumpsys batterystats --charged app.pokeplan` and `adb shell dumpsys activity services app.pokeplan` (which services are bound, and since when), then repeat with Notification access for PokéLife switched off for a day and compare the Background figure. Tracked as REP-055.

## 4. Module findings

The recommendations of the first draft that are **not already built** (section 2) are listed here, each with its backlog home. "Existing" means a row already carries it; do not open a second one.

### The first-use rule, and a conflict to settle

The audit asks for contextual explanations and one-tap examples in MINT, BASE, CARE and HEAL. The owner's bar of 12 November (`UI-REVIEW-0.172.0.md` section 2, `DESIGN_SYSTEM.md` 5.1 and 12A) says no caption that restates what is on screen, no helper text, empty states are a title and one action. Both are right, so the way to meet both is:

- **Examples** are the field's placeholder and, where useful, a row of tappable example chips that fill the field ("Detergent", "Clean AC filter"). They are not a sentence under a title.
- **Definitions** (account types, chore versus maintenance, supply versus asset) sit behind one small info key that opens a short dialogue, and are written once in Help.
- **The first record needs the least**: a name, plus one more field only where the module cannot work without it (an amount for an expense).
- **Every module's first-use screen gets one concrete next action**, not a tour.

This is REP-056.

### PLAN (P1)
Owner use: tasks, custom lists as workstreams (`Work`, `Resto`), focus sessions; Google Calendar stays the event source (read-only), because of its maps and event screen. Capture is built (section 2). Open: a short default editor with "More options" for time, recurrence, reminders, tags and subtasks (check what the editor shows now); reusable recurrence presets and "every N weeks"; an **Outstanding** view across lists. Home: REP-030 depth and REP-035 (Tier 2 item 18); labels and saved filters still need a spec.

### CARE (P1)
Owner use: breathing and skills are used on demand; routines, journal and mood are forgotten; Pokémon care and its rewards motivate. Open: a landing built around one Today action with Calm one tap away; a quiet "not logged today" mark on Reflect with no nagging; skills searchable with recents. Do not read missed routines as a wish to drop routines. Home: REP-036 (Tier 3 item 25: Reflect destination, grounding tool, diary reminder).

### HEAL (P1)
Owner use: the Huawei reader is the strength; hydration, nutrition and weight are logged only when remembered. Open: persistent quick-add for hydration, weight and workout with presets and "repeat last"; choose which metrics show on Today; show each metric's state (read, entered by hand, stale, unavailable, none yet) and never a missing value as zero. **Nutrition is not built** (section 2) and must not be advertised until meal entry, saved foods and history exist. Home: REP-040 (Tier 2 item 15 is medication first), REP-046 for the Huawei captures.

### MINT (P1, the first-use blocker)
Owner use: accounts not set up; account types and the other features are unclear; wants a consolidated view, expenses, budgets, bills, savings goals, net worth, nicer card visuals (the Agila card idea); asked about stock advice and timing. Open: account creation as "where is this money?" with the type defined in one info dialogue (e-wallet, digital bank, cash, credit card = money owed, loan = liability); a card-style picker that is cosmetic only and never changes accounting; a first run that ends on a useful overview after one account and a transaction or two; expense capture as amount, account, category; a short worked example for Budgets, Bills, Reconciliation, Statement import, Savings targets and Reports. **Stock recommendations and buy/sell timing stay out of scope** (`MINT.md`; the Bible excludes financial advice); a read-only holdings record would need an explicit scope change. Home: REP-039 (Tier 2 item 16 is recurrence first) and REP-056 for the first run.

### BASE (P1, medium)
Owner use: not started because set-up looks long; chores versus maintenance unclear; Supplies and Reference unclear; the real use is **condo upkeep**, plus occasional jobs at another home. Open: name-only quick add for each kind; example chips; the one-line meaning of chore, maintenance, supply, asset and reference behind an info key; one default place and a second added later; "buy an air conditioner" stays a PLAN task and the installed unit may then become a BASE asset with warranty and upkeep, the payment being MINT's. Home: REP-029 (decisions D3), REP-041 (Tier 3 item 28), REP-056.

### LINK (P2)
Owner use: nearly unused; adding people feels strange; information feels scattered. Add Person is already name-only. Open: **walk the real profile and create flows on the phone first** (the draft's restructuring advice was written without that), then one profile that holds overview, notes, dates and follow-ups; one search over people, notes, dates and follow-ups; follow-ups title-only. Contacts import with the birthday reminder is REP-043 (Tier 2 item 14, the adoption blocker). D8 stands: no "keep in touch every N weeks".

### GEAR (P2)
Owner use: the unit and currency converters; the other tools feel niche or hard to reach. Built: favourites, recents, search. Open: Currency and Unit Converter pinned as the top quick tools; a **GEAR launcher widget** for one or more favourite tools (D9); tool and drop-or-merge decisions are D2 and REP-042.

### WORLD (P1, the clearest flaw in the best loop)
Owner use: collect, choose quests, send a team, leave. "Send my strongest" is used. Each dungeon favours types; the previous team stays selected even when it suits the new dungeon badly, so the owner removes members by hand and scrolls about 20 Pokémon. **When picking by hand, type suitability comes before strength.** Roster growth comes from recruiting and other modules; duplicates are worth keeping because they transfer for Candy and Poké. Speed-up minigames are wanted but are not a priority. Quest choice weighs reward, difficulty, type match and progression.

Specified in `WORLD.md` 2.3 (this audit): Recommend team for the chosen dungeon, ranked by type suitability first and strength within it, with a one-line reason per pick and a stated fallback when nothing matches; the saved team stays but is marked a poor match when the dungeon changes; Replace team in one action; the picker shows matching types first with type chips and search; every member can be overridden. The ranking sits in the recommendation layer. The expedition formula (`WORLD.md` 3.2) is unchanged; any change to it is simulated first (`simulateWorldEconomy`). Home: REP-054.

### Launcher and rewards
Keep the PMD town calm and alive (roaming, touch response, landmarks, shortcuts); do not add permanent dashboard cards over it; stable positions and predictable lifecycle first. Rewards stay bounded and for validated completion, never for sensitive content, and quantities are not raised to prop up an unused feature: fix the entry flow first. One collection, one Bag, one Poké, idempotent settlement (all specified). The battery figures of section 3 belong to this heading.

## 5. Cross-cutting

- **Persistence.** Answered in section 2. The open piece is REP-020, and nothing old is deleted until the migration, backup, restore and rollback tests pass.
- **Source of truth.** The ownership rules the draft restated (BASE owns chores, MINT owns payment state, LINK owns a follow-up until converted, HEAL owns physical health, CARE owns mood) are already `DATA_AND_MIGRATIONS.md` section 5; no change.
- **Permissions.** The 0.199.0 manifest declares network, network state, vibration, activity recognition, fine and coarse location, microphone, notifications, set-alarm, calendar read, boot, exact alarms, package deletion and install queries (`QUERY_ALL_PACKAGES`), `WRITE_SECURE_SETTINGS`, and Health Connect reads for exercise, sleep, weight, body fat and hydration, plus the two Huawei services. `INTEGRATIONS_AND_PRIVACY.md` 4.2 is the permission matrix, 4.3 the manifest audit from 0.123.0 (remove what is unjustified) and 5.2.2 the Huawei reader; the public-release policy question for `QUERY_ALL_PACKAGES` was dropped with REP-047. Whether the 4.3 removals were all done, and whether each declared permission still has a use at 0.199.0, has not been checked here.
- **Testing.** The suite exists (`jvmTest`, the Robolectric Android tests of REP-033, the screenshot tests). No result from this audit; nothing here is called tested or device-verified.

## 6. Where each finding went

| Finding | Backlog |
|---|---|
| WORLD recommend team, poor-match mark, replace team, type-first picker | **REP-054**, Tier 1 |
| Why the app counts as running 6 h 9 m with the screen off | **REP-055**, Tier 1 (one `adb` session) |
| First-use paths and the examples-without-captions rule (MINT, BASE, CARE, HEAL, LINK) | **REP-056**, Tier 2 |
| GEAR launcher widget | D9, then REP-022 |
| Default place for BASE | D10, then REP-056 |
| LINK contacts import; HEAL medication; MINT recurrence; PLAN depth; Seal Care's journal | Existing Tier 2 items 14 to 18 |
| Persistence, HEAL nutrition, Huawei reader, GEAR tool list | Existing REP-020, REP-040, REP-046, D2 |

## 7. Acceptance, shortened

Each change meets these before it closes; the full lists are in the module specs and `TEST_STRATEGY.md`.

- A basic record can be made with the minimum fields; advanced fields stay reachable and out of the way.
- A first-use screen has one next action and teaches a domain word by example or one info dialogue, never by a standing caption.
- Dense screens are looked at in both palettes at 1.3× text, and with TalkBack where the screen is new.
- One owner per record; a linked view never pays twice; core use works offline; sensitive content never reaches WORLD, the launcher or a reward.
- For the battery item: after the fix, the Background figure for a normal day is explained by named causes, and nothing runs while the screen is off except what the owner switched on.
