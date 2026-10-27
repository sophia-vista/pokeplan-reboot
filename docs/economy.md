# The economy: sources, sinks and caps

Every reward source, every use and every cap, as the code has them under **rules version 9** (`RewardRules.VERSION` in
`pokemon-core`). This is principle 6 of `docs/ecosystem.md` ("every reward source, cap and use is written down"). The
reasons and pacing targets are in `docs/design.md`; the simulated pacing is in `docs/economy-report.md`.

**Keep it true.** A change to anything below that changes what an event pays bumps `RewardRules.VERSION` and updates this
file in the same PR (`GoldenVectorTest` refuses to regenerate `tools/server/golden.json` with new outcomes under the old
version). Re-run `./gradlew :plan-core:simulateEconomy` after it. Names in `code` are the constants to search for.

Where the rules live: `RewardEngine`, `TrainerRank`, `CandyRewards` and `Egg` (the rules versioned above, also run by the
server); `Transfer`, `Items`, `Perks`, `Rules` (`CandyRules`, `FriendshipRules`, `Shiny`), `EvolutionRules` in
`pokemon-core`; `TaskRules`, `Goal`, `Review`, `Streak`, `FocusSession` in `plan-core`; `CareRules` in `care-core`; `GameStore`, `CareStore` and `Quests` in `app-core`.

**Version history.** 1: everything before Care. 2: the Care events (`RewardEngine.care`) and the friendship sources Routine and Calm break; nothing that version 1 paid changed. 3: trade-evolution items (Metal Coat, King's Rock...) join the drop table; Magic Guard hatches a failed session's egg once a week; the Streak keeper perk forgives a second missed day. 4: training plans are gone, so tasks pay no EVs; Synchronize gives +3 Super Candy chance instead of +25% EVs. 5: a new trainer rank curve, quick at first and slower after (0, 50, 150, 325, 650, 1,150, 1,900, 3,000, 5,000, 9,000; it was 0, 625 ... 10,625). 6: the Pick-for-me bonus (candies × 1.10), task friendship capped at 10 a day, and the streak day rolls over at 4am. 7: Care's practised skill (a DBT skill from the library: friendship (skill) and a berry, 3 a day) and the friendship source Skill; nothing else changed. 8: a Pokémon records which module's party it is in (`partyId`, six per party); nothing paid changed. 9: Care has its own lead (`careLeadId`) and perk buckets: the lead takes Care's friendship, and Friendship gain (a chance of one extra friendship step) and Berry finder (a chance of a second berry) apply; with no Care lead Care pays as before.

## 1. Sources

### What each event pays

| Event | Pays | Constant |
|---|---|---|
| **Focus session completed** (5 minutes or more) | One egg, hatched at once (rarity from the length, section 1.3) · coins `minutes / 5` · trainer points `minutes / 5` · 1 random berry · buddy friendship (focus session) · an active day | `COINS_PER_FIVE_MINUTES`, `TrainerRank.pointsForEffort`, `Egg.MIN_MINUTES` |
| **Task or habit completed** (tier 1 to 5 = rank E to A) | Rare Candies (table below), maybe Super Candies · coins `tier × 2` · trainer points `tier × 2` · an item drop chance `8 + 2 × tier` % · buddy friendship (task) · `tier` points on the Egg meter · an active day | `COINS_PER_TIER`, `pointsForAchievement`, `DROP_BASE_PERCENT`, `DROP_PER_TIER_PERCENT` |
| **Egg meter full** (10 points) | A Task Egg, rolled as a 15 minute session (no lure, no spotlight); the rest carries over | `EGG_METER_SIZE`, `TASK_EGG_MINUTES` |
| **Subtask done** | 1 Rare Candy, once per subtask, at most 5 paid per task, after the task rank's minimum age | `SUBTASK_RARE_CANDIES`, `MAX_PAID_SUBTASKS` |
| **Goal milestone done** (24 hours old or more) | 1 Super Candy, at most 3 a day; an active day | `SUPER_CANDIES_PER_MILESTONE`, `MILESTONE_MIN_AGE_MILLIS`, `MAX_MILESTONE_REWARDS_PER_DAY` |
| **Every milestone of a goal done** | 1 Nature Mint, once per goal | `Goal.bonusGiven` |
| **Weekly review** | 1 berry, once a week | `Review.REWARD_BERRIES` |
| **Mini-quest** ticked (honour system, 3 a day, one reroll before any is done) | 1 random berry each, 1 more for the third; no active day | `MiniQuests.ALL_THREE_BONUS_BERRIES`, `GameStore.completeMiniQuest` |
| **Welcome back** after 7 or more days away (once per absence) | 5 Rare Candies and 2 berries of one flavour | `WELCOME_BACK_RARE_CANDIES`, `WELCOME_BACK_BERRIES` |
| **Transfer** a Pokémon | Rare Candies (Common 8, Uncommon 12, Rare 22, Super Rare 60, plus a fifth of the candies it was fed), coins twice the base candies (16 / 24 / 44 / 120), its held item back | `Transfer.candiesByRarity`, `COIN_FACTOR`, `REFUND_DIVISOR` |
| **Legendary or mythical quest** claimed | That Pokémon (out of the party; it can be shiny) | `Quests` |
| **Friend's gift** opened | 3 Rare Candies (one gift per friend a day) | `platform.send_gift` |
| **Starter** | The starter (never shiny) and a tutorial task that pays at once | `GameStore.start` |
| **Petting the buddy** | Friendship (pet), up to 10 a day | `PET_DAILY_CAP` |
| **Care: routine done** (first tick of the day for that routine) | Buddy friendship (routine) · 1 trainer point. At most 5 routines a day | `CARE_POINTS`, `CareRules.ROUTINES_PAID_PER_DAY` |
| **Care: calm break** (1 to 2 minutes; under a minute is kept but pays nothing) | Buddy friendship (calm break) · 1 random berry · 1 trainer point. At most 3 a day | `CareRules.BREAKS_PAID_PER_DAY`, `BREAK_MIN_SECONDS` |
| **Care: skill practised** (a DBT skill from the library, each skill once a day; paced breathing pays as a calm break) | Buddy friendship (skill) · 1 random berry · 1 trainer point. At most 3 a day | `CareRules.SKILLS_PAID_PER_DAY` |
| **Care: kept a rhythm** (care done on 4 days of a week: a routine, a calm break or a skill) | 3 random berries · 5 trainer points, once a week | `RHYTHM_BERRIES`, `RHYTHM_POINTS`, `CareRules.RHYTHM_DAYS` |
| **Care: befriend a visitor** *(decided, not built; ecosystem.md section 6)* | An Egg (rarely the Pokémon itself) from the Care Region pool. One befriend a day | not yet in `RewardRules` |
| **Care: diary card, journal entry, note** | Nothing, ever: entering sensitive information never earns (ecosystem.md, section 6) | |
| **A bag stack is full** | 1 coin per candy or item that did not fit | `Inventory.addCapped` |

Task perks: candies × 1.10 when finished on time (by its due date), × 1.10 when it had focus minutes and × 1.10 when it was the task "Pick for me" chose (`PICKED_BONUS`, once per pick), then the buddy's
perks. A perk bonus rounds to the nearest whole number and never pays less than the base (`RewardEngine.scaled`), so small
bonuses on small rewards round away.

### Rare and Super Candies per task (`CandyRewards`)

| Tier (rank) | Rare Candies | Super Candy chance | Super Candies |
|---|---|---|---|
| 1 (E) | 3–5 | 10% | 1–2 |
| 2 (D) | 4–10 | 15% | 2–3 |
| 3 (C) | 5–13 | 20% | 3–4 |
| 4 (B) | 6–17 | 30% | 4–5 |
| 5 (A) | 7–20 | 40% | 6–7 |

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
- The pool is the chosen region's dex, base forms only. A region opens at the rank equal to its position (Home at rank 0, Paldea at 9).
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
| Rare Candy | 1 per feed; a level costs `2 + level² / 150` candies (2 at the start, 18 at level 50, 67 at level 99; 2,341 for 1 to 100), half when the Pokémon is more than 5 levels below the party's best | `CandyRules.candiesNeeded`, `CATCH_UP_GAP` |
| Super Candy | 1 per evolution by level, friendship, time, random branch, trial, or gender with no item | `EvolutionRules` |
| Evolution stone, gender item | 1 per evolution that needs it | `EvolutionRules` |
| Link Cord | 1 per trade evolution (and the held item it needs) | `EvolutionRules` |
| Vitamin | +10 EVs in one stat | `VITAMIN_EVS` |
| Berry | +1 friendship (more with the Gifts or Balanced style) | `GameStore.useItem` |
| Lure | Used up when its session hatches; kept when the session fails | `RewardEngine.effort` |
| Pokémon | Transferred for candy and coins (never the buddy, the last party member, a starred one, or a legendary or mythical) | `Transfer.block` |
| Trainer points | Never spent; they open regions, features, Guardian slots and quests. Ranks at 0, 50, 150, 325, 650, 1,150, 1,900, 3,000, 5,000 and 9,000 | `TrainerRank` |
| Coins | **Nothing yet** (section 5) | |

## 3. Caps

| Cap | Value | Constant |
|---|---|---|
| Candy stack (Rare, Super) | 9,999; the rest becomes coins | `Inventory.MAX_CANDIES` |
| Other item stack | 999; the rest becomes coins | `Inventory.MAX_STACK` |
| Level | 100; legendaries and mythicals are not fed | `CandyRules.MAX_LEVEL` |
| Friendship | 255; friendship evolutions at 220 | `FriendshipRules.MAX`, `EVOLVE_AT` |
| Pet friendship | 10 a day | `PET_DAILY_CAP` |
| Care | 5 routines, 3 calm breaks, 3 skills and 1 rhythm a day (the world checks again: `GameStore.CARE_DAILY_CAP`), at most 11 trainer points a day, plus 5 on a rhythm day | `CareRules`, `CARE_DAILY_CAP` |
| EVs | 252 a stat, 510 in all | `StatCalculator` |
| Party | 6 | `OwnedPokemon.PARTY_SIZE` |
| Perk bucket | 25; Guardians add up to 15 on top | `BUCKET_CAP`, `Guardians` |
| Care perk bucket | 25 (Friendship gain, Berry finder, Visitor luck), from Care's party only | `CarePerks` |
| Guardian slots | 0, then 1 at rank 4, 2 at rank 6, 3 at rank 9 | `Guardians.slots` |
| Active quests | 3 | `Quests.MAX_ACTIVE` |
| Egg meter | 10 | `EGG_METER_SIZE` |
| Paid subtasks | 5 a task | `MAX_PAID_SUBTASKS` |
| Milestone Super Candies | 3 a day | `MAX_MILESTONE_REWARDS_PER_DAY` |
| Weekly review berry | 1 a week | `Review` |
| Habit | pays once a day, even after an undo | `Habit.paysOn`, `paidOn` |
| Task | pays once, after its rank's minimum age (2, 5, 10, 15, 20 minutes for E to A); a repeating task's instance pays from its own day, never ahead | `Rank.minAgeMinutes`, `TaskRules.paysOnCompletion`, `plan.finish_task` |
| Focus session | 5 minutes at least; 180 in the app; 240 on the server (the shiny bonus stops at 240) | `Egg.MIN_MINUTES`, `MAX_MINUTES` |
| Egg tickets | 20 a device; 200 reported per sync | `TICKET_STOCK`, `MAX_SPENT` |
| Friend gifts | 1 per friend a day | `platform.send_gift` |
| Trainer points, coins | no cap; points never go down (the server refuses it) | `apply_outcome` |

Care is the first module with a per-module daily cap on the world's side (`CARE_DAILY_CAP`). There is no daily cap on tasks, habits or focus sessions yet: `RewardEngine` leaves daily limits to the caller, and
design.md's "daily reward limit per app" *(tune)* is not built. It matters once a second module earns through the same door.

## 4. What can be lost

- **A failed focus session** (left the app for over 10 seconds, moved the phone in Deep Focus, gave up) pays nothing (except once a week with Magic Guard, section 1); its egg ticket is spent so the egg can't be rerolled, and a Lure is kept. This is the one deliberate exception (ecosystem.md, principle 4).
- **A transferred Pokémon** is gone; its species, shiny and forms stay in the Pokédex.
- **Evolution** uses its Super Candy, stone or Link Cord.
- **A task reward** can be undone for 10 seconds (`UNDO_WINDOW_MILLIS`).
- Nothing expires: no timed items, no decay, no daily resets beyond the counters above. Unopened gifts wait.

## 5. Gaps between the code and docs/design.md

Found while writing this down; each is its own change, none is in this rules version.

- **Coins have no use.** design.md plans a shop (rank step 2) and Lures bought or crafted from berries; neither exists, so coins only pile up.
- **Earned items with no use yet:** Lures (Focus never passes one). Mega Stones can be held but never drop.
- **Trial evolutions** can never happen: nothing records a completed trial.
- **Stack caps are uneven:** transfers, the focus berry and the weekly review berry add without the cap; the server's gift caps every item at 9,999, the app caps non-candies at 999.
- **Who decides.** The app computes every reward itself and the server checks the snapshot on sync (points never drop, the party and Guardians fit, column ranges). The server's `effort`, `task` and `achievement` kinds exist and are tested but no app calls them yet. Moving rewards to them (and adding the daily caps) is the step before a public release (`supabase/README.md`, Known limits).
