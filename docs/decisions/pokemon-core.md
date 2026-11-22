# Decision records: pokemon-core

Moved here from code comments in the comment audit (21 November 2026). Each entry is a decision, rule or constraint that
the code alone does not show. Entries are grouped by source file; a line saying "Documented in" means the owning doc
already holds the rule and the entry only points to it.

## `pokemon-core/build.gradle.kts`

### Dex tables as web sources

Documented in `docs/code-map.md pokemon-core`.

### Dex JSON excluded from the web resources

The web build compiles the Dex tables into Kotlin strings (`generateDexWebSources`), so `wasmJsProcessResources` excludes `dex/**` in pokemon-core; otherwise the JSON files would ship as a second copy of the same data in the page.

## `pokemon-core/src/commonMain/kotlin/app/pokeplan/core/dex/Dex.kt`

### Dex.learnset for Mega and Gigantamax forms

`Dex.learnset` gives a Mega or Gigantamax form the learnset of its species default form, because the data holds only the older games rows for those forms. A form with no moves of its own also falls back to the default form; a form with none anywhere gets `Learnset.EMPTY`.

### Dex.lineEvolutions pairs

`Dex.lineEvolutions` lists every evolution in a species line from its first stage down, once per pair of species: rows that differ only by form (Pikachu into Raichu and Alolan Raichu) count once, and the row for the default forms wins over a regional variant row.

## `pokemon-core/src/commonMain/kotlin/app/pokeplan/core/dex/DexModels.kt`

### Region stored names and retired game regions

`Region.PLAN` was the Home Region and keeps its stored name `HOME` (saves, focus sessions, the reward function); `BASE` is saved as `HOUSEHOLD`. The games regional dexes (Kanto to Paldea) are retired (0.44.0, owner decision): only the module regions are collected. The game regions stay in the enum, never released, only so saves, sessions and events that name one still load, and `Region.open` sends them to Plan.

### SpeciesInfo units

`SpeciesInfo.id` is the National Dex number. `femaleEighths` is the chance of female in eighths (0 to 8), null when genderless. `heightDm` and `weightHg` are the default form height in decimetres and weight in hectograms, as the games store them; `genus` is the Pokédex category ("Seed Pokémon"); `cosmeticForms` are purely visual variants (Unown letters, Vivillon patterns).

### EvolutionKind

Documented in `docs/design.md Evolution`.

### EvolutionKind.LEVEL

Documented in `docs/design.md Evolution`.

### EvolutionKind.TRADE

Documented in `docs/design.md Evolution`.

### EvolutionKind.FRIENDSHIP

Documented in `docs/design.md Evolution`.

### EvolutionKind.LEVEL_TIME

Documented in `docs/design.md Evolution`.

### EvolutionKind.RANDOM

Documented in `docs/design.md Evolution`.

### EvolutionKind.TRIAL

Documented in `docs/design.md Evolution`.

### Learnset data semantics

`Learnset` holds the moves one form learns from the newest game it appears in; `levelUp` pairs a move id with the level it is learned at (first is the move id, second the level).

## `pokemon-core/src/commonMain/kotlin/app/pokeplan/core/dex/DexParser.kt`

### Regions table HISUI list

The `regions` Dex table maps a region name to its ordered species ids and also holds an extra `HISUI` list, which is not a collecting region; it is used to pick the Hisuian variant when a species hatches in Sinnoh.

## `pokemon-core/src/commonMain/kotlin/app/pokeplan/core/dex/DexResources.kt`

### dexJson per platform

Documented in `docs/code-map.md pokemon-core`.

## `pokemon-core/src/commonMain/kotlin/app/pokeplan/core/dex/HatchRules.kt`

### HatchRules rarity

Documented in `docs/design.md Roster`.

### Region egg entry points

Documented in `docs/design.md Regions`.

## `pokemon-core/src/commonMain/kotlin/app/pokeplan/core/dex/SpecialSpecies.kt`

### SpecialSpecies

Documented in `docs/design.md Legendaries and mythicals`.

## `pokemon-core/src/commonMain/kotlin/app/pokeplan/core/pokemon/EvolutionRules.kt`

### EvolutionRules

Documented in `docs/design.md Evolution`.

### EvolutionRules.trialId

`EvolutionRules.trialId` gives one id per evolution row: two rows between the same pair of species (Mr. Mime and Galarian Mr. Mime) differ in the form they require or give, so the id includes the required and evolved form.

### Everstone blocks evolution

Documented in `docs/design.md Evolution`.

### Held evolution item used up

Documented in `docs/design.md Evolution`.

## `pokemon-core/src/commonMain/kotlin/app/pokeplan/core/pokemon/EvolutionTrials.kt`

### TrialNeed

Documented in `docs/design.md Evolution`.

### TrialNeed.Hold used up

Documented in `docs/design.md Evolution`.

### TrialNeed.Stats sign

`TrialNeed.Stats.sign` is 1 when Attack must be above Defense, -1 when below and 0 when equal (Tyrogue evolutions).

### Toxtricity Amped natures

Toxtricity takes its Amped form for the natures in `EvolutionTrials.AMPED`; every other nature gives Low Key, as in Sword and Shield.

### worldConditions become a WORLD trial

Documented in `docs/design.md Evolution`.

## `pokemon-core/src/commonMain/kotlin/app/pokeplan/core/pokemon/Gear.kt`

### GearTool names are persisted

`GearTool` constants are stored by name (favourites, recents, helper assignments), so a tool is never renamed or removed; new tools are only appended.

## `pokemon-core/src/commonMain/kotlin/app/pokeplan/core/pokemon/Items.kt`

### Item ids are plain strings

Item ids are plain strings so they match the ids used in the evolution data.

### Inventory.addCapped

Documented in `docs/economy.md Sinks and caps`.

### ItemEffects contract

Each `ItemEffects` function returns the changed Pokémon and does not touch the bag; the caller removes the item.

## `pokemon-core/src/commonMain/kotlin/app/pokeplan/core/pokemon/Moveset.kt`

### Moveset

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 8.3`.

### Moveset.TUTOR_LEVEL

Tutor and machine moves may be chosen only from level 30 (`Moveset.TUTOR_LEVEL`, a starting value to tune), so a young Pokémon cannot hold the strongest moves of the game.

### Moveset.active

`Moveset.active` uses the stored move choice only while it is still legal for the Pokémon form, since an evolution can leave a move behind; otherwise it falls back to the natural moves. The result is at most `MAX` moves and never repeated.

## `pokemon-core/src/commonMain/kotlin/app/pokeplan/core/pokemon/Nature.kt`

### BondStyle

Documented in `docs/design.md Ideas adopted from other apps (Bond styles)`.

### BondStyle

Documented in `docs/design.md Ideas adopted from other apps (Bond styles)`.

### BondStyle

Documented in `docs/design.md Ideas adopted from other apps (Bond styles)`.

### BondStyle

Documented in `docs/design.md Ideas adopted from other apps (Bond styles)`.

### BondStyle

Documented in `docs/design.md Ideas adopted from other apps (Bond styles)`.

### BondStyle

Documented in `docs/design.md Ideas adopted from other apps (Bond styles)`.

## `pokemon-core/src/commonMain/kotlin/app/pokeplan/core/pokemon/OwnedPokemon.kt`

### OwnedPokemon xp and candyProgress

Documented in `docs/economy.md World member experience`.

### OwnedPokemon.moves

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 8.3`.

### Guardians take no candies

Documented in `docs/design.md Guardians (legendaries and mythicals)`.

### feedRareCandy catch-up

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 8.2`.

### OwnedPokemon.gainXp

Documented in `docs/economy.md World member experience`.

### OwnedPokemon.withMoves

`OwnedPokemon.withMoves` stores the identifiers as given and does not validate them; callers check `Moveset.canSet` first. An empty list means the Pokémon goes back to its natural moves.

### OwnedPokemon.evolvedInto

Evolving keeps IVs, EVs, nature, friendship and shininess; the ability keeps its slot; a nickname still equal to the old species name follows the evolution.

## `pokemon-core/src/commonMain/kotlin/app/pokeplan/core/pokemon/Perks.kt`

### PerkBucket

Documented in `docs/design.md Buddy and perks`.

### Held item battle percent

Documented in `docs/pokelife/MODULES/WORLD.md BattleIdentity`.

### Magic Guard keeps failed egg

Documented in `docs/design.md Buddy and perks`.

### AbilityPerks table

Documented in `docs/design.md Buddy and perks`.

### PerkContext.taskRank

`PerkContext.taskRank` is the finished task difficulty rank as 1 (E) to 5 (A).

### Perks.total with Guardians

Documented in `docs/design.md Guardians (legendaries and mythicals)`.

### Guardians

Documented in `docs/design.md Guardians (legendaries and mythicals)`.

### Guardians.BUCKET_CAP

Documented in `docs/design.md Guardians (legendaries and mythicals)`.

### Guardian lore buckets

Documented in `docs/design.md Guardians (legendaries and mythicals)`.

### Guardians.MODULES order

Documented in `docs/design.md Guardians (legendaries and mythicals)`.

### Guardians.addTo

Documented in `docs/design.md Guardians (legendaries and mythicals)`.

### Module perk buckets

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 2.2.1`.

### Module perk rule

Documented in `docs/design.md Parties per module`.

### Module perk buckets

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 2.2.1`.

### Module perk rule

Documented in `docs/design.md Parties per module`.

### Module perk buckets

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 2.2.1`.

### Module perk rule

Documented in `docs/design.md Parties per module`.

### Module perk buckets

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 2.2.1`.

### Module perk rule

Documented in `docs/design.md Parties per module`.

## `pokemon-core/src/commonMain/kotlin/app/pokeplan/core/pokemon/Rules.kt`

### CandyRules curve

Documented in `docs/design.md Pokémon`.

### XP_PER_FLOOR

Documented in `docs/economy.md World member experience`.

### CATCH_UP_GAP

Documented in `docs/economy.md Rare Candy`.

### FriendshipRules daily caps

`FriendshipRules` does not cap anything: the daily caps on friendship gains are applied by the caller.

### FriendshipRules.amount

Documented in `docs/design.md Ideas adopted from other apps (Bond styles)`.

### Shiny odds 1 in 128

Documented in `docs/design.md Pokémon`.

## `pokemon-core/src/commonMain/kotlin/app/pokeplan/core/pokemon/Shop.kt`

### Poké Mart

Documented in `docs/economy.md The Poké Mart`.

### Super Candy price

Documented in `docs/economy.md The Poké Mart`.

## `pokemon-core/src/commonMain/kotlin/app/pokeplan/core/pokemon/StatCalculator.kt`

### StatCalculator Shedinja HP

`StatCalculator` gives any species whose base HP is 1 (Shedinja) exactly 1 HP at every level, as in the main games, instead of applying the Gen 3+ HP formula.

## `pokemon-core/src/commonMain/kotlin/app/pokeplan/core/pokemon/Transfer.kt`

### Transfer

Documented in `docs/design.md Duplicates → candy`.

## `pokemon-core/src/commonMain/kotlin/app/pokeplan/core/reward/CandyReward.kt`

### CandyRates thresholds

`CandyRates.superAbove` is a threshold out of 100: a roll of 1..100 above it adds Super Candies on top of the always-paid Rare Candies, so a higher value makes Super Candies rarer. Higher task priority (1 to 5) uses a more generous row.

## `pokemon-core/src/commonMain/kotlin/app/pokeplan/core/reward/Outbreak.kt`

### Outbreak

Documented in `docs/pokelife/MODULES/MINT.md 16.2A`.

### Outbreak.shinyRolls

`Outbreak.shinyRolls` is a multiplier of the ordinary shiny chance applied to the caught Pokémon, not a probability: 1 means the ordinary odds.

## `pokemon-core/src/commonMain/kotlin/app/pokeplan/core/reward/RewardEngine.kt`

### PlayerState.buddyId

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 2.3`.

### PlayerState.caught

`PlayerState.caught` is every species ever owned and never shrinks: transferring a Pokémon keeps its Pokédex entry.

### PlayerState.guardians

`PlayerState.guardians` is the old global Guardian list from before 0.158.0. On load the app (`Places`) moves each into `moduleGuardians` and empties this list, so the field must stay to read old saves.

### PlayerState.moduleGuardians

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 2.2`.

### PlayerState.rolesVersion

`PlayerState.rolesVersion` is 0 for a save from before the global buddy and each module's party lead were separate roles, and 1 once `planLeadId` has been set from the old buddy (the app does this once on load). Before the split Plan's lead was the buddy.

### PlayerState.party

`PlayerState.party` is Plan's party, the one whose lead and perks the reward engine applies to Plan's rewards. Other modules use `partyOf(PartyIds.X)`; the global buddy is not part of it.

### PlayerState.lead

A module party's lead (slot 1) is the chosen lead id if that Pokémon is still in the party, otherwise the party's first member, otherwise none. A null lead id therefore means the first member leads.

### PlayerState.planLead

`PlayerState.planLead` is the Pokémon whose perks and friendship Plan's rewards use. It is not the global buddy, which is cosmetic and never changes a reward (owner, 7 November 2026).

### Reward.Evs

`Reward.Evs` is no longer paid (training plans are gone, rules version 4) but stays in the sealed hierarchy so reward-log entries written by older versions still decode and display.

### Reward.Poke

`Reward.Poke` is World's own money entry in the reward log. Only World's log entries carry it; the reward engine never pays it, and the engine's `Coins` reward is the Plan and module currency.

### Reward.Decoration

Documented in `docs/pokelife/MODULES/BASE.md 9.5`.

### Reward.Exp

Documented in `docs/pokelife/MODULES/PLAN.md 8.2`.

### EffortEvent.epochDay

`EffortEvent.epochDay` is the player's local day number (not the UTC day); it picks the day's spotlight type for the egg roll.

### BefriendEvent

Documented in `docs/pokelife/MODULES/CARE.md 6.6`.

### BefriendEvent.visitorId

`BefriendEvent.visitorId` is the befriended visitor's own id; an egg it leaves may take light traits from it (`VisitorInheritance`). An empty id means a friend made before ids were kept and passes nothing on.

### RewardFocus

Documented in `docs/economy.md Task reward focus`.

### AchievementEvent

Documented in `docs/economy.md Task or habit completed`.

### AchievementEvent.friendshipTasksToday

`AchievementEvent.friendshipTasksToday` counts paid work that already warmed Plan's lead today; once it reaches `RewardEngine.TASK_FRIENDSHIP_DAILY_CAP` further work adds no friendship.

### CareKind

A `CareKind` event is a Care moment of participation only. Diary cards, journal entries and notes are never events and earn nothing, and a practised skill carries no word of which skill it was.

### CareKind.PLAY

Documented in `docs/pokelife/MODULES/CARE.md 4`.

### CareEvent

`CareEvent` has no field beyond kind, ids and time: no titles, mood or health values reach the shared world. Daily caps are the caller's job, not the engine's.

### CareEvent.pokemonId

`CareEvent.pokemonId` is set only for `CareKind.PLAY`: it names the Pokémon played with, which takes the friendship instead of Care's lead.

### HealKind

Documented in `docs/pokelife/MODULES/HEAL.md 12.3`.

### HealKind.STEPS

Documented in `docs/economy.md Heal: steps`.

### HealKind.IMPORT

Documented in `docs/economy.md Heal: records read in`.

### HealEvent

Documented in `docs/pokelife/MODULES/HEAL.md 12.4`.

### MintKind

Documented in `docs/pokelife/MODULES/MINT.md 16.2`.

### MintKind.LOGGED

Documented in `docs/pokelife/MODULES/MINT.md 16.2`.

### MintEvent

Documented in `docs/pokelife/MODULES/MINT.md 16.3`.

### MintEvent.streak

Documented in `docs/pokelife/MODULES/MINT.md 16.2A`.

### BaseKind

Documented in `docs/pokelife/MODULES/BASE.md 9.2`.

### BaseEvent

Documented in `docs/pokelife/MODULES/BASE.md 9.4`.

### LinkMoment

Documented in `docs/pokelife/MODULES/LINK.md 9.2`.

### LinkEvent

Documented in `docs/pokelife/MODULES/LINK.md 4.2`.

### ModuleDrops

Documented in `docs/economy.md Module drops (`ModuleDrops`)`.

### ModuleDrops.COIN_HANDFUL

`ModuleDrops.COIN_HANDFUL` makes a Gimmighoul Coin drop pay 10 at a time, because Gimmighoul needs 999 coins to evolve.

### ModuleDrops.add

`ModuleDrops.add` adds a module's drop roll to an outcome; an item that would exceed the Bag limit is converted to Poké, as everywhere else in the engine.

### RewardEngine purity

`RewardEngine` is pure: the same state, event and random source always give the same outcome, so a result can be replayed or previewed. Daily limits and idempotency are the caller's responsibility, not the engine's.

### NEW_SPECIES_CANDIES

Documented in `docs/economy.md First of a species`.

### RewardEngine.MODULE_POKE

`RewardEngine.MODULE_POKE` pays 10 Poké for each deliberate paid moment of a life module (rules version 26, owner 14 November 2026: the economy was stingy outside World). Care Play, Heal's step and import payments and a befriended visitor pay none, as they drop no item either.

### UNCAUGHT_WEIGHT

Documented in `docs/design.md Soft pity`.

### FOCUS_CANDY_PERCENT

Documented in `docs/economy.md Task reward focus`.

### EGG_FOCUS_POINT_FACTOR

Documented in `docs/economy.md Task reward focus`.

### ITEMS_FOCUS_DROP_FACTOR

Documented in `docs/economy.md Task reward focus`.

### BEFRIEND constants

Documented in `docs/economy.md Care: befriend a visitor`.

### HEAL_TRAINER_POINTS

`RewardEngine.HEAL_TRAINER_POINTS` pays trainer points only for a logged activity and the first drink of the day; steps and record imports pay candies (and steps friendship) only, so Heal's trainer points stay at most 4 a day.

### MINT_TRAINER_POINTS

`RewardEngine.MINT_TRAINER_POINTS` pays no trainer point for a logged record (only a reconciled account and a review do), so Mint's trainer points stay at most 3 a day.

### LINK_EGG_POINTS

Documented in `docs/pokelife/MODULES/LINK.md 9.3`.

### RewardEngine.befriend

Documented in `docs/economy.md Care: befriend a visitor`.

### egg lead assignment

When a hatched or recruited Pokémon joins a module's party, the lead the party already has (chosen, or implicitly its first member) keeps leading; a newcomer becomes lead only of an empty party. This fixed a reported bug where each new Pokémon took over the lead. The same rule applies to every module's egg and outbreak catch.

### tutorialEgg

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 10.2.1`.

### RewardEngine module eggs

Care, Field, Ledger, Habitat and Postcard Eggs are rolled like a Task Egg from the module's region with the ordinary shiny odds and IVs: a module egg fills the Pokédex, it does not make a better Pokémon.

### RewardEngine.heal item drops

Only deliberate Heal moments (an activity, the first drink) can drop an item. Step payments (up to five a day) and imports (up to three a day) would otherwise drop one most days.

### MINT_OUTBREAK_RARITY

Documented in `docs/economy.md Mint: an outbreak`.

### mintOutbreak

Documented in `docs/pokelife/MODULES/MINT.md 16.2A`.

### claimMintOutbreak

Documented in `docs/pokelife/MODULES/MINT.md 16.2A`.

### safariVisit

Documented in `docs/pokelife/MODULES/LINK.md 9.5`.

## `pokemon-core/src/commonMain/kotlin/app/pokeplan/core/reward/RewardRules.kt`

### RewardRules.VERSION

Documented in `docs/economy.md Keep it true`.

## `pokemon-core/src/commonMain/kotlin/app/pokeplan/core/reward/Safari.kt`

### SafariEvent

Documented in `docs/pokelife/MODULES/LINK.md 9.5`.

### SafariRules

Documented in `docs/pokelife/MODULES/LINK.md 9.5`.

### SafariRules.PEOPLE_CAP

`SafariRules.PEOPLE_CAP` must equal `LinkRules.SAFARI_PEOPLE_CAP` (20): the reward module and LINK cannot share a constant across the module boundary, so a test (`LinkSafariTest`) keeps them equal.

### SafariRules.seed

`SafariRules.seed` hashes a person's opaque ref with FNV-1a over its characters into a 64-bit seed, the same on every platform (the literal -3750763034362895579 is 0xcbf29ce484222325, FNV's offset basis). `String.hashCode` is not used because it is not stable across platforms.

### SafariRules.seed literal

The signed Long literal in `SafariRules.seed` is the FNV-1a 64-bit offset basis 0xcbf29ce484222325.

### SafariRules.pool

Documented in `docs/pokelife/MODULES/LINK.md 9.5`.

## `pokemon-core/src/commonMain/kotlin/app/pokeplan/core/reward/TownFinds.kt`

### TownFinds

Documented in `docs/pokelife/FEATURES/LAUNCHER.md 6.4`.

## `pokemon-core/src/commonMain/kotlin/app/pokeplan/core/reward/TrainerRank.kt`

### TrainerRank.Feature

Documented in `docs/design.md ADHD-friendly design (lowkey)`.

### TrainerRank.thresholds

`TrainerRank.thresholds` are quick at first: a typical player reaches Apprentice on day 2 and Ace in under two weeks, so the feature reveal never makes anyone wait; later ranks are slower, for the Guardians and quests. Values are starting numbers (tune).

### TrainerRank.unlockedRegions

The games' regions (Kanto and the others) are retired: every released region is open at any rank, and eggs go to the module regions, with the Plan Region as the default.

## `pokemon-core/src/commonMain/kotlin/app/pokeplan/core/reward/VisitorInheritance.kt`

### VisitorInheritance

Documented in `docs/pokelife/MODULES/CARE.md 6.6`.

## `pokemon-core/src/commonTest/kotlin/app/pokeplan/core/reward/CareRewardTest.kt`

### careState test helper

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 2.2.1`.

## `pokemon-core/src/commonTest/kotlin/app/pokeplan/core/reward/RewardFocusTest.kt`

### RewardFocusTest

Documented in `docs/economy.md Task reward focus`.

### RewardFocusTest.candies

In an achievement outcome the task's own candy rolls come before its Poké reward; a hatched Task Egg pays its new-species candies after them, at any reward focus.

## `pokemon-core/src/jvmMain/kotlin/app/pokeplan/core/tools/CurationReport.kt`

### CurationReport

Documented in `docs/code-map.md pokemon-core`.

## `pokemon-core/src/jvmTest/kotlin/app/pokeplan/core/reward/GoldenVectorTest.kt`

### GoldenVectorTest

`golden.json` holds one fixed input and the exact JSON the reward engine must give for it, so any change to the reward rules is deliberate. After a deliberate rule change regenerate it with `UPDATE_GOLDEN=1 ./gradlew :pokemon-core:jvmTest --tests "*GoldenVectorTest*"`; new outcomes need a new `RewardRules.VERSION`, and regenerating refuses to write them under the version already in the file.
