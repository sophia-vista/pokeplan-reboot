# The economy: sources, sinks and caps

> **Retired (0.130.0, REP-012):** Supabase, accounts, cloud sync, the friends board and gifts, the server reward function and the JavaScript build were removed. Wherever this document describes them, read it as history; `docs/pokelife/` is the current plan.

> **Describes what the code pays today (rules version 17).** The source of truth for the economy's shape is `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md` (reward ledger, one Bag, one currency: Poké). Coins and World Poké are one balance, Poké, since rules version 15 (the tables below still say "coins" for what Plan pays and "Poké" for what World pays; both are the same Poké); this file stays accurate for the code**

Every reward source, every use and every cap, as the code has them under **rules version 30** (`RewardRules.VERSION` in
`pokemon-core`). This is principle 6 of `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md` ("every reward source, cap and use is written down"). The
reasons and pacing targets are in `docs/design.md`; the simulated pacing is in `docs/economy-report.md`.

**Keep it true.** A change to anything below that changes what an event pays bumps `RewardRules.VERSION` and updates this
file in the same PR (`GoldenVectorTest` refuses to regenerate `tools/server/golden.json` with new outcomes under the old
version). Re-run `./gradlew :plan-core:simulateEconomy` after it. Names in `code` are the constants to search for.

Where the rules live: `RewardEngine`, `TrainerRank`, `CandyRewards` and `Egg` (the rules versioned above, also run by the
server); `Transfer`, `Items`, `Perks`, `Rules` (`CandyRules`, `FriendshipRules`, `Shiny`), `EvolutionRules` in
`pokemon-core`; `TaskRules`, `Goal`, `Review`, `Streak`, `FocusSession` in `plan-core`; `CareRules` in `care-core`; `GameStore`, `CareStore` and `Quests` in `app-core`.

**Version history.** 1: everything before Care. 2: the Care events (`RewardEngine.care`) and the friendship sources Routine and Calm break; nothing that version 1 paid changed. 3: trade-evolution items (Metal Coat, King's Rock...) join the drop table; Magic Guard hatches a failed session's egg once a week; the Streak keeper perk forgives a second missed day. 4: training plans are gone, so tasks pay no EVs; Synchronize gives +3 Super Candy chance instead of +25% EVs. 5: a new trainer rank curve, quick at first and slower after (0, 50, 150, 325, 650, 1,150, 1,900, 3,000, 5,000, 9,000; it was 0, 625 ... 10,625). 6: the Pick-for-me bonus (candies × 1.10), task friendship capped at 10 a day, and the streak day rolls over at 4am. 7: Care's practised skill (a DBT skill from the library: friendship (skill) and a berry, 3 a day) and the friendship source Skill; nothing else changed. 8: a Pokémon records which module's party it is in (`partyId`, six per party); nothing paid changed. 9: Care has its own lead (`careLeadId`) and perk buckets: the lead takes Care's friendship, and Friendship gain (a chance of one extra friendship step) and Berry finder (a chance of a second berry) apply; with no Care lead Care pays as before. 10: befriending a Care visitor (`RewardEngine.befriend`), one a day. 11: a befriended visitor usually leaves an egg of its family's first stage (`Dex.lineRoot`), and joins itself 10% of the time (was 5%, and always its own species); each visitor can have a visit a day, so up to 4 friends a day. 12 *(owner, October 2026: "easy to complete the Pokédex, hard to get good versions")*: a level costs half as many Rare Candies (`CandyRules.CURVE` 300, was 150); focus sessions pay `CANDIES_PER_FIVE_MINUTES` (2) Rare Candies per 5 minutes; every Care moment pays Rare Candies (`CARE_CANDIES`: routine 3, calm break 4, skill 4, rhythm 15) and fills Care's Egg meter (`CARE_EGG_POINTS`, a Care Egg every `CARE_EGG_METER_SIZE` (3) points); the first of each species pays `NEW_SPECIES_CANDIES` (5); species not caught yet are 6 times as likely in an egg (`UNCAUGHT_WEIGHT`, was 3); coins buy Rare Candies at the shop (`Shop.COINS_PER_RARE_CANDY`, 5); tasks pay twice the Rare Candies (`CandyRewards`) and the Egg meter fills at 5 (`EGG_METER_SIZE`, was 10); the first of a species also pays a Super Candy; mini-quests pay 2 Rare Candies each and 5 more for all three. Shinies stay as rare per player: eggs come about 1.5 times as often, so the base odds are 1 in 192 (`Shiny.BASE_ODDS`, was 128). IVs and natures are unchanged. 13 *(owner, October 2026)*: shinies go back to 1 in 128 per egg (`Shiny.BASE_ODDS`); with eggs coming more often under version 12, that makes shinies about 1.5 times as common per player as before. Nothing else changed. 14 *(owner, 6 November 2026: Pokémon roles, `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md` section 2.2)*: the global buddy (`buddyId`) is cosmetic and no longer pays or warms anything; Plan's perks, egg luck and friendship come from Plan's party **lead** (`planLeadId`, slot 1; the first member if none is chosen) and its party, and Care's friendship and perks come from Care's lead only (with no Care party Care no longer warms the Plan buddy). A save from before this version gets `planLeadId` set to its buddy when the buddy is in Plan's party (`rolesVersion` 1), so its rewards do not change. No amount or table changed. 15 *(owner, 6 November 2026: one currency)*: Plan's coins and World's Poké are one balance, **Poké**, merged 1:1 (`Poké = coins + World Poké`); every source that paid coins or World Poké pays Poké, and every sink (the Bag's Rare Candy shop, World's facilities, shop and Dojo) takes from the same balance. `AppData.pokeMigration` keeps both source values, the merge time and the rules version. No amount or price changed. 16 *(owner, 7 November 2026: Care Play, CARE.md section 4)*: Care Play (`CareKind.PLAY`) pays its first time with each Pokémon each day, at most 6 a day: 1 friendship step to that Pokémon, 1 Rare Candy, 1 point on Care's Egg meter. An egg a Care friend leaves may take light traits from the visitor (`VisitorInheritance`: IV floors, a nature), which makes no Pokémon rarer, shinier or stronger than the IV caps allow. Nothing else changed. 17 *(owner, 7 November 2026: HEAL, `docs/pokelife/MODULES/HEAL.md` section 22)*: Field Research (`HealKind`, `RewardEngine.heal` and `healEgg`): a logged activity pays 4 Rare Candies, a berry, 1 friendship step to Heal's lead (`FriendshipRules.Source.ACTIVITY`) and 1 trainer point and adds 2 points to Field Research's Egg meter; the day's first logged drink pays 1 Rare Candy, 1 trainer point and 1 point; 4 points give a Field Egg from the new Heal Region (`tools/species/regions/heal.txt`) into Heal's party. The event carries no health value (`HealEvent`: id, module, opaque reference, kind, time). On by default (since 0.141.0; it was off until turned on) and off in Settings at any time. Nothing a rule paid before changed. 18: Mint's Ledger Research (`RewardEngine.mint`, `MintKind`) and the friendship source Ledger: a reconciled account pays 3 Rare Candies, 1 friendship step to Mint's lead and 1 trainer point and adds 2 points to Ledger Research's Egg meter; a finished weekly review pays 5 Rare Candies, 1 friendship step and 1 trainer point and adds 3 points; 4 points give a Ledger Egg from the Mint Region (`tools/species/regions/mint.txt`) into Mint's party. The event carries no figure (`MintEvent`: id, module, opaque reference, kind, time). Nothing a rule paid before changed. 19 *(owner brief, 8 November 2026: HOME, `docs/pokelife/MODULES/BASE.md` section 16)*: Home's Habitat Discovery (`RewardEngine.home` and `homeEgg`, `HomeKind`) and the friendship source Habitat: a chore done pays 2 Rare Candies, 1 friendship step to Home's lead and 1 trainer point and adds 1 point to Habitat Discovery's Egg meter; a piece of maintenance done pays 4 Rare Candies, 1 step, 1 point and adds 2 points; 5 points give a Habitat Egg from the Home Region (`tools/species/regions/household.txt`) into Home's party. The event carries no household word (`HomeEvent`: id, module, opaque reference, kind, time). Nothing a rule paid before changed. 20 *(owner brief, 8 November 2026: LINK, `docs/pokelife/MODULES/LINK.md` section 15)*: Link's Postcard Trail (`RewardEngine.link` and `linkEgg`, `LinkKind`) and the friendship source Follow-up: a follow-up finished pays 2 Rare Candies, 1 friendship step to Link's lead and 1 trainer point and adds 1 point to Postcard Trail's track; 3 points give a Postcard Egg from the Link Region (`tools/species/regions/link.txt`) into Link's party. The event carries no word of a person or a follow-up (`LinkEvent`: id, module, opaque reference, kind, time, policy). Nothing a rule paid before changed. 23 *(owner, 14 November 2026)*: the life modules drop items (`ModuleDrops`: 10% per paid moment of Care, Heal, Mint, Base and Link from a themed table, below), a finished Mint review always pays a Nature Mint, and Paldea floors in World find Gimmighoul Coins. Nothing a rule paid before changed. 24 *(owner, 14 November 2026: `INTEGRATIONS_AND_PRIVACY.md` 5.2.2)*: a session read from Huawei Health's own pages (provider `huawei-screen`) pays like one logged by hand, under the same length rule and daily share on the session's own day, if it ended within the last 8 days (`HealRules.IMPORT_FRESH_MILLIS`, reads are nightly to weekly) and is not flagged as a possible repeat of one already here. Health Connect imports still pay nothing. No amount changed. 25 *(owner, 14 November 2026: `INTEGRATIONS_AND_PRIVACY.md` 5.2.2, `MODULES/HEAL.md` 22B)*: Field Research pays for **steps** and for **reading records in** (`HealKind.STEPS` and `HealKind.IMPORT`): one payment for each full 2,000 steps of today, at most 5 a day (10,000 steps), and one for each family of record a read brought something new in (workouts that do not pay for themselves, sleep, body measurements, drinks, heart rate and the other readings), once a day each, at most 3 a day; each pays 1 Rare Candy, steps also 1 friendship step to Heal's lead (`FriendshipRules.Source.ACTIVITY`); neither adds Egg meter points, trainer points, berries or item drops (`HEAL_EGG_POINTS` 0, `HEAL_TRAINER_POINTS` 0). Set with `simulateEconomy` (its "Heal: steps" table). The events carry no figure. Nothing a rule paid before changed, except that a Health Connect read now pays through the new import rule. 26 *(owner, 14 November 2026: "the economy seems stingy", and anything that grows one Pokémon should be generous, while permanent upgrades stay as they were)*: Poké from real life goes up: a focus session pays 1 Poké a minute (`COINS_PER_FIVE_MINUTES` 5, was 1), a task `tier × 10` (`COINS_PER_TIER` 10, was 2), and each deliberate paid moment of Care, Heal, Mint, Base and Link pays `MODULE_POKE` (10; Care's weekly rhythm `RHYTHM_POKE` 30; Care Play, Heal's steps and imports and befriending pay none). The Poké Mart becomes its own section of the Pokémon tab and sells Super Candy (100) and each berry (20) besides Exp. Candy (5); the Kecleon Shop stops selling its climbing-price Exp. Candy and its prices fall to about a fifth (below). World's facilities, the Dojo and the Café are unchanged. Nothing else changed. 27 *(owner, 14 November 2026: `FEATURES/LAUNCHER.md` 4.5, REP-052)*: the launcher town's dungeon floor has `TownFinds.PER_DAY` (3) finds a day, below; a tap puts one in the Bag or adds its Poké. Nothing a rule paid before changed. 28 *(owner, 15 November 2026: `MODULES/BASE.md` 9.5, REP-053)*: Base's Secret Base room has a **Decoration meter**: the Habitat Discovery payments that already exist add points (a chore done 1, maintenance done 2), under the same switch, daily caps and event ids; at 5 points one **decoration** is found (`Reward.Decoration`, a random one of the 42 the player does not own yet, drawn from the event id), the rest carries over, and with all 42 owned the meter stops. A new save, and a save from before the room, is given 3 starter decorations once. Decorations are cosmetic: no bonus, no sale value, not a Bag item. Nothing a rule paid before changed. 29 *(owner, 15 November 2026: Care Play was the cheapest egg in the app)*: Care Play adds no point to Care's Egg meter (`CARE_EGG_POINTS`, `PLAY` is 0); its friendship step and Rare Candy are unchanged. Nothing else changed. 30 *(owner, 20 November 2026)*: the weekly review is scrapped, so its 1 berry a week is gone, and the legendary quest requirement `WEEKLY_REVIEWS` goes with it (Uxie, Mesprit and the mythical long-road quests simply lose it; `HABIT_DAYS` now counts the days a repeating task was finished, plus any old ticked-target days). Frequency targets are scrapped: a saved target becomes an ordinary recurring task on load (the conversion pays nothing), so completions of repeating tasks pay as ordinary tasks, with no payout path of their own. 31 *(owner, 20 November 2026)*: a task has a **reward focus** (Exp, Egg or Items; Exp is exactly the old payout, so every old case pays the same) and a PLAN event can name a **companion** that gains exp and may find an item when an occurrence ends (table above). The Egg meter is 5 points in the code and here (design.md said 10 by mistake and is fixed). Nothing else changed.

## 1. Sources

### What each event pays

| Event | Pays | Constant |
|---|---|---|
| **Focus session completed** (5 minutes or more) | One egg, hatched at once (rarity from the length, section 1.3) · Rare Candies `2 × minutes / 5` (× Candy yield) · Poké `minutes` (1 a minute, counted in 5 minute steps) · trainer points `minutes / 5` · 1 random berry · buddy friendship (focus session) · an active day | `COINS_PER_FIVE_MINUTES`, `TrainerRank.pointsForEffort`, `Egg.MIN_MINUTES` |
| **Task or habit completed** (tier 1 to 5 = rank E to A) | Rare Candies (table below), maybe Super Candies · Poké `tier × 10` · trainer points `tier × 2` · an item drop chance `8 + 2 × tier` % · buddy friendship (task) · `tier` points on the Egg meter · an active day | `COINS_PER_TIER`, `pointsForAchievement`, `DROP_BASE_PERCENT`, `DROP_PER_TIER_PERCENT` |
| **Egg meter full** (5 points) | A Task Egg, rolled as a 15 minute session (no lure, no spotlight); the rest carries over | `EGG_METER_SIZE`, `TASK_EGG_MINUTES` |
| **Task reward focus** (owner, 20 November 2026; version 31) | **Exp** (default): the row above, unchanged. **Egg**: half the Rare and Super Candies of each roll (rounded down, at least 1) and `ceil(tier × 1.5)` points on the Egg meter instead of `tier`. **Items**: half the candies and double the item-drop chance (capped at 100%). Poké and trainer points never change. Chosen per task (default in Settings), read when the task is ticked. Not simulated: `simulateEconomy` plays the default focus only *(tune)* | `RewardFocus`, `EGG_FOCUS_POINT_FACTOR`, `FOCUS_CANDY_SHARE`, `ITEMS_FOCUS_DROP_FACTOR` |
| **Event with a companion ends** (owner, 20 November 2026; version 31) | The companion (a PLAN party Pokémon able to take exp) gains `candies × XP_PER_CANDY` exp, `candies` = 1 for each 30 minutes of a timed event (a quarter hour counts as 1; 4 at most for one event), at most 6 a day across all events (counted on the day it is paid, so moving an event never resets it); and a 10% chance of one item from the ordinary drop table when it paid. Once per occurrence, only if the occurrence ends after the companion was set (and after the last edit to the event's day, time, length or repeat), within two days of ending; an all-day event, a skipped occurrence and a device calendar's event never pay. No Poké, no trainer points, no Egg points, no friendship *(tune)* | `EventRewards`, `EVENT_MINUTES_PER_CANDY`, `EVENT_MAX_CANDIES`, `EVENT_CANDIES_DAILY_CAP`, `EVENT_ITEM_PERCENT` |
| **Subtask done** | 1 Rare Candy, once per subtask, at most 5 paid per task, after the task rank's minimum age | `SUBTASK_RARE_CANDIES`, `MAX_PAID_SUBTASKS` |
| **Goal milestone done** (24 hours old or more) | 1 Super Candy, at most 3 a day; an active day | `SUPER_CANDIES_PER_MILESTONE`, `MILESTONE_MIN_AGE_MILLIS`, `MAX_MILESTONE_REWARDS_PER_DAY` |
| **Every milestone of a goal done** | 1 Nature Mint, once per goal | `Goal.bonusGiven` |
| **Mini-quest** ticked (honour system, 3 a day from 4am, one reroll before any is done that gives three others; petting the buddy ticks "Pet your buddy") | 1 random berry and 2 Rare Candies each, 1 more berry and 5 more candies for the third; no active day | `MiniQuests.ALL_THREE_BONUS_BERRIES`, `RARE_CANDIES`, `ALL_THREE_BONUS_CANDIES`, `GameStore.completeMiniQuest` |
| **Welcome back** after 7 or more days away (once per absence) | 5 Rare Candies and 2 berries of one flavour | `WELCOME_BACK_RARE_CANDIES`, `WELCOME_BACK_BERRIES` |
| **Transfer** a Pokémon | Rare Candies (Common 8, Uncommon 12, Rare 22, Super Rare 60, plus a fifth of the candies it was fed), coins twice the base candies (16 / 24 / 44 / 120), its held item back | `Transfer.candiesByRarity`, `COIN_FACTOR`, `REFUND_DIVISOR` |
| **Legendary or mythical quest** claimed | That Pokémon (out of the party; it can be shiny) | `Quests` |
| **Friend's gift** opened | 3 Rare Candies (one gift per friend a day) | `platform.send_gift` |
| **Starter** | The starter (never shiny) and a tutorial task that pays at once | `GameStore.start` |
| **A module's first real record** (Plan, Care, Heal, Mint, Home, Link; once per save) | One Common egg of the module's region, hatched into its party and leading it (the first of a species pays as usual) | `GameStore.firstRecordEgg`, `RewardEngine.tutorialEgg` |
| **Petting the buddy** | Friendship (pet), up to 10 a day | `PET_DAILY_CAP` |
| **Care: routine done** (first tick of the day for that routine) | Buddy friendship (routine) · 3 Rare Candies · 10 Poké · 1 point on Care's Egg meter · 1 trainer point. At most 5 routines a day | `MODULE_POKE`, `CARE_POINTS`, `CARE_CANDIES`, `CARE_EGG_POINTS`, `CareRules.ROUTINES_PAID_PER_DAY` |
| **Care's Egg meter full** (3 points: a routine 1, a calm break or a skill 2, a rhythm 0) | A Care Egg from the Care Region, rolled as a 15 minute session (species not caught yet × 6), hatched at once into Care's party (else the PC); it leads Care if there is no lead; the rest carries over. Kept by the app (counter `care-egg-meter`), never sent to the server | `RewardEngine.careEgg`, `CARE_EGG_METER_SIZE` |
| **Care Play** (the first time with each Pokémon on a day, at most 6 a day) | 1 friendship step to the Pokémon played with, 1 Rare Candy; no Egg meter point (since rules version 29); repeats pay nothing | `CareKind.PLAY`, `CARE_CANDIES`, `CARE_EGG_POINTS` |
| **First of a species** (from any egg, Task Egg, Care Egg or friend) | 5 Rare Candies and 1 Super Candy, so it can evolve | `NEW_SPECIES_CANDIES`, `NEW_SPECIES_SUPER_CANDIES` |
| **Care: calm break** (1 to 2 minutes; under a minute is kept but pays nothing) | Buddy friendship (calm break) · 1 random berry · 10 Poké · 1 trainer point. At most 3 a day | `CareRules.BREAKS_PAID_PER_DAY`, `BREAK_MIN_SECONDS` |
| **Care: skill practised** (a DBT skill from the library, each skill once a day; paced breathing pays as a calm break) | Buddy friendship (skill) · 1 random berry · 10 Poké · 1 trainer point. At most 3 a day | `CareRules.SKILLS_PAID_PER_DAY` |
| **Care: kept a rhythm** (care done on 4 days of a week: a routine, a calm break or a skill) | 3 random berries · 30 Poké · 5 trainer points, once a week | `RHYTHM_POKE`, `RHYTHM_BERRIES`, `RHYTHM_POINTS`, `CareRules.RHYTHM_DAYS` |
| **Care: befriend a visitor** (its warmth reaching `Visitors.FRIEND_PROGRESS` (16), about two visits; `MODULES/CARE.md` section 6.8, which replaced the four warm days in 0.132.0) | An egg of the visitor's family's first stage (`Dex.lineRoot`), which hatches at once into Care's party (else the PC); `BEFRIEND_DIRECT_JOIN_PERCENT` (10%, up to +5 with Visitor luck) of the time the visitor itself joins instead, with friendship `BEFRIEND_JOIN_FRIENDSHIP` (150). Either way every IV is at least `BEFRIEND_IV_FLOOR` (10, up to +6 with Visitor luck) · `BEFRIEND_POINTS` (3) trainer points. No visit limit in a day (a visitor rests three hours after one) and no paid Care action needed first; visitors arrive about 1.35 a day, four in the meadow and up to six waiting, and at most `BEFRIEND_DAILY_CAP` (4) friends a day. Free play pays nothing | `RewardEngine.befriend`, `Visitors` |
| **Heal: activity logged** (Field Research on; a new record of 10 minutes or more that ended within the last 12 hours, not in the future; never edited or backfilled; imported from Health Connect never. **Read from Huawei Health's pages** (rules version 24): within the last 8 days instead, and not flagged as a repeat of one already here) | Heal's lead 1 friendship step (activity) · 4 Rare Candies · 10 Poké · 1 random berry (a second one with Berry finder) · 1 point on Field Research's Egg meter ×2 · 1 trainer point. At most 3 a day | `HEAL_CANDIES`, `HEAL_EGG_POINTS`, `HEAL_POINTS`, `HealRules.ACTIVITIES_PAID_PER_DAY`, `RESEARCH_MIN_MINUTES`, `FRESH_MILLIS`, `IMPORT_FRESH_MILLIS`, `HealImport.PAYING_PROVIDERS` |
| **Heal: first drink of the day** (Field Research on; logged now) | 1 Rare Candy · 10 Poké · 1 point on Field Research's Egg meter · 1 trainer point. Once a day. Later drinks pay nothing | `HealKind.PARTICIPATION`, `HealRules.PARTICIPATION_PAID_PER_DAY` |
| **Field Research's Egg meter full** (4 points: an activity 2, a drink 1) | A Field Egg from the Heal Region, rolled as a 15 minute session (species not caught yet × 6), hatched at once into Heal's party (else the PC); it leads Heal if there is no lead; the rest carries over. Kept by the app (counter `heal-egg-meter`) | `RewardEngine.healEgg`, `HEAL_EGG_METER_SIZE` |
| **Heal: steps** (Field Research on; Huawei Health's step count of *today*, read from its Health page or step notification) | One payment for each full 2,000 steps (`HealRules.STEPS_PER_REWARD`), at most 5 a day: 1 Rare Candy · 1 friendship step to Heal's lead (activity). No berry, no Egg meter point, no trainer point, no item drop. A later read pays only the new amounts; deleting the record and reading again pays nothing twice (sealed marks) | `HealKind.STEPS`, `HEAL_CANDIES`, `HealRules.STEPS_PAID_PER_DAY` |
| **Heal: records read in** (Field Research on; Health Connect or Huawei Health) | 1 Rare Candy for each family of record that brought something new and recent (within 8 days; not steps, not a workout from Huawei that paid for itself): workouts, sleep, body measurements, drinks, readings. Once a day per family, at most 3 a day. No other reward | `HealKind.IMPORT`, `HealRules.IMPORTS_PAID_PER_DAY`, `importCounts` |
| **Heal: heart rate, resting heart rate, SpO2, skin temperature, stress, sleep score, sleep, body measurement, goal, note, anything else** | Nothing for the value, ever (HEAL.md section 12.3); only the family's read above | |
| **Mint: account reconciled** (Ledger Research on; the account has real transactions; once per account a day, 2 a day; a difference is recorded only as an explicit adjustment) | Mint's lead 1 friendship step (Ledger) · 3 Rare Candies (a fourth with Candy finder) · 10 Poké · 2 points on Ledger Research's Egg meter · 1 trainer point | `MINT_CANDIES`, `MINT_EGG_POINTS`, `MINT_POINTS`, `MintRules.RECONCILES_PAID_PER_DAY` |
| **Mint: weekly review finished** (Ledger Research on; there is an open account and a transaction; once a week) | Mint's lead 1 friendship step (Ledger) · 5 Rare Candies · 10 Poké · 3 points on the meter · 1 trainer point | `MintKind.REVIEW`, `MintRules.REVIEWS_PAID_PER_WEEK` |
| **Ledger Research's Egg meter full** (4 points: a reconciled account 2, a review 3) | A Ledger Egg from the Mint Region, rolled as a 15 minute session (species not caught yet × 6), hatched at once into Mint's party (else the PC); it leads Mint if there is no lead; the rest carries over. Kept by the app (counter `mint-egg-meter`) | `RewardEngine.mintEgg`, `MINT_EGG_METER_SIZE` |
| **Mint: spending, income, a transfer, a balance, a budget, a bill, anything else** | Nothing, ever: MINT.md section 16.2 | |
| **Home: a chore done** (Habitat Discovery on; at most 5 paid a day; one occurrence pays once however often it is undone and done again) | Home's lead 1 friendship step (Habitat) · 2 Rare Candies (a third with Candy finder) · 10 Poké · 1 point on Habitat Discovery's Egg meter · 1 trainer point | `HOME_CANDIES`, `HOME_EGG_POINTS`, `HOME_POINTS`, `HomeRules.CHORES_PAID_PER_DAY` |
| **Home: maintenance done** (on; at most 2 paid a day; one schedule due date pays once) | Home's lead 1 friendship step · 4 Rare Candies · 10 Poké · 2 points on the meter · 1 trainer point | `HomeKind.MAINTENANCE`, `HomeRules.MAINTENANCE_PAID_PER_DAY` |
| **Habitat Discovery's Egg meter full** (5 points: a chore 1, maintenance 2) | A Habitat Egg from the Home Region, rolled as a 15 minute session (species not caught yet × 6), hatched at once into Home's party (else the PC); it leads Home if there is no lead; the rest carries over. Kept by the app (counter `home-egg-meter`) | `RewardEngine.homeEgg`, `HOME_EGG_METER_SIZE` |
| **A new save, or a save from before the room** (once) | 3 starter decorations: Snorlax Doll, Thunder Mat, Big Plant | `Decorations.STARTERS`, `AppData.decorationStarters` |
| **Base's Decoration meter full** (5 points on the same payments: a chore 1, maintenance 2; stops when all 42 are owned) | One Secret Base decoration the player does not own yet (random, from the event id), the rest of the points carry over. A cosmetic: no effect, no value, not in the Bag. The Department Store and Mom's gifts (REP-053, later) add to the same set. Kept by the app (counter `home-decor-meter`, `AppData.decorations`) | `Decorations.advance`, `Decorations.pick`, `Decorations.METER_SIZE`, `GameStore.base` |
| **Home: creating or editing anything, a quantity, the restocking list, a warranty, a note, a schedule, a reminder** | Nothing, ever: BASE.md section 9.2 | |
| **Link: a follow-up finished** (Postcard Trail on; at most 3 paid a day; one follow-up pays once however often it is reopened and finished again) | Link's lead 1 friendship step (Follow-up) · 2 Rare Candies (a third with Candy finder) · 10 Poké · 1 point on Postcard Trail's track · 1 trainer point | `LINK_CANDIES`, `LINK_EGG_POINTS`, `LINK_POINTS`, `LinkRules.FOLLOW_UPS_PAID_PER_DAY` |
| **Postcard Trail's track full** (3 points: one a finished follow-up) | A Postcard Egg from the Link Region, rolled as a 15 minute session (species not caught yet × 6), hatched at once into Link's party (else the PC); it leads Link if there is no lead; the rest carries over. Kept by the app (counter `link-egg-meter`) | `RewardEngine.linkEgg`, `LINK_EGG_METER_SIZE` |
| **Link: adding or importing people, notes, dates, tags, favourites; editing, rescheduling, cancelling or reopening a follow-up; a reminder** | Nothing, ever: LINK.md section 9.2 | |
| **Care: diary card, journal entry, note** | Nothing, ever: entering sensitive information never earns (docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md) | |
| **A bag stack is full** | 1 coin per candy or item that did not fit | `Inventory.addCapped` |

Task perks: candies × 1.10 when finished on time (by its due date), × 1.10 when it had focus minutes and × 1.10 when it was the task "Pick for me" chose (`PICKED_BONUS`, once per pick), then the buddy's
perks. A perk bonus rounds to the nearest whole number and never pays less than the base (`RewardEngine.scaled`), so small
bonuses on small rewards round away.

### Rare and Super Candies per task (`CandyRewards`)

| Tier (rank) | Rare Candies | Super Candy chance | Super Candies |
|---|---|---|---|
| 1 (E) | 6–10 | 10% | 1–2 |
| 2 (D) | 8–20 | 15% | 2–3 |
| 3 (C) | 10–26 | 20% | 3–4 |
| 4 (B) | 12–34 | 30% | 4–5 |
| 5 (A) | 14–40 | 40% | 6–7 |

The Super Candy chance perk adds percentage points.

### Eggs (`Egg`, `HatchRules`)

| Session minutes | Common / Uncommon / Rare / Super Rare |
|---|---|
| 5–19 | 60 / 30 / 9 / 1 |
| 20–39 | 45 / 35 / 15 / 5 |
| 40–59 | 20 / 45 / 25 / 10 |
| 60–119 | 5 / 35 / 40 / 20 |
| 120–239 | 0 / 30 / 45 / 25 |
| 240 and more | 0 / 10 / 50 / 40 |
| Deep Focus at 240 and more | 0 / 0 / 30 / 70 |

- Deep Focus moves a session up one row. Egg luck then moves the rolled rarity up one with its chance (`luckyBump`).
- A species' rarity comes from its evolution line's highest base stat total: 580 or more is Super Rare (`SUPER_RARE_LINE_BST`); the rest are split by rank, the bottom 30% Common, the next 40% Uncommon, the rest Rare. Legendaries and mythicals never hatch.
- Within a rarity: a species not caught yet × 3 (`UNCAUGHT_WEIGHT`), the Lure's type × 3 (`LURE_WEIGHT`), the day's spotlight type × 2 (`SPOTLIGHT_WEIGHT`, one of 18 types by `epochDay % 18`).
- The pool is the chosen region's dex, base forms only: the Plan or the Care Region, both open from the start. The games' regions are retired (0.44.0); a save or event that names one hatches from the Plan Region (`Region.open`). The engine still pays a retired region's pool if an event names it, so the rules version is unchanged.
- Shiny chance: `1/128 × (1 + 0.5 × min(minutes, 240) / 240)` times the shiny perk (`Shiny.chance`); a Task Egg counts as 15 minutes.
- A signed-in phone rolls focus eggs from egg tickets, seeds the server issued ahead of time (stock 20 a device, `TICKET_STOCK`), so the dice are fixed before the session.

### Item drops (`DropTable`, one roll per paid task or habit)

| Group | Weight each |
|---|---|
| 6 vitamins (HP Up, Protein, Iron, Calcium, Zinc, Carbos) | 8 |
| 5 berries | 4 |
| Link Cord | 2 |
| 18 type Lures, each evolution stone, gender-evolution item and trade-evolution item (`Items.tradeItems`) | 1 |
| Lucky Egg, Oval Charm, Star Piece, Scope Lens, Leftovers, Amulet Coin | 1 |
| Shiny Charm, Bottle Cap, Nature Mint, Ability Capsule | 0.5 |
| 18 Tera Shards | 0.1 |

Never dropped: Ability Patch, Mega Stones, the Dynamax Band. Focus sessions never drop items (they pay a berry).

### Module drops (`ModuleDrops`, version 23, owner 14 November 2026)

Every paid moment of Care, Heal, Mint, Base and Link has a 10% chance (`PERCENT`) of one item from its own table; a finished Mint review also pays one Nature Mint every time. Gimmighoul Coins come 10 at a time (`COIN_HANDFUL`). *(tune)*

| Module | Table (relative weights) |
|---|---|
| Care | Strawberry Sweet 4, Oval Stone 2, Everstone 2, Moon, Shiny and Dawn Stone 1 each, Leftovers 0.5 |
| Heal | each vitamin 3, Razor Claw 1, Razor Fang 1, Sun, Leaf and Water Stone 1 each |
| Mint | Nature Mint 6, Gimmighoul Coin 3, Bottle Cap 1, Amulet Coin 1, Star Piece 1 |
| Base | Fire, Water, Thunder and Leaf Stone 2 each, Ice and Dusk Stone 1 each, Everstone 2, Oval Stone 1 |
| Link | Link Cord 4, each trade-evolution item 1 |

World: a Paldea floor has Gimmighoul Coins 30% of the time (1 to 8, `WorldSim.COIN_PERCENT`, `COIN_MAX`), and Paldea's milestones pay 670 in all.

### The town floor's finds (`TownFinds`, version 27, owner 14 November 2026)

The launcher's dungeon floor (`FEATURES/LAUNCHER.md` 4.5) shows `PER_DAY` (3) finds each day, chosen from the date alone (the same all day, whatever the layout; New floor layout moves them and never adds any). A tap collects one, or a Pokémon walking onto it picks it up (0.189.0), once (`Counters.TOWN_FINDS`, a bit per find): an item goes into the Bag (over the limit, Poké, as everywhere), Poké is added. Weights *(tune)*: each of the 5 berries 4, each of the 6 vitamins 2, Poké 8 (`POKE_MIN` 10 to `POKE_MAX` 30). So a day averages about 1 berry, 0.7 of a vitamin and 13 Poké (about one berry's price at the Poké Mart) for a tap or three on the home screen; no other source changed.

### The Poké Mart (`Shop`, its own section of the Pokémon tab since 0.187.0)

The everyday shop: Exp. Candy 5, Super Candy 100, a berry of any flavour 20 (`COINS_PER_RARE_CANDY`, `SUPER_CANDY_PRICE`, `BERRY_PRICE`), bought 1, 10 or as many as the Poké and the Bag's room allow (`GameStore.martBuy`). *(tune)*

### The Kecleon Shop (`KecleonShop`, 0.182.0; prices 0.187.0)

The specialty shop in World's Town; it sells no candy since 0.187.0 (it had an Exp. Candy at 2,500 rising 500 a purchase). Prices are about a fifth of 0.182.0's, as everything here grows one Pokémon. **Always in stock** every evolution stone 400, the Link Cord 500, the Everstone 300, 50 Gimmighoul Coins 150; **today's shelf**, 5 items chosen for the player's local day from trade and held evolution items 500, vitamins 100, perk held items 500 (Shiny Charm 1,500), Tera Shards 1,000, Nature Mint 400, Ability Capsule 600, Bottle Cap 800, Ability Patch 2,000. No countdown, nothing sold only on the shelf (every catalogue item comes round). *(tune)*

### Friendship (`FriendshipRules`, 0 to 255, starts at 70)

| Source | Base | Who |
|---|---|---|
| Pet | 2 | the buddy, up to 10 a day |
| Focus session | 3 | the buddy |
| Task or habit | 1 | the buddy, the first 10 pieces of paid work a day (`TASK_FRIENDSHIP_DAILY_CAP`) |
| Berry | 1 | the Pokémon fed |
| Level up | 1 | the Pokémon |
| Care routine | 1 | the buddy, up to 5 routines a day |
| Calm break | 1 | the buddy, up to 3 a day |
| Care skill | 1 | the buddy, up to 3 skills a day |
| Heal activity | 1 | Heal's lead, up to 3 activities a day |
| Mint (reconciled account, review) | 1 | Mint's lead, up to 2 reconciled accounts a day and 1 review a week |
| Home (chore done, maintenance done) | 1 | Home's lead, up to 5 chores and 2 pieces of maintenance a day |

Bond styles (from the nature) double their matching source (Comfort: pet and routine, Presence: focus, calm break and skill, Gifts: berry); Balanced gets
1.5 × on every source, rounded up. Friendship never goes down.

### Perks (`Perks`)

Perk buckets: egg luck, candy yield, Super Candy chance, item finder, shiny hunter, streak keeper, coins. The buddy's types
give up to 15 each (× power, friendship up to × 1.2, nature × 1.1 or 0.9; a second type half), party members assist at a
quarter, a held item +4 (Amulet Coin +10 coins), abilities +3 to +10 under their condition. A Guardian adds its
signature perk (10, +5 for a second type). Caps in section 3.

- **Held items** are given from the Bag or the Pokémon's page (`GameStore.holdItem`, `takeItem`); what it held before goes
  back to the bag. Perk items, Mega Stones and trade-evolution items can be held; Guardians hold nothing.
- **Streak keeper** at 10 or more (`STREAK_KEEPER_THRESHOLD`) forgives a second missed day a week, for the daily streak and
  for habit streaks (`PerkBonuses.extraForgivenDays`).
- **Magic Guard** (the buddy's ability when the session started): once a week, a failed session of 5 minutes or more
  still hatches its egg, paid as a session of the minutes focused, never as Deep Focus. It stays a failed session in the
  history and does not count as an active day (`GameStore.magicGuard`, counter `magic-guard-<week>`).
- The perks that count for a session are the buddy's when it started (`FocusSession.buddyIdAtStart`).

## 2. Sinks (uses)

| Resource | Spent on | Constant |
|---|---|---|
| Rare Candy | 1 per feed; a level costs `2 + level² / 300` candies (2 at the start, 10 at level 50, 34 at level 99; 1,249 for 1 to 100), half when the Pokémon is more than 5 levels below the party's best | `CandyRules.candiesNeeded`, `CATCH_UP_GAP` |
| Poké | The Poké Mart: 5 an Exp. Candy, 100 a Super Candy, 20 a berry; the Kecleon Shop (above); World's facilities, Café and Dojo | `Shop`, `GameStore.martBuy`, `KecleonShop`, `GameStore.worldBuy` |
| Super Candy | 1 per evolution by level, friendship, time, random branch, trial, or gender with no item | `EvolutionRules` |
| Evolution stone, gender item | 1 per evolution that needs it | `EvolutionRules` |
| Link Cord | 1 per trade evolution (and the held item it needs) | `EvolutionRules` |
| Vitamin | +10 EVs in one stat | `VITAMIN_EVS` |
| Berry | +1 friendship (more with the Gifts or Balanced style) | `GameStore.useItem` |
| Lure | Used up when its session hatches; kept when the session fails | `RewardEngine.effort` |
| Pokémon | Transferred for candy and coins (never the buddy, the last party member, a starred one, or a legendary or mythical) | `Transfer.block` |
| Trainer points | Never spent; they open features, Guardian slots and quests. Ranks at 0, 50, 150, 325, 650, 1,150, 1,900, 3,000, 5,000 and 9,000 | `TrainerRank` |

## 3. Caps

| Cap | Value | Constant |
|---|---|---|
| Candy stack (Rare, Super) | 9,999; the rest becomes coins | `Inventory.MAX_CANDIES` |
| Other item stack | 999; the rest becomes coins | `Inventory.MAX_STACK` |
| Level | 100; legendaries and mythicals are not fed | `CandyRules.MAX_LEVEL` |
| Friendship | 255; friendship evolutions at 220 | `FriendshipRules.MAX`, `EVOLVE_AT` |
| Pet friendship | 10 a day | `PET_DAILY_CAP` |
| Heal | 3 logged activities, 1 first drink, 5 step amounts and 3 record reads a day, and an activity only within 12 hours of its end (the world checks again: `GameStore.HEAL_DAILY_CAP`; an event id pays once, `AppData.healPaid`), at most 4 trainer points a day (steps and reads add none) | `HealRules`, `HEAL_DAILY_CAP` |
| Home | 5 chores and 2 pieces of maintenance a day (the world checks again: `GameStore.HOME_CAPS`; an event id pays once, `AppData.homePaid`) | `HomeRules`, `HOME_CAPS` |
| Link | 3 follow-ups a day (the world checks again: `GameStore.LINK_CAPS`; an event id pays once, `AppData.linkPaid`) | `LinkRules`, `LINK_CAPS` |
| Mint | 2 reconciled accounts a day and 1 review a week (the world checks again: `GameStore.MINT_CAPS`; an event id pays once, `AppData.mintPaid`), at most 3 trainer points a day | `MintRules`, `MINT_CAPS` |
| Care | 5 routines, 3 calm breaks, 3 skills and 1 rhythm a day (the world checks again: `GameStore.CARE_DAILY_CAP`), at most 11 trainer points a day, plus 5 on a rhythm day | `CareRules`, `CARE_DAILY_CAP` |
| EVs | 252 a stat, 510 in all | `StatCalculator` |
| Party | 6 | `OwnedPokemon.PARTY_SIZE` |
| Perk bucket | 25; Guardians add up to 15 on top | `BUCKET_CAP`, `Guardians` |
| Care perk bucket | 25 (Friendship gain, Berry finder, Visitor luck), from Care's party only | `CarePerks` |
| Heal perk bucket | 25 (Friendship gain, Berry finder), from Heal's party only | `HealPerks` |
| Mint perk bucket | 25 (Candy finder, Friendship gain), from Mint's party only | `MintPerks` |
| Home perk bucket | 25 (Candy finder, Friendship gain), from Home's party only | `HomePerks` |
| Guardian slots | One per module (Plan, Care, Heal, Mint, Home, Link), no rank gate (0.158.0, `RewardRules.VERSION` 21) | `Guardians.MODULES` |
| Active quests | 3 | `Quests.MAX_ACTIVE` |
| Egg meter | 5 | `EGG_METER_SIZE` |
| Paid subtasks | 5 a task | `MAX_PAID_SUBTASKS` |
| Milestone Super Candies | 3 a day | `MAX_MILESTONE_REWARDS_PER_DAY` |
| Habit | pays once a day, even after an undo | `Habit.paysOn`, `paidOn` |
| Task | pays once, after its rank's minimum age (2, 5, 10, 15, 20 minutes for E to A); a repeating task's instance pays from its own day, never ahead | `Rank.minAgeMinutes`, `TaskRules.paysOnCompletion`, `plan.finish_task` |
| Focus session | 5 minutes at least; 180 in the app; 240 on the server (the shiny bonus stops at 240) | `Egg.MIN_MINUTES`, `MAX_MINUTES` |
| Egg tickets | 20 a device; 200 reported per sync | `TICKET_STOCK`, `MAX_SPENT` |
| Friend gifts | 1 per friend a day | `platform.send_gift` |
| Trainer points, coins | no cap; points never go down (the server refuses it) | `apply_outcome` |

**Care's Pokémon income (checked at 0.35.0; see 0.45.0 below).** `simulateEconomy` does not play Care, and re-running it after rules version 10 left `docs/economy-report.md` byte for byte the same, so Plan's numbers did not move. By construction Care can add at most one Pokémon every 4 days (one befriend a day, 4 warm-up days per visitor, each needing a paid Care action), about 7 in 30 days; from 0.41.0 a liked berry (a sink: one berry per visitor) makes it one every 3 days at best, about 10 in 30 days, with no Pokémon from any Care action but befriending, against several a day from Plan's focus and Task Eggs. A Care-only player always has the starter and a new friend about every 4 days; the Care Region pool has no Super Rare except through quests.

**Care's Pokémon income at 0.132.0 (rules version 15, unchanged).** What a friend pays did not change; how often one can happen did. Before, a friend took four warm days of one visit a day, each needing a paid Care action, so four visitors gave at most about one friend a day. Now arrivals set the pace: about 1.35 visitors arrive a day and every visitor can become a friend in about two visits spread over at least three hours, so over a long stretch Care can add at most about 1.35 friends a day (about 40 in 30 days), and a first burst of up to 10 (the meadow and the queue) when the queue has filled while away, never more than `BEFRIEND_DAILY_CAP` (4) in one day. That is a little above the old ceiling (about 1 a day), still below Plan's several eggs a day, and it no longer asks for a paid Care action first. A player who never opens Care still has a full queue waiting. `simulateEconomy` still does not play Care, so Plan's report is unchanged. *(tune: lower `Visitors.CATCH_UP_DAYS` or the second-arrival chance, or raise `FRIEND_PROGRESS`, if Care's income outruns Plan's in play.)*

**Care's Pokémon income at 0.46.0 (rules version 12).** Care Eggs bring Care level with Plan: a Care moment fills the Egg meter (a routine 1 point, a calm break or a skill 2, 3 to an egg), so a day with every routine, calm break and skill done gives 5 Care Eggs (17 points) and a few moments give 1 or 2, on top of about a friend a day from visitors. Plan's typical day is 1 to 3 focus eggs and about a Task Egg. Care moments also pay Rare Candies (3 to 4 each, 15 for a rhythm), up to about 45 a day. `simulateEconomy` still plays only Plan.

**Heal's Pokémon income at 0.136.0 (rules version 17; not simulated).** Field Research is on by default (0.141.0). An active day of three logged sessions and a drink pays 13 Rare Candies and 4 trainer points and adds 7 meter points, so at most 1.75 Field Eggs a day (the meter carries over); a day of one session is half an egg. That is below Care's 5 Care Eggs a day at the top and above its typical few, and well below Plan's several eggs a day. The Heal Region has the same rarity mix as the others (a Super Rare line only through eggs' rarity roll: Jangmo-o and Frigibax). `simulateEconomy` still plays only Plan and `docs/economy-report.md` was not re-run; the new source cannot change Plan's numbers. *(tune: `RESEARCH_MIN_MINUTES`, `ACTIVITIES_PAID_PER_DAY`, `HEAL_EGG_METER_SIZE`, `HEAL_CANDIES` once play shows the pace.)*


**Heal's steps and reads at 0.186.0 (rules version 25; simulated).** `docs/economy-report.md` now ends with a walker table: a light walker (3,500 steps, 4 days a week, one family read a day) earns about 1.3 Rare Candies a day, a typical walker (7,000 steps, 6 days) 3.4, an active one (11,000 steps every day, two families read) 6.6, which is 1 to 4% of what a Typical Plan player earns in the same time and about half of what three logged activities pay at most (12). Steps add no Egg meter points or trainer points, so they cannot speed up Field Eggs or ranks; the friendship step goes to Heal's lead only. *(tune: `STEPS_PER_REWARD`, `STEPS_PAID_PER_DAY`, `IMPORTS_PAID_PER_DAY`, `HEAL_CANDIES` for STEPS and IMPORT.)*

**Mint's Pokémon income at 0.141.0 (rules version 18; not simulated).** Ledger Research is on by default but pays only for organising, never for money moving. A day with two reconciled accounts pays 6 Rare Candies and 2 trainer points (4 meter points: one Ledger Egg); a reviewed week adds 5 candies and 3 points. At most about 1 egg a day on a very organised day and typically 1 to 2 a week, which is Care's and Heal's pace or lower and well below Plan's. The Mint Region has the same rarity mix as the others. `simulateEconomy` plays only Plan and was not re-run; the new source cannot change Plan's numbers. *(tune: `MINT_CANDIES`, `MINT_EGG_POINTS`, `RECONCILES_PAID_PER_DAY`, `MINT_EGG_METER_SIZE` once play shows the pace.)*

**Home's Pokémon income at 0.143.0 (rules version 19; not simulated).** Habitat Discovery is on by default but pays only for finished work, never for making lists. A tidy day of 3 chores pays 6 Rare Candies and 3 trainer points (3 meter points); a Habitat Egg comes after 5 points, so a household doing 2 or 3 chores a day finds one about every 2 days, capped at 5 chores (one egg) a day. That is Plan's pace and above Care's and Heal's, on purpose: chores are frequent and small, and the daily cap stops one job split into many from farming it. The Home Region has the same rarity mix as the others. `simulateEconomy` plays only Plan and was not re-run. *(tune: `HOME_CANDIES`, `HOME_EGG_POINTS`, `HOME_EGG_METER_SIZE`, `CHORES_PAID_PER_DAY`.)*
**Base's decorations at 0.195.0 (rules version 28; not simulated).** The Decoration meter rides on Habitat Discovery's payments and adds nothing to what they pay. At 5 points a decoration, a household doing 3 chores a day finds one about every 2 days (all 42 in about 10 weeks); the daily caps (5 chores and 2 maintenance, 9 points) allow at most 2 a day. Nothing is sold and nothing leaves the game: the set is finite and cosmetic, so it cannot flood anything. *(tune: `Decorations.METER_SIZE`, `CHORE_POINTS`, `MAINTENANCE_POINTS`.)*
**Link's Pokémon income at 0.145.0 (rules version 20; not simulated).** Postcard Trail is on by default but pays only for a follow-up the user finished, never for people, notes, dates or edits. A day with 3 follow-ups finished pays 6 Rare Candies and 3 trainer points and one Postcard Egg (LINK.md 9.3 asks for three completions per discovery); the daily cap of 3 means at most one egg a day, and a typical week of a few follow-ups finds one or two, below Home's pace and about Care's and Heal's. The Link Region has the same rarity mix as the others. `simulateEconomy` plays only Plan and was not re-run. *(tune: `LINK_CANDIES`, `LINK_EGG_POINTS`, `LINK_EGG_METER_SIZE`, `FOLLOW_UPS_PAID_PER_DAY`.)*

**Care's Pokémon income at 0.45.0.** With a visit a day for every visitor and up to 4 visitors in the meadow, someone who visits them all warmly every day makes about one friend a day (four visitors each four days along, a new one arriving as each leaves), against about one every 4 days before: about 30 in 30 days at most, still well below Plan's several eggs a day (`economy-report.md`). Most are first stages (a family's egg), so they add to the Care dex's first stages and leave evolving to candies; the visitor itself joins 1 time in 10. `simulateEconomy` still does not play Care, so Plan's report is unchanged by this.

Care is the first module with a per-module daily cap on the world's side (`CARE_DAILY_CAP`). There is no daily cap on tasks, habits or focus sessions yet: `RewardEngine` leaves daily limits to the caller, and
design.md's "daily reward limit per app" *(tune)* is not built. It matters once a second module earns through the same door.

## 4. What can be lost

- **A failed focus session** (left the app for over 10 seconds, moved the phone in Deep Focus, gave up) pays nothing (except once a week with Magic Guard, section 1); its egg ticket is spent so the egg can't be rerolled, and a Lure is kept. This is the one deliberate exception (docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md, principle 4).
- **A transferred Pokémon** is gone; its species, shiny and forms stay in the Pokédex.
- **Evolution** uses its Super Candy, stone or Link Cord.
- **A task reward** can be undone for 10 seconds (`UNDO_WINDOW_MILLIS`).
- Nothing expires: no timed items, no decay, no daily resets beyond the counters above. Unopened gifts wait.

## 5. Gaps between the code and docs/design.md

Found while writing this down; each is its own change, none is in this rules version.

- **Coins buy only Rare Candies** (0.46.0). design.md also plans Lures bought or crafted from berries; not built.
- **Earned items with no use yet:** Lures (Focus never passes one). Mega Stones can be held but never drop.
- **Trial evolutions** (0.182.0) are checked on the spot, done with the phone, or done in World (`EvolutionTrialSites`); see docs/design.md, "Evolution".
- **Stack caps are uneven:** transfers and the focus berry add without the cap; the server's gift caps every item at 9,999, the app caps non-candies at 999.
- **Who decides.** The app computes every reward itself and the server checks the snapshot on sync (points never drop, the party and Guardians fit, column ranges). The server's `effort`, `task` and `achievement` kinds exist and are tested but no app calls them yet. Moving rewards to them (and adding the daily caps) is the step before a public release (`supabase/README.md`, Known limits).

## World (0.83.1)

**Floor outcomes (0.156.0, `Outcomes`):** a floor a team is too weak for pays half the Poké and Exploration XP (a setback) or ends the expedition safely (a turn-back); see `MODULES/WORLD.md` 3.6. `docs/world-economy-report.md` was re-run with it (a team that turns back is sent again; the career and year simulations pick the deepest dungeon the team would not turn back in, and take only jobs it can clear). Investigation, Challenge and Story jobs are on the board; the simulations do not play them.

World pays through `AppData.world` and the Bag only, not the server reward engine, so `RewardRules.VERSION` is unchanged. Sources: per floor Exploration XP (`XP_BASE` + floor), Poké (5 + 2 × floor + 0 to 4), a berry (70%), a treasure box (6%) and a wild find (3%); a milestone (Rare Candies and Poké) once per dungeon floor, never repeated. The only sink is rank. Floors take `baseFloorSeconds / (1 + power / 100)` (never under a quarter of it), at most 12 hours of away time counts per open. Numbers and pacing (about 1 find a day, Gold in about 4 weeks, Platinum in about 3 months at two opens a day): `docs/world-economy-report.md`, regenerate with `./gradlew :world-core:simulateWorldEconomy` after changing any World number. All *(tune)*; Poké has no sink yet, so it only piles up.

**Member experience (0.145.0, WORLD.md 5.2):** each Pokémon on a team gets 1 XP (`CandyRules.XP_PER_FLOOR`) for each floor the team climbs, the full amount for every member; 20 XP (`XP_PER_CANDY`) are one step of candy progress on the shared level curve, so a level costs the same experience as candies. Guardians and level-100 Pokémon get none. Paid with the settle that moves the team's cursor, so once per floor. Not simulated: `world-economy-report.md` does not model Pokémon levels. *(tune)*

**Missions (0.84.0):** nine jobs, each paying 60 to 400 Poké and 30 to 250 Exploration XP per claim, with an occasional berry; the only Rare Candy is the once-only Eevee rescue, so repeatable jobs never pay one. Not simulated yet.

**World, the rest (0.86.0).** Simulated in `docs/world-economy-report.md` (`./gradlew :world-core:simulateWorldEconomy`): one team, missions, research, Camp and Lab, Café and shop all played (Deliver jobs and the Dojo are not).
- **Sinks:** Camp and Lab (400 and 600 base, tripling over five levels, 121,000 Poké for both with all levels), the shop (Rare Candy at 2,500 + 500 per candy bought), the Dojo (200 Poké for 60 skill XP) and the Café (3 berries).
- **Rare Candy sources, per year of play** (typical engaged player, 4 × Lv 30 and two opens a day): about 85 from milestones, jobs and research, about 130 to 160 expected from treasure boxes, and about 35 bought. A casual player (1 open a day, 2 × Lv 10) gets about 35, 40 and 10. These add to Plan and Care, so watch them if candies feel too easy.
- **Pace:** the engaged player reaches Platinum in about a month, Diamond and the Guardian in about 11 weeks; the casual player reaches Gold in about 3 weeks, Platinum in about 11 weeks and Diamond in about 8 months. Ordinary jobs wait 7 days, daily jobs 1 (150 Poké), because repeating cheap jobs in a low dungeon out-earned exploring (about 1,100 jobs a year).
- Skills and facilities add at most 20% Poké, +10 points berry chance, +3 points find chance, +5 points treasure chance, +50% Exploration XP; none changes floor time. The only change to floor time is the Guardian's Temporal Mastery (10% faster once the story is complete).

**Jobs, Swap Shop and Dojo (0.88.1), simulated** (`docs/world-economy-report.md`, a year of play; Defeat jobs never came up because one team of four level-30 Pokémon has about 120 power and the lowest Defeat job asks for 150):
- **Jobs are about 40 to 50% of World's Poké and 35 to 55% of its Exploration XP**, about 420 to 500 claims a year for an engaged player (a casual one about 230, over half of what they earn). They were about 700 a year with a 7 day wait, so ordinary jobs now wait 14 days. If World feels too generous, lengthen that further or cut the rewards before touching the dungeons.
- **Rare Candies from jobs:** about 30 to 45 a year, mostly the weekly Mt. Steel patrol (52 a year at most) and the Eevee and Bagon rescues.
- **Swap Shop:** 0 to 2 swaps a year, because a player who explores has plenty of every flavour. It is a convenience, not a sink or a source.
- **Dojo:** after the Base is built an engaged player trains about 17 times for about 3,400 Poké and has every skill at level 10 at day 72 instead of day 96. A small sink; its price (200 Poké for 60 XP) would have to fall a lot to matter before the Base is built, when Poké is short.

**World polish (0.89.0):** ordinary jobs pay 60% of what they did (the daily berry run 100 Poké), which brings jobs to about 33 to 38% of an engaged player's Poké and Exploration XP and 45% of a casual one's (`docs/world-economy-report.md`). Story chapters 3 to 6 need 3, 4, 5 and 7 *different* dungeons cleared, so the Guardian comes at about day 100 for an engaged player (it was about day 70 when repeating Tiny Woods counted). Abilities add up to three levels to a skill (one for each helper on the team); they change the odds of berries, finds and Poké by at most 3, 9 and 6 points.

**World and the reward history (0.90.0):** World's Poké and items are written to the reward history as `World: exploring`, `jobs`, `research` and `treasure` entries (`Reward.Poke` is only ever written by World; the reward engine never pays it) and items go straight into the Bag, so World keeps no stock of its own. This changes no amount, so `RewardRules.VERSION` stays 13.
