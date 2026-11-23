# The economy: sources, sinks and caps

> **Describes what the code pays today.** The shape of the economy (reward ledger, one Bag, one currency: Poké) is in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md`; this file stays accurate for the code.

Every reward source, every use and every cap, as the code has them under `RewardRules.VERSION` (in
`pokemon-core`). This is principle 6 of `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md` ("every reward source, cap and use is written down"). The
reasons and pacing targets are in `docs/design.md`; the simulated pacing comes from `./gradlew :plan-core:simulateEconomy`.

**Keep it true.** A change to anything below that changes what an event pays bumps `RewardRules.VERSION` and updates this
file in the same PR (`GoldenVectorTest` refuses to regenerate `pokemon-core/src/jvmTest/resources/golden.json` with new outcomes under the old
version). Re-run `./gradlew :plan-core:simulateEconomy` after it. Names in `code` are the constants to search for.

Where the rules live: `RewardEngine`, `TrainerRank`, `CandyRewards` and `Egg`; `Transfer`, `Items`, `Perks`, `Rules` (`CandyRules`, `FriendshipRules`, `Shiny`), `EvolutionRules` in
`pokemon-core`; `TaskRules`, `Goal`, `Streak`, `FocusSession` in `plan-core`; `CareRules` in `care-core`; `GameStore`, `CareStore` and `Quests` in `app-core`.

## 1. Sources

### What each event pays

| Event | Pays | Constant |
|---|---|---|
| **Focus session completed** (5 minutes or more) | One egg, hatched at once (rarity from the length, the Eggs table below) · Rare Candies `2 × minutes / 5` (× Candy yield) · Poké `minutes` (1 a minute, counted in 5 minute steps) · trainer points `minutes / 5` · 1 random berry · Plan lead friendship (focus session) · an active day | `COINS_PER_FIVE_MINUTES`, `TrainerRank.pointsForEffort`, `Egg.MIN_MINUTES` |
| **Task or habit completed** (tier 1 to 5 = rank E to A) | Rare Candies (table below), maybe Super Candies · Poké `tier × 10` · trainer points `tier × 2` · an item drop chance `8 + 2 × tier` % · Plan lead friendship (task) · `tier` points on the Egg meter · an active day | `COINS_PER_TIER`, `pointsForAchievement`, `DROP_BASE_PERCENT`, `DROP_PER_TIER_PERCENT` |
| **Egg meter full** (5 points) | A Task Egg, rolled as a 15 minute session (no lure, no spotlight); the rest carries over | `EGG_METER_SIZE`, `TASK_EGG_MINUTES` |
| **Task reward focus** | **Exp** (default): the row above, unchanged. **Egg**: half the Rare and Super Candies of each roll (rounded down, at least 1) and `ceil(tier × 1.5)` points on the Egg meter instead of `tier`. **Items**: half the candies and double the item-drop chance (capped at 100%). Poké and trainer points never change. Chosen per task (default in Settings), read when the task is ticked. Not simulated: `simulateEconomy` plays the default focus only *(tune)* | `RewardFocus`, `EGG_FOCUS_POINT_FACTOR`, `FOCUS_CANDY_SHARE`, `ITEMS_FOCUS_DROP_FACTOR` |
| **Event with a companion ends** | The companion (a PLAN party Pokémon able to take exp) gains `candies × XP_PER_CANDY` exp, `candies` = 1 for each 30 minutes of a timed event (a quarter hour counts as 1; 4 at most for one event), at most 6 a day across all events (counted on the day it is paid, so moving an event never resets it); and a 10% chance of one item from the ordinary drop table when it paid. Once per occurrence, only if the occurrence ends after the companion was set (and after the last edit to the event's day, time, length or repeat), within two days of ending; an all-day event, a skipped occurrence and a device calendar's event never pay. No Poké, no trainer points, no Egg points, no friendship *(tune)* | `EventRewards`, `EVENT_MINUTES_PER_CANDY`, `EVENT_MAX_CANDIES`, `EVENT_CANDIES_DAILY_CAP`, `EVENT_ITEM_PERCENT` |
| **Subtask done** | 1 Rare Candy, once per subtask, at most 5 paid per task, after the task rank's minimum age | `SUBTASK_RARE_CANDIES`, `MAX_PAID_SUBTASKS` |
| **Goal milestone done** (24 hours old or more) | 1 Super Candy, at most 3 a day; an active day | `SUPER_CANDIES_PER_MILESTONE`, `MILESTONE_MIN_AGE_MILLIS`, `MAX_MILESTONE_REWARDS_PER_DAY` |
| **Every milestone of a goal done** | 1 Nature Mint, once per goal | `Goal.bonusGiven` |
| **Mini-quest** ticked (honour system, 3 a day from 4am, one reroll before any is done that gives three others; petting the buddy ticks "Pet your buddy") | 1 random berry and 2 Rare Candies each, 1 more berry and 5 more candies for the third; no active day | `MiniQuests.ALL_THREE_BONUS_BERRIES`, `RARE_CANDIES`, `ALL_THREE_BONUS_CANDIES`, `GameStore.completeMiniQuest` |
| **Welcome back** after 7 or more days away (once per absence) | 5 Rare Candies and 2 berries of one flavour | `WELCOME_BACK_RARE_CANDIES`, `WELCOME_BACK_BERRIES` |
| **Transfer** a Pokémon | Rare Candies (Common 8, Uncommon 12, Rare 22, Super Rare 60, plus a fifth of the candies it was fed), Poké twice the base candies (16 / 24 / 44 / 120), its held item back | `Transfer.candiesByRarity`, `COIN_FACTOR`, `REFUND_DIVISOR` |
| **Legendary or mythical quest** claimed | That Pokémon (out of the party; it can be shiny) | `Quests` |
| **Starter** | The starter (never shiny) and a tutorial task that pays at once | `GameStore.start` |
| **A module's first real record** (Plan, Care, Heal, Mint, Base, Link; once per save) | One Common egg of the module's region, hatched into its party and leading it (the first of a species pays as usual) | `GameStore.firstRecordEgg`, `RewardEngine.tutorialEgg` |
| **Petting the buddy** | Friendship (pet), up to 10 a day | `PET_DAILY_CAP` |
| **Care: routine done** (first tick of the day for that routine) | Care lead friendship (routine) · 3 Rare Candies · 10 Poké · 1 point on Care's Egg meter · 1 trainer point. At most 5 routines a day | `MODULE_POKE`, `CARE_POINTS`, `CARE_CANDIES`, `CARE_EGG_POINTS`, `CareRules.ROUTINES_PAID_PER_DAY` |
| **Care's Egg meter full** (3 points: a routine 1, a calm break or a skill 2, a rhythm 0) | A Care Egg from the Care Region, rolled as a 15 minute session (species not caught yet × 6), hatched at once into Care's party (else the PC); it leads Care if there is no lead; the rest carries over. Kept by the app (counter `care-egg-meter`) | `RewardEngine.careEgg`, `CARE_EGG_METER_SIZE` |
| **Care Play** (the first time with each Pokémon on a day, at most 6 a day) | 1 friendship step to the Pokémon played with, 1 Rare Candy; no Egg meter point; repeats pay nothing | `CareKind.PLAY`, `CARE_CANDIES`, `CARE_EGG_POINTS` |
| **First of a species** (from any egg, Task Egg, Care Egg or friend) | 5 Rare Candies and 1 Super Candy, so it can evolve | `NEW_SPECIES_CANDIES`, `NEW_SPECIES_SUPER_CANDIES` |
| **Care: calm break** (1 to 2 minutes; under a minute is kept but pays nothing) | Care lead friendship (calm break) · 1 random berry · 10 Poké · 1 trainer point. At most 3 a day | `CareRules.BREAKS_PAID_PER_DAY`, `BREAK_MIN_SECONDS` |
| **Care: skill practised** (a DBT skill from the library, each skill once a day; paced breathing pays as a calm break) | Care lead friendship (skill) · 1 random berry · 10 Poké · 1 trainer point. At most 3 a day | `CareRules.SKILLS_PAID_PER_DAY` |
| **Care: kept a rhythm** (care done on 4 days of a week: a routine, a calm break or a skill) | 3 random berries · 30 Poké · 5 trainer points, once a week | `RHYTHM_POKE`, `RHYTHM_BERRIES`, `RHYTHM_POINTS`, `CareRules.RHYTHM_DAYS` |
| **Care: befriend a visitor** (its warmth reaching `Visitors.FRIEND_PROGRESS` (16), about two visits; `MODULES/CARE.md` section 6.8) | An egg of the visitor's family's first stage (`Dex.lineRoot`), which hatches at once into Care's party (else the PC); `BEFRIEND_DIRECT_JOIN_PERCENT` (10%, up to +5 with Visitor luck) of the time the visitor itself joins instead, with friendship `BEFRIEND_JOIN_FRIENDSHIP` (150). Either way every IV is at least `BEFRIEND_IV_FLOOR` (10, up to +6 with Visitor luck) · `BEFRIEND_POINTS` (3) trainer points. No visit limit in a day (a visitor rests three hours after one) and no paid Care action needed first; visitors arrive about 1.35 a day, four in the meadow and up to six waiting, and at most `BEFRIEND_DAILY_CAP` (4) friends a day. Free play pays nothing | `RewardEngine.befriend`, `Visitors` |
| **Heal: activity logged** (Field Research on; a new record of 10 minutes or more that ended within the last 12 hours, not in the future; never edited or backfilled; imported from Health Connect never. **Read from Huawei Health's pages**: within the last 8 days instead, and not flagged as a repeat of one already here) | Heal's lead 1 friendship step (activity) · 4 Rare Candies · 10 Poké · 1 random berry (a second one with Berry finder) · 1 point on Field Research's Egg meter ×2 · 1 trainer point. At most 3 a day | `HEAL_CANDIES`, `HEAL_EGG_POINTS`, `HEAL_POINTS`, `HealRules.ACTIVITIES_PAID_PER_DAY`, `RESEARCH_MIN_MINUTES`, `FRESH_MILLIS`, `IMPORT_FRESH_MILLIS`, `HealImport.PAYING_PROVIDERS` |
| **Heal: first drink of the day** (Field Research on; logged now) | 1 Rare Candy · 10 Poké · 1 point on Field Research's Egg meter · 1 trainer point. Once a day. Later drinks pay nothing | `HealKind.PARTICIPATION`, `HealRules.PARTICIPATION_PAID_PER_DAY` |
| **Field Research's Egg meter full** (4 points: an activity 2, a drink 1) | A Field Egg from the Heal Region, rolled as a 15 minute session (species not caught yet × 6), hatched at once into Heal's party (else the PC); it leads Heal if there is no lead; the rest carries over. Kept by the app (counter `heal-egg-meter`) | `RewardEngine.healEgg`, `HEAL_EGG_METER_SIZE` |
| **Heal: steps** (Field Research on; Huawei Health's step count of *today*, read from its Health page or step notification) | One payment for each full 2,000 steps (`HealRules.STEPS_PER_REWARD`), at most 5 a day: 1 Rare Candy · 1 friendship step to Heal's lead (activity). No berry, no Egg meter point, no trainer point, no item drop. A later read pays only the new amounts; deleting the record and reading again pays nothing twice (sealed marks) | `HealKind.STEPS`, `HEAL_CANDIES`, `HealRules.STEPS_PAID_PER_DAY` |
| **Heal: records read in** (Field Research on; Health Connect or Huawei Health) | 1 Rare Candy for each family of record that brought something new and recent (within 8 days; not steps, not a workout from Huawei that paid for itself): workouts, sleep, body measurements, drinks, readings. Once a day per family, at most 3 a day. No other reward | `HealKind.IMPORT`, `HealRules.IMPORTS_PAID_PER_DAY`, `importCounts` |
| **Heal: heart rate, resting heart rate, SpO2, skin temperature, stress, sleep score, sleep, body measurement, goal, note, anything else** | Nothing for the value, ever (HEAL.md section 12.3); only the family's read above | |
| **Mint: account reconciled** (Ledger Research on; the account has real transactions; once per account a day, 2 a day; a difference is recorded only as an explicit adjustment) | Mint's lead 1 friendship step (Ledger) · 3 Rare Candies (a fourth with Candy finder) · 10 Poké · 2 points on Ledger Research's Egg meter · 1 trainer point | `MINT_CANDIES`, `MINT_EGG_POINTS`, `MINT_POINTS`, `MintRules.RECONCILES_PAID_PER_DAY` |
| **Mint: weekly review finished** (Ledger Research on; there is an open account and a transaction; once a week) | Mint's lead 1 friendship step (Ledger) · 5 Rare Candies · 10 Poké · 3 points on the meter · 1 trainer point | `MintKind.REVIEW`, `MintRules.REVIEWS_PAID_PER_WEEK` |
| **Ledger Research's Egg meter full** (4 points: a logged record 1, a reconciled account 2, a review 3) | A Ledger Egg from the Mint Region, rolled as a 15 minute session (species not caught yet × 6), hatched at once into Mint's party (else the PC); it leads Mint if there is no lead; the rest carries over. Kept by the app (counter `mint-egg-meter`) | `RewardEngine.mintEgg`, `MINT_EGG_METER_SIZE` |
| **Mint: a purchase, a payment or a transfer logged** (Ledger Research on; an expense, a bill or card installment paid, or a transfer; typed by the player, posted, dated today; **no daily limit**; **never based on the amount**; income, refunds, fees, adjustments, imports and interest estimates do not pay) | Mint's lead 1 friendship step (Ledger) · 1 Rare Candy (a second with Candy finder) · 10 Poké · 1 point on Ledger Research's Egg meter · no trainer point | `MintKind.LOGGED`, `MINT_CANDIES`, `MINT_EGG_POINTS`, `MINT_TRAINER_POINTS`, `MintRules.RECORDS_PAID_PER_DAY`, `MintRules.logPays` |
| **Mint: an outbreak** (a reconciled account or a finished weekly review, on top of its row above; `MINT.md` 16.2A) | A pick of 3 different Pokémon of the Mint Region (reconciled: 60% Uncommon, 30% Rare, 10% Super Rare; review: 70% Rare, 30% Super Rare; uncaught × 6), caught directly into Mint's party (else the PC), with 2 shiny rolls (reconciled) or 3 (review) plus 1 per week in a row with a review, at most 7. At most 5 wait. | `RewardEngine.mintOutbreak`, `claimMintOutbreak`, `MINT_OUTBREAK_ROLLS`, `MAX_OUTBREAK_ROLLS`, `AppData.outbreaks` |
| **Mint: income, a balance, a budget, a bill set up, interest, anything else** | Nothing, ever: MINT.md section 16.2 | |
| **Base: a chore done** (Habitat Discovery on; at most 5 paid a day; one occurrence pays once however often it is undone and done again) | Base's lead 1 friendship step (Habitat) · 2 Rare Candies (a third with Candy finder) · 10 Poké · 1 point on Habitat Discovery's Egg meter · 1 trainer point | `HOME_CANDIES`, `HOME_EGG_POINTS`, `HOME_POINTS`, `BaseRules.CHORES_PAID_PER_DAY` |
| **Base: maintenance done** (on; at most 2 paid a day; one schedule due date pays once) | Base's lead 1 friendship step · 4 Rare Candies · 10 Poké · 2 points on the meter · 1 trainer point | `BaseKind.MAINTENANCE`, `BaseRules.MAINTENANCE_PAID_PER_DAY` |
| **Habitat Discovery's Egg meter full** (5 points: a chore 1, maintenance 2) | A Habitat Egg from the Base Region, rolled as a 15 minute session (species not caught yet × 6), hatched at once into Base's party (else the PC); it leads Base if there is no lead; the rest carries over. Kept by the app (counter `home-egg-meter`) | `RewardEngine.homeEgg`, `HOME_EGG_METER_SIZE` |
| **A new save, or a save from before the room** (once) | 3 starter decorations: Snorlax Doll, Thunder Mat, Big Plant | `Decorations.STARTERS`, `AppData.decorationStarters` |
| **Base's Decoration meter full** (5 points on the same payments: a chore 1, maintenance 2; stops when all 42 are owned) | One Secret Base decoration the player does not own yet (random, from the event id), the rest of the points carry over. A cosmetic: no effect, no value, not in the Bag. The Department Store and Mom's gifts (REP-053, later) add to the same set. Kept by the app (counter `home-decor-meter`, `AppData.decorations`) | `Decorations.advance`, `Decorations.pick`, `Decorations.METER_SIZE`, `GameStore.base` |
| **Base: creating or editing anything, a quantity, the restocking list, a warranty, a note, a schedule, a reminder** | Nothing, ever: BASE.md section 9.2 | |
| **Link: a follow-up finished** (Postcard Trail on; at most 3 paid a day; one follow-up pays once however often it is reopened and finished again) | Link's lead 1 friendship step (Follow-up) · 2 Rare Candies (a third with Candy finder) · 10 Poké · 1 point on Postcard Trail's track · 1 trainer point | `LINK_CANDIES`, `LINK_EGG_POINTS`, `LINK_POINTS`, `LinkRules.FOLLOW_UPS_PAID_PER_DAY` |
| **Postcard Trail's track full** (3 points: one a finished follow-up) | A Postcard Egg from the Link Region, rolled as a 15 minute session (species not caught yet × 6), hatched at once into Link's party (else the PC); it leads Link if there is no lead; the rest carries over. Kept by the app (counter `link-egg-meter`) | `RewardEngine.linkEgg`, `LINK_EGG_METER_SIZE` |
| **Link, Friend Safari: a visit to a person** (Postcard Trail and the Safari setting on; free entry; ONE catch per person per day, at most 5 Safari catches a day in total) | One Pokémon of the Link Region from that person's Safari pool (slot fixed by a hash of the opaque record key; species not caught yet × 6), caught directly into Link's party else the PC, ordinary shiny chance; candies for a new species only. No Poké, trainer point, track point or item. Own event `SafariEvent` (`link:safari:<ref>:<day>`), paid once (`AppData.linkPaid`) | `SafariRules`, `RewardEngine.safariVisit`, `GameStore.SAFARI_PER_DAY` |
| **Link: adding people** (or the Me card) | Nothing: more people widen the Safari (slots 2, +1 per 6 people, max 5; people capped at 20, Me card excluded) but a Safari catch still needs a visit, and the 5 a day cap holds however many contacts exist | `SafariRules` |
| **Link: adding or importing people, notes, dates, tags, favourites; editing, rescheduling, cancelling or reopening a follow-up; a reminder** | Nothing, ever: LINK.md section 9.2 | |
| **Care: diary card, journal entry, note** | Nothing, ever: entering sensitive information never earns (docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md) | |
| **A bag stack is full** | 1 Poké per candy or item that did not fit | `Inventory.addCapped` |

Task perks: candies × 1.10 when finished on time (by its due date), × 1.10 when it had focus minutes and × 1.10 when it was the task "Pick for me" chose (`PICKED_BONUS`, once per pick), then Plan's lead's
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
- Within a rarity: a species not caught yet × 6 (`UNCAUGHT_WEIGHT`), the Lure's type × 3 (`LURE_WEIGHT`), the day's spotlight type × 2 (`SPOTLIGHT_WEIGHT`, one of 18 types by `epochDay % 18`).
- The pool is the dex of the Plan Region (for a module's own egg, that module's region), base forms only. An event that names any other region hatches from the Plan Region (`Region.open`).
- Shiny chance: `1/128 × (1 + 0.5 × min(minutes, 240) / 240)` times the shiny perk (`Shiny.chance`); a Task Egg counts as 15 minutes.

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

Never dropped: Ability Patch and the Dynamax Band (which is not read yet). Focus sessions never drop items (they pay a berry). Mega Stones are not in the ordinary drop table: every one is always sold at the Kecleon Shop on its own Mega Stones shelf for 2,000 Poké, and when a silver or gold treasure box is opened it has a 3% (silver) or 10% (gold) chance of a Mega Stone (`worldOpenTreasure`); only a stone the player does not own yet is picked.

### Module drops (`ModuleDrops`)

Every paid moment of Care, Heal, Mint, Base and Link has a 10% chance (`PERCENT`) of one item from its own table; a finished Mint review also pays one Nature Mint every time. Gimmighoul Coins come 10 at a time (`COIN_HANDFUL`). *(tune)*

| Module | Table (relative weights) |
|---|---|
| Care | Strawberry Sweet 4, Oval Stone 2, Everstone 2, Moon, Shiny and Dawn Stone 1 each, Leftovers 0.5 |
| Heal | each vitamin 3, Razor Claw 1, Razor Fang 1, Sun, Leaf and Water Stone 1 each |
| Mint | Nature Mint 6, Gimmighoul Coin 3, Bottle Cap 1, Amulet Coin 1, Star Piece 1 |
| Base | Fire, Water, Thunder and Leaf Stone 2 each, Ice and Dusk Stone 1 each, Everstone 2, Oval Stone 1 |
| Link | Link Cord 4, each trade-evolution item 1 |

World: a Paldea floor has Gimmighoul Coins 30% of the time (1 to 8, `WorldSim.COIN_PERCENT`, `COIN_MAX`), and Paldea's milestones pay 670 in all.

### The town floor's finds (`TownFinds`)

The launcher's dungeon floor (`FEATURES/LAUNCHER.md` 4.5) has `PER_DAY` (3) places for finds each day and each one holds a find with only `APPEAR_PERCENT` (25) *(tune; owner, 23 November 2026: low chance)*, so a day shows about 0.75 finds and often none, chosen from the date alone (opening the home screen again never rerolls them) (the same all day, whatever the layout; New floor layout moves them and never adds any). A tap collects one, or a Pokémon walking onto it picks it up, once (`Counters.TOWN_FINDS`, a bit per find): an item goes into the Bag (over the limit, Poké, as everywhere), Poké is added. Weights *(tune)*: each of the 5 berries 4, each of the 6 vitamins 2, Poké 8 (`POKE_MIN` 10 to `POKE_MAX` 30). So a day now averages about a quarter of a berry, 0.2 of a vitamin and 3 Poké (it was 1 berry, 0.7 of a vitamin and 13 Poké with every place filled; `RewardRules.VERSION` 34).

### The Poké Mart (`Shop`, its own section of the Pokémon tab)

The everyday shop: Exp. Candy 5, Super Candy 100, a berry of any flavour 20 (`COINS_PER_RARE_CANDY`, `SUPER_CANDY_PRICE`, `BERRY_PRICE`), bought 1, 10 or as many as the Poké and the Bag's room allow (`GameStore.martBuy`). *(tune)*

### The Kecleon Shop (`KecleonShop`)

The specialty shop in World's Town; it sells no candy. Prices are low on purpose, as everything here grows one Pokémon. **Always in stock** every evolution stone 400, every Mega Stone 2,000 on its own Mega Stones shelf (ten to a page in Town), the Link Cord 500, the Everstone 300, 50 Gimmighoul Coins 150; **today's shelf**, 5 items chosen for the player's local day from trade and held evolution items 500, vitamins 100, perk held items 500 (Shiny Charm 1,500), Tera Shards 1,000, Nature Mint 400, Ability Capsule 600, Bottle Cap 800, Ability Patch 2,000. No countdown, nothing sold only on the shelf (every catalogue item comes round), and the daily five never carries a Mega Stone. A silver or gold treasure box can also hold one (3% and 10%; only a stone not owned yet). *(tune)*

### Friendship (`FriendshipRules`, 0 to 255, starts at 70)

| Source | Base | Who |
|---|---|---|
| Pet | 2 | the Pokémon petted (the buddy, or a module's lead on its own page), up to 10 a day |
| Focus session | 3 | Plan's lead |
| Task or habit | 1 | Plan's lead, the first 10 pieces of paid work a day (`TASK_FRIENDSHIP_DAILY_CAP`) |
| Berry | 1 | the Pokémon fed |
| Level up | 1 | the Pokémon |
| Care routine | 1 | Care's lead, up to 5 routines a day |
| Calm break | 1 | Care's lead, up to 3 a day |
| Care skill | 1 | Care's lead, up to 3 skills a day |
| Heal activity | 1 | Heal's lead, up to 3 activities a day |
| Mint (logged record, reconciled account, review) | 1 | Mint's lead, any number of logged records, up to 2 reconciled accounts a day and 1 review a week |
| Base (chore done, maintenance done) | 1 | Base's lead, up to 5 chores and 2 pieces of maintenance a day |
| Link (follow-up finished) | 1 | Link's lead, up to 3 follow-ups a day |

Bond styles (from the nature) double their matching source (Comfort: pet and routine, Presence: focus, calm break and skill, Gifts: berry); Balanced gets
1.5 × on every source, rounded up. Friendship never goes down.

### Perks (`Perks`)

Perk buckets: egg luck, candy yield, Super Candy chance, item finder, shiny hunter, streak keeper, Poké (`PerkBucket.COINS`). Plan's lead's types
(the global buddy is cosmetic) give up to 15 each (× power, friendship up to × 1.2, nature × 1.1 or 0.9; a second type half), party members assist at a
quarter, a held item +4 (Amulet Coin +10 Poké), abilities +3 to +10 under their condition. A Guardian adds its
signature perk (10, +5 for a second type). Caps in section 3.

- **Held items** are given from the Bag or the Pokémon's page (`GameStore.holdItem`, `takeItem`); what it held before goes
  back to the bag. Perk items, Mega Stones and trade-evolution items can be held; Guardians hold nothing.
- **Streak keeper** at 10 or more (`STREAK_KEEPER_THRESHOLD`) forgives a second missed day a week, for the daily streak and
  for habit streaks (`PerkBonuses.extraForgivenDays`).
- **Magic Guard** (Plan's lead's ability when the session started): once a week, a failed session of 5 minutes or more
  still hatches its egg, paid as a session of the minutes focused, never as Deep Focus. It stays a failed session in the
  history and does not count as an active day (`GameStore.magicGuard`, counter `magic-guard-<week>`).
- The perks that count for a session are Plan's lead's when it started (`FocusSession.leadIdAtStart`, saved as `buddyIdAtStart`).

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
| Pokémon | Transferred for candy and Poké (never the buddy, the last party member, a starred one, or a legendary or mythical) | `Transfer.block` |
| Trainer points | Never spent; they open features, Guardian slots and quests. Ranks at 0, 50, 150, 325, 650, 1,150, 1,900, 3,000, 5,000 and 9,000 (names in `docs/design.md`) | `TrainerRank` |

## 3. Caps

| Cap | Value | Constant |
|---|---|---|
| Candy stack (Rare, Super) | 9,999; the rest becomes Poké | `Inventory.MAX_CANDIES` |
| Other item stack | 999; the rest becomes Poké | `Inventory.MAX_STACK` |
| Level | 100; legendaries and mythicals are not fed | `CandyRules.MAX_LEVEL` |
| Friendship | 255; friendship evolutions at 220 | `FriendshipRules.MAX`, `EVOLVE_AT` |
| Pet friendship | 10 a day | `PET_DAILY_CAP` |
| Heal | 3 logged activities, 1 first drink, 5 step amounts and 3 record reads a day, and an activity only within 12 hours of its end (the world checks again: `GameStore.HEAL_DAILY_CAP`; an event id pays once, `AppData.healPaid`), at most 4 trainer points a day (steps and reads add none) | `HealRules`, `HEAL_DAILY_CAP` |
| Base | 5 chores and 2 pieces of maintenance a day (the world checks again: `GameStore.BASE_CAPS`; an event id pays once, `AppData.basePaid`, stored as `homePaid`) | `BaseRules`, `BASE_CAPS` |
| Link | 3 follow-ups a day (the world checks again: `GameStore.LINK_CAPS`; an event id pays once, `AppData.linkPaid`) | `LinkRules`, `LINK_CAPS` |
| Link Safari | 1 catch per person per day; at most 5 Safari catches a day in total (`GameStore.SAFARI_PER_DAY`, `Counters.SAFARI`, *(tune)*, added by the build so made-up contacts buy nothing; owner to confirm) | `GameStore.SAFARI_PER_DAY` |
| Mint | Any number of logged records, 2 reconciled accounts a day and 1 review a week (the world checks again: `GameStore.MINT_CAPS`; an event id pays once, `AppData.mintPaid`), at most 3 trainer points a day (a logged record pays none) | `MintRules`, `MINT_CAPS` |
| Care | 5 routines, 3 calm breaks, 3 skills and 1 rhythm a day (the world checks again: `GameStore.CARE_DAILY_CAP`), at most 11 trainer points a day, plus 5 on a rhythm day | `CareRules`, `CARE_DAILY_CAP` |
| EVs | 252 a stat, 510 in all | `StatCalculator` |
| Party | 6 | `OwnedPokemon.PARTY_SIZE` |
| Perk bucket | 25; Guardians add up to 15 on top | `BUCKET_CAP`, `Guardians` |
| Care perk bucket | 25 (Friendship gain, Berry finder, Visitor luck), from Care's party only | `CarePerks` |
| Heal perk bucket | 25 (Friendship gain, Berry finder), from Heal's party only | `HealPerks` |
| Mint perk bucket | 25 (Candy finder, Friendship gain), from Mint's party only | `MintPerks` |
| Base perk bucket | 25 (Candy finder, Friendship gain), from Base's party only | `BasePerks` |
| Guardian slots | One per module (Plan, Care, Heal, Mint, Base, Link), no rank gate | `Guardians.MODULES` |
| Active quests | 3 | `Quests.MAX_ACTIVE` |
| Egg meter | 5 | `EGG_METER_SIZE` |
| Paid subtasks | 5 a task | `MAX_PAID_SUBTASKS` |
| Milestone Super Candies | 3 a day | `MAX_MILESTONE_REWARDS_PER_DAY` |
| Habit | pays once a day, even after an undo | `Habit.paysOn`, `paidOn` |
| Task | pays once, after its rank's minimum age (2, 5, 10, 15, 20 minutes for E to A); a repeating task's instance pays from its own day, never ahead | `Rank.minAgeMinutes`, `TaskRules.paysOnCompletion`, `plan.finish_task` |
| Focus session | 5 minutes at least; 180 at most (the shiny bonus stops at 240 minutes, past the longest session) | `Egg.MIN_MINUTES`, `FocusRules.MAX_MINUTES` |
| Trainer points, Poké | no cap; points never go down | `TrainerRank` |

**Pacing of the life modules (not simulated; `simulateEconomy` plays only Plan, so none of this moves Plan's report).** At most about 1.35 Care friends a day (`BEFRIEND_DAILY_CAP` 4) plus Care Eggs (3 points: a routine 1, a calm break or skill 2), up to 5 a day on a full day. Heal: at most 1.75 Field Eggs a day (an activity 2 points, a drink 1); steps and reads add no Egg points or trainer points (a walker earns 1 to 7 Rare Candies a day). Mint: about one Ledger Egg on a very organised day, typically 1 to 2 a week. Base: a chore 1 point and maintenance 2, one Habitat Egg per 5 points, capped at 5 chores and 2 maintenance a day; the Decoration meter rides on the same payments (5 points a decoration, 42 in all). Link: at most one Postcard Egg a day (3 follow-ups). All of it is below Plan's several eggs a day, on purpose. *(tune: `CARE_EGG_METER_SIZE`, `HEAL_EGG_METER_SIZE`, `MINT_EGG_METER_SIZE`, `HOME_EGG_METER_SIZE`, `LINK_EGG_METER_SIZE`, `Visitors.CATCH_UP_DAYS`, `Decorations.METER_SIZE`, each module's `*_PAID_PER_DAY`.)*

Care was the first module with a per-module daily cap on the world's side (`CARE_DAILY_CAP`); Heal, Mint, Base and Link followed (section 3). There is no daily cap on tasks, habits or focus sessions, on purpose (many real tasks always pay, `docs/design.md`): `RewardEngine` leaves daily limits to the caller.

**Friend Safari at 0.225.0 (rules version 33; not simulated).** `EconomySimulator` plays only Plan and models no Link rule, so `simulateEconomy` was not re-run. Analytic bound: at most 5 Safari catches a day whatever the number of contacts, each only for a species weighted ×6 when not caught, no Poké, trainer point or item; new-species candies only. Expected new species fall fast as the Link Region's species are caught, then a catch is a duplicate that pays nothing. Built under Sonnet with the Opus advisor, not an Opus economy session; owner to confirm the 5 a day total cap.

## 4. What can be lost

- **A failed focus session** (left the app for over 10 seconds, moved the phone in Deep Focus, gave up) pays nothing (except once a week with Magic Guard, section 1); a Lure is kept. This is the one deliberate exception (docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md, principle 4).
- **A transferred Pokémon** is gone; its species, shiny and forms stay in the Pokédex.
- **Evolution** uses its Super Candy, stone or Link Cord.
- **A task reward** can be undone for 10 seconds (`UNDO_WINDOW_MILLIS`).
- Nothing expires: no timed items, no decay, no daily resets beyond the counters above.

## 5. Gaps between the code and docs/design.md

- Lures bought or crafted from berries (design.md): not built. Lures are earned but Focus never passes one.
- Stack caps are uneven: transfers and the focus berry add without the cap; the app caps non-candies at 999.

## World

World pays through `AppData.world` and the Bag only, not the reward engine's state, so `RewardRules.VERSION` does not cover it. Its Poké and items are written to the reward history as `World: exploring`, `jobs`, `research` and `treasure` (`Reward.Poke` is only ever written by World); items go straight into the Bag. All numbers *(tune)*; pace, yields and the year simulations come from `./gradlew :world-core:simulateWorldEconomy` (re-run after changing any World number), not repeated here.

- **Sources:** per floor Exploration XP (`XP_BASE` + floor), Poké (5 + 2 × floor + 0 to 4), a berry (70%), a treasure box (6%) and a wild find (3%); a milestone (Rare Candies and Poké) once per dungeon floor, never repeated. Opening a silver or gold box can also yield a Mega Stone (3% and 10%; only a stone not owned yet; section 1). Floors take `baseFloorSeconds / (1 + power / 100)` (never under a quarter of it); at most 12 hours of away time counts per open.
- **Floor outcomes** (`Outcomes`, `MODULES/WORLD.md` 3.6): a floor a team is too weak for pays half the Poké and Exploration XP (a setback) or ends the expedition safely (a turn-back).
- **Missions:** the job board and its pay and pace come from `./gradlew :world-core:simulateWorldEconomy`; the daily berry run pays 100 Poké; ordinary jobs wait 14 days, daily jobs 1. The only Rare Candy from a job is a once-only rescue (Eevee, Bagon) and the weekly Mt. Steel patrol.
- **Member experience** (`MODULES/WORLD.md` 5.2): each Pokémon on a team gets `CandyRules.XP_PER_FLOOR` (1) XP for each floor the team climbs; `XP_PER_CANDY` (20) XP are one step of candy progress on the shared level curve. Guardians and level-100 Pokémon get none. Not simulated.
- **Skills, facilities and abilities** add at most 20% Poké, +10 points berry chance, +3 points find chance, +5 points treasure chance and +50% Exploration XP; none changes floor time except the Guardian's Temporal Mastery (10% faster once the story is complete). Abilities add up to three levels to a skill (one per helper on the team).
- **Poké sinks in World:** Camp and Lab (400 and 600 base, tripling over five levels), the Dojo (200 Poké for 60 skill XP), the Café (3 berries) and the Kecleon Shop (above); the Swap Shop is a convenience, not a sink or a source. The Poké Mart is in the Pokémon tab. Story chapters 3 to 6 need 3, 4, 5 and 7 different dungeons cleared.
