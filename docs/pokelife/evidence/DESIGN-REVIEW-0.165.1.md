# PokéLife design review at 0.165.1

**Date:** 11 November 2026. **Scope:** every module and shared feature, as a UX/UI designer, product designer, Pokémon fan and frontend reviewer.

**Method.** `ScreenshotTest` was run and about 60 of its 277 renders were looked at (both palettes for the main pages). The screen code, the `*-core` rules and each module's spec were read. Nothing was run on a phone, so reminder delivery, sensors, the launcher's feel and real performance are **not verified**. This is evidence, not a specification: a rule changes in its owning `docs/pokelife/` file.

Each finding says how it is known: **(seen)** in a render, **(code)** from a file, **(spec)** against the module document.

---

## 1. Verdict

The foundation is unusually strong: one visual grammar, real assets, forgiving copy, honest privacy, and rules that are tested. What stands between today's app and "the best version of each app it replaces" is not polish first, it is three things:

1. **Logging is slower than the dedicated apps.** Every daily action sits 3 taps deep under a picture window, and most records can only be dated today or yesterday.
2. **The numbers are hard to read.** The zero of the Pokémon DP font reads as an 8.
3. **The Pokémon layer is wide but invented where canon already has an answer** (Rare Candies as wages, flavour-named berries, Super Candy evolutions, module-named Pokédexes, a "trainer" in a world without humans).

## 2. The twelve findings that matter most

| # | Finding | Evidence | Fix |
|---|---|---|---|
| 1 | **0 looks like 8.** "30 min" reads 38, "60" reads 68, "PHP 1,499.00" reads 1,499.88, the calculator has two 8 keys, "Feed 10" reads "Feed 18". | seen: Focus, Heal activity, Mint bills, Gear calculator, Pokémon detail | Use a plain-zero digit set for every number (patch the glyph, or follow DESIGN_SYSTEM 5.3, which already asks for a readable sans with tabular numerals for money and health values). Highest priority in the whole review. |
| 2 | **The module landing page spends its first screen on the lead.** The picture window is about 370 of 1080 px; with the reward box and the tutorial card, the first real control starts at the fold or below. | seen: Plan, Mint, Heal, Home, Link, Care | Shrink the stage to a strip (about 96 dp) once the module has records, and put the module's one primary action above it. Keep the full window for an empty module. |
| 3 | **Daily actions are 3 to 5 taps.** Water: Modules, Heal, Add a drink, +250. Supply minus one: 5 taps. Expense: 3 taps plus re-picking the account each time. | code: Heal.kt:123, Household.kt:640-678, Mint.kt:409 | A quick-log row on the Home tab (drink, expense, mood, task), a widget per module, and remembered last account and category. |
| 4 | **Home is not a dashboard of the app.** It shows Plan, Care and World only; Mint's bills, Heal's day, Home's chores and Link's dates never appear. | code: Home.kt HomeScreen | One card per module that has something due today, in rainbow order, each with its one action. Nothing shown for a module with nothing due. |
| 5 | **No backdating and thin editing.** Mint: today or yesterday only, and edit cannot change date, account or category. Heal: drinks, sleep and body are always "now" and cannot be edited. Home and Link type dates as text. | code: Mint.kt:433-437, 521-560; Heal.kt:341-482; Household.kt:367-373 | One shared pixel date picker and time picker, used everywhere; every record editable in full. |
| 6 | **No charts where numbers need them.** Mint's review is a text list of one month; weight has a sentence; water has seven lines. Plan's Progress has the only charts. | seen: Mint overview, Heal menu; code | Reuse Plan's bar chart: Mint month over month and by category, Heal weight line and water week, Care mood calendar. |
| 7 | **Focus cannot replace a focus app yet.** Leaving the app for 10 seconds fails the session, there is no foreground service or ongoing notification, no break cycle and no count-up mode. | code: FocusSession.kt:112-173; manifest has no foreground service | Keep strict mode as Deep Focus. Add a normal mode that survives leaving the app (ongoing notification), a 25/5 cycle with a long break, and a stopwatch mode. The egg still hatches from finished minutes. |
| 8 | **Rare Candy is the wage for everything.** "3 Rare Candies" for a routine, 78 in the Bag. A fan reads Rare Candy as rare. | seen: reward box, Bag | Pay Exp. Candy XS/S/M/L/XL (Sword and Shield, real icons exist) and keep Rare Candy as the occasional prize. Same economy, canon names. |
| 9 | **Berries are named by flavour.** "Bitter Berry", "Dry Berry", "Sour Berry" under real berry icons. | seen: Bag; code: Items.kt:59 | Cheri (spicy), Chesto (dry), Pecha (sweet), Rawst (bitter), Aspear (sour), with the flavour as the second line. |
| 10 | **Six module parties plus Guardians plus the buddy** make the role section of a Pokémon twelve buttons ("Make Care lead", "To Care party", ...). | seen: Pokémon detail, Party | One "Move to..." key opening a place picker (the eight places as rows with their free slots). Consider whether six-strong parties per module earn their complexity; a lead plus two would read as clearly. |
| 11 | **A human trainer in a PMD world.** "Trainer name", "Rank: Rookie", an Oak-voiced welcome with no portrait (the file is absent, so a Poké Ball shows), next to a World with "no humans" and its own Normal/Bronze rank. | seen: onboarding, Home header, Settings; code: assets/npc holds only a README | Choose one frame. PMD frame: the player is a team ("Team Sophia"), one rank ladder (Normal, Bronze, Silver, Gold, Platinum, Diamond and up, with the real badges), and a PMD-style opening. |
| 12 | **Mixed pixel sizes in one picture.** The lead in a scene is a small sprite scaled about 5 times over a backdrop whose pixels are about a third of that size; lists use the same sprite at 1 to 2 times. | seen: Home, Plan, Mint, detail | Scale sprites by whole multiples matched to the scene's own pixel size (a PMD sprite on a PMD map at the map's scale), and use the portrait, not a huge sprite, where a face is wanted. |

## 3. Cross-cutting design system notes

- **Colour inside a module (seen).** Selected chips and tabs are blue in every module (Heal's activity types, Care's 0 to 5 keys, Mint), while the header and frames carry the module accent. Rule 5.1 says one accent per module; chips should take it.
- **Red as an ordinary action (seen).** "Log activity" in Heal, "Make buddy" and "Make Plan lead" on a Pokémon, and the friendship bar are red. The design system reserves red for a few game actions and it reads as destructive. Friendship wants pink or the heart colour.
- **Boxes in boxes (seen).** Mint's bills are framed rows inside a framed group; every Bag item is its own framed box. Rule: one frame per group, rows divided by a line.
- **Captions that explain (seen, code).** "Where your effort went" has four lines of method; the Atlas has a legend for its marker shapes; Mint explains what bills do not do; the tutorial cards restate themselves. Where a caption is needed to decode a symbol, the symbol is the problem.
- **Emoji are not game assets.** The diary card's six faces (seen) and the buddy's tap reactions (code: BuddyMood.kt:68-95) use the phone's emoji font. Use the PMD emotion portraits (Happy, Sad, Angry, Worried, Joyous, Normal) for moods, which is also the most on-theme mood picker possible, and the existing `Emote` and status icons for reactions.
- **Back sits on the right on the Pokédex entry** and on the left everywhere else (seen).
- **Reward box (seen).** A long list in the page flow pushes work down on every module page. Make it a one-line strip ("5 rewards, tap to see") or let the lead's celebration carry it.
- **First-run cards.** "Tally for 5 seconds", "Write for 5 seconds", "Tidy for 5 seconds" are pretend actions. Give the first Pokémon for the first real record (first account, first chore, first person).
- **Icons.** Item icons stand in for meanings they do not have: the Link Cable is both Link and the Calculator, the Town Map is both the Home tab and World. One item, one meaning; tools can use PMD or Pokégear-era icons.
- **Terms.** Done / Tick / Mark done; Archive / Put away; Cancel / Close / Keep it; Skip / Won't do / Cancel it; Steps / subtasks; Targets / habits / frequency. Pick one of each in the design system.
- **Name clash.** "Home" is a tab and a module.
- **Tab bar.** It takes the module's colour inside a module (seen in dark Focus); DESIGN_SYSTEM 5.1 says it is neutral chrome. Decide and align the document.
- **Destructive actions with no confirm or undo (code):** budget, bill, void (Mint); drink, sleep, measurement, goal (Heal); diary card, journal entry, urge (Care); archived chore, archived supply, warranty (Home); note (Link); list, event (Plan). One shared undo strip, as Plan's task delete already has.

## 4. Module by module

### PLAN
**Strong:** quick add with natural language and a preview, six views, rich recurrence, undo, the calendar with drag to an hour, Today with "from before" and one "Move to today".
**Gaps to best in class:**
- Priority is missing; rank (E to A) is difficulty and reward tier, and every task wears a letter a newcomer cannot read (spec 4.4).
- Subtasks cannot be unticked, renamed, removed or reordered (Planner.kt:569-590).
- No manual order, no tags, no saved filters, no bulk actions, no templates, no task export.
- The quick-add draft is lost on leaving (Planner.kt:400; spec 4.6).
- Focus links only the first four of today's tasks (Focus.kt:176); no focus history screen or per-task time.
- A blank title on save silently keeps the old one and says "Saved" (Planner.kt:630).
- Habits have no heatmap.
- Focus: see finding 7.

### MINT
**Strong:** exact money, eight account types, reconciliation, sealed storage, statement import, calm wording ("over the plan").
**Gaps:**
- Custom categories exist in `MintStore` but no screen reaches them. This blocks real use first.
- No recurring transactions; paying a bill does not offer to record the expense.
- No splits, savings targets or budget rollover (spec says not built).
- "PHP 1,250.50" instead of ₱1,250.50.
- Activity list capped at 50 with filters by type only; review locked to the current month.
- Export is JSON only; no CSV out.
- The weekly review is one tap that reviews nothing and pays more than reconciling.

### HEAL
**Strong:** tone ("no rush", "so far"), units, Health Connect import with provenance, sealed storage.
**Gaps:**
- No day navigation; drinks only in ml with no drink type.
- Sleep length by 15-minute steps; no naps.
- No weight goal or chart.
- Strength is minutes only (no sets and reps); no nutrition, steps or heart rate.
- The weight unit is set in two places.
- The first drink of the day is the only paid drink, which invites a token log.

### CARE
**Strong:** routines with one-tap tick on the landing page, DBT skills with steps, a journal draft that survives, nothing emotional is ever rewarded.
**Gaps:**
- No one-tap mood check-in; the diary card is 36 keys and its title is cut off (seen).
- Journal entries cannot be edited though the store supports it (CareStore.kt:380).
- Diary history stops at 14 cards; tags are fixed and not filterable.
- Calm has one breathing pace and no grounding tool.
- Care is not lockable although Mint is.
- Eight menu cards where the spec has four areas.
- Reward copy inside a calm break ("under a minute, so it doesn't earn anything").
- Visitor play uses the five "love languages"; Pokémon-Amie and Refresh (pet, feed, play, groom) are the canon equivalent.

### HOME
**Strong:** deterministic schedules with no backlog, exact quantities, restock list, warranties, search across everything, neutral overdue wording.
**Gaps:**
- Chores cannot repeat "after it is done" (maintenance can), the main Tody-style need.
- No room view ("Kitchen: 3 due").
- No inline minus on a supply row; the shop list cannot be ticked off while walking.
- No photos or receipts on assets.
- `Household.kt` is 1,316 lines of repeated edit rows.

### LINK
**Strong:** title-only capture, duplicate hints that never merge, 29 February handled, follow-ups convert to Plan tasks.
**Gaps:**
- No contact import (no READ_CONTACTS anywhere), so every person is typed.
- A birthday cannot be added from a person's page.
- Reminders are off by default and can only be set after saving.
- No age or "turns N", no birthday month view.
- Tags are not filterable though the store supports it.
- No vCard or CSV.
- Gift ideas and an opt-in "last talked" note would make it a real personal CRM.

### GEAR
**Strong:** 18 working tools, search with typo tolerance, favourites, timers that survive with notification actions, honest sensor fallbacks.
**Gaps:**
- No alarm clock; no notes or scratchpad.
- Calculator keys are small with wide gaps and the display is a small field (seen); a half-typed sum is lost on leaving.
- The helper row and Star key take the top of every tool.
- Compass has no true north; level has no calibration.
- A denied camera permission only says "Nothing was scanned".
- No launcher shortcut straight to a tool.

### WORLD
**Strong:** real PMD town and map art, real rank badges, "Since you last looked", one next action, facilities that are canon (Kecleon Shop, Spinda's Café, Croagunk's Swap Shop, the Dojo).
**Gaps and canon:**
- Three dungeons are marked invented in the code (Glacier Palace, Ancient Ruins, Distortion Rift) and wild tables are "illustrative until checked".
- Only 14 PMD dungeons although the repo holds maps for far more.
- No music at all.
- Kangaskhan Storage and Duskull Bank are missing; Camp should be Friend Areas.
- Power uses a "favoured type" flag instead of the type chart.
- No IQ or Gummis.
- The report is a wall of text where a results table with icons would do.
- The mainline regions use trainer-world lines ("Every trainer dreams of it") in a world without humans.

### Launcher
**Seen:** the edit mode has overlapping labels ("App buttons" over "Folders", "Date and time" over the date) and the Buddy widget is a cropped, heavily enlarged portrait.
**Gaps:** module widgets from the spec (focus, tasks, care, heal, mint, home), app shortcuts on long press, search beyond app names (Gear tools, people, tasks), swipe down. Needs a device pass before judging its feel.

## 5. Pokémon: canon and collection

**Faithful today (code):** stat formula, 25 natures, IVs and EVs, abilities with the hidden slot, gender ratios, 527 evolution rows with stones, time of day, gender and regional forms, real learnsets, cries, genus, height and weight, and "Oh?" at a hatch.

**Deviations a fan will feel, most jarring first:**
1. Rare Candy as wages and flavour-named berries (findings 8 and 9).
2. **Level evolutions cost a Super Candy** (an invented item). Offer the evolution when the level is reached; keep stones, the Linking Cord and held items as the gates.
3. **One candy price for every species.** Export `growth_rate` and price by the real curve. The same export can bring `base_happiness` (everything starts at 70 today), egg groups and catch rate.
4. **Pokédexes named after modules** ("Plan Region #001 Snivy"). Lead with the National Dex and real regional dexes; show module habitats as a filter or a habitat page (FireRed's habitat list is the model).
5. **Shiny odds 1 in 128.** An owner decision; worth knowing it removes the meaning of a shiny for a fan. The Masuda-style 1 in 512 would still be generous.
6. **51 "trial" evolutions.** Those that are "level up knowing a move" can now be checked against the real moveset.

**Summary and PC against the games:**
- Summary: one long scroll of text rows. Wanted: pages (Info, Stats with the hexagon, Moves, Memo), the ball it came in, ribbons and marks (spec 20.1.6 and 20.1.7, not built). Stat bars are nearly empty at low level because they scale to the maximum (seen).
- PC: three wide tiles per row on a blank page (seen). Wanted: a 6 by 5 box on a real wallpaper (six are already in assets and used elsewhere), box names, drag between box and party, multi-select.
- Pokédex: a visible cry button, size comparison, an entry per form, more than one game's text.

**Assets:**
- PMD covers 983 of 1,025 species; the other 42 and about 240 forms fall to Black and White sprites or modern stills, so some lists mix three styles. Use the base species' PMD sprite for a form before leaving PMD (ASSETS policy step 2).
- The level-up sound is recorded in the repo's own notes as a dungeon wind.
- UI sounds are a synth approximation "not yet listened to".
- Cries are the modern set; fine, but a PMD-first app could offer the classic set.

## 6. Suggested order

**Now (small, high return)**
1. Fix the zero glyph and number typography.
2. Shared date and time picker; full edit and backdating in Mint, Heal, Home, Link.
3. Wire Mint's category screen; remember last account and category; ₱.
4. Undo strip for every delete.
5. Compact lead stage, one-line reward strip, real first-record tutorials.
6. Chips take the module accent; red only for game actions; no boxes in boxes; Back on the left.
7. Real berry names; PMD portraits for moods and reactions; the missing welcome portrait or a PMD opening.

**Next (the "replace my other apps" work)**
8. Home tab as a dashboard with quick log, and a widget per module.
9. Focus: normal mode with an ongoing notification, break cycle, stopwatch, history.
10. Charts in Mint, Heal and Care.
11. Recurring transactions and "paid, record it"; contact import and birthday-first flow; chores "after it is done" and a room view; one-tap mood.
12. Plan: priority, full subtasks, manual order, tags, bulk actions.
13. Gear: alarm, notes, a proper calculator keypad.

**Then (the Pokémon depth)**
14. Exp. Candies, growth rates, evolution at level, base friendship.
15. Summary pages, ball, ribbons; PC boxes with wallpapers.
16. One identity frame (team or trainer) and one rank ladder.
17. World: canon-check the dungeon list, music, Storage and Bank, Friend Areas.
18. Launcher: edit-mode layout, module widgets, a device pass.

## 7. Decisions for the owner

1. **Trainer or team?** Mainline framing (a trainer, Oak) or PMD framing (a team, no humans). Everything in finding 11 follows from this.
2. **Body font.** Keep Pokémon DP for body text with a fixed zero, or move numbers and long text to a sans as DESIGN_SYSTEM 5.3 says.
3. **Strict focus.** May a normal session survive leaving the app, with strictness kept for Deep Focus?
4. **Candies.** Rename the wage to Exp. Candy and drop Super Candy for level evolutions?
5. **Module parties.** Keep six per module, or shrink to a lead and two?
6. **Shiny odds** and **module-named Pokédexes:** keep as decided, or move toward canon?
