# The economy: sources, sinks and caps

Every reward source, every use and every cap, as the code has them under **rules version 1** (`RewardRules.VERSION` in
`pokemon-core`). This is principle 6 of `docs/ecosystem.md` ("every reward source, cap and use is written down"). The
reasons and pacing targets are in `docs/design.md`; the simulated pacing is in `docs/economy-report.md`.

**Keep it true.** A change to anything below that changes what an event pays bumps `RewardRules.VERSION` and updates this
file in the same PR (`GoldenVectorTest` refuses to regenerate `tools/server/golden.json` with new outcomes under the old
version). Re-run `./gradlew :plan-core:simulateEconomy` after it. Names in `code` are the constants to search for.

Where the rules live: `RewardEngine`, `TrainerRank`, `CandyRewards` and `Egg` (the rules versioned above, also run by the
server); `Transfer`, `Items`, `Perks`, `Rules` (`CandyRules`, `FriendshipRules`, `Shiny`), `EvolutionRules` in
`pokemon-core`; `TaskRules`, `Goal`, `Review`, `Streak`, `FocusSession` in `plan-core`; `GameStore` and `Quests` in `app-core`.

## 1. Sources

### What each event pays

| Event | Pays | Constant |
|---|---|---|
| **Focus session completed** (5 minutes or more) | One egg, hatched at once (rarity from the length, section 1.3) · coins `minutes / 5` · trainer points `minutes / 5` · 1 random berry · buddy friendship (focus session) · an active day | `COINS_PER_FIVE_MINUTES`, `TrainerRank.pointsForEffort`, `Egg.MIN_MINUTES` |
| **Task or habit completed** (tier 1 to 5 = rank E to A) | Rare Candies (table below), maybe Super Candies · coins `tier × 2` · trainer points `tier × 2` · an item drop chance `8 + 2 × tier` % · EVs `tier` to the buddy's training plan, `tier / 2` to each other party member · buddy friendship (task) · `tier` points on the Egg meter · an active day | `COINS_PER_TIER`, `pointsForAchievement`, `DROP_BASE_PERCENT`, `DROP_PER_TIER_PERCENT` |
| **Egg meter full** (10 points) | A Task Egg, rolled as a 15 minute session (no lure, no spotlight); the rest carries over | `EGG_METER_SIZE`, `TASK_EGG_MINUTES` |
| **Subtask done** | 1 Rare Candy, once per subtask, at most 5 paid per task, after the task rank's minimum age | `SUBTASK_RARE_CANDIES`, `MAX_PAID_SUBTASKS` |
| **Goal milestone done** (24 hours old or more) | 1 Super Candy, at most 3 a day; an active day | `SUPER_CANDIES_PER_MILESTONE`, `MILESTONE_MIN_AGE_MILLIS`, `MAX_MILESTONE_REWARDS_PER_DAY` |
| **Every milestone of a goal done** | 1 Nature Mint, once per goal | `Goal.bonusGiven` |
| **Weekly review** | 1 berry, once a week | `Review.REWARD_BERRIES` |
| **Transfer** a Pokémon | Rare Candies (Common 8, Uncommon 12, Rare 22, Super Rare 60, plus a fifth of the candies it was fed), coins twice the base candies (16 / 24 / 44 / 120), its held item back | `Transfer.candiesByRarity`, `COIN_FACTOR`, `REFUND_DIVISOR` |
| **Legendary or mythical quest** claimed | That Pokémon (out of the party; it can be shiny) | `Quests` |
| **Friend's gift** opened | 3 Rare Candies (one gift per friend a day) | `platform.send_gift` |
| **Starter** | The starter (never shiny) and a tutorial task that pays at once | `GameStore.start` |
| **Petting the buddy** | Friendship (pet), up to 10 a day | `PET_DAILY_CAP` |
| **A bag stack is full** | 1 coin per candy or item that did not fit | `Inventory.addCapped` |

Task perks: candies × 1.10 when finished on time (by its due date) and × 1.10 when it had focus minutes, then the buddy's
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
| 18 type Lures, each evolution stone and gender-evolution item | 1 |
| Lucky Egg, Oval Charm, Star Piece, Scope Lens, Leftovers, Amulet Coin | 1 |
| Shiny Charm, Bottle Cap, Nature Mint, Ability Capsule | 0.5 |
| 18 Tera Shards | 0.1 |

Never dropped: Ability Patch, Mega Stones, the Dynamax Band. Focus sessions never drop items (they pay a berry).

### Friendship (`FriendshipRules`, 0 to 255, starts at 70)

| Source | Base | Who |
|---|---|---|
| Pet | 2 | the buddy, up to 10 a day |
| Focus session | 3 | the buddy |
| Task or habit | 1 | the buddy |
| Berry | 1 | the Pokémon fed |
| Level up | 1 | the Pokémon |

Bond styles (from the nature) double their matching source (Comfort: pet, Presence: focus, Gifts: berry); Balanced gets
1.5 × on every source, rounded up. Friendship never goes down.

### Perks (`Perks`)

Perk buckets: egg luck, candy yield, Super Candy chance, item finder, shiny hunter, streak keeper, coins. The buddy's types
give up to 15 each (× power, friendship up to × 1.2, nature × 1.1 or 0.9; a second type half), party members assist at a
quarter, a held item +4 (Amulet Coin +10 coins), abilities +3 to +10 under their condition. A Guardian adds its
signature perk (10, +5 for a second type). Caps in section 3.

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
| Trainer points | Never spent; they open regions, features, Guardian slots and quests | `TrainerRank` |
| Coins | **Nothing yet** (section 5) | |

## 3. Caps

| Cap | Value | Constant |
|---|---|---|
| Candy stack (Rare, Super) | 9,999; the rest becomes coins | `Inventory.MAX_CANDIES` |
| Other item stack | 999; the rest becomes coins | `Inventory.MAX_STACK` |
| Level | 100; legendaries and mythicals are not fed | `CandyRules.MAX_LEVEL` |
| Friendship | 255; friendship evolutions at 220 | `FriendshipRules.MAX`, `EVOLVE_AT` |
| Pet friendship | 10 a day | `PET_DAILY_CAP` |
| EVs | 252 a stat, 510 in all | `StatCalculator` |
| Training plan | 2 stats | `OwnedPokemon` |
| Party | 6 | `OwnedPokemon.PARTY_SIZE` |
| Perk bucket | 25; Guardians add up to 15 on top | `BUCKET_CAP`, `Guardians` |
| Guardian slots | 0, then 1 at rank 4, 2 at rank 6, 3 at rank 9 | `Guardians.slots` |
| Active quests | 3 | `Quests.MAX_ACTIVE` |
| Egg meter | 10 | `EGG_METER_SIZE` |
| Paid subtasks | 5 a task | `MAX_PAID_SUBTASKS` |
| Milestone Super Candies | 3 a day | `MAX_MILESTONE_REWARDS_PER_DAY` |
| Weekly review berry | 1 a week | `Review` |
| Habit | pays once a day, even after an undo | `Habit.paysOn`, `paidOn` |
| Task | pays once, after its rank's minimum age (2, 5, 10, 15, 20 minutes for E to A) | `Rank.minAgeMinutes`, `plan.finish_task` |
| Focus session | 5 minutes at least; 180 in the app; 240 on the server (the shiny bonus stops at 240) | `Egg.MIN_MINUTES`, `MAX_MINUTES` |
| Egg tickets | 20 a device; 200 reported per sync | `TICKET_STOCK`, `MAX_SPENT` |
| Friend gifts | 1 per friend a day | `platform.send_gift` |
| Trainer points, coins | no cap; points never go down (the server refuses it) | `apply_outcome` |

There is no daily cap on tasks, habits or focus sessions yet: `RewardEngine` leaves daily limits to the caller, and
design.md's "daily reward limit per app" *(tune)* is not built. It matters once a second module earns through the same door.

## 4. What can be lost

- **A failed focus session** (left the app for over 10 seconds, moved the phone in Deep Focus, gave up) pays nothing; its egg ticket is spent so the egg can't be rerolled, and a Lure is kept. This is the one deliberate exception (ecosystem.md, principle 4).
- **A transferred Pokémon** is gone; its species, shiny and forms stay in the Pokédex.
- **Evolution** uses its Super Candy, stone or Link Cord.
- **A task reward** can be undone for 10 seconds (`UNDO_WINDOW_MILLIS`).
- Nothing expires: no timed items, no decay, no daily resets beyond the counters above. Unopened gifts wait.

## 5. Gaps between the code and docs/design.md

Found while writing this down; each is its own change, none is in this rules version.

- **Coins have no use.** design.md plans a shop (rank step 2) and Lures bought or crafted from berries; neither exists, so coins only pile up.
- **Earned items with no use yet:** Lures (Focus never passes one), held items (no way to equip, so their perks never apply), Nature Mint, Ability Capsule, Bottle Cap, Tera Shards (`ItemEffects` exists, not called).
- **Perks not wired:** Magic Guard (`keepsFailedEgg`) and the streak keeper bucket are computed and never read.
- **Trial evolutions** can never happen: nothing records a completed trial.
- **Stack caps are uneven:** transfers, the focus berry and the weekly review berry add without the cap; the server's gift caps every item at 9,999, the app caps non-candies at 999.
- **Who decides.** The app computes every reward itself and the server checks the snapshot on sync (points never drop, the party and Guardians fit, column ranges). The server's `effort`, `task` and `achievement` kinds exist and are tested but no app calls them yet. Moving rewards to them (and adding the daily caps) is the step before a public release (`supabase/README.md`, Known limits).
