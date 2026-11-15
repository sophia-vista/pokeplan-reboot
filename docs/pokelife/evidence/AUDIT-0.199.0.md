# PokéLife feature and personal-use audit at 0.199.0

15 November 2026. **Evidence, not authority** (Product Bible section 10): the rules live in the specifications and the work in `IMPLEMENTATION_BACKLOG.md`. Rows REP-054 to REP-056 and decisions D9 and D10 there come from this audit.

**What it is.** An audit of the 0.199.0 APK (SHA-256 `42064dee…6f69`, 24 DEX files, 29,993 entries) against the owner's interview (filed whole as `INTERVIEW-2026-11-15.md`; that file is the primary record of how the owner feels about each module, and where this one condenses it, it wins), the owner's screenshots of MINT's forms, and one battery screen (section 3). The interview says it is not a code audit or a usability test, and this one is not a usability test either. The first draft was written from the APK's class metadata and the documents alone. This version reconciles it with the source at 0.199.0 (`main` at `f957b6f1`) by reading the code. **Nothing was built or run, no test was run, and no device was used**; "built" below means the code is there, not that it works.

## 1. Verdict

PokéLife should not get smaller; it should get easier to start. The owner uses what is immediately useful and understandable, and postpones what needs set-up, unfamiliar words or a cluttered screen. WORLD is the exception because its loop is clear and asynchronous. The launcher's PMD-style town is a core reason the owner uses the app and is an acceptance criterion, not polish.

So: keep all eight modules and their depth, make each one useful after one small action, reveal structure later, and fix the one concrete flaw in the best-liked loop (WORLD team choice). Local-only, no accounts, no telemetry and private content staying in its module are unchanged.

## 2. What the source changes

Two different things were found, and they must not be mixed up. **Existing in code is not the same as working for the owner**: where the audit asked for something that exists, the owner's experience (sections 4) is still the evidence, and the fix is the screen, not the feature.

### 2.1 The draft was factually wrong (corrected)

| First draft said | Source says | Where |
|---|---|---|
| The public name is HOME and the implementation uses `base`, a naming drift | **BASE is the name** (renamed from HOME on 13 November 2026); code, labels and docs agree. "Home" is only the launcher's first page | `CLAUDE.md`, `PartySwitcher.kt:57` |
| The guide's baseline is 0.123.0 and the source is unavailable | Guide section 3.2 was rewritten at 0.197.0; the source is this repository at 0.199.0 | backlog, 0.198.0 |
| Which store is authoritative is unknown | On Android SQLite is authoritative since 0.129.0 (`LiveDocuments`); JSON stores are desktop/web dev conveniences; retiring the leftovers is REP-020 | `DATA_AND_MIGRATIONS.md` 4.5, 7.3.0 |
| HEAL nutrition may exist under other names | **Not built**: `heal-core` has no meal, food or nutrient model (the word appears only in `HealImport.kt`); REP-040 puts it last | backlog REP-040 |
| Huawei is an import path through `HealStore` | It is an on-phone reader: an accessibility service for `com.huawei.health` that turns itself off after every walk, and a notification listener that drops other apps' notifications on arrival. Unofficial and fragile by owner decision | `INTEGRATIONS_AND_PRIVACY.md` 5.2.2 |

### 2.2 It exists, and the owner still has the problem (not closed)

These are **not** "already built, skip". Each is something the owner called tedious, awkward, cluttered, forgotten or hard to find, in a build where the code for it already existed. The open question is why it still fails for them, which the code cannot say; it needs the phone and the owner's eyes.

| Feature in the code | What the owner says (section 4) | What to look at |
|---|---|---|
| PLAN quick-add box on Today, Inbox and lists (`plan/Planner.kt:162`, `Lists.kt:111`) | Quick tasks are still forgotten before they are entered; recurrence set-up is boring; the screen feels cluttered | Whether the box is where the thought happens (launcher shortcut, a widget, a notification), not whether it exists |
| LINK name-only Add Person (`link/Link.kt:298`) | Adding people feels strange and awkward; information feels scattered | The step after Add: the profile that opens, and what is on it |
| MINT account form: group and last digits behind a key (`mint/Mint.kt:298-313`) | Laborious and barebones; account types and the other features are unclear | Name, type, currency and opening balance are still all on the first screen, and no type is explained |
| GEAR favourites, recents, search (`gear/Gear.kt:85-156`) | Only the two converters are useful; the rest are poorly designed, niche or inaccessible | Reaching the two converters in one tap from Home (no GEAR widget exists: the five are Buddy, Care, Focus, Today, World) |
| WORLD "Send my strongest", six presets, a suitability line (`world/World.kt:202,325,331`) | Convenient, but the saved team stays poorly suited and the owner removes members by hand and scrolls about 20 | The manual list is the 20 strongest idle (`World.kt:729`), with no type ordering |

## 3. New evidence: battery usage of the app

User-observed (Samsung "Battery usage for app", 15 November 2026, 16:33): total usage 7 h 4 m, **screen on 55 m, background 6 h 9 m**; wake-ups 0, wake locks under 1 m, CPU 31 m; mobile data 70 packets, Wi-Fi 1,044 packets; GPS 0, Bluetooth scans 0. "Background usage limits" is off.

What it does and does not say. The wake-up and wake-lock figures are clean, and the traffic is small (the only HTTP clients found in the code are the launcher's weather forecast and Gear's currency rates). The unexplained part is **why the process counts as running for 6 h 9 m with the screen off**, and what the 31 m of CPU is spent on (55 m of an animated town on screen would explain much of it; it may not explain all).

Candidate causes, each checked only in code, none measured:

1. **A bound system service keeps the process alive.** `HuaweiNotificationService` is a notification listener; while the owner has Notification access on, Android keeps it bound, so the app process stays resident all day. The accessibility service turns itself off after every walk, so it should not be bound for long. This is the likeliest reason for the "background" time and is cheap to confirm.
2. **The app is the home app** (`CATEGORY_HOME`), so Android keeps its process warm by design; this is expected and costs little by itself.
3. **A loop that keeps running while stopped.** `Foreground.kt` already gates paint and poll loops on `Lifecycle.State.STARTED`, and `LAUNCHER.md` section 12 requires animation to stop when hidden. Whether every loop (town life, weather, the buddy stage, widgets) obeys that has not been measured.
4. **Alarms, widgets or the backup job** waking more often than expected: exact alarms and boot receivers are declared; the daily backup job (0.199.0) is new.

How to settle it with one phone session, no new code: `adb shell dumpsys batterystats --charged app.pokeplan` and `adb shell dumpsys activity services app.pokeplan` (which services are bound, and since when), then repeat with Notification access for PokéLife switched off for a day and compare the Background figure. Tracked as REP-055.

## 4. Module findings: what the owner says, what is there, what is open

Each module has three lines. **The owner says** is the interview as the audit reported it, in its own words as far as they survive (I did not see the raw interview; add anything missing). **In the code** is what 0.199.0 has. **Open** is what the owner's experience says still needs work. "Existing" means a backlog row already carries it.

### The first-use rule, and a conflict to settle

The audit asks for contextual explanations and one-tap examples in MINT, BASE, CARE and HEAL. The owner's bar of 12 November (`UI-REVIEW-0.172.0.md` section 2, `DESIGN_SYSTEM.md` 5.1 and 12A) says no caption that restates what is on screen, no helper text, empty states are a title and one action. Both hold, so:

- **Examples** are the field's placeholder and, where useful, tappable example chips that fill it ("Detergent", "Clean AC filter"). Not a sentence under a title.
- **Definitions** (account types, chore versus maintenance, supply versus asset) sit behind one small info key that opens a short dialogue, and are written once in Help.
- **The first record needs the least**: a name, plus one more field only where the module cannot work without it.
- **Every first-use screen has one concrete next action**, not a tour.

This is REP-056.

### PLAN (P1; the owner's priority 2)
**The owner says:** uses tasks, custom lists, focus sessions and the calendar widget. Does **not** report regular use of Inbox, Upcoming, recurring tasks or Goals and Milestones. The lists are workstreams, used to see what is **outstanding** rather than to plan today: **Work** (the job) and **Resto** (Balai Yllana: publications, R&D research, website upkeep). Used TickTick before, for tasks but also groceries, learning and certification tracking, and still values reminders, checklists, precise date and time, flexible recurrence and customisation; its recurring reminders are still what the owner uses for **bill payments and household chores**. Three reasons PLAN is not used more: quick tasks are forgotten because they never get captured; setting up recurrence is tedious; the UI feels cluttered. Focus is used partly because the reward makes focusing easier.
**Boundaries the owner set:** Google Calendar stays the event and schedule tool (maps, attachments, and a visual record of WFH/RTO and whether the owner is at the condo or at home); Samsung Notes stays the quick-note tool; PokéLife fills gaps and does not force a move.
**In the code:** the quick-add box on Today, the Inbox and lists, with "Added to ..." feedback and a kept draft; and three ways in from outside the app: the `add_task` launcher shortcut, a Quick Settings tile and a widget button, which open the app on the add-task screen (`QuickTiles.kt`, `shortcuts.xml`, `GlanceWidgets.kt:226`). Recurrence, frequency targets, tags and lists exist.
**Open:** the owner forgets in spite of those entry points, so the question is whether they are found and used, and whether they land on a title-only box (not checked); a short default editor with the rest under "More options"; reusable recurrence presets; one **Outstanding** view across Work and Resto; and the recurring bills and chores now living in TickTick, which PLAN, MINT bills (REP-039) and BASE chores can only win over if recurrence setup is quick. Existing: REP-030 depth and REP-035 (Tier 2 item 18); the capture, outstanding and recurrence slice is REP-057.

### CARE (P1)
**The owner says:** routines were added but are not followed; journal and mood are forgotten; breathing is useful; skills are valuable when needed; the UI feels cluttered; Pokémon care and its rewards motivate.
**In the code:** routines with Skip and Yesterday, Calm, Diary and Journal, Skills, the Meadow visitor loop (0.198.0 small fixes, not checked on a device).
**Open:** a landing built around one Today action; Calm one tap away; a quiet "not logged today" mark on Reflect with no nagging; skills searchable with recents. A missed routine does not mean the owner wants routines gone. Existing: REP-036 (Tier 3 item 25).

### HEAL (P1)
**The owner says:** the Huawei reader is a major strength; records whatever data is available; hydration, nutrition and weight depend on remembering; wants one place for a dashboard of current health and activity, trends over time, a place to record, goals and progress, and recommendations or insights from the data. Hydration, nutrition and weight depend on remembering; the owner says so explicitly, and a gap in manual data is not a lack of interest in those features.
**In the code:** the Huawei reader and Health Connect reads, activity, sleep, hydration and measurements. **Nutrition is not built** (section 2.1).
**Open:** persistent quick add for hydration, weight and workout with presets and "repeat last"; choose which metrics show on Today; show each metric's state (read, entered by hand, stale, unavailable, none yet) and never a missing value as zero; do not advertise nutrition. Existing: REP-040 (Tier 2 item 15 is medication first), REP-046.

### MINT (P1, the first-use blocker)
**The owner says:** set-up is not finished: it is cumbersome and the module feels barebones, and no regular workflow exists yet. Money Manager did not stick; Agila was used and its card-template idea is liked. Adding accounts feels like a lot; manual set-up is disliked, though the owner understands banking apps may offer no import like Huawei Health. The account types are not understood, the card visuals could be better, and the other features are not understood. The activity form (expense, income, transfer, refund, fee/interest, balance adjustment; account, category, date/time, payee/note) has not yet been tried long enough to say whether it is convenient. Wants all of: money across banks, e-wallets and cash in one place; how much is safe to spend; expenses and spending; bills and payment schedules; savings and goals; net worth; and help with stocks, what to invest in and when to sell, which the owner sees may be too complex. MINT should be a comprehensive finance hub, not an expense tracker.
**In the code:** accounts, transactions, budgets, bills, imports, reconciliation; group and last digits behind a key.
**Open:** account creation as "where is this money?" with each type defined in one info dialogue (e-wallet, digital bank, cash, credit card = money owed, loan = liability); a cosmetic card-style picker that never changes accounting; a first run that ends on a useful overview after one account and a transaction or two; expense as amount, account, category; a worked example for Budgets, Bills, Reconciliation, Statement import, Savings targets and Reports. **Stock advice and sell timing stay out of scope** (`MINT.md`, the Bible), and the interview itself calls portfolio tracking and research "more tractable starting points" than personal buy/sell instructions; whether a read-only holdings record becomes a later scope is D11. Existing: REP-039 (Tier 2 item 16 is recurrence first).

### BASE (P1, medium)
**The owner says:** not used because set-up seems tedious; chores versus maintenance is unclear; Supplies is "mysterious"; Notes/Reference feels random. The real use is condo upkeep, with occasional jobs at another home (for example buying an air conditioner).
**In the code:** areas, assets, chores, references, restocks, schedules, supplies, warranties and maintenance completion.
**Open:** name-only quick add for each kind; example chips; what chore, maintenance, supply, asset and reference mean behind an info key; one default place with a second added later (D10); "buy an air conditioner" stays a PLAN task, the installed unit may become a BASE asset with warranty and upkeep, and the payment is MINT's; "Reference" as a clear destination. Existing: REP-029 (D3), REP-041.

### LINK (P2)
**The owner says:** mostly unused; adding people feels strange and awkward; information feels scattered across the module.
**In the code:** a directory, a profile, name-only Add Person.
**Open:** **walk the real profile and create flows on the phone first**, because the code already does the name-only step and the feeling remains; then one profile holding overview, notes, dates and follow-ups; one search across them; title-only follow-ups. Contacts import with the birthday reminder is REP-043 (Tier 2 item 14, the adoption blocker). D8 stands.

### GEAR (P2)
**The owner says:** the unit and currency converters are useful; the other tools feel poorly designed, too niche or inaccessible; suggested launcher widgets or a separate drawer.
**In the code:** favourites, recents, search; 18 tools; no GEAR widget.
**Open:** the two converters one tap from Home (D9); each remaining tool judged on its own design, not only reachability; D2 and REP-042 decide which tools to drop or merge.

### WORLD (P1, the clearest flaw in the best loop)
**The owner says:** the loop is collect finished rewards, choose quests, send a team, leave until the next result. "Send my strongest" is convenient. Each dungeon favours types, but the previous team stays selected, so the owner removes members one by one and scrolls about 20 Pokémon. **When picking by hand, type suitability comes before strength.** The roster is small now; recruitment and other modules will grow it. Quest choice weighs rewards, difficulty, type match and progression. Recruitment is fine; duplicates matter because they transfer for Candy and Poké. Speed-up minigames are welcome but not a priority. Rewards and recruitment are what keep the owner progressing.
**In the code:** Send my strongest, six presets, a suitability line per team; the manual list is the 20 strongest idle.
**Open:** specified in `WORLD.md` 2.3 (this audit): Recommend team ranked by type first, a poor-match mark, Replace team, a type-first picker. The expedition formula (3.2) is unchanged and any change is simulated first. REP-054.

### Launcher and rewards
**The owner says:** the PMD-style town coming to life on the phone is a main reason for using the app; rewards motivate, most clearly for easy actions (a five-minute Focus session, caring for Pokémon).
**Open:** keep the town calm and alive; no permanent dashboard cards over it; stable positions and predictable lifecycle first. Rewards stay bounded and for validated completion, never for sensitive content, and are not raised to prop up an unused feature: fix the entry flow first. The battery figures of section 3 belong here.

## 4A. The owner's own priority order, and how the backlog compares

From the interview (section 11 there), which is a UX proposal from reported friction and value, not an effort estimate. The cross-cutting rule is that the launcher, animations, interactions and Pokémon progression are improved throughout and never deferred until the utilities are done.

| Owner's order | Area | Backlog now |
|---|---|---|
| 1 | WORLD team selection | REP-054, Tier 1 (matches) |
| 2 | PLAN quick capture and recurrence | REP-057 (new); before this the backlog had only PLAN depth (priority, bulk actions) at Tier 2 item 18 |
| 3 | MINT onboarding and explanation | REP-056; MINT recurrence is Tier 2 item 16 |
| 4 | CARE information architecture | REP-056 and REP-036 |
| 5 | HEAL manual entry | REP-056 and REP-040 (medication first) |
| 6 | BASE (HOME) feature clarity and progressive set-up | REP-056, D10 |
| 7 | LINK person creation and profile | REP-056 (walkthrough first) and REP-043 |
| 8 | GEAR access | D9 |

The first draft of this audit ranked LINK and GEAR together at P2, which agrees; it put CARE and HEAL above MINT, which the owner's order does not.

## 5. Cross-cutting

- **Persistence.** Answered in section 2.1. The open piece is REP-020, and nothing old is deleted until the migration, backup, restore and rollback tests pass.
- **Source of truth.** The ownership rules the draft restated (BASE owns chores, MINT owns payment state, LINK owns a follow-up until converted, HEAL owns physical health, CARE owns mood) are already `DATA_AND_MIGRATIONS.md` section 5; no change.
- **Permissions.** The 0.199.0 manifest declares network, network state, vibration, activity recognition, fine and coarse location, microphone, notifications, set-alarm, calendar read, boot, exact alarms, package deletion and install queries (`QUERY_ALL_PACKAGES`), `WRITE_SECURE_SETTINGS`, and Health Connect reads for exercise, sleep, weight, body fat and hydration, plus the two Huawei services. `INTEGRATIONS_AND_PRIVACY.md` 4.2 is the permission matrix, 4.3 the manifest audit from 0.123.0 (remove what is unjustified) and 5.2.2 the Huawei reader; the public-release policy question for `QUERY_ALL_PACKAGES` was dropped with REP-047. Whether the 4.3 removals were all done, and whether each declared permission still has a use at 0.199.0, has not been checked here.
- **Testing.** The suite exists (`jvmTest`, the Robolectric Android tests of REP-033, the screenshot tests). No result from this audit; nothing here is called tested or device-verified.

## 6. Where each finding went

| Finding | Backlog |
|---|---|
| WORLD recommend team, poor-match mark, replace team, type-first picker | **REP-054**, Tier 1 |
| Why the app counts as running 6 h 9 m with the screen off | **REP-055**, Tier 1 (one `adb` session) |
| PLAN capture, Outstanding view, recurrence presets (the owner's priority 2) | **REP-057**, Tier 2 |
| First-use paths and the examples-without-captions rule (MINT, BASE, CARE, HEAL, LINK) | **REP-056**, Tier 2 |
| A read-only portfolio record in MINT | D11, no work until answered |
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
