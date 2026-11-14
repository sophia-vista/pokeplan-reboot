# Changelog

Versions are `MAJOR.MINOR.PATCH` ([semantic versioning](https://semver.org)), kept in the `VERSION` file. While the app is
below 1.0, a new feature bumps the minor number and a fix bumps the patch number. Bump `VERSION` in the pull request that
ships something; the first merge with a new number publishes that version's own GitHub release (the rolling "Latest build"
release always has the newest merge).

## 0.188.1

Fixes to the launcher's dungeon floor from phone testing (owner, 14 November 2026; `FEATURES/LAUNCHER.md` 4.5).

- **Nobody walks in the walls:** each Pokémon's walk starts again on a new floor (it kept walking the floor that was there before, through the new one's walls), and a meeting only steps onto a square it can stand on.
- **Only Ghost types pass through walls**, as in the games; Water types swim and Flying types fly over water.
- **Every trap springs** when a Pokémon steps on it (a found one sprang only one time in four), with the game's sound on Home; they step round the traps they know about and never stop on one.
- **Less lag coming back to the home screen:** paths, the tile pictures and their weather colours are worked out off the main thread, and the floor's tiles are worked out once per floor and drawn in a layer of their own.

## 0.188.0

The launcher town is a Mystery Dungeon floor (owner, 14 November 2026; REP-052, `FEATURES/LAUNCHER.md` 4.5). `RewardRules.VERSION` 27.

- **Rooms and corridors** from the dungeon's own tiles across every page, walls everywhere else, ponds in some rooms and lakes in the walls; a new floor each day, and Launcher settings → Town → New floor layout for another one. Rooms go where the widgets are not.
- **Real shapes:** each Explorers of Sky tileset's full autotile set (the 47 ways a square can meet its neighbours, the game's three variants) cut from the ROM by `tools/tiles/extract_sky_tiles.py`; the named towns that are Explorers dungeons use it too. Red Rescue Team tilesets keep rectangles until that ROM's sets are cut.
- **Walking on the floor:** the Pokémon follow paths over the floor; Water types wade into water, Flying types cross walls; widgets are soft walls (nobody stops under one). A Guardian comes up the stairs and goes down them.
- **Traps, for show:** hidden Warp, Spin, Slumber, Gust, Slow and Trip traps that a Pokémon finds by stepping on them, and sometimes a Wonder Tile; the game's own tiles (`tools/tiles/extract_sky_floor.py`).
- **Three finds a day** on the floor (`TownFinds`: berries, vitamins or a little Poké), drawn with the game's floor pictures; a tap picks one up into the Bag, once a day.

## 0.187.0

A kinder economy and a Poké Mart of its own (owner, 14 November 2026: "economy seems stingy", "anything for individual Pokémon growth should be generous", "held items?"). `RewardRules.VERSION` 26.

- **Poké Mart:** its own row in the Pokémon tab (it was a box at the top of the Bag), separate from World's Kecleon Shop. Exp. Candy 5 Poké as before, plus Super Candy 100 and each berry flavour 20; Buy 1, 10 or as many as you can afford.
- **More Poké from real life:** a focus session pays 1 Poké a minute (was 1 per 5), a task 10 per rank tier (was 2), and each paid Care, Heal, Mint, Base and Link moment 10 (Care's weekly rhythm 30; Care Play, steps and imports none). About twice the Poké over 60 days in `docs/economy-report.md`.
- **Kecleon Shop at about a fifth of its prices:** stones 400, Link Cord 500, Everstone 300, vitamins 100, perk held items 500 (Shiny Charm 1,500), Nature Mint 400, Ability Capsule 600, Bottle Cap 800, Tera Shards 1,000, Ability Patch 2,000. It no longer sells Exp. Candy (2,500 and rising); the Mart does. World's facilities, Café and Dojo keep their prices.

## 0.186.0

Heal keeps steps and the readings Huawei Health shows (owner, 14 November 2026, `MODULES/HEAL.md` 22B). Own PR: **schema 11** (`10.sqm`, `databases/11.db`; `heal_steps` and `heal_vital`, sealed like every Heal row). `RewardRules.VERSION` 25.

- **New records, history only:** daily steps, heart rate, resting heart rate, SpO2, skin temperature, and Huawei's own stress number and sleep score. Read from the Health page, the Sleep page and the step notification (parsers written from the owner's probe capture); in Heal → Readings, export and erase. Never judged: no target, no good or bad.
- **No duplicates:** one record per day (and bpm); the page and the notification, a repeated read, or the Sleep page and the insights line are one record; a larger step count or a new value for the day updates it. The preview says new, updated and already here.
- **Rewards:** steps pay per 2,000 (at most 5 a day: 1 Rare Candy and a friendship step each); reading records in pays 1 Rare Candy per family of record that brought something new, once a day each, at most 3 a day, for Health Connect too. No reading pays for its value. Simulated in `docs/economy-report.md`.
- **Reader:** also taps the Week tab of a page that has one, to capture it for the next probe (not parsed yet).
- `HealthKind.unsupported` and HEAL.md's "Not read" list updated.

## 0.185.0

Huawei Health records, nightly to weekly (REP-046, owner 14 November 2026, `INTEGRATIONS_AND_PRIVACY.md` 5.2.2). `RewardRules.VERSION` 24. Nothing stored on the phone needs a migration (new optional settings).

- **Saved into Heal after a preview:** workouts from Exercise records (type, start, duration, distance), weight and body fat from the Weight page (at their own time), and today's sleep (its length, ending at the get-up time on the Health page; other nights show no times, so they are left out). Reading the same pages again adds nothing twice.
- **Huawei workouts pay like ones you log** (rules version 24): same length rule and daily share on the session's own day, up to 8 days back so a weekly read still counts. A workout you also logged by hand is flagged as a repeat and is not paid twice. Health Connect imports still pay nothing.
- **Remind me to read:** Off, Nightly or Weekly at a time you pick, counted from the last read. A daily read keeps every night's sleep; weekly keeps every workout and the latest weight.
- **Save, numbers hidden** writes the capture to a `.txt` file instead of the clipboard.
- The walk scrolls each card page a little further (6 steps), for a week of Exercise records.

## 0.184.0

Huawei Health reader, second step (REP-046, `INTEGRATIONS_AND_PRIVACY.md` 5.2.2), from the owner's first capture. Nothing is saved or paid yet.

- **Banking apps open again.** MariBank and GCash refuse to run while a sideloaded accessibility service is on. Screen reading now turns itself off as soon as a read ends, however it ends, and Connections has a Turn off button. Optional one-tap reads: after `adb shell pm grant app.pokeplan android.permission.WRITE_SECURE_SETTINGS` from a computer, Read Huawei Health switches screen reading on for that one read and off after.
- **The walk opens the card pages.** After scrolling the Health page, Read Huawei Health opens the Sleep, Weight and Exercise records cards one at a time (found by their title), scrolls each a little and goes back. It stops if Back does not return to the Health page. These three cards are the only things it taps.
- **Only what is on screen is captured.** Huawei Health keeps its hidden Devices and Me tabs in the window, which made the first capture too long to paste.
- **The Health page is read:** steps, last night's sleep and get-up time, weight, heart rate and the day's exercise distance show on Connections. They are not saved, because the page gives no times; the card pages should have them.

## 0.183.0

Reading Huawei Health on the phone, probe stage (REP-046, owner decision 14 November 2026, `INTEGRATIONS_AND_PRIVACY.md` 5.2.2). Nothing is saved or paid yet, so nothing stored on the phone changes.

- **Heal → Connections → Huawei Health.** Two readers you turn on in Android's settings: screen reading (an accessibility service declared for Huawei Health only) and notification reading (drops every other app's notifications before reading them). On Android 13 and later, a sideloaded app needs App info → ⋮ → Allow restricted settings first; the page links there.
- **Read Huawei Health** opens it, scrolls its first page to the end, capturing each step, and comes back (at most 12 scrolls or 30 seconds; it never taps or types). Pages you open in Huawei Health yourself are captured too.
- **What it saw** is shown on the page, with the step count from Huawei Health's notification, held in memory only. **Copy, numbers hidden** puts the capture on the clipboard with every digit replaced by `#`, to share as the sample the parsers will be written against.
- Decided for the next step: workouts, sleep and weight read this way go into Heal's existing records and pay like manual ones (with a `RewardRules.VERSION` bump then); English labels only.

## 0.182.0

Evolution methods, items and the shop (owner, 14 November 2026). `RewardRules.VERSION` 23: the life modules drop items. Nothing stored on the phone needs a migration (new optional fields).

- **Every special evolution has a way in.** Checked on the spot: a known move (Mime Jr., Tangela, Yanma, Lickitung, Aipom, Piloswine, Steenee, Poipole, Clobbopus, Girafarig, Dipplin), a held item at a time of day (Happiny, Sneasel, Gligar), the party (Mantyke, Pancham), Attack against Defense (Tyrogue), the nature (Toxel), a Fairy move and friendship (Sylveon), 400 Exp. Candies (Meltan), 999 Gimmighoul Coins (Gimmighoul), a Link Cord (Finizen).
- **Evolutions done with the phone:** Inkay turns into Malamar when you hold the phone upside down, Milcery into Alcremie when you spin with it (gyroscope), and Pawmo, Bramblin and Rellor after 1,000 real steps (the step counter keeps counting while the app is closed; a 5-floor World expedition also counts).
- **Evolution Trials in World**, in the evolution's own region: a team with the Pokémon reaches the trial floor (Galarian Sirfetch'd, Runerigus, Weezing and Mr. Mime in Galar; the Hisuian forms, Wyrdeer, Basculegion and Overqwil in Hisui; Alolan Marowak in Alola; Goodra in Kalos's rain; Annihilape and Kingambit in Paldea; Shedinja in Hoenn). The Pokémon's page says where.
- **Six new World regions:** Unova, Kalos, Alola, Galar, Paldea and Hisui, nine dungeons each with their own Pokémon, five story chapters, and their legendaries as quests and Guardians. They have no map picture yet.
- **Evolve asks first**, with Cancel, and **Stop** during the evolution cancels it with nothing used up. A Pokémon **holding an Everstone** does not evolve.
- **Items from every module:** each Care, Heal, Mint, Base and Link moment has a small chance of a themed item (Care sweets and soft stones, Heal vitamins and claws, Mint Nature Mints and coins, Base stones and the Everstone, Link the Link Cord and trade items), and a finished Mint review always gives a Nature Mint.
- **The Kecleon Shop** always sells every evolution stone, the Link Cord, the Everstone and Gimmighoul Coins, and has a shelf of five more items that changes each day.
- New item icons: Everstone, Oval Stone, Razor Claw, Razor Fang, Strawberry Sweet, Gimmighoul Coin (PokeAPI/sprites).

## 0.181.0

The launcher's town (LAUNCHER.md 6.2): calmer, more natural roaming, and a setting for it. Cosmetic only: no reward number changes, `RewardRules.VERSION` is unchanged, nothing stored needs a migration.

- **How lively** (launcher settings → Town, under "Pokémon walk about"): ten blocks from Sleepy to Restless, default Calm (4), which is quieter than before. It sets how often the Pokémon set off and how long they rest; Restless is about as busy as the old town.
- **Rest and restless spells:** each Pokémon alternates between resting (standing, looking about, grazing, shifting a step) and pottering about (two to four connected legs with short pauses), with a temper of its own, so the town has a rhythm instead of everyone rolling a fresh dice every beat.
- **Walks that look walked:** each leg carries on roughly the way it was going (a correlated random walk) and bends back towards the Pokémon's own corner the further it strays, leans along the back and front of the ground instead of bouncing off it, avoids stopping on top of another Pokémon, and only now and then crosses the town. A leg follows a gentle curve and eases in and out instead of a straight line at one speed, and the stroll is a touch slower.
- The volume and liveliness rows' value text now shares the label's centre line.
- **The clock widget's battery shows its charge:** empty, one, two or three bars (Pixelarticons' battery levels), red at 20% or lower, with a bolt beside it while charging, instead of an empty outline whatever the charge.
- **The agenda widget on a light day:** on the widget grid the list starts at the top instead of floating in the middle of its cells, it shows only whole rows (never one cut in half), the time column is as wide as the times instead of a third of the row, and the next day with anything on it (within a week) fills the room left, dimmed under its own heading ("Tomorrow" or the date).
## 0.180.0

A better PC. No reward number changes; `RewardRules.VERSION` is unchanged, and nothing stored on the phone needs a migration.

- **The PC shows the Pokémon resting in it**, as before, and a **Show all Pokémon** switch lists every one you own: the buddy, the leads and parties, the Guardians, the World teams and the Pokégear helpers. Each tile then names its place (Buddy, Plan lead, Care, Heal Guardian, World, Pokégear).
- **Where** joins the sort and filters while every Pokémon shows: PC, Buddy, Leads, Parties, Guardians, World teams or Pokégear. Clear keeps the switch and the sort.
- An empty PC with Pokémon elsewhere offers Show all Pokémon instead of a dead end.

## 0.178.0

Plan's dates (REP-030, PLAN.md sections 4.7 and 5.1). No reward number changes; `RewardRules.VERSION` is unchanged, and nothing stored on the phone needs a migration.

- **One set of day keys** wherever a task or a Plan event gets a day (Move to, Deadline, quick add, a new or edited event): Today, Tomorrow, **This weekend**, **Next week** (the coming Monday), **Pick a day** and No date. A key that names the same day as an earlier one is left out, so two never light up together. Quick add gets Pick a day too.
- **Repeat says more:** Yearly joins the keys, and Weekly takes every 1 to 4 weeks and the weekdays. A repeating task reads it in words ("repeats every 2 weeks on Mon, Thu"), and one the keys cannot show (every 3 days) is said in words instead of "Custom".
- **Reminders:** 30 min and 2 hours before join the keys, and **Other** takes a typed length before, up to a week.
- **A moved day of a repeating task says "this one only"** and where it came from; the others keep their days.
- **The Calendar's tray says how to place a task** ("Tap one, then an hour (or drag).") until the first task has a time.
- Removed the day-load estimate against an "awake" window: it was worked out but never shown.

## 0.177.0

Plan's task row and card (REP-030, PLAN.md section 10.1). No reward number changes; `RewardRules.VERSION` is unchanged, and nothing stored on the phone needs a migration (a list's category is a new optional field).

- **The check key comes first on every row.** A finished task's is ticked in a quiet colour and a won't-do task's is a cross; tapping either reopens it (the Reopen button no longer pushes the title aside). The rank is a small framed tag after the title.
- **The When tab is the day and the list**, then one **Time and repeat** row that says what is set ("Any time, no reminder, does not repeat") and unfolds the time, reminders and repeat.
- **A finished task's card has Reopen and Delete** side by side.
- **A list can carry a category** ("New tasks are" in Edit list): a task added to it, by its quick add or with `#List`, starts with it unless the words name one. The tasks already in the list keep theirs.
- **A task can count toward a goal** (Goal on the Edit tab: None or an unfinished goal; one goal at a time). A goal with no milestones shows "N of M tasks" on its card. Linking pays nothing.

## 0.176.1

Where a Pokémon is, shown where it matters (REP-009, the last open part). No reward number changes; `RewardRules.VERSION` is unchanged, and nothing stored on the phone changes.

- **The Gear helper list says where each Pokémon is now** ("In Mint's party", "Your buddy"), since choosing one takes it from there, and leaves out a team on an expedition (choosing one was refused without a word).
- **"Move to..." on a Pokémon reads its real place.** The PC row said "Here" for the buddy, a Guardian, a World team member or a Gear helper, and its button did nothing for them; it now sends a Guardian, a team member or a helper back to the PC (`GameStore.sendToPc`), and stays off for the buddy (choose another buddy instead). On an expedition under way, Make buddy and Move to... are off.
- A Gear helper's place names its tool ("Helping the Flashlight").

## 0.176.0

Plan's structure (REP-030, PLAN.md section 3): Modules → Plan is Today, with one menu. No reward number changes; `RewardRules.VERSION` is unchanged, and nothing stored on the phone changes.

- **Plan opens on Today.** Quick add, the day's progress, the task to do next (Focus on this, Pick for me; a daytime task before an overdue or evening one), From before with Move to today, Today, **today's unmet targets** as a group ticked with the same check key, This evening, and Done today folded. The Planner menu is gone.
- **One menu below:** Inbox, Upcoming, Lists, Calendar, Focus and More, as rows with counts. More holds Progress, Goals, Targets and All tasks.
- **Inbox** is an open task with no list and no date; **Upcoming** has no 7-day cap and a Calendar key in its header.
- **The weekly review is a card in Progress** when one is ready: each unfinished task has Move (tomorrow), Later (next week's first day) and Won't do (with Undo); past reviews and their notes are listed under Progress.
- **Done is a preset of All tasks** (the Done key beside Search), and shows won't-do tasks too (`TaskFilter.Status`).
- **Plan's settings page is retired:** the length and Deep Focus a session starts with become the usual ones; the week start and the phone's calendars sit at the foot of the Calendar.

## 0.175.1

Automated Android tests, first slice (REP-033; owner: automated tests over manual phone checks).

- **Fixed: a damaged database was wiped instead of recovered.** androidx's SQLite helper deletes a database it finds corrupt and opens an empty one, so the app's own recovery (the damaged file kept aside, the newest daily backup put back) never ran and the app would have opened empty. `AndroidDatabase` now leaves the file alone and reports the damage, and `LiveStore` recovers as designed.
- **`androidApp` has tests**, on the JVM with Robolectric (`./gradlew :androidApp:testDebugUnitTest`, run by CI's Android step): the database cutover on the five 0.123.0 save fixtures, a process death, a corrupt and a newer-schema database, Heal, Mint and the daily backups sealed (checked in the raw database bytes) and failing closed without the Keystore key, the shared reminders (exact alarms with no title in them, Done and 10 min on the notification, quiet hours, a reminder missed while the phone was off, the boot, alarm and time-zone receivers), the Today widget's tick against the app's store, and a focus session across a process death.

## 0.175.0

The HOME module is now **Base**, after Hoenn's Secret Bases (owner, 13 November 2026), and World's Base is now **Town**; a leaner test suite. No reward number changes; `RewardRules.VERSION` is unchanged, and nothing stored on the phone changes.

- **Base.** Replaces the "Nest" label 0.174.0 gave the module (the owner's final choice is Base). Modules → Base, its party, perks, region ("Base Region"), tutorial ("a 5 second tidy of your secret base"), reminders channel, the launcher's search and every place that named the module say Base; its scene is a secret base under the trees (`forest`). "Home" now means only the Home tab. In code: `base-core` (package `app.pokeplan.base`), `BaseStore`, `BaseData`, `BaseRules`, `BaseEvent`, `BaseTables`, `screens/Base.kt` and the rest. Stored names stay so no migration is needed: the `home_*` tables, save keys (`homeLeadId` and `homePaid` through `@SerialName`), ledger and reminder ids, and the constants stored by name (`Region.HOUSEHOLD`, `Module.HOME`, `Place.HOUSEHOLD`, `ReminderModule.HOME`). New reward-log entries start "Base: "; old "Home: " entries are still read.
- **Town.** World's headquarters destination (facilities, Skills, Finds) is called Town, so one screen never shows two Bases (`WorldSection.TOWN`, WORLD.md section 2).
- **Fewer, more important tests** (Bible section 3, REP-050): `LegacyJsonImporter`, used only by its 9 tests, is deleted with them (REP-025 d), and 11 tests of constants, copy, a test fixture or a removed feature are gone; a Gear test loses its retired-loan half.
- Docs: the owner's decisions of 13 November 2026 (lean tests, BASE, Town) in the Product Bible; REP-049 (Secret Base decorations) and REP-050 in the backlog.
## 0.174.0

The UI/UX review of every screen (REP-031) and its fixes, against Laws of UX, Laws of UI and the owner's bar: "as simple as Linear or Apple Notes; cut taglines, helper text and secondary labels; if a screen needs them, fix the layout." `RewardRules.VERSION` is 22 (one rule change, below).

- **Screens stop explaining themselves.** About 450 sentence-length strings were in the screens; the paragraphs under Settings switches, privacy and method notes, unavailable-feature notes, legends written as prose, research notes in Mint Import and Heal Connections, the perks and research captions on every module page, "Label:" prefixes and echo lines are gone. `CopyBudgetTest` fails on a new caption of eight or more words in `screens/`; a long string that is a result, warning or game line says so with `// copy-ok`. `EmptyState` is a title and one action (its line parameter is gone).
- **One input per value.** A date is one row of keys (the typed box opens with the calendar); times and lengths lost their nudge keys and their "9:30 am" echo; a typed value is rewritten in the app's spelling when the box loses focus.
- **One colour per module.** Each module screen sets its own tone (`ModuleTone`), and `Pixel.Tone` replaced the blue and green defaults inside modules, so keys, chips, frames and bars match the module wherever a screen is drawn. Heal's Goals and Care's Trends cards take their module's colour; the Pokémon tab is neutral.
- **Module pages lead with the work**: lead stage, today's items, the section cards, then the party, which is one row of six sprites. Lists sit in one box with lines between rows (Help, Bag, routines, history, accounts, bills, budgets, skills). Add forms open from one key. Checks come first on every checkable row.
- **Sections that hold records are rows with counts** (`MenuList`, owner's decision): the Planner's views, the Pokémon tab, World's Journal and Me.
- **A module's first Pokémon comes with its first real record** (owner's decision), not a 5-second demo card (`GameStore.firstRecordEgg`; `POKEMON_ECOSYSTEM.md` 10.2.1, `docs/economy.md`).
- **HOME shows as Nest** (owner's decision), so "Home" is only the tab; code ids and saves keep HOME.
- **The tab bar keeps the module's colour inside a module** (owner's decision; `DESIGN_SYSTEM.md` 5.1 now says so).
- **Fixed:** Care's mood keys cut to "Ro…" (also in Home's Quick log); Mint category names wrapping a letter at a time; Link's follow-up label drawn over its placeholder at large text; a Heal goal chip in the wrong font (the pixel font has no brackets); week titles cut to four letters on phones (three days at a time now); a log tag leaking into World's Finds; Back on the right of the Pokédex entry.
- Plan: one Start key, the Planner's filters behind one key. Mint: Available first, categories as rows. World: the "Since you last looked" report is a headline and the finds. Gear: the star sits in the helper bar; the Picker shows its keys on the tapped row.

## 0.173.1

Reminder and World clock fixes left by the 0.167.2 follow-up (REP-026 a, b, d). No reward number changes; `RewardRules.VERSION` is unchanged.

- **A reminder due while the phone was off now shows when it comes back** (REP-026 a). The scheduler remembers when it last planned; at the next plan (boot, opening the app, any alarm) the reminders the modules had planned for the time in between, and that did not go off, are held to go off at once, checked again by their module first (a task still open, a routine not yet ticked, a streak still for today). Only the latest of each source counts (a daily routine missed three days is one notice), only the last 24 hours are looked at, and one that went off already is never repeated (`ReminderCoordinator.missed`, tested). The same covers an alarm the phone delivered so late that a newer plan had dropped it.
- **Two reminders can no longer share one alarm** (REP-026 b). An alarm and a notification button are now told apart by the reminder's id in the intent itself, not only by `id.hashCode()`, so two ids with the same hash no longer replace each other's alarm (or make one notification's Done act on the other). The alarms an older version set are cancelled once.
- **World: a clock set back and forward again no longer pays the same hours twice** (REP-026 d). Teams still climb on the earlier clock (no freeze), but the moment the clock was set back from is kept (`WorldData.rewoundFrom`), and when the clock reaches it again each team's settle point moves up to it (WORLD.md 3.1). The most a back-and-forward change can add is the time really spent on the earlier clock.
- Still open: REP-026 (c), asking before a damaged database with no backup falls back to the old files.
## 0.173.0

The rest of REP-025: corrections in Mint, and one copy of the parts Home, Link and Mint shared. No reward number changes; `RewardRules.VERSION` is unchanged.

- **Mint: settle a bill again.** A paid or skipped occurrence used to leave the Bills list for good. It now waits under "Settled lately" for 60 days, where its amount and day can be put right (Paid, Part or Skip again) or the settlement taken back, with Undo. A partly paid bill opens with what was paid so far already filled in.
- **Mint: a budget's period can change** with its amount (weekly to monthly and back); a second budget for the same category and period is refused, as when adding.
- **One screen-lock door.** Mint's lock and the lock in front of Care, Heal, Home and Link were two copies of the same code; they are now one (`ScreenLockGate`). Nothing changes on screen.
- **Home and Link share their list row, tabs and date field** (`ModuleParts.kt`); nothing changes on screen.
## 0.172.1

The easy fixes left by the Plan audit and the Care review, the owner's answers of 12 November 2026, and the Home dashboard. No reward number changes.

- **Plan.** "Add a task" in the empty Next 7 days and All tasks opens Today with the keyboard up (it did nothing); the empty Done offers "Open Today"; the empty Inbox and Targets point at the field above them, and Targets says "Add a target". "No date" clears the planned day only, not the deadline. "Won't do" offers Undo. A task's edits are saved as they are made (a short pause after typing, and when the tab closes): no Save key, nothing lost by switching tabs. Steps can be renamed, removed and unticked; a goal's milestone can be unticked; a goal deleted from its editor offers Undo; a target can be edited (words, how often, rank, category) and deleted with Undo; a finished task can be deleted. Unticking keeps "paid", so ticking again pays nothing more. The calendar pages freely back and forward, and a week is the calendar week from the day Settings sets. Every delete in Plan is grey; the Targets add key is no longer red; Give up asks "Really give up?" first; Progress points at Plan, Goals.
- **Care.** The sweet berry is offered to a visitor even when the Bag holds all five flavours. The Meadow's corner labels are readable in the dark palette. A visitor is visited or played with in the Meadow itself, and Care Play's invite list is your own Pokémon with Show more. The journal and the earlier diary cards end with Show older, and a diary card's urge ratings show in its history row. A calm break survives leaving the screen and finishes in an effect, not during drawing (one left for ten minutes past its length is dropped, not paid). Surprise me changes after each time played. Care's landing and the Meadow call `arrive()` once. Words: routines earn Exp. Candy and a little friendship (not berries); the Egg meter line counts a play; `befriend` says up to four a day. Removed the dead `CareRules.paidOn`.
- **Home dashboard.** The new-rewards box shows on Home only (it showed on every module page, so a World Pokémon appeared in Care). A Quick log card (task, +250 ml, expense, and how is today in one tap), and "Also today" adds Mint bills due in the next 3 days and Link dates this week (counts only, never a name or an amount; nothing for a locked or gated module).
- Left for the owner or for a bigger change: REP-030 in `docs/pokelife/IMPLEMENTATION_BACKLOG.md`.

## 0.172.0

Exact dates, times and lengths in every module, and edit screens where a record could only be added or deleted (owner, 12 November 2026: "I would prefer to input specific dates and times, to have edit functions"). No reward number changes; `RewardRules.VERSION` is unchanged, and a correction never pays and never takes back.

- **Typed entry, one set of fields everywhere** (`ExactInput` in app-core, `ExactFields.kt` in the UI, `docs/pokelife/FEATURES/DESIGN_SYSTEM.md` 6.4). Every date field (Plan, Heal, Mint, Home, Link) now has a text box beside its keys and month: `2026-11-07`, `7/11/2026`, `7 Nov 2026`, or `today`/`tomorrow`; a time takes `9`, `930`, `21:05`, `9pm`, `noon`; a length takes `45`, `1h30`, `1:30`. Each reads back what it understood, and a day or time out of range (a payment in the future, a day that does not exist) says so instead of being guessed. Tested spelling by spelling (`ExactInputTest`).
- **Plan.** A task's day ("Move to") is a full date field (it had three keys and no calendar); its time of day is typed; a deadline has its own time (it could only keep 23:59); the estimate is typed (`1h30`) as well as nudged. A goal's target date is typed, and **goals and milestones can finally be edited and deleted** (title, area, target date, milestone names; before, a typo meant a new goal and a goal could never be removed). A repeating target (frequency task) can mark or take back **any earlier day** ("Another day"); it counts toward the target and never pays. A calendar event's day, start time and length are typed, an all-day event's first and last days are typed, and the focus length (the screen and the default in Settings) and quiet hours are typed.
- **Care.** A routine's time is typed. The diary can be filled in for **any earlier day** and an earlier card opened and corrected (one tap on it); a journal entry can be dated to an exact moment when written, and **edited afterwards** (words, tags and moment). `CareStore.editEntry` existed with no screen.
- **Heal.** An activity's end and start, a night's two moments ("fell asleep" and "woke up", with the length worked out), a drink's and a measurement's exact moment are typed. **Drinks, nights and measurements can be edited** (`updateDrink`, `updateSleep`, `updateMeasurement`, with tests); an activity's edit now also changes when it started. The wake-up alarm and each hydration reminder are typed times, and a new reminder time is chosen before it is added.
- **Mint.** A record's moment (day and time) is always shown, not behind "Date, payee and a note"; a bill is settled on a day you choose (it was always today); a bill's first due day and frequency can be corrected **until a payment is recorded** (then they stay fixed, so no settled occurrence is rewritten).
- **Home.** A record of work can be edited (what was done, the exact day, the item, notes, cost) from the maintenance history and from an item's page (`updateMaintenanceRecord`). Warranties are editable too (merged with the HOME audit's change).
- **Link.** A follow-up takes its day when it is added (it could only be set afterwards), and a follow-up on a person's page opens the same editor as the main list.
- **Gear.** A timer's length is typed as hours, minutes and seconds (`90`, `1h30`, `1:30:00`, `45s`) and an existing timer can be **renamed and resized** (it goes back to ready).
- Not changed, for the owner: reminder hours in Mint, Home and Link and the streak reminder are whole hours (a stored number, so minutes need a schema change); a budget's period and a bill payment's amount stay as they are (settle again to correct a payment).

## 0.171.4

HOME audit (end to end: rules, store, reminders, screens). No reward number changes; `RewardRules.VERSION` is unchanged.

- **Editing a maintenance schedule no longer moves the date it counts from.** Any edit, a rename included, reset the start date to the next date, so a monthly job begun on the 31st and standing at 28 February drifted to the 28th for good. The start now moves only when the next date or the kind of repeat changes.
- **A tick can be taken back wherever it was made.** Ticking a chore or maintenance offers Undo. Before, a chore ticked on the overview, or an overdue repeating chore ticked anywhere, left no row to undo it from, and finished maintenance could not be undone at all (deleting its record left the schedule moved on).
- **Search opens something every time.** Archived chores, finished one-time maintenance and the warranties of an archived item were found and then opened an empty list; a chore done today opened the wrong tab.
- **Warranties can be edited** (tap one); before, a typo meant deleting and typing it again. Archived items can be deleted, like archived chores and supplies.
- **"Bought" on a low supply asks for the new quantity.** Saving it blank did nothing and left the supply on the list.
- **A chore's instructions show when it is opened**, as maintenance instructions already did; they were visible only inside the edit form.
- Faster: the chore list reads the history once instead of once per chore, and the Home menu works out the overview once, not twice. Smaller: an unreachable branch and a wrapper in `Schedules`, and a never-set error in the schedule options, are gone.
- What was left for the owner to decide is REP-029 in the backlog.

## 0.171.3

WORLD story rewrite, first pass (scripts only; no rules, rewards or scene ids change).

- **The opening, Rescue Team and the first four Explorers chapters are rewritten** for warmth and continuity: the lunch Mew steals in scene 1 comes back in scene 6, the Sentret siblings and Pelly's and Braviary's habits recur, the visiting team sleeps in the loft over Persian's bank and walks the Rescue Team's roads one step behind (blanket, berries and all), and Hoopa now drops the team into Explorers with a joke instead of a lecture. Side characters get small lives of their own (Dugtrio's worried family, Caterpie's waiting, Bidoof's third apple).
- Still to do: Explorers 5 to 8, both post-story sets and the four regions.

## 0.171.2

Gear audit (end to end: the 18 tools, their saved state, the Android adapters). Nothing here pays, so no reward number changes.

- **The calculator's Ans key gives the last answer.** It typed the value of the sum being written onto its own end ("2+3" became "2+35").
- **A time converted in the World clock follows today's summer time.** It was worked out for 1 January 2000, so London or New York were an hour out for half the year.
- **The Picker's "Skip last" skips the last pick.** With motion on and the history off it skipped whichever name the roll happened to flash last.
- **The Sound meter's quietest, average and loudest follow the offset.** They kept the offset the tool was opened with.
- **Currency does not fetch on every opening.** The reference rates are a working day old most of the day (and all weekend), so each opening asked the service again; a copy fetched in the last 6 hours is now used, and Refresh still asks.
- **One row over a tool instead of two or three.** The star and the helper share a row; a tool with no helper shows no "Helper / Show" strip (GEAR.md section 6: never an empty placeholder), and the large helper box with "Make it small" is gone. A helper is still chosen from the row when you own one that fits.
- A search with no match says so with one way back; "After 5.0 years" reads "5 years"; the QR tool shows no empty scan box where there is no scanner.
- Removed: `GearTool.blurb` and `move`, `GearData.helperCompact` and `currencyPair`, `convertCurrency` (none was read; old saves load, unknown keys are ignored), a second copy of the "not available here" box and 50 unused imports in `GearTools.kt`.
- Left for the owner: REP-028 in `docs/pokelife/IMPLEMENTATION_BACKLOG.md`.

## 0.171.1

Mint audit: fixes only. No reward number changes.

- **A transfer goes where the screen says.** The destination shown as chosen is now the one saved; before, with accounts in two currencies, the default could be an account in the other currency and the transfer was refused.
- **Activity shows all of its history.** The list stopped at 50 records with no way further except search; it now has "Show more". The quick "Income" or "Transfer" key on the overview no longer sticks for the next visit.
- **Bills.** The first due day is any date (the shared date picker), not five fixed offsets. A bill's name and amount can be edited (tap it under "Your bills"). Paid, part paid and skipped each offer an undo, since a settled occurrence leaves the list. A part payment that covers the whole bill is saved as paid.
- **Weekly review** uses the app's first day of the week on the screen, as the rule already did.
- **Faster with a long history:** every account's balance is worked out in one pass over the records; the Activity search is not redone on every keystroke in the entry form; the import preview looks for look-alikes by day.
- Removed: the "Close" key under a record (tapping the row closes it).
## 0.171.0

Link audit (end to end: rules, store, screens, reminders, tables). No reward number changes.

- **Find a person by phone, email or address.** The directory and the one search matched names, organization, role, category and tags only; a phone is matched by its digits however it is spaced (three digits or more).
- **Add a date from a person's profile** (LINK.md 3.3). It was only possible from Dates, by picking the person again.
- **Deleting a person can be undone** (their notes went with them for good).
- **Opening one search hit no longer sets the tab of the next profile** (a note hit left every profile opening on Notes).
- **A cancelled follow-up shows the day it was cancelled**, not always today.
- **Link and Plan agree about a handed-over follow-up.** Reopening one Plan finished is done in Plan (the button is gone; it used to bounce back at once), and a task Plan takes back to open opens the follow-up here too. Neither pays.
- **"N due" counts only what Link owns.** Home and the Modules card counted follow-ups that had become Plan tasks; the Overview already left them out (`LinkStore.dueNow`).
- **A long directory shows 40 people at a time** (every row was composed at once).
- **Categories:** a person's form has a New category field; Settings no longer has a Categories box whose chips did nothing.
- Tests: three new in `LinkRulesTest` and `LinkStoreTest`.

## 0.170.1

HEAL audit (end to end). No reward number changes; `RewardRules.VERSION` is unchanged.

- **Every record stays reachable.** Activity, Sleep and Body histories end with **Show older** (30 more each tap). Before, anything past the newest 30 activities, 21 nights or 10 measurements could not be seen, edited or deleted one at a time.
- **A night that has not ended is refused** ("That night is not over yet."), as an imported one already was; "Woke today" at a time still to come used to save a night in the future and show it as the last sleep.
- **An active-days goal is at most 7**; the goal field no longer accepts a number the store then silently dropped.
- **One delete pattern.** Deleting an activity, or an import from "Might be the same", now offers Undo like drinks, nights, measurements and goals (the activity's confirm step is gone; the import had neither).
- **One save per log.** A paid activity or drink is written once, already marked, instead of saved and then rewritten: half the passes over Heal's sealed rows for the commonest action.
- Smaller: the 7 or 30 days choice in Connections is a pair of tab buttons like every other choice; histories are sorted once per change, not on every keystroke; two captions that restated the screen are gone; `updateDrink`, `updateSleep` and `drinksToday` (no caller) are deleted.

## 0.169.0

Pokémon asset audit (11 November 2026, `docs/ASSETS/ASSET_USAGE_AUDIT.md`) and what it found. No reward number changes. The app grows by roughly 25 MB of sprite sheets (the new rows and faces) and loses 12.5 MB of Black/White sheets it never drew.

- **Story faces match the story.** Eight more SpriteCollab portrait faces (Sad, Angry, Worried, Teary-Eyed, Pain, Sigh, Dizzy, Stunned) join the first six. Sad, angry, teary and worried lines (about 225) show those faces instead of Normal or Surprised; a form without a face shows a near one, then Normal. Focus shows a sigh when you leave and a dizzy or sighing face when a session fails.
- **A shiny is never in the regular colours.** The 282 forms SpriteCollab has no shiny for (sheets, portraits or both) get one recoloured from their real regular and shiny still sprites (`tools/sprites/derive_shiny.py`, listed in `assets/pmd/derived-shiny.tsv`); a real one replaces it on the next fetch.
- **The buddy reacts with Mystery Dungeon status icons, not emoji** (`ReactionIcon`): a sparkle, "!" and "?", the Zz, and by type a flame, a bolt, a block of ice, a fist, a shield or a drop, with a heart, a note, the sun, the moon, a star and an apple (real item icons and glyphs) where the games have no icon. The Home and module stages and the buddy widget draw them. Four stat icons (flame, shield, fist, ice) were cut from the status sheet.
- **Walking keeps its direction.** A Pokémon in the town that stops stands facing the way it went (new Idle side and up rows). New one-offs: Wake (the buddy stretches awake after a focus session), Pose (just hatched), Rotate (evolved, or a level gained on the Pokémon page), DeepBreath (Care's calm break). A form without a row hops instead; Eat falls back to Hop the same way, so feeding always shows a bite or a hop.
- **Sound.** Every owned Pokémon's tap and pet plays its own slightly detuned cry; up to three cries overlap on Android; the town's meeting icons have their game sounds beside them when the buddy is in the meeting. Unused sounds (`pokemon-center`, the drawer pair, `reminder`, `timer-done`) and the skull icon are no longer bundled, nor the Black/White sheets of forms that have a Mystery Dungeon one.
- Three portrait credits that were blank now name the SpriteCollab contributor. `PmdAssetsTest` checks every sheet table against its files.

## 0.168.0

Design review batch 1. No reward number changes.

- **The zero is no longer an 8.** Both Pokémon DP font files get a bar through the middle of the 0 (`tools/fonts/patch_zero.py`, the licence covers the changed files). The PMD dialogue font's zero already has one; it is a bitmap drawn by `PmdText`, not a font family, so it is not used for running text.
- **₱.** The font gains the peso sign (U+20B1, drawn as a P with two bars) and Mint shows `₱1,250.50` for PHP; other currencies keep their code.
- **Mint categories.** Settings has a Categories box (spending and income: add, rename, put away, bring back, delete when nothing uses it), and the entry form has a New key beside the category keys. The store already supported all of it; no screen reached it.
- **One date picker.** `PixelDatePicker`: Today and Yesterday as keys, and Pick a date opens a month to tap, up to today. Mint's entry form uses it, so a record can be made for any past day. Other modules and editing a record's date follow.
- **Undo for more than tasks.** The undo bar now also brings back, for 10 seconds, a deleted budget, bill or record (Mint), drink, sleep, measurement or goal (Heal), diary card or journal entry (Care), chore, supply or warranty (Home) and note (Link). Plan's lists and events are still without it.
- **Any day for a record.** Heal drinks and body measurements take a past day, a Mint record's date, account and category can be edited, and Home and Link use the date picker instead of a typed `2026-11-07` (`PixelDateField` allows no date and future days).
- **The work comes first on a module page.** The lead's picture window is a 144 dp strip (it was 248) with a smaller sprite, and the new-rewards box runs a payout together as small icon-and-words pieces (a new Pokémon keeps its own row).
- **Also today, on Home.** One card, only when something waits: Mint bills to settle, Home things to do, Link follow-ups due, and Heal's water so far with a +250 ml key. Words are counts only, never a bill, chore or person; a module behind the screen lock (and Mint with its lock on) shows nothing.
- **Exp. Candy.** The everyday level-up candy is shown as Exp. Candy (the games' Exp. Candy icon) instead of Rare Candy, which a fan reads as rare. Only the name and icon moved: its id, rules and every reward number are unchanged, so saves and the ledger are untouched and `RewardRules.VERSION` stays. Super Candy is unchanged. A true Rare Candy prize is not built (it needs an owner decision on where it is earned).
- **One tap for mood (Care).** "How is today?" on Care's page: Rough, Low, Okay, Good, Great as five keys, kept on today's diary card (`DiaryCard.mood`, no schema change), shown in Trends, never paid. PMD portraits for the faces are waiting on fetching more emotions (the strips hold only Normal, Happy, Joyous, Inspired, Determined and Surprised).
- **One place list on a Pokémon.** Twelve role buttons became Make buddy and one Move to... list: the PC and each module's party with "n of 6", Move here, and Make lead in the party it is in.
- **Undo for Plan's lists and events too.** Deleting a list (its tasks return to it) or an event, one occurrence or all, shows the undo bar for 10 seconds.
- **Two ranks, two names.** Home and Me say "Trainer rank" and World says "Exploration rank", so one word no longer means two ladders.
- **Link: the month is a key.** A date's month is picked from twelve keys; the day and the optional year stay short fields.
- **Module colour on keys.** Keys and chips default to the open module's accent (blue only outside a module), the lead buttons on a Pokémon use each module's accent, friendship is pink, and Heal's Log activity is no longer red.

## 0.167.2

What the 0.167.1 audit found and left. No reward number changes.

- **Fix: on a phone out of storage, another store could bring the older save back.** A save that does not fit is kept in memory; a widget or a reminder opening its own store then read the older one from the disk and could write it over the unsaved changes. The stores of the process share what could not be saved until a save fits (`Storage.saved`, `SharedDocuments.keep`).
- **Fix: a snooze or a repeat that came due while the phone was off was never shown.** The planner looks only ahead, so it sat until it was dropped a day later. It goes off at the next plan (boot, opening the app), after the usual check that it is still wanted.
- **Fix: putting one reminder off cleared every held reminder of the same thing.** A snooze lets go of its own reminder and its repeats; "Done" still clears them all.
- **A restore says what it did not bring back.** A backup that lacks a module (an older one), or holds Heal or Mint sealed with another phone's key, leaves that module as it was; the message now names it instead of saying only "Restored" (`LiveDocuments.replaceAll` returns the names).

## 0.167.1

Repo-wide audit (11 November 2026): fixes found by reading every state machine, dead code out, and the agent files brought up to date. No reward number changes.

- **Fix: a focus timer that ran out inside the 10 second grace failed the egg.** Leaving with less than 10 seconds to go and coming back later ended as "left the app", although time inside the grace counts as focus. It is a finished session now (`FocusSession.tick`).
- **Fix: a recurring task moved to another day came back twice.** The day it was moved from had no task planned on it, so the next refresh made that day's instance again, with the same id. An instance is known by its id now (`RecurringTasks.instancesToCreate`).
- **Fix: a transfer could leave a Pokémon's seat behind.** A Pokémon on a World team, in a Guardian slot or helping a Gear tool was removed while its place still named it, and one out on an expedition could be transferred. The transfer waits for the expedition and gives the place up (`Places.movable`, `Places.vacate`).
- **Fix: the screen-lock door of Mint, Care, Heal, Home and Link could stay open.** The once-a-second "still here" tick kept running after the app was left, which counted as coming back. It counts only while the app is in front.
- **Fix: restoring a backup did not reload Link**, so Link's next save could write the old records over the restored ones.
- **Fix: a hatch scene could be replaced by a World moment** started in the same frame, and was then never shown.
- **Fix: a second tap on "Start" (focus) or on the last onboarding line crashed** instead of doing nothing.
- **Fix: the open app could save an older copy over a widget's or a notification's change.** The app, each widget and each reminder open their own store on the same save, each with a lock of its own. There is one lock for the process now, every change starts from the saved document when another store wrote it (`SharedDocuments`, `Storage.shareKey`), and the app's tick picks such a change up within half a second (`sync()`). `worldAssign` builds its change from the document being written.
- **Android's own backup is off** (`allowBackup="false"`, `data_extraction_rules.xml`): nothing goes to the system's cloud backup or to another phone by itself. Heal's and Mint's rows are sealed with a key that never leaves the phone, so such a copy could not be opened there; the app's own backups are the way to move a save (INTEGRATIONS_AND_PRIVACY.md).
- **Fix: a repeatable World job the board offered could be refused** east of UTC: accepting counted the day in UTC, the board and the claim in the player's time.
- **Fix: a clock set back during a focus pause** shortened the pauses already counted.
- **Removed (nothing used them, tests included):** `GameStore.worldClaimItems` (the items go to the Bag in `worldPay`), `CareStore.hasEntries`, `LiveBackup.lastBackupMillis`, `LinkRules.tagsInUse`, `Learnset.methodsFor` and `LearnMethod`, `Inventory.removeAll` and `asContext`, `FloorEventResult`, `LauncherModel.pageIsEmpty`, `Pixel.RedDark`, `Spacing.XXXL`, `Spacing.CardRadius`, two private helpers and two imports.
- **Simpler:** `Confirm`, `Rule`, `Actions` and `parseDay` were copied in the Home, Link and Mint screens; they are one each in `screens/ModuleParts.kt`. The launcher's agenda uses `CalendarEvent.shownTitle` for a private event.
- **Agent files:** `CLAUDE.md` is a third shorter (its Layout detail moved, word for word, to `docs/code-map.md`); stale lines fixed (no server, `.idea/` is ignored, the backlog is the record of progress, cloud sessions work on their own branch); `.claude/settings.json` refuses a push to `main` and a forced push; `ci.yml`'s token is read-only; the old `docs/DESIGN_SYSTEM.md` says it is superseded.
## 0.167.0

A launcher audit and the town's behaviour pass. No reward number changes.

- **Fix: the launcher no longer saves or pays on its own clock.** Its one-second tick settled tasks and focus sessions and wrote the launcher's copy of the save, even with the screen off; it now only moves the clock, and only while the launcher is in sight.
- **Fix: widget set-up screens** are started through the widget host, so a provider whose set-up is not exported to other apps no longer fails and leaves the widget unconfigured.
- **Swipe up and swipe down** on the open town (up opens the app list; down is free to set in Launcher settings → Gestures).
- **Less work per frame.** A page swipe no longer composes the whole screen every frame; walking Pokémon move without composing; the dungeon-tile town builds its map once and sits on its own layer; weather draws at 30 frames a second; the town's Pokémon list, the widgets' room and each hosted widget's size are worked out once, not on every change.
- **Less work on returning home.** One read of the save per Home press (was two), no widget reloads on every resume, icons drawn at the size they are shown, kept to a memory budget, and only the changed app's picture is drawn again after an install. The clock widget no longer polls the battery itself and redraws only each minute without seconds; the app list's blur is off in battery saver. A dark window shows before the first frame.
- **Town behaviour.** Idle variation (turning, grazing, hopping), roaming in short legs around a favourite spot at each Pokémon's own pace, staggered bedtimes with a wake-up hop and breakfast, surprise at a tap or an arrival, weather felt through the game's own status icons, tap reactions (hop, happy, turn, flustered when tapped over and over).
- **The Guardian is calmer and changes.** It is left out of the pairwise meetings, glances from others are rare and do not hold it, and a startle happens at most once every few minutes. Unless the buddy is a Guardian, the Guardians take turns one a day: the old one walks off the edge of Home and the new one walks in.
- **Launcher polish.** Icons on the Folders, All apps, Widgets and Gear buttons, the search placeholder in the game's font, and TalkBack reaches the buddy, the Guardian and favourite Pokémon in the town by name (the rest of the crowd is scenery).
- Docs: `LAUNCHER.md` 6.1 and 11.4.

## 0.165.1

Weather against Red Rescue Team (REP-024; `tools/weather/`). No reward number changes.

- **Fix: Guardian and the Pokémon it met stayed frozen on the launcher.** The reaction and chirp holds were taken off only after their delay; when the town stopped being calm in the middle (a page swiped, the launcher out of sight) the effect was cancelled and the hold stayed for good. They are cleared in a `finally` now.
- **Fix: the sandstorm showed the sheet's caption.** The two sand textures were cut from 8 rows too high, so the word "Sandstorm" and a dark band repeated over the town. Cut from the right rows.
- **Snow, blizzard, clouds and sand follow the game's own numbers.** From the decompiled ground-weather code: a snow frame lasts 5 ticks (83.7 ms, was 220), a blizzard frame 2 ticks (33.5 ms, was 80) and neither has a veil any more (the flakes are opaque in the game); the cloud shadows are the game's blue at a quarter (was black at 16%) and drift 30 pixels right and 15 up a second in whole game pixels (was 6 right); the sand textures are blended at a quarter with no tint rectangle and slide at the game's two texture speeds.
- **Towns built from a dungeon's tiles are graded like the dungeon.** The game sends every tile colour through a per-weather table for red, green and blue (`colvec`: sandstorm yellow-brown, rain dark blue, cloudy cool, hail and snow cold); the town does the same through the real tables (`assets/weather/ramps.tsv`) instead of keeping its clear colours.
- Tools: `tools/weather/decode_bg.py` rebuilds the game's weather layers from its files, `match_sheets.py` proves which layer each sheet is (snow = W03P01, blizzard = W03P03, clouds = W01), `inspect_sheets.py` reports every frame, `extract_ramps.py` writes the ramps. The rain's timing and loop points and the sandstorm's own effect are still open (REP-024).

## 0.165.0

REP-023 (UI consistency) and a launcher pass. No reward number changes.

- **The open task card is a sheet with three tabs.** **When** (move to, list, time, reminder, repeat), **Steps** (notes and small steps) and **Edit** (title, notes, difficulty, category, deadline, estimate, and Won't do and Delete, apart from the day-to-day tabs); a finished task shows only its notes and steps. About twenty controls in one column became one tab at a time. Screenshots in both palettes (`task-card-*`).
- **One feedback system.** `AppModel.say(text, kind)` and `Report(model, text, tone)` put saved, reward and error lines in one bar above the tabs (`FeedbackHost`, over the undo bar), the same on every screen: saved 2.5 seconds, reward 4, error 6, tap to dismiss, errors announced assertively, an error not hidden by a "Saved." arriving with it, the same words again shown again. Heal, Mint, Home, Link and the module lock use it instead of their own notice boxes; a field that cannot be read still says so under the field.
- **Every empty state has a next step.** Its button is in the primary colour: add a task, add a routine or log (the focus moves to the field above), start a focus session for eggs, or Clear for a search or filters that found nothing.
- **Tokens.** `Sizes` (hairline, line, icon and sprite steps) beside `Spacing`; the screens use them for icon and sprite sizes, borders and the last typed gaps.
- **Launcher: one Guardian, and the town notices it.** At most one Guardian walks the town (the buddy if it is one, else the highest level); a Pokémon that walks close to it stops and is startled while the Guardian sparkles. A new weather choice, **Guardian's weather**, gives the town the Guardian's own (Kyogre rain, Articuno snow, Kyurem a blizzard, Landorus a sandstorm, Rayquaza cloud shadows; none of its own is a clear sky); Live forecast stays the default.
- **Launcher: Pokémon chirp.** Now and then one Pokémon in the town says its own cry, quiet and short, with a hop (the buddy more often, the Guardian rarely and lower); only while the town is in sight, never in Reduce motion, battery saver, a call or with Sound off. Settings → Town → Pokémon chirp turns it off.

## 0.164.0

Code-review fixes (saving, memory, battery) and a first batch of Android hooks. No reward number changes.

- **Saving is safer.** `FileStorage` writes a synced temporary file and swaps it in atomically; if the swap fails it throws and never writes over the live save. The save before the last write is kept as `<name>.bak`, and a save that cannot be read is kept aside and the `.bak` is used before the document starts empty (and is written back, so the next backup is a good one). `SavedDocument` now saves first and only then updates the value and tells the listeners, so a failed save leaves the app exactly as it was. Every store change (`GameStore`, Care, Heal, Mint, Home, Link) runs under one re-entrant lock (`StoreLock`), so two threads (a widget, a receiver, the UI) can no longer both start from the same document and drop one change.
- **Crash loops.** `CrashGuard` records every uncaught crash (time, and a log of exception types and stack frames with no messages). More than two crashes within 60 seconds start the launcher in **safe mode**: a plain list of installed apps built from ordinary Android views, with **Copy crash log** and **Try again**, touching nothing of the save.
- **Daily backups.** Seven daily and four weekly copies are kept (`BackupRetention`, was the newest three); every file in them is sealed with a Keystore key (`BK1:` prefix, `KeystoreBackupKey`), including the Care journal, and no backup is made without the key. A WorkManager job (`BackupWorker`) takes the backup about once a day even if the app is not opened. Settings → Backups shows the last backup. Older plain backups still restore; a backup sealed with a key the phone no longer has is skipped.
- **Exact reminders.** An alarm that the phone will not run exactly is no longer set as an inexact one in silence: it is logged, and Settings → Notifications says so with a button to the phone's "Alarms & reminders" screen.
- **Memory and battery.** Decoded pictures (scenes, sprite sheets, tiles, weather, world art) live in size-bounded `BitmapCache`s that let go of the least recently used instead of growing for ever. The Pokégear timer, the dungeon strips, the camp yard and the weather layer animate and poll only while the app is in sight (and the strips only while on screen); the camp yard no longer recomposes every frame. The flashlight's `TorchCallback` is unregistered when the tool or the app is left. Launcher category chips have stable keys.
- **Gear number fields** say "Enter a number, like 12.5." under the field instead of silently turning text into 0 (fees, rates, price and the everyday sums); a wrong value gives no answer instead of a wrong one.
- **Undo** shows a bar that drains over the ten seconds left to undo.
- **The Dex data is JSON, not Kotlin source.** The seven tables (species, forms, evolutions, regions, entries, moves, learnsets) are `pokemon-core/src/commonMain/resources/dex/*.json`, typed rows one per line (numbers, text, nulls and real lists instead of pipe-separated text), written by `tools/species/generate.py` (a fresh run from PokeAPI reproduces them byte for byte) and read by `DexParser`. The JVM and Android read them as resources; the web build gets them built into its code by a `generateDexWebSources` Gradle task (nothing generated is committed). `DexData.kt` and its 760 KB of Kotlin string constants are gone, so the compiler no longer has to chew them on every build. CI checks the JSON instead of the Kotlin file.
- **Android:** share text to PokéLife from any app (it opens the Planner's quick add with the text typed in, nothing added until you say so); Quick Settings tiles for **Add task** and **Focus**; **Call, Message and Email** buttons on a person in Link (they open the dialer, messages and mail apps, never place anything); a **wake-up alarm** in Heal → Sleep handed to the phone's clock app; the phone's screen lock can now also guard **Care, Heal, Home and Link** (Settings → Private modules; Mint keeps its own switch).

## 0.163.0

- **Story scenes on a ground map are shown the way the games show them.** The map is no longer squeezed into a ten by six box with black bands round it: it fills the screen at a whole-number pixel scale (about 256 pixels of the art across, as on the DS), never smoothed, and the characters are drawn at that same scale, so a Pokémon is the size it is next to a house instead of towering over the square. A camera looks at the characters: it centres on them when they fit in the view, follows the speaker when they are further apart, glides between the two, stays inside the map and rests on a whole map pixel. Conversations on a map put the two speakers close together in the middle of the view; cinematics are one step closer, with a "big_" character at twice the scale. The script's grid is now a window of ten by six cells (about a tile and a half each) centred on the map, so existing scripts keep their layout. Scenes on stock backdrops and a dungeon's own tiles are unchanged for now.
- **Nothing is "claimed" any more.** Finishing a quest now says **Welcome Wigglytuff** (the Pokémon joins you), a finished World job says **Report back** (on the board, in the journal and in the primary action), and a research step says **Collect**. The rules are unchanged.

## 0.162.0

HEAL and MINT external integrations (REP-014, `ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md` sections 5, 7 and 14; `MODULES/HEAL.md` section 10; `MODULES/MINT.md` section 10). No reward number changes (`RewardRules.VERSION` stays 20): nothing imported ever pays. Tested on the JVM and compiled for Android; **not run on a device, and Health Connect has not been tried on a real phone or the Huawei Watch Fit 4 Pro**.

- **MINT: statement import from a CSV file** (user-assisted import, Mint → Import). The player picks an account and a file through Android's document picker (no storage permission; read once as strict UTF-8, 2 million characters at most, 5,000 rows at most). `StatementImport` reads quotes, doubled quotes, line breaks in a field, comma, semicolon or tab and a byte-order mark; guesses the date, description, amount (or money out and money in) and reference columns from the header, all of which can be changed; reads dates in the order the file uses (it says so when the file cannot settle it) and money exactly, with no rounding and no conversion; and builds a preview: new rows, rows already added (recognised by a fingerprint of account, day, signed amount, description, the bank's reference and a count, so two identical coffees stay two), rows that may match a record the player wrote (left out unless tapped; never merged or deleted) and unreadable rows with the reason. `MintStore.importStatement` saves the kept rows in one write as posted, unreviewed transactions of source `IMPORTED` with their batch, file name and fingerprint, and a retry or a second import adds nothing. Earlier imports are listed; each can be marked reviewed or deleted on its own, which never touches what the player wrote. An import is never a reward event, and reconciling still counts only records the player wrote.
- **HEAL: Health Connect, read only** (Android platform API, Heal → Connections). The player chooses which of exercise sessions, sleep sessions, weight, body fat and drinks Heal may read; Android's own screen asks for each read permission (no write, no background and no history permission, so only the 30 days before the first grant can be read), and every visit rechecks them. Nothing is read until the player taps Read now (last 7 or 30 days). A preview shows what would be added, skipped (same provider id: idempotent), left out (impossible or future) and flagged before anything is saved. A record that looks like one the player wrote is added beside it and flagged; the player chooses Keep both or Delete the import, and the record they wrote is never changed. Every imported record keeps its provenance (provider, writing app's package, provider id, import time) and shows as imported; imported records never pay Field Research and never repay history. Disconnect stops reading and takes Android's permissions back but keeps what was imported; imported records can be deleted per writing app. A failed or partial read keeps what came and does not move the last-read time. No exception text is logged or shown.
- **Honest status** (`Integrations`): each page lists its integrations with class and status. The statement file is a user-assisted import. GCash, Maya, MariBank, GoTyme and UnionBank, reading payment notifications and a direct Huawei link are listed as unsupported or unverified, with why; nothing logs in, scrapes or asks for a password, PIN or code. HEAL lists what it does not read (steps, heart rate, HRV, stress, SpO2, calories, sleep stages, nutrition).
- **Android:** `androidx.health.connect:connect-client:1.1.0` (read permissions for the five record types, the `com.google.android.apps.healthdata` query, a rationale screen and the Android 14 permission-usage alias), `AndroidHealthConnect`, `AndroidTextFiles`.
- **Data:** no schema change. `Transaction` gains `importBatch`, `importLabel`, `fingerprint` and `unreviewed`; the four HEAL record kinds gain `provenance`; HEAL's settings keep the chosen kinds and the last successful read. All are optional with defaults, so older saves and backups read as before.

## 0.161.0

- **Launcher: who walks the town follows the spec** (`LAUNCHER.md` 6.1). The buddy first, then the favourites, then the other Pokémon; inside the favourites and the others, those with an animation sheet (PMD SpriteCollab or Black/White) come before those without one, then by level. With more than 24 owned, the Pokémon without a sprite are the first to stay out of the town. Tested on the JVM (`LauncherModelTest`), **not run on a device**.

## 0.160.0

- **The two Mystery Dungeon arcs have their own places and real pictures.** The old home dungeon list is split by the brief (`docs/world-story-rewrite.md`, work item 2): **Rescue Team** has Tiny Woods, Thunderwave Cave, Mt. Steel, the new **Frosty Forest** and Sky Tower; **Explorers** has the new **Beach Cave**, **Drenched Bluff**, **Crystal Cave** and **Hidden Land** besides Waterfall Cave, Glacier Palace, Ancient Ruins, the Temporal Tower and the Distortion Rift (14 dungeons in all; every id kept, so clears, missions and teams in old saves still resolve; Thunderwave Cave moved to the Rescue Team layer). The new places use the game's own tilesets (`beach-cave`, `drenched-bluff`, `crystal-cave`, the Explorers sheet 42 for Hidden Land, Rescue Team's 36 for Frosty Forest) and sit on the world map; the map now uncovers in eight steps whatever the layer's number of dungeons. The scenes that were set in a stand-in dungeon (the stolen treasure, Bidoof's lesson, the frozen pond, the Hidden Land, Absol in the snow) now play in their own.
- **Story scenes are set on real Mystery Dungeon maps instead of stock backdrops.** A scene's background can be `map:<name>`: a ground map of Red Rescue Team (Pokémon Square, the village, Frosty Forest, the snow forest, the comet, the Nightmare, the sky) or of Explorers of Sky (Treasure Town, the Guild gate by day and by sunset, the hall, the mess hall, the dorm, the sea cliff, the Hidden Land, the Temporal Tower under its red sky, the dark future, the forest clearing), 42 pictures bundled (about 2 MB; `StoryPlaces`, `storyRescueMaps` and `storySkyMaps` in `composeApp/build.gradle.kts`). A test checks that every scene's picture exists and is bundled.
- **The optional episodes after each story are written.** Rescue Team: Gengar's Debt, The Three Birds, The Buried Chamber, The Wish and The Old Legend (chapters 101 to 105); Explorers: Bidoof's Wish, Igglybuff the Prodigy, Today's "Oh My Gosh", In the Future of Darkness, Manaphy's Return and The Marine Resort (101 to 106). They open when the arc's Guardian has been faced, are never queued or forced, and are played (and replayed) from World → Journal → Story archive under "<arc>: after the story" (`StoryEpisode.special`, `WorldMoment.SpecialEpisode`, `StoryCatalog.specialsOf`). No scene pays anything.
- Not done: the Rescue Team arc has no world map picture of its own (the game has none among the ground maps; it shares the Explorers map with its own markers).

## 0.159.0 and earlier

One line each; the full notes are in this file's git history.

- **0.159.0**: Battle identity: moves, abilities and held items count in a World team's power (WORLD.md 3.2, `POKEMON_ECOSYSTEM.md` 4.1 and 8.3).
- **0.158.0**: Guardians are normal Pokémon with a slot per module (owner, 8 November 2026; `RewardRules.VERSION` 21).
- **0.157.0**: The story has sound (WORLD.md 4.4, REP-017).
- **0.156.0**: The Rescue Team and Explorers of Sky arcs have their scenes (`RescueChapters` 6 episodes and 20 scenes, `ExplorersChaptersA` and `B` 8 episodes and 19 scenes), ported from the owner's brief…
- **0.154.0**: Heal is sealed (REP-014; owner decision 6 of 8 November 2026).
- **0.152.0**: Launcher: other apps' widgets are hosted by what their provider allows, a removed provider no longer leaves a hole, and search finds PokéLife's own places (REP-014, `FEATURES/LAUNCHER.md` sections 5.2 and 11.5).
- **0.150.0**: PLAN's and CARE's rewards settle under their own ledger event ids (REP-014, `DATA_AND_MIGRATIONS.md` section 7.3.0 step 2).
- **0.149.0**: Link reminders, and the streak reminder and weekly recap on the shared scheduler (REP-014, REP-011).
- **0.148.0**: World: expedition Pokémon experience (WORLD.md 5.2), and a fix for Guardians that could not be seen.
- **0.147.0**: 23 more Pokémon get their Mystery Dungeon sprite.
- **0.146.0**: Every module opens on its lead, and the rounded parts that were left are pixel parts.
- **0.145.0**: Link, the connection module, built manual first (REP-014).
- **0.144.0**: Cross-module links, Home reminders, and real reward events in the ledger (REP-014).
- **0.143.0**: Home, the household module, built manual first (REP-014).
- **0.142.0**: Mint follow-ups (REP-014): a backup of Mint that opens on any phone, bill reminders, the optional access gate, and a full edit for accounts (owner: "mint edit accounts").
- **0.141.0**: Mint, the finance module, built manual first (REP-014), and a scene of its own for every way a new Pokémon arrives (owner: "all are egg, that was made for Plan's focus eggs: reuse it, but not too much, whatever makes…
- **0.140.0**: Real Rescue Team art from the game's own files.
- **0.139.0**: Species lists of every module region, curated together (owner: "curate all modules species list, as much as possible, no dupes, keep it relevant to the theme, follow mainline regional dex patterns, module guardians…
- **0.138.0**: From phone testing of 0.137.0.
- **0.137.0**: Design pass, from the owner's phone testing: consistency, pixel parts and fewer words.
- **0.136.0**: Heal (Modules → Heal), the manual slice.
- **0.135.0**: Your Pokémon, Bag and Poké now live in their own database tables (schema 6), the way Plan's and Care's already do.
- **0.134.0**: Each module teaches how it gives Pokémon.
- **0.133.0**: One Pokémon, one place.
- **0.132.0**: Visitors are calmer and kinder (Care).
- **0.131.0**: One reminder system for Plan and Care.
- **0.130.0**: PokéLife is fully local.
- **0.129.0**: Your save now lives in a database on the phone (Android).
- **0.128.0**: Debug builds get a second icon, "DB check", that runs the database checks on a throwaway file (SQLite version, foreign keys, WAL mode, rollback, speed, backup copy, reopen) and shows PASS or FAIL for each line.
- **0.127.0**: Under the hood (REP-011): reminder source adapters for Plan and Care and a router for notification actions.
- **0.126.0**: Care's routine reminders now respect quiet hours.
- **0.125.0**: One currency: Poké.
- **0.124.0**: The buddy and each party's lead are now separate.
- **0.123.0**: The home layer is gone; World has two optional Mystery Dungeon arcs.
- **0.122.0**: World opens with Mew's prank.
- **0.121.0**: A simpler World landing.
- **0.120.0**: Currency is a set of rows you can type in.
- **0.119.3**: Other apps' widgets fit their cells.
- **0.119.2**: From the deep review of 3 November (its P0 story and dungeon items):
- **0.119.1**: Launcher faster.
- **0.119.0**: Every chapter is a scene now.
- **0.118.0**: World story engine, first slice.
- **0.117.0**: Pokégear, the field tools (Phase 2 finished): four more tools (eighteen in all).
- **0.116.0**: World: the Story archive (a small button on the World landing): every rank reached, every story chapter opened (by layer) and every legendary Pokémon that joined, each with a play button to watch its cutscene again.
- **0.115.0**: World: the Sinnoh layer (fifth button on the Map's layer switch, open from the first day): nine dungeons from Diamond, Pearl and Platinum (Eterna Forest, Oreburgh Mine, Wayward Cave, Old Chateau, Iron Island, Mt.
- **0.114.0**: World, Phase 3 finished: hazards and choices.
- **0.113.0**: Cutscenes for a new World rank and a new story chapter.
- **0.112.0**: World, dungeon modifiers (Phase 3).
- **0.111.0**: World, Phase 3 (first part): side paths opened with field moves.
- **0.110.0**: Pokégear, the specialist toolbox (Phase 2): six new tools (fourteen in all), with a new Measure group on the home.
- **0.109.0**: World, Phase 2 (first part).
- **0.108.0**: Launcher: pages and Pokémon.
- **0.107.0**: Pokégear is a toolbox now: eight tools, with a home that has search, favourites, the tools you used lately and groups.
- **0.106.0**: Pokégear privacy fix.
- **0.105.0**: World landing, rebuilt around one action.
- **0.104.0**: World, Expeditions: an "All teams" card at the top says what every team is doing (where, which floor, who is on it, or ready or empty) and how many Pokémon are waiting to be recruited.
- **0.103.0**: Plan has no quest for the legendaries World gives.
- **0.102.0**: World teams: a Guardian (legendary or mythical) can join a team while it is idle, like any other Pokémon; one that is slotted as a Guardian cannot, and a Guardian on a team cannot be slotted until it is recalled.
- **0.101.0**: World: the Hoenn layer (fourth button on the Map's layer switch, open from the first day): nine dungeons from Ruby, Sapphire, Emerald, Omega Ruby and Alpha Sapphire (Petalburg Woods, Granite Cave, Meteor Falls, Mt.
- **0.100.0**: World: the Johto layer (open from the first day, third button on the Map's layer switch): nine dungeons from Gold, Silver, Crystal, HeartGold and SoulSilver (Sprout Tower, Ilex Forest, Union Cave, Ruins of Alph,…
- **0.99.0**: World: the Kanto layer.
- **0.98.0**: Primal Dialga is its own Dex form (`dialga-primal`, id 20001, an alternate form of species 483), separate from Dialga, for the home layer's Temporal Tower Guardian.
- **0.97.0**: World job alert (Android): a notification when an exploring team finishes a job on the mission board ("A World job is done", or how many are ready), opening World.
- **0.96.0**: Moves in the Dex (owner's decision d16): `tools/species/generate.py` now also writes every move (919: type, category, power, PP, accuracy, priority, generation) and each form's learnset from the newest game it…
- **0.95.1**: Care: the journal draft is kept across an app restart (saved after a short pause, cleared when the entry is written).
- **0.95.0**: World, clearer (audit Phase 5, first slice): the landing opens with "Next up" after your first trip (the first open dungeon you have not cleared, or what the nearest closed one is waiting for, with a one-tap "Send my…
- **0.94.4**: First-use states (audit): a screenshot test renders every landing page for a brand-new player.
- **0.94.3**: Audit: a screenshot test (`ScreenshotTest.largeTextPlaces`) renders Home, Plan, Planner, Focus, Care, Pokémon, Me and World at 130% system text; all eight were looked at and nothing is clipped or overlapping.
- **0.94.2**: Plan usability, Phase 3 (finished): checked against the code and the audit's three points.
- **0.94.1**: Back chains are tested with real taps (`BackChainTest`): the test presses the header's Back button in an offscreen screen and checks where it lands.
- **0.94.0**: Launcher pages: make your own pages and drag widgets between them.
- **0.93.0**: Launcher widget grid is on by default (Launcher settings → "Widget grid (drag, resize)" goes back to the old list).
- **0.92.0**: Launcher widget grid (preview switch in Launcher settings): drag and resize like Smart Launcher.
- **0.91.1**: World map: the wild Pokémon shown on each dungeon are dark shapes standing still, not walking sprites: they are samples of who lives there, not Pokémon you have.
- **0.91.0**: Launcher: the widget grid can be switched on as a preview (Launcher settings → "Widget grid (preview)", off by default).
- **0.90.8**: Groundwork for launcher widgets like Smart Launcher's (drag anywhere, resize): the grid model (`launcher/WidgetGrid.kt`: 4 columns, per-kind size limits, add, move with what is in the way going down, resize, and the…
- **0.90.7**: Launcher: the night tint is one sheet over the whole town, so it reaches the right edge (a lighter, untinted strip showed there at night).
- **0.90.6**: Plan usability, Quick Add (audit Phase 3, first step): the box now shows what it understood while you type ("Understood: Tomorrow · 17:00 · Social · Difficulty B · 30 min"), so a typed line is never a guess.
- **0.90.5**: First-use pass: every landing page was rendered on a brand-new save (`ScreenshotTest.firstUse`, 33 pages: Home, Modules, Plan and its sections, the planner's views, Care and its sections, World's sections, the…
- **0.90.4**: Control system, the rest of it: a tappable list row tints faintly under a mouse and shrinks to 98% when pressed (like a key); the bottom tab bar items tint under a mouse.
- **0.90.3**: `PixelField`: one text field for the whole app, replacing 29 plain Material fields (`theme/Fields.kt`).
- **0.90.2**: Design system: one key for every control.
- **0.90.1**: Care routines can be edited: tap a routine, then Edit to change its name, time and days (what it earned and the days it was done stay, and editing never lets today pay again).
- **0.90.0**: Phase 1 of the audit (`docs/audit-2026-10-31.md`) and its five decisions: World's rewards are in the reward history (Poké and items, merged per ten minutes, never a reveal) and its items go straight into the Bag, so…
- **0.89.1**: Audit (`docs/audit-2026-10-31.md`, answering the owner's repository audit brief): findings with severity, what was and was not inspected, a clean-up classification, a phased plan and the decisions only the owner can…
- **0.89.0**: Emoji show in the launcher's pixel text (an agenda event or a task called "🏠 Home" showed "??"): the dialogue font cannot draw them, so the system's emoji font draws them beside the letters.
- **0.88.2**: World review fixes: a daily or weekly job comes back on the right day even when no team has settled since (the screens now pass the clock); a Café brew is no longer wasted by brewing again while one still works; a…
- **0.88.1**: The World economy simulation now plays jobs of every kind (Explore, Find items, Rescue, Deliver, Escort, Defeat), the Swap Shop and the Dojo (`docs/world-economy-report.md`).
- **0.88.0**: World jobs: Escort (the team must be at least three Pokémon) and Defeat (the team must have enough power) jobs, five in all.
- **0.87.0**: World's map draws each dungeon like the expedition box: the dialogue border round that dungeon's tiles, its wild Pokémon walking the floor, and its details written on the wall tiles.
- **0.86.2**: World expeditions, redone: the box of a team that is out is the app's dialogue box filled with its dungeon's own tiles: one row of wall on top, then a floor that scrolls past while the Pokémon walk across it (only on…
- **0.86.1**: World expeditions: the box of a team that is out is made of its dungeon's own tiles (every dungeon now has its own tileset except the Distortion Rift, which uses the World Abyss): a frame of walls round a floor that…
- **0.86.0**: World, the rest of the first design: five more dungeons across the other regions (Waterfall Cave, Glacier Palace, Ancient Ruins, Sky Tower, Temporal Tower) and a post-story Distortion Rift; dungeons…
- **0.85.0**: World skills, base and research: three skills level as teams explore, even away (Exploration: 2% more Poké per level; Foraging: 1% more berry chance; Scouting: 0.3% more find…
- **0.84.0**: World missions and rescues: a Missions section in World, open with story chapter 2 (Bronze rank and one dungeon clear).
- **0.83.1**: World economy simulation (`./gradlew :world-core:simulateWorldEconomy` writes `docs/world-economy-report.md`).
- **0.83.0**: World, the dungeon game (not the same as the Explore module in the ecosystem plan): a new card in Modules.
- **0.82.0**: Buttons are redrawn as flat pixel keys (the "Outline" option): a stair-cornered outline in the key's colour on the panel colour, the label in the same colour, and while held the key fills with its colour and the…
- **0.81.0**: Pokégear: a hub of handy tools with helper Pokémon, in Modules (the Pokégear card) and from the launcher (the Pokégear button, which replaces the phone Utilities screen of 0.80.0).
- **0.80.0**: Launcher: battery saver (Launcher settings → Battery saver: Off, Automatic or Always).
- **0.79.0**: Launcher: the Buddy widget, with portraits and no words.
- **0.78.0**: Launcher: more space between an app's icon and its name.
- **0.77.0**: Launcher: hold any quiet part of a widget on the home screen (or the Today page) to edit the widgets right there.
- **0.76.0**: Launcher: put another app's Android widget on the screen.
- **0.75.0**: Launcher: every box on the home page is a widget now, and so are the calendar and the day's agenda on Today.
- **0.74.0**: Launcher: Niagara-style features.
- **0.73.0**: Sound: only the moments of the game make a sound now (an egg, a rank up, a berry, a focus session starting or ending, the lock when Home is pressed during focus, notifications).
- **0.72.0**: Launcher: weather over the town, from the real Mystery Dungeon effects: rain with splashes, snow, a blizzard, a sandstorm and the shadows of drifting clouds, set in the launcher settings (Weather).
- **0.71.0**: Launcher: a second page beside the town, Today: a month calendar (Prev and Next change month, a dot marks days with plans) and the chosen day's agenda, events first and then its tasks with a box to tick.
- **0.70.3**: Launcher: the app list comes in from the right again, but only a short glide (56 dp) while it fades in and its blur clears, on the app's smooth curve (a flick still keeps its speed and settles on a spring).
- **0.70.2**: Launcher: the app list no longer slides in.
- **0.70.1**: Launcher: the Pokémon's "?" and "!" icons sit just over their heads (they floated far above them).
- **0.70.0**: Sound: the app and the launcher now have their own sounds, from the Mystery Dungeon sound effects: a cursor blip for tabs, menu cards, settings rows and the letter scroller, confirm and back for buttons, whooshes for…
- **0.69.0**: Launcher: the app list now comes in from the right.
- **0.68.0**: Launcher settings: All apps has a Settings button that opens a dialogue box with the town (the picture or the scene), how many Pokémon live in it (8, 16 or 24), whether they walk about, whether the status box shows,…
- **0.67.0**: The whole app uses the game's dialogue box as its card: cards, list rows, menu cards and the Home region cards are drawn with a thin version of the launcher's frame (a pale hairline, a blue band, an inner line,…
- **0.66.1**: Launcher: the dialogue boxes are drawn like the game's menu boxes: a pale hairline, a thin blue band at the top and bottom that is thicker at the sides, a deep-blue line, rounded corners, and a see-through black…
- **0.66.0**: Polish and states (phase 3 of the design audit).
- **0.65.0**: Tokens and consistency (phase 2 of the design audit).
- **0.64.0**: Accessibility, contrast and layout (phase 1 of the design audit, `docs/DESIGN_SYSTEM.md`).
- **0.63.0**: Launcher home: a clearer hierarchy, in three layers.
- **0.62.1**: Launcher: the home page's text is one dialogue box, not several, with even padding all round.
- **0.62.0**: The launcher's home page uses the game's dialogue boxes: your pinned apps and the parts of PokéLife (Plan, Focus, Care, Party, Pokémon) are two menu boxes side by side, the buddy and your next task sit in a message…
- **0.61.0**: Launcher: pressing Home now always returns to the home page, from the app list, the long-press sheet or the town picker (it used to stay in the app list).
- **0.60.0**: The whole app can use a scene or a dungeon town as its page background: Settings → Look → Background → Change shows small pictures of Day (the default time-of-day gradient), the 11 scenes and the 17 dungeon towns.
- **0.59.0**: The launcher's town can be a dungeon: 17 towns laid out from the tilesets of Mystery Dungeon dungeons (Lush Prairie, Apple Woods, Drenched Bluff, Craggy Coast, Crystal Cave, Dark Hill and more), with a wall band…
- **0.58.0**: The Pokémon of the launcher's town now walk: towards you, away from you, or to the side (mirrored for left), with their own Mystery Dungeon walk animations, instead of sliding about facing front.
- **0.57.0**: The launcher's town can be set in any of 11 real Pokémon scenes (meadow, city, route, forest, mountain, beach, desert, thunder plains, ice cave, damp cave, volcano cave): press and hold the town and pick one.
- **0.56.0**: The launcher's home page is now a town: a real Pokémon scene where every Pokémon you own (up to 24, the buddy and the highest levels first) wanders with its own idle animation; tap one for its cry and a hop.
- **0.55.0**: PokéLife can be the phone's home screen, opt-in: nothing changes until it is chosen as the home app in Android's settings (the home page has a button that opens them).
- **0.54.0**: The app icon has a themed version: on Android 13 and later, with Themed icons on, the launcher tints the egg's outline, check and stopwatch in the wallpaper's colours.
- **0.53.0**: The PC is now boxes of 30, like the games, with Previous, Next and "Box 2 of 5" above and below the grid.
- **0.52.0**: The Party page shows every region's party at once: Plan's, then Care's, each under its region's name and colour with its lead first and its perks after, instead of a switch between them.
- **0.51.0**: A Pokémon's page reads in a clear order: who it is (its Pokédex number, species, level and types), its traits (nature with the stat it raises and lowers, ability, gender, shiny, and the IV judge with its best stat),…
- **0.50.0**: Each region has its own look.
- **0.49.0**: Home is now an overview of your whole world: a card for each region (Plan and Care) with its lead, how today is going there and the one next thing to do, then your streak, mini-quests and what happened recently.
- **0.48.1**: Shinies are back to 1 in 128 per egg (0.46.0 made them 1 in 192).
- **0.48.0**: Moving around feels smoother: the bottom tabs fade into each other, and opening a module, a section or a Pokémon slides it in a little, with Back sliding it out the other way.
- **0.47.1**: A save that cannot be read is now kept aside (as `save.json.unreadable-…` or `care.json.unreadable-…` beside it) instead of being overwritten when the app starts fresh, so progress and Care's diary can still be…
- **0.47.0**: The app is now called PokéLife: a Pokémon world for real life, where each module is its own region.
- **0.46.0**: Much more candy, and a Pokédex that fills faster.
- **0.45.1**: Mini-quests answer when you tap Done: the quest turns to "Done" straight away and the card says what you got (the berry, and the bonus one for all three).
- **0.45.0**: More time with visitors.
- **0.44.0**: Only the Plan and Care Regions now.
- **0.43.1**: Care's perks now open at rank Trainer, together with Plan's party perks: before then, Care's party panel and its card in Pokémon, Party say which rank shows them.
- **0.43.0**: Plan and Care pages now match.
- **0.42.0**: Care feels more like Pokémon.
- **0.41.0**: Visitors like berries.
- **0.40.0**: A Care widget for the home screen: today's routines with a tick (ticking one there counts and pays just as in the app), the ones already done, and whether today's diary card is saved, in the meadow.
- **0.39.0**: Gentle routine reminders on Android.
- **0.38.0**: Care can keep a copy in the cloud, if you want it to.
- **0.37.0**: Visits finished.
- **0.36.0**: Befriending is now a visit instead of a button.
- **0.35.1**: Fixed the "Just 5 minutes" button on the Focus screen: it was drawn on top of the Start button, so one hid the other.
- **0.35.0**: One party per module.
- **0.34.0**: Care, the second module, under Modules, inspired by journalling and DBT: small routines you tick off each day, a 1 or 2 minute calm break with your buddy asleep beside you, a daily diary card (how strongly you felt…
- **0.33.0**: A new Modules tab, ready for Care and the modules after it.
- **0.32.0**: The Care Region's dex is chosen: 143 gentle, soothing Pokémon (Chansey, Snorlax, Munna, Comfey, Milotic, Wooloo and more), with Cresselia and Shaymin as its patrons and Goomy and Slakoth as its Super Rare lines.
- **0.31.1**: The Home Region is now the Plan Region, the first of the module regions.
- **0.31.0**: Pick for me on Home's Next up card: it picks one of today's tasks for you, and finishing that one pays 10% more candies.
- **0.30.0**: Notifications on Android that the design promised.
- **0.29.0**: Editing a task now also sets its category, its notes and its deadline (a day, with Today, Tomorrow, Next week and a day-by-day nudge, or none).
- **0.28.0**: Trainer ranks come quickly at first and slow down later: Apprentice after a day or two of play (it took over two weeks), Ace in under two weeks, and each rank after that takes longer than the last.
- **0.27.0**: Training plans are gone: no more choosing focus stats.
- **0.26.0**: Party perks you can see: the Party screen shows each perk's total and, opened, where every part comes from (your buddy's types and ability, its held item, the rest of the party, Guardians).
- **0.25.0**: Behind the scenes, the Care module's first part (no screens yet): self-care routines, 1 to 2 minute calm breaks, private mood check-ins with an optional note and tags, gratitude and reflection prompts, and rolling…
- **0.24.2**: Fix: undoing a habit and ticking it again the same day no longer pays twice.
- **0.24.1**: Behind the scenes, before a second module: the cloud save is now Plan's own (`plan.saves`), so the shared game tables hold only the game; every reward the server records says which version of the reward rules made it…
- **0.24.0**: Your own events can repeat: daily, on weekdays, weekly, every 2 weeks, monthly or yearly, timed or all day ("Gym" every Monday, "Home" every weekend).
- **0.23.0**: More Pokémon sounds.
- **0.22.0**: A Month view in the Calendar (Day / Week / Month), after TickTick and Google Calendar: events over several days are one solid bar across them, one-day all-day events are short bars, and timed events and tasks sit on…
- **0.21.0**: Lists in the Planner, after TickTick.
- **0.20.0**: The Pokédex tracks forms.
- **0.19.5**: Each caught Pokémon's Pokédex entry now shows it in a real scene for its type, the same ones buddies get on Home: Water Pokémon on the beach, Grass and Bug in the forest, Electric on the thunder plains, and so on.
- **0.19.4**: Pokémon in the Pokédex no longer lose their animation (or show nothing) after you page through entries quickly with Next: a sprite whose loading was cut short is no longer remembered as missing.
- **0.19.3**: The Planner's menu and lists are redesigned after well-regarded to-do apps (Apple Reminders' smart lists, Things' Today and Logbook, Todoist's Upcoming).
- **0.19.2**: Your buddy no longer shrinks for a moment when you pet it or give it a berry: its hop and bite play at its usual size.
- **0.19.1**: The Calendar in the Planner is redesigned after well-regarded calendar apps (Fantastical's day ticker, Apple and Google Calendar's grid, Structured's inbox).
- **0.19.0**: Easier to use, after a review of every screen against the Laws of UX.
- **0.18.0**: Eggs hatch like in the games: "Oh?", the egg rocks three times, harder each time, a flash, and your new Pokémon is there and calls out, with its category ("the Seed Pokémon").
- **0.17.1**: Easier to read and tap: green and blue text is a little darker so it stands out on cards (and red a little lighter in dark mode), every button is at least 48 dp tall, and the starter picker, PC tiles and the opening…
- **0.17.0**: Clearer numbers under Me: the trainer card says which totals are all time and how the streak counts.
- **0.16.1**: Focus keeps your egg when you lock the screen or a call comes in, as it always should have: put the phone face down and the timer runs on, and a call pauses the timer until you come back.
- **0.16.0**: The Pokédex tells you where each Pokémon stands: Owned (in your party or PC now, with a Poké Ball on it), Caught (yours once, since transferred), Seen (a friend's buddy or a quest's Pokémon, still a grey shape but…
- **0.15.0**: Your own events: New event on Planner → Calendar adds an appointment or a block of time that isn't a task, like a lesson or lunch.
- **0.14.0**: Drag to plan: Planner → Day is now Planner → Calendar, with Day and Week.
- **0.13.0**: More of the Mystery Dungeon look: your buddy curls up and sleeps on Home and the widget while a focus session runs, hops when you pet it and munches a berry you give it, all with its own SpriteCollab animations (the…
- **0.12.0**: Your calendar on the Day timeline (Android): Me → Settings → Calendars lets PokéPlan read the phone's calendars, Google Calendar included, and you tick which to show.
- **0.11.0**: A new look for every Pokémon: the Pokémon Mystery Dungeon style sprites of the PMD SpriteCollab, idling in place on Home, the widget and each Pokémon's page and standing still in lists, the Pokédex and the PC…
- **0.10.0**: Egg tickets: when you are signed in, your phone keeps a stock of 20 eggs the server has already rolled the dice for, so a focus egg hatches the same way with or without a connection and failing a session never…
- **0.9.0**: Planner → Day: a timeline of your hours.
- **0.8.1**: A cleaner interface: four text sizes instead of seven (none below 12 sp, widgets included), no coloured strips on the left of cards, nested corners that line up, commas instead of "·" separators, and shorter copy…
- **0.8.0**: Settings → Your data: export everything as a JSON file (the share sheet on Android, Downloads on desktop, a download on the web), delete your account and its cloud data for good, or erase this device and start over.
- **0.7.0**: Tasks have a time: open one and pick At 9:00, 12:00, 15:00 or 19:00 and nudge it by 15 minutes; the card shows the time and whether a reminder is set.
- **0.6.1**: The Buddy widget keeps its idle animation while it reacts to a pet or a berry (it used to freeze on one picture until the emoji went away), and a still buddy (Reduce motion, during focus) stands on its shadow instead…
- **0.6.0**: Task reminders: open a task with a day and pick up to three under Remind me (at the time, 10 minutes, an hour or a day before).
- **0.5.0**: Pokémon evolve at their real game level (Charmander at 16, Charmeleon at 36) instead of half of it.
- **0.4.0**: Every finished task now pays Rare Candies; a Super Candy bonus comes on top (10% for rank E up to 40% for rank A, raised by the Super Candy chance perk) instead of replacing them.
- **0.3.1**: Fix: the Buddy widget froze on a still picture after a pet or a berry (and the Today and Focus widgets could show old data for a while).
- **0.3.0**: Dark mode: Settings has Match phone (the default), Light and Dark; the dithered page background turns deep navy and the Android navigation bar icons follow.
- **0.2.0**: The Focus widget shows a live countdown; the Today widget has a "+ Add" button; long-press the app icon for "Add task" and "Start focus" shortcuts; the app is single-instance so a widget tap or shortcut never opens a…
- **0.1.0**: The first private build.
