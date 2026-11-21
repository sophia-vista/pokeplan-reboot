# PokéLife design (rules the code implements)

> **Evidence, not authority.** The source of truth is `docs/pokelife/` (start at `docs/pokelife/README.md`); this file records the rules the code implements today, and where they differ `docs/pokelife/` wins. PokéLife is local-only with Android priority. The buddy and perks follow the four Pokémon roles in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md` section 2.2 (global buddy; a party of one lead and five per module, with bonuses for that module only; one Pokémon, one place; see "Buddy and perks" below).

A fun productivity, growth and self-improvement app for Pokémon lovers: your Pokémon grow as you do. This is the product and game design for the multiplatform rewrite. Numbers marked *(tune)* are starting values to be settled by simulation before release. Everything not marked is decided.

## Pillars

1. **Strict focus.** Leaving the app during a session fails the egg. No loopholes.
2. **More tasks, more productive.** The economy rewards finishing many real tasks, not gaming one big one.
3. **Your partner matters.** A well-raised older Pokémon stays worth using; new hatches never make it obsolete.
4. **Nostalgic pixel.** Gen 3 to 5 look and feel (GBA/DS era), cute and readable.
5. **Gentle by design.** It should quietly help people with ADHD without ever saying so: low friction to start and capture, instant rewards, no shame (see ADHD-friendly design). Strictness applies to distractions, never to the person.
6. **Growth you can see.** Levelling and evolution mirror real progress: goals and habits feed the same loop as tasks and focus, and the app shows how you are growing.

## Core loop

```
        focus session ──► egg hatches ──► new Pokémon (stats, nature, ability, shiny?)
             ▲                                   │ duplicates ──► transfer ──► Rare Candies
             │                                   ▼
   buddy perks (type / ability)  ◄────  partner raised with candies, items, Vitamins
             ▲                                   ▲
             └──────── finished tasks ───────────┘  (candies, items, Vitamins for EVs)
```

Focus time brings new Pokémon. Finished tasks make Pokémon stronger. The buddy's perks feed back into both.

## Focus

- Minimum session 5 minutes.
- **Deep Focus:** the phone must stay still (accelerometer, as in the old app); picking it up or moving it fails the session. In exchange the egg odds go up one band (see `Egg` in `:pokemon-core`).
- **Perks are locked when the session starts:** egg-luck and shiny perks use the buddy at the moment start is pressed, so swapping later does not change that egg. Task perks use the buddy at completion time.
- **Strict mode, always on.** Leaving the app (switching to another app or the home screen) starts a **10 second grace period**. Returning in time keeps the session, and so does a timer that runs out before the grace does (time inside the grace counts as focus); otherwise the egg is lost, and a failed session does not count toward the streak. The only pauses are the daily emergency pause and phone calls (below).
- **The focus timer is phone-only.** Desktop and web are companions (see Platforms).
- **Locking the screen does not count as leaving.** The session continues with the screen off; unlocking and opening another app starts the grace period. Face-down on the desk is the intended way to focus. On Android, when the app goes to the background it checks whether the screen is off or locked; a lock reported just after the leave cancels the grace period.
- **A failed session costs only the egg** (plus the streak rules). The buddy's friendship is never reduced.
- **Phone calls pause the timer** (incoming or ongoing); it resumes when the call ends. Android reads this from the audio mode (ringing, in a call, call screening), which needs no permission; app voice chats do not count.
- **The session is watched on every tab**, not only on Focus: leaving the app or moving the phone in Deep Focus counts wherever the player is.
- **Focused time is what counts:** the running timer shows focused minutes, and paused minutes apart from them. The setup screen lists these rules in plain words.
- **Two emergency pauses per day**, up to 3 minutes each, started by the player before leaving. Anything else that takes the player out of the app fails the egg after the grace period.
- **No-blame recovery:** a failed session shows a neutral screen ("That egg went cold. Try again?") with a one-tap 5 minute retry. No red, no scolding.
- Sweet spot is built in: Super Rares per focus-hour peak at about 60 minutes.

## Tasks

- **Difficulty rank E, D, C, B, A**. The player picks how hard a task is; **higher rank pays more, A the most**. Urgency comes from the due date, not the rank. Default rank is C. **S rank is not selectable**: it is reserved for special missions (legendary quest steps, weekly challenges). Reward tables are already ported (`CandyRewards`, indexed 1 to 5). **Every paid task gives Rare Candies** (E 6–10 up to A 14–40; exact table in `docs/economy.md`); a **Super Candy bonus** comes on top with a chance by rank (E 10%, D 15%, C 20%, B 30%, A 40%, raised by the Super Candy chance perk) *(tune)*. The original app paid one or the other.
- **Minimum task age** scales with the difficulty rank *(tune)*: **E 2 minutes, D 5, C 10, B 15, A 20**, counted from creation. A one-off task only pays after that, so tiny tasks done right away still pay, but big ones cannot be created and instantly ticked. Younger tasks complete without a reward. There is no daily cap, so many real tasks always pay.
- **Recurring tasks are exempt:** their instances are created ahead of time by the schedule, so they pay when their day comes.
- On-time bonus: finishing before the due date adds a bonus *(tune)*. **Overdue tasks pay the normal reward**, just without the bonus, so old tasks get finished rather than abandoned.
- **Subtasks** pay a flat 1 Rare Candy each *(tune)*, for up to 5 subtasks per task. The parent pays in full when it is finished. The minimum age applies to subtasks too.
- **Undo:** completing a task shows its reward and grants it after a 10 second undo window; undo cancels it. After that completion is final.
- **No categories:** tasks, habits, goals and lists have no category. A list is the only way to group: the Insights wheel and the Review recap split by list, the weekly recap names the top list, quick-add `#word` is always a list, and the calendar and widgets take colour and default icon from the task's list. Nothing here changes rewards.

## ADHD-friendly design (lowkey)

The app never mentions ADHD; it just removes friction and shame.

- **Low friction to start:** one-tap capture into the Inbox (widget, share sheet, quick-add), a **"Just 5 minutes"** button on any task that starts a minimum-length focus session on it, short sessions always welcome.
- **Pick for me:** a button on Plan's Next up action that picks a random task from today's list; finishing that task pays candies × 1.10 *(tune)*, for when choosing is the hard part.
- **Time awareness:** a task you focused on shows **estimate vs actual** time in its line ("focused 35 of 30 min"), and Growth says how your estimates run once 3 tasks have both.
- **Gradual reveal:** day one is tasks, focus, the buddy and the collection only. Other systems appear as the trainer rank rises, and the early ranks come within days (`Feature` in `TrainerRank.kt`; a "Rank up" card on Home lists what each rank opened, once). Only features that exist are listed:

| Rank | Typical day | Appears |
|---|---|---|
| Rookie (start) | 1 | tasks, habits, planner, focus and eggs, buddy, party and PC, Pokédex, Rare Candy |
| Apprentice | 2 | Super Candy and evolution, the Bag, goals, the region picker (Plan or Care Region) |
| Trainer | 5 | held items, party perks (Plan's and Care's alike), the Growth screen, Poké |
| Ace | 11 | Nature Mints, Bottle Caps, Ability Capsules and Tera Shards; stats and the IV judge |
| Veteran | 22 | Guardians and legendary quests |

  Items still drop from the first day; the Bag shows them from Apprentice. Shop, Lures, contests, perfect dex and Hall of Fame join this table when they are built.

- **No shame:** warm, neutral copy; overdue is shown calmly (no red alarm styling); streaks forgive (see Retention); failing a focus session is recoverable in one tap.
- **Few choices:** every form has sensible defaults (rank C, 25 minute session) so the first screen asks for as little as possible.
- **Reminders that persist gently:** up to 3 repeats (10 minutes apart) until acknowledged, then stop; the chain ends as soon as the notification is answered, tapped or swiped away. *(shipped, Android)*
- **Considered, not included for now:** routines (recurring checklists with timers) and a one-tap "reschedule all overdue". Both can be added later.

## Growth: goals, habits, insights

- **Goals with milestones.** A goal ("Learn guitar", "Run a 10k") has an optional target date, ordered **milestones**, and linked tasks, habits, lists and focus time, with a progress bar. **Finishing a milestone grants a Super Candy** *(tune)*, so a real achievement evolves a Pokémon; finishing the goal grants a bonus item. Anti-farming: a milestone must exist for at least a day before it pays, and milestone rewards are capped per day *(tune: 3 Super Candies)*.
- **Habits are a recurring-task type** (no frequency targets). Each habit has its own **forgiving streak** and a simple completion history. Completions pay like recurring tasks (once per day per habit). Habits appear in Today and in the widget.
- **No weekly review.** Notes and reflection are private; not therapy, no diagnosis, no scores about the person.
- **Growth screen** (under Me): a **balance wheel** across the lists (tasks finished and focus time, last 7 / 30 / 90 days), trends for focus hours and tasks per week, estimate accuracy, goal progress, habit streaks and collection layers. Every metric states what it counts and over which days, and the trainer card says "all time" and how the streak counts.

## Planner (v1)

In scope for v1: tasks with difficulty rank, planned date, due date/time, estimated duration and reminders; **recurring tasks** (daily, weekly, monthly, custom) and **habits**; **goals** with milestones; **subtasks**; an **own calendar** (month, week, day and agenda views) with a **drag-and-drop day / week planner** (time-blocking), which also feeds the agenda widget; **user-created lists** (projects); and **read-only import of device calendar events** on Android (needs the calendar permission; shown on the calendar and agenda, never paid as tasks).

### Task model, Today and focus link

- **Fields:** title, notes, difficulty rank, optional list, **planned date** (when you intend to do it), **due date/time** (the deadline), estimated duration (optional), recurrence, reminders, subtasks.
- **Views:** Plan has three places, **Tasks**, **Calendar** and **Focus**. Tasks is one workspace that opens on **Today** (planned today + due today + overdue + nudges), with the views **Today**, **Upcoming**, **All** (open tasks in no list show under "No list") and **Done**; the Filter on All and Done has a List chooser. Goals and Settings are the other places in the Sections key, and Plan progress is listed on Me.
- **Focus on this task:** a session can be started from a task. The minutes are logged on it, and it shows its total focused time. Finishing a task you focused on gives a small bonus *(tune: +10% candies)*. Egg odds are unchanged. Sessions can also be started without a task.

## Pokémon

Each owned Pokémon has: species, level 1 to 100, nature, ability, 6 IVs, 6 EVs, held item, friendship, shiny flag, nickname.

- **Stats** use the Gen 3+ formula:
  - HP = ⌊(2·Base + IV + ⌊EV/4⌋) · Level / 100⌋ + Level + 10
  - Other = ⌊(⌊(2·Base + IV + ⌊EV/4⌋) · Level / 100⌋ + 5) · Nature⌋, with nature ×1.1 / ×0.9
  - IV 0 to 31 rolled at hatch. EV 0 to 252 per stat, 510 total, gained from Vitamins (below).
- **Natures:** the existing 25. Each raises one stat and lowers another (five are neutral).
- **Abilities:** each species has one or two; one is rolled at hatch. Abilities give buddy perks (below).
- **Shiny:** rolled at hatch, base chance **1 in 128** per egg *(owner)*, with a small bonus for longer sessions. Shinies get a distinct sprite palette and a hatch animation (a gold flash, twice, instead of the white one).
- **Levelling:** Rare Candies per level grow with the level, the shape of the games' Medium Fast experience group: **2 + level² / 300**, rounded down *(tune)* (2 a level up to level 17, 5 at 30, 10 at 50, 34 at 99; 30 candies from level 1 to 16, 106 to 36, 1,249 to 100). A Pokémon more than 5 levels below your partner needs half the candies, rounded up *(tune)*, so new hatches catch up without outshining the partner.
- **Evolution:** Super Candy at the species' game evolution level (Charmander at 16). Evolution stones for the species that use them.

### Evolution

Every evolution is reachable solo. Each game trigger maps to an in-app one:

| Game trigger | In the app |
|---|---|
| Level | Super Candy once the Pokémon reaches **the species' game level** (Charmander at 16, Charmeleon at 36); there are no battles, so levels only pace progress, and the level curve above keeps that pace |
| Stone / item | the evolution item |
| Trade, trade with an item | a **Link Cord** item (holding the item where the game needs one) |
| Friendship (with or without day / night) | friendship at 220 or more, then a Super Candy, at the right time of day where it matters |
| Level plus time of day | Super Candy used between 06:00 and 18:00 (day) or otherwise (night), local time |
| Gender-dependent | uses the Pokémon's gender |
| Special conditions, checked on the spot | a known move (Mime Jr., Tangela, Yanma...), a held item at a time of day (Happiny's Oval Stone, Sneasel's Razor Claw, Gligar's Razor Fang; used up), the party (Mantyke with a Remoraid, Pancham with a Dark type in its own module party), Attack against Defense (Tyrogue), the nature (Toxel), a Fairy move and friendship (Sylveon), 400 Exp. Candies (Meltan), 999 Gimmighoul Coins (Gimmighoul), a Link Cord (Finizen); then a Super Candy unless it spends instead |
| Special conditions done with the phone | Inkay at 30: hold the phone upside down for 1.5 s (accelerometer); Milcery holding a Strawberry Sweet in the day: a full turn with the phone flat in under 5 s (gyroscope); Pawmo, Bramblin, Rellor: 1,000 steps on the phone's step counter (asks for the activity permission; counts between openings), or a World expedition of 5 floors with it on the team. Kept per Pokémon (`AppData.completedTrials`) |
| Special conditions that need a battle, a place or the weather | an **Evolution Trial in World** (`EvolutionTrialSites`): a team with the Pokémon (at the level it needs) reaches a given floor of a dungeon in the evolution's own region: Galarian forms in Galar, Hisuian forms and the strong and agile style moves in Hisui, Alolan Marowak in Alola, Goodra in Kalos's rain, Annihilape and Kingambit in Paldea, Shedinja in Hoenn. Kept per Pokémon (`WorldData.evolutionTrials`) |
| Everstone (held) | the Pokémon does not evolve: every option says so and Evolve is off |
| Evolve | asks "Evolve X into Y?" with Cancel; the evolution is made at the end of the scene, and Stop during it cancels with nothing used up |
| Branching (Eevee, Tyrogue, Poliwhirl…) | the trigger picks the branch |
| Random branch (Wurmple) | decided when it hatches, as in the games |

The data pipeline classifies every evolution into one of these.

### EVs (no training plans)

Tasks give no EVs (no training plans: picking focus stats was one more choice for little play). EVs come only from **Vitamins** (+10 in one stat, used from the Bag), which drop from paid tasks; perks still scale with the stat behind each bucket, so a Vitamin on the buddy is the way to raise a perk.

### Items (so a mid partner is not overshadowed)

| Item | Effect | Source |
|---|---|---|
| Rare Candy (shown as Exp. Candy; the id is `rare-candy`), Super Candy | level up, evolve | task rewards |
| Vitamins (HP Up, Protein, Iron, Calcium, Zinc, Carbos) | +10 EV in one stat | task drops, streak rewards |
| Nature Mint | change the nature | rare drop, badge reward |
| Ability Capsule | swap to the other ability | rare drop |
| Bottle Cap | raise one IV to 31 *(tune)* | rare drop, Pokédex milestones |
| Held items (e.g. Leftovers, Choice Band style) | one small buddy perk | drops, shop |
| Tera Shard (one per type) | set a Pokémon's Tera type; its type perk then uses it | shop, rare drops |
| Evolution stones | evolve stone species | drops |
| Link Cord | trade-style evolutions without a trade | shop |
| Ability Patch | switch to the hidden ability | shop (expensive), rare drop |
| Berries | feed one for a little friendship; also a mini-quest reward and the ingredient for Lures | drops from paid tasks, focus sessions, mini-quests |
| Lure (by type) | bias the next hatch toward a type | crafted from berries, shop |
| Form items (Arceus plates, Silvally memories, Rotom appliances, Griseous Orb, DNA splicers, etc.) | change a species' form | shop, drops (generated from species data) |

**Sources (both):** common items drop at random from paid tasks (chance by rank) and come as streak, badge and Pokédex milestone rewards. Rare items (Nature Mint, Ability Capsule, Bottle Cap, Ability Patch, Link Cord, Tera Shards, form items, stones) are bought in a shop with **Poké** (the one currency, formerly coins) earned from play. **Poké** is earned from paid tasks (by rank), completed focus sessions (by minutes), the life modules and transferring duplicates, plus streak and badge bonuses; amounts are in `docs/economy.md`. Nothing is sold for real money. The Poké Mart (Pokémon tab) sells Exp. Candy, Super Candy and berries, and the Kecleon Shop (World) every stone, the Link Cord, the Everstone, Gimmighoul Coins and a daily shelf of the rest, at the prices in `docs/economy.md`; items also drop from paid tasks and the life modules.

### Duplicates → candy

- Transfer any Pokémon you do not need for Rare Candies. Yields by rarity and rank are in `docs/economy.md` *(tune)*. Perfect-IV or shiny ones show a warning first.
- You cannot transfer your buddy or your last party member, and **legendaries and mythicals cannot be transferred**. Transferring the only copy of a species keeps its Pokédex entry: caught is permanent.
- This is the sink for hatches, so the PC never needs to be a graveyard.

## Roster

**Every species through Gen 9 (about 1,025) with all alternate forms**: regional (Alolan, Galarian, Hisuian, Paldean), gender differences, Mega / Gigantamax, and cosmetic forms (Unown, Vivillon, Alcremie, etc.). The 150-species list in `:pokemon-core` became the **Plan Region** (below); the rest comes from generated data.

- **Mega / Gigantamax are a temporary focus form.** A buddy holding its Mega Stone (or Dynamax Band for G-Max species) shows that form for the length of a focus session, with a bonus to that session's perk *(tune)*, then reverts. The stones are reusable key items, one per species, from the shop or rare drops.
- **Form dex**: each caught species' Pokédex entry lists all its forms, with a Regular / Shiny switch. A form counts once the player has had a Pokémon in it, as a regular and separately as a shiny: its own form (default or regional), the species' female form for a female (Meowstic, Indeedee, Basculegion, Oinkologne), and a Mega form while it holds that Mega Stone. Forms had stay after a transfer. Forms not had yet are the real sprite in black; Gigantamax and the other alternate forms (Rotom's appliances, Deoxys' formes...) cannot be had yet, so they are shown faded and not counted. Tapping a form the player has had shows it in the entry's scene, with its types, abilities and stats. Saves from before the form dex count a caught species' default form (and a Shiny Dex species' default shiny). Client-side data in the save like the Shiny Dex (`AppData.caughtForms`, `shinyForms`, rules in `FormDex`); the region's list shows its form total. Cosmetic forms (no sprites of their own) are not in it.
- **Cosmetic forms are a side collection** with their own progress bar and badge. 100% Pokédex means one of each species plus regional forms; cosmetic forms still appear when hatching.
- **Rarity by rule** from game data (implemented in `HatchRules`): a species' rarity is the strength of its whole evolution line (the highest base stat total of any default form in it). Lines at 580 or more (the pseudo-legendary families, plus Slakoth and Duraludon) are Super Rare; the rest are ranked and split 30% Common, 40% Uncommon, 30% Rare *(tune)*. Every member of a line shares its rarity. **Legendaries, mythicals, Ultra Beasts and Paradox Pokémon are not in any egg pool** (see below). Ultra Beasts and Paradox Pokémon are not flagged legendary in the source data, so they are listed explicitly *(proposal)*. Hand overrides are not planned, and the rule applies to the module regions too. **Every region must have at least one hatchable species in every rarity tier**; a data test fails the build otherwise.
- **Art:** the PMD SpriteCollab sprites are preferred (see "Look and feel" and `docs/assets.md`); the fallback is the sprites in the public PokeAPI/sprites repository: 96×96 pixel PNGs, front and shiny, for every form (`assets/sprites`, fetched by `tools/sprites/fetch.py`; 2,684 files, about 5 MB). Gen 6 to 9 sprites will not match the Gen 3 to 5 pixel look of the rest, which is accepted for now. Animated Gen 5 sprites can be added later for the species that have them. The same IP caveat as the rest of the project applies (see Risks), and the sprites' own terms should be checked before any public release.

### Legendaries and mythicals

Not hatched from random eggs, which keeps them special and avoids a 4 hour Deep Focus egg being 70% legendary.

- **Legendary quests:** each region has quests for its legendaries (the games' legendaries for that region). A quest unlocks at a trainer rank plus a share of that regional dex *(tune: about 80%)* and asks for a long strict focus session (Deep Focus) and a set number of paid tasks *(tune)*, with no deadline (see "Implemented" below). Finishing it grants that specific species (as a Guardian candidate; a Legendary Egg with its own hatch is not built yet). There is no extra pacing: the requirements set the pace.
- **Mythical rewards:** *(superseded)* the first idea was one mythical per milestone (long streaks, task totals, top rank, full regional dex). What shipped instead is a long-term quest per mythical (below).
- **Every legendary and mythical has a quest** *(decided by the owner; the ones World gives through its own region quests, Kanto, Johto and Hoenn's main legendaries and Mew, have no Plan quest, `Legends.coveredSpecies`; the rest keep theirs until a region or module gives them)*. Mythicals with no in-game encounter get **long-term quests**: available fairly early, but slow to finish (weeks of steady use, not a single push). Legendaries in the Plan Region are tied to its lore: the lake trio (Uxie, Mesprit, Azelf) stand for the core self-improvement ideas of the app, so their quests are written around those ideas (knowledge, emotion, willpower) rather than generic task counts. **Implemented (first pass, *(tune)*):** every legendary-class and mythical species has a quest, generated by rule with the lake trio hand-written.
  - *Legendaries:* three requirements counted from when the quest is taken (Deep Focus sessions, paid tasks of rank B or A, active days), scaled by the strength of the species (tier 1, 2 or 3 at rank 4, 6 or 8) and unlocked by catching a share of their region's ordinary species (50%, 60%, 70%). Uxie, Mesprit and Azelf are written around knowledge (focus sessions, goal milestones), emotion (habit days, active days) and willpower (Deep Focus, hard tasks, a 7 day streak), all at rank 4.
  - *Mythicals:* long-term quests from rank 2: 45 active days, 120 paid tasks, 1,200 focus minutes.
  - Up to three quests at a time, no deadlines. Finishing one lets the player welcome the Pokémon (the button says "Welcome <name>"), which joins the collection as a Guardian candidate. Progress is derived from the player's history, so nothing extra is tracked.
  - The 26 quest-only species in the Plan list are confirmed as quest-only.
- **Species with a tradition of being obtainable in the wild** (the pseudo-legendary lines, Absol, etc.) stay in the egg pools, in the Super Rare tier by the rule.

### Regions

- The **Plan Region** (`Region.PLAN`, stored as `HOME`) is a curated list kept in `tools/species/regions/plan.txt` (one name per line; edit it and run the generator). It is one of six module lists that together hold every one of the 1025 species exactly once (**Species lists**, below); it has 179 species in 68 hatchable lines plus 22 quest and milestone species. The dex is meant to be completed in about 2 months at about 5 eggs a day; the economy simulator checks that pace. Its rarities are re-derived by the rarity rule like every other region.
- **Species lists**: the module regions are Plan, Care, Heal, Mint, Base and Link (`tools/species/regions/plan.txt`, `care.txt`, `heal.txt`, `mint.txt`, `base.txt`, `link.txt`). **No species is in two lists and none is left out**; a line (every stage of an evolution family) is never split; each list has about 65 to 75 hatchable lines, a hatchable line of every rarity and exactly two of the 12 Super Rare lines. Each list follows a mainline regional dex: its starters first (Plan: Unova and Paldea, Care: Kanto, Heal: Hoenn and Galar with Litten, Mint: Sinnoh, Base: Johto, Link: Kalos), then the lines by rarity in National Dex order with each stage beside its pre-evolution, then the ultra beasts, paradox, legendary and mythical species, and **its patrons last**: Uxie, Mesprit and Azelf (Plan), Cresselia and Shaymin (Care), Raikou, Entei and Suicune (Heal), Diancie and Melmetal (Mint), Tapu Bulu and Ogerpon (Base), Latias and Latios (Link). Themes: Plan everyday route Pokémon, workers and thinkers; Care soft Fairy, Grass, Water and Normal; Heal Fighting, Fire, Water and fast runners; Mint Steel, Rock, Ground, fossils, gems and hoarders; Base kitchen, garden, hearth, fridge, closet and pets; Link postal birds, loyal dogs, pairs, singers and gift-givers. All six are released with their modules (`Region.released`; `Region.BASE` is stored as `HOUSEHOLD` and `Region.PLAN` as `HOME`). To change a list, edit it, run `python3 -I tools/species/generate.py` and `./gradlew :pokemon-core:jvmTest`, and keep the numbers in this paragraph true.
- **Module regions** *(owner's decision, October 2026)*: each life module gets its own curated region of about 150 species, named "<Module> Region". The Home Region became the **Plan Region**, and "Home Region" passed to the household module, now the Base Region (`Region.BASE`, stored as `HOUSEHOLD`); no species is in two module regions. Module regions are open to everyone from the start, once their module ships. The game regions below left the Plan module for World (they are no longer collected, below). Details and draft themes: docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md, "Module regions".
- **Only the module regions are collected** *(owner)*. The games' regional dexes (Kanto, Johto, Hoenn, Sinnoh, Unova, Kalos, Alola, Galar, Paldea) are not collected: they stay in the generated data and in `Region` (never released, `Region.open` sends them to the Plan Region) only so old saves, sessions and events still load. Pokémon already hatched from them are kept, and the National Dex tab still lists every species.
- A Pokémon has one record with a **National Dex number**; a region is just a list of those. Regional forms hatch in their home region (Alolan forms in Alola, and so on).
- **Soft pity:** within a rarity, species the player has not caught yet are 6 times as likely to be picked (`UNCAUGHT_WEIGHT`; rarity odds are unchanged), so the last few species do not take forever. "Caught" is permanent, even after a transfer.
- **Where a region's eggs start:** a species can hatch in a region if its pre-evolution is not in that region's dex (a region that has Pikachu but not Pichu can hatch Pikachu), so no region depends on another to be completed. Hisui's dex is merged into Sinnoh, and Hisuian forms hatch there.
- **Region picker:** `AppData.settings.region` (default the Plan Region) is the region focus and Task Eggs hatch from, and eggs hatch from that region's pool only; each module's own eggs come from its own region. No screen sets it in the current code (found 20 November 2026; the Settings picker this paragraph used to describe is gone). Each region has its own dex progress bar.
- Mega / Gigantamax forms have no region source yet *(proposal)*.

## Pacing and collection layers

Collecting is **fast at the start and deep at the end.** The simulator tunes toward these targets (at roughly 5 eggs and 5 tasks a day):

- **Easy to fill, hard to perfect** *(owner, October 2026)*: completing a Pokédex should be easy, with more Pokémon and Rare Candies a day in every region; what stays hard is a good version of a Pokémon (a shiny, high IVs, the right nature).
- **Basic dex of the Plan Region (every species caught, including evolutions): about 6 weeks** *(tune)*. Every module region should give roughly as many Pokémon as Plan for comparable use (Care Eggs, docs/economy.md).
- **Checked by simulation** (`docs/economy-report.md`, regenerate with `./gradlew :plan-core:simulateEconomy`): a typical player (6 days a week, about 5 tasks and 2 sessions a day) completes the basic Plan dex in about 6 weeks and evolves the whole Plan Region in weeks, not months. The simulator does not play Care, mini-quests or the shop, which add more.
- **The long tail** (months to years) is made of layers, each with its own progress, badge and showcase:

| Layer | Goal |
|---|---|
| Basic dex | one of each species caught |
| Form dex | regional, gendered and Mega / G-Max forms; cosmetic forms are the side collection |
| Shiny dex | a shiny of each species (1 in 128 per egg makes this the longest chase) |
| Perfect dex | a "perfect build" of each species: 31 IVs, an EV spread of 252 / 252 / 6, a favourable nature, a chosen ability. Competitive-style building, and the endgame use for Nature Mints, Bottle Caps, Vitamins and Ability Capsules |
| Ribbons | contest ribbons, legendary quests, mythical milestones |

Perfect builds are shown on a Pokémon's page and in the Hall of Fame; they have no gameplay effect beyond perks.

## Spin-off inspired features

Planned, with build order to be set later.

- **Mission board (Mystery Dungeon):** tasks are shown as a job board using their difficulty rank. Presentation only; rewards stay as designed.
- **Trainer rank (Mystery Dungeon guild ranks):** one rank for the whole save, earned in every module: 2 points per tier of finished work (so 2 for an E task up to 10 for an A task) and 1 point per 5 minutes of effort. Ten ranks (Rookie, Apprentice, Trainer, Ace, Veteran, Expert, Elite, Champion, Master, Legend) (thresholds in `docs/economy.md`): **quick at first, slower after** (owner's direction). Each rank takes longer than the one before. Each rank reveals features (see "Gradual reveal"). Names and thresholds are *(tune)*; see `docs/economy-report.md` for the days.
- **No personality quiz starter:** onboarding stays a starter picker.
- **Poké Assist perks (Pokémon Ranger):** the buddy's type and ability perks are presented as assists. No extra mechanics.
- **Secret Base and photo mode (Secret Base, Pokémon-Amie, Snap):** decorate the buddy's home scene (with a berry garden) and take snapshots of the buddy or a hatch. After v1.
- **Focus view for v1 is the egg only** (calm and distraction-free). An expedition view was considered and left out.

## Buddy and perks

**One Pokémon, one place (owner, 7 November 2026; `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md` section 2.2).** A Pokémon is the buddy, or in one module's party, or a slotted Guardian, or on a World team, or a GEAR helper, or in the PC: never two. Putting it somewhere takes it from where it was (the previous buddy goes to the PC), except that a team out on an expedition cannot be moved. The starter is the buddy and starts in no party (the first Pokémon to hatch joins Plan's party). `Places` derives the place and `GameStore` vacates before every assignment; a save with a Pokémon in two places is resolved once on load, keeping the highest of Guardian, World team, buddy, party, helper (the starter stays the buddy) and giving the buddy to the best Pokémon in the PC if it was lost. Each module with an acquisition method gives its first Pokémon once, with the module's first real record (`GameStore.firstRecordEgg`, owner 13 November 2026; it replaced the 5-second demos). The "Keep party Pokémon out of World" setting is gone (it is always so). A GEAR tool shows a helper only if the player chose one (or a Ranger loaner).

The buddy can be **swapped freely at any time**. Perks apply when a reward is earned, so a player can pick an Egg luck buddy before a session. Friendship is per Pokémon, which gives a reason to stick with one.

**The buddy and the party leads are separate roles** (`docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md` section 2.2): the buddy is the global companion and is cosmetic; the perks described below come from **Plan's party lead** (slot 1) and its party, and are chosen separately ("Make Plan lead").

The **buddy** lives on the home screen and widget, reacts to real events only (sleeps during focus, one emote when a task pays or an egg hatches; it never droops, nags or shows a mood, as CLAUDE.md and `BuddyMood.kt` say), and gives perks that scale with its level and friendship.

- **Type perk** (one per type, from the buddy's primary type; second type gives half strength *(tune)*). Starting mapping:

| Perk bucket | Types | Effect |
|---|---|---|
| Egg luck | Normal, Flying, Fairy | better rarity odds on hatches |
| Candy yield | Fighting, Rock, Ground, Steel | more Rare Candies from tasks |
| Super Candy chance | Psychic, Dragon, Ghost | higher chance of Super Candy |
| Item finder | Grass, Bug, Dark | chance of an item drop per task |
| Shiny hunter | Electric, Ice | small shiny chance bonus |
| Streak keeper | Water, Fire, Poison | a second missed day forgiven per week (on top of the free one) |

- **Ability perk:** one extra, from the buddy's ability. Real abilities as flavour, data driven. Examples: Pickup (extra item chance), Early Bird (bonus for the first task before 9am), Technician (bonus for 5 to 20 minute sessions), Magic Guard (one failed session a week keeps its egg), Guts (bonus on A-rank tasks), Synchronize (+3% Super Candy chance).
- **Stats matter:** perk strength scales with the relevant stat (e.g. Attack for candy yield) and nature, so raising and training a partner pays.
- **Caps:** total bonus from perks, held item and party assists stays below +25% *(tune)* so no single buddy breaks the economy.
- **Shown, not hidden:** the Party screen has a **Party perks** card (each bucket's total, and opened, every part of it: the buddy's types, its ability, its held item, each assist and each Guardian, with when a conditional ability applies). Each Pokémon's page shows what it gives as the buddy and as an assist, and its held item with Give, Swap and Take. Items are also given from the Bag; the Bottle Cap and Nature Mint ask which stat or nature. Plan's and Care's pages (Modules) show the whole party in six equal slots with its perks, laid out alike (`PartyPanel.kt`); below the rank that opens party perks, Plan's say which rank shows them, since they already count.
- **Magic Guard** hatches the egg of a failed session of 5 minutes or more, once a week, paid as a session of the minutes focused; the session still counts as failed. **Streak keeper** at +10% forgives a second missed day a week, for the daily streak and habit streaks.

## Guardians (legendaries and mythicals)

*Direction from the owner: legendaries and mythicals act differently from ordinary Pokémon. They slot into a **Guardian** role and give **big boosts**.* The numbers below are a first proposal *(tune)*.

- **A normal Pokémon that can also be a module's Guardian (owner, 8 November 2026, replacing the earlier "different role").** Legendary-class and mythical Pokémon (the ones kept out of every egg pool) are ordinary Pokémon to keep: they show in the PC, can join any module's party and any World team, and are still in **one place only**. They still cannot be the buddy, cannot be transferred, and do not take Rare Candies, hold items or evolve (they are at full power). They are earned through quests (see "Legendaries and mythicals"), never hatched.
- **One Guardian slot per module, no rank to wait for.** Plan, Care, Heal, Mint, Base and Link each have one Guardian slot under their party (`Guardians.MODULES`, stored as `pokemon_place` kind `guardian` with the module's number as the slot); a Guardian is chosen from the Party page or from the Pokémon's own page, and choosing it takes it from where it was. A Guardian's perk applies in that module only. The old global slots (rank 4, 6 and 9) became the first modules' slots in order on load.
- **Big boosts.** Each Guardian has a **signature perk** in one of the existing buckets (Egg luck, Candy yield, Super Candy chance, Item finder, Shiny hunter, Streak keeper, Poké (`COINS` in code)) worth **+10%**, plus half of that as a second perk from its second type where it has one. By default the bucket follows the Guardian's primary type, as for ordinary perks; lore gives overrides (Uxie: Super Candy chance for knowledge, Mesprit: Streak keeper for emotion, Azelf: Candy yield for willpower, Jirachi: Egg luck for wishes, Celebi: Streak keeper for time).
- **Outside the cap.** The ordinary perk cap (+25% per bucket from buddy, held item and assists) does not apply to Guardians: their bonus is added on top, up to **+15%** in a bucket from Guardians, so a bucket can reach +40%. A Guardian's perk does not depend on level or friendship, which is what makes it different from the buddy's.
- **Why.** The buddy is the one you grow and bond with; Guardians are the rare, hard-earned reward for sustained effort. A new Guardian changes how you play (which bucket do I want this week?) without adding a second thing to level.

## Parties per module

*Owner: effectively many parties, one per region/module, but one PC and one Pokédex.* The details below that the owner did not
state are marked *(proposed)*.

- **One collection.** Every owned Pokémon lives in the one PC and one Pokédex (caught is still permanent). A **party** is a
  list of up to 6 from that collection, one per module: **Plan, Care, Heal, Mint, Base and Link** (World has expedition teams, GEAR a helper). Moving a Pokémon
  between a party and the PC is free and instant; there is no copy.
- **One party at a time** *(proposed)*. A Pokémon is in at most one party, so each party is a real choice and the best six
  cannot serve every module at once. Putting it in another party moves it.
- **Lead per party**. Each party has a **lead**: it sits in that module's scenes, takes
  that module's friendship, and gives the **buddy perk**; the other five give **assists** (25% of type perk strength), as
  now. The Home tab shows every region's lead; the widgets show the Plan lead. A party can be empty (module unused), except Plan's, which keeps
  at least one (the "last party member" rule moves to it). Transfer still refuses a lead.
- **Perks stay inside the module.** A party's perks apply to that module's own rewards only. Plan's party keeps today's
  buckets and numbers unchanged. **Care's party** *(proposed)* has Care's own buckets, all capped at +25% like Plan's:
  **Friendship gain** (Normal, Fairy, Water: more friendship from routines, breaks and skills), **Berry finder** (Grass,
  Bug, Fighting: a chance of a second berry), **Visitor luck** (Flying, Psychic, Ghost: better rarity and IV floor on a
  befriended egg, a little more direct-join chance). Ability perks follow the same rule. Perks never read what the user
  wrote; they only change what a *kind* of Care action pays.
- **Guardians are per module (8 November 2026).** Each module has one Guardian slot; the Guardian's type perk (10%, the second type half) applies in that module's own buckets only, on top of the party's cap (up to +15% a bucket). A Guardian is a normal Pokémon otherwise and can be in a party.
- **Where new Pokémon go.** A hatch, befriend or granted Pokémon goes **into the party of the module that paid it if there
  is room, else the PC** *(proposed)*; nothing is lost either way. Quest rewards go to the PC.
- **Held items, friendship, level, evolution** are per Pokémon, not per party, so moving a Pokémon changes nothing about it.
- **Migration.** The existing party becomes Plan's party. Until Care's party has a lead, Care keeps paying the Plan lead as
  today, so nobody loses a reward by not setting it up. The Party page lists every module's party at once, each under its region's name with its lead first, over the shared PC.
- **Reward engine and saved state.** `PlayerState` carries a party per module (a map keyed by module id) and the reward engine is passed
  the party of the event's module. The golden vector (`pokemon-core/src/jvmTest/resources/golden.json`) covers it.
- **Economy check before release.** Run `./gradlew :plan-core:simulateEconomy`: Plan's numbers must not move; then add
  Care's befriending and perks and confirm Care's Pokémon income stays below Plan's and that a Care-only player is not
  stuck.

**Build order** *(1 to 4 shipped; 5 is the open multi-region simulator, docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md)*: (1) per-module party in `pokemon-core`/`app-core` with the migration, Plan only; (2) the Party screen tabs;
(3) Care's party, buckets and the lead's friendship; (4) Care befriending (docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md); (5) economy report.

## Retention

- **Forgiving daily streak:** kept alive by one paid task **or** one completed focus session that day. **Everyone gets one free missed day per week** that does not break it, and Home shows the streak as a line of text (a ring is not built). Weekly goals, and a Pokédex with seen/caught, region completion rewards and a trainer card.
- Badges for milestones (tasks finished, focus hours), shown as gym badges.

## Loop crossover

The two loops feed each other so task-only and focus-only players both progress.

- **Focus sessions also give Poké and a berry** (amounts by minutes *(tune)*), not just an egg.
- **Paid tasks fill an Egg meter** (rank-weighted: E counts 1, A counts 5 *(tune)*). Every 5 points *(tune)* produces a **Task Egg** from the player's selected region, hatched instantly with a small ceremony. It rolls on the odds of a short (about 15 minute) focus session, so long focus sessions remain the way to the rarest hatches.

## Ideas adopted from other apps

Studied: Forest and Tiimo (focus and planning), Finch and Pokémon Sleep (self-care and live collecting), Pokémon GO, Habitica, Duolingo, Todoist, Sunsama, SuperBetter. **Avoided on purpose:** HP loss for missed tasks, leagues against strangers, guilt-based notifications, ads, and paywalled core features.

- **"This evening".** Tasks planned for 18:00 or later, or flagged evening, sit under a separate **This evening** header on Today. (A task without an estimate takes 30 minutes *(tune)* on the Calendar's hours.)
- **Icon timeline.** The day view shows colour-coded blocks with an icon per task (defaults from the task's list, changeable). Focus sessions of 15 minutes or more give a quiet **halfway notification** (on by default), and always an end alert, to ease transitions. Both come from the app itself and never count as leaving it (they are plain notifications, never a full-screen intent), and both are cancelled if the session fails or is given up. *(shipped, Android; each can be switched off in Settings)*
- **Daily mini-quests.** Three one-minute actions a day from a pool of about 30 system-defined ones (drink water, stand up and stretch, tidy for 2 minutes, text a friend, "open the task you've been avoiding", which starts a Just 5 minutes on your oldest unfinished task). Each is a berry *(tune)*, completing all three adds a small bonus *(tune)*. Honour system: tap when done. One re-roll a day. No streak on mini-quests. Appears at rank step 1. On Home; the day turns over at 4am like streaks, the card shows what each quest paid, petting ticks "Pet your buddy", and the reroll never repeats the first three.
- **Rest days.** The player marks a day or a range as rest (sick, holiday, burnout), up to 14 days a month *(tune)*. The streak pauses without using up the free missed day, habits skip, reminders are silenced, and the welcome-back gift still applies. No penalty and no reward. "Rest today" is on Home's progress card, one day at a time.
- **Calm break after sessions.** After a completed session of 25 minutes or more, an optional 1 to 2 minute breathing moment with the buddy resting beside you, over a short original ambient loop. Skippable. Built as Care's calm break (`care-core`): friendship and a berry, up to 3 a day *(tune)*; see docs/economy.md.
- **Sleep and steps tie-in (after v1).** On Android, with permission, Health Connect sleep and step data can complete Health habits and goals automatically (for example a 7 hour sleep habit). Off by default, never required, no extra rewards beyond the habit's normal ones; only the result ("habit done") leaves the device.
- **Lures.** A type-specific Lure item (crafted from berries or bought) used before a session makes species of that type 3 times as likely in that egg's pool *(tune)*. It does not touch rarity or shiny odds. The lure is only used up if the egg hatches; a failed session refunds it. Rank step 2.
- **Spotlight days.** Each day a featured type (rotating through all 18) has species weighted 2 times in hatches *(tune)*, announced the day before. Missing one costs nothing. Seasonal events and a featured species can be added later. Rank step 2.
- **Party assists.** Non-buddy party members each add 25% of their type perk strength *(tune)*. Type perks only (no ability perks); the overall perk cap still applies, so the other five slots matter without breaking the economy.
- **Bond styles (from Voidpet Garden).** A Pokémon's nature decides how it likes to bond. **Presence** (Attack and Speed natures): focus sessions with it as buddy give double friendship. **Comfort** (Defense and Sp. Def natures): petting gives double. **Gifts** (Sp. Atk natures): berries give double. The five neutral natures are **balanced** and get +50% from every source. The nature → style table is data *(tune)*. Other care always works; the matching style is just faster. Shown on the Pokémon page. Rank step 3.
- **Optional mood check-in.** A one-tap "how are you feeling?" (five faces) on Home. Never required, no reward, and off by default (a "Track mood" setting). The buddy answers with a line of text. If on, a simple mood trend appears on the Growth screen. Private. *(Care's mood check-in in `care-core` is the built version.)*
- **Haptics.** Soft vibration when petting the buddy on Android (a setting turns it off). A **home garden** you grow from berries comes with the Secret Base scene after v1.
- **Not a health product.** Voidpet is built with therapists; PokéLife borrows the warmth, not the claims. No diagnosis, treatment or scores about the person, and copy never promises a mental-health benefit.
- **Considered, not adopted:** body-double focus rooms (co-op focus stays a later release), a task breakdown helper, growth-profile evolution branching, expeditions.

## Notifications

Beyond task reminders: a **streak-at-risk reminder** in the evening if nothing has counted yet that day (on by default, easy to turn off); a **welcome-back gift** after 7 or more days away, with the streak simply starting fresh and no scolding; and a **weekly recap** on Sundays (tasks, focus time, top list). No buddy messages. All can be switched off in settings, and reminders are silenced during focus. *(Shipped on Android: the streak reminder at 20:00 by default (adjustable), only while a streak is running, nothing has counted today, it is not a rest day and not in quiet hours; the recap on Sunday 18:00 with the last seven days' tasks finished, focus minutes and top list, skipped after an empty week. Both wait for the end of a focus session. The welcome-back gift is separate.)*

## Look and feel

- Gen 3 to 5 pixel art: GBA/DS-era text boxes and menus. **Pokémon are drawn with the Pokémon Mystery Dungeon style sprites of the PMD SpriteCollab**: the idle animation facing the viewer everywhere a Pokémon appears, its first frame where it stands still, and the PokeAPI and Black/White sprites only for the few forms SpriteCollab lacks. The buddy also uses SpriteCollab's Sleep (during a focus session, on Home and the widget), Hop (a pet or an event) and Eat (a berry) animations, and **portraits** show a Pokémon's face beside text the way the Mystery Dungeon games do: on its page (Normal), beside the Focus timer (Determined, Surprised when the player leaves) and the hatch message (Joyous), and in the corner of Home for a real event (Happy for a task, Joyous for a hatch, Inspired for a shiny). Faces are only ever glad, set or surprised, never sad. A shiny without its own recolour of an animation or portrait keeps its idle sprite. Their licence (CC BY-NC 4.0) keeps the app non-commercial and asks for credit, shown in small faint text under each species' Pokédex entry.
- Hatch, level-up and evolution are full ceremonies (egg wobble, text box lines, cry, flash), skippable.
- **Screen transitions** *(docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md)*: the bottom tabs crossfade (180 ms); opening a module, a section, a Pokédex entry or a Pokémon slides a short way in from the end and Back slides it out the other way (220 ms); one Pokédex entry to the next crossfades. Each page keeps its scroll position while another is open. Reduce motion makes every switch instant. All of it is `ScreenSwitch` in `screens/Menu.kt`.
- **Regional looks** *(docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md)*: each region has its own world, one real scene and one colour, in `RegionLook` (`theme/RegionLook.kt`, scenes in `BuddyScene`). **Plan is a town** (Showdown's city backdrop, red brick; Plan's colour has moved with the rainbow table, today orange): the Plan buddy stands there on Plan's page, on its Home card and in the widgets, at every type (a Pokédex entry still shows the scene of its type). **Care is a meadow** in green: Care's page and its sections have a deep meadow-green header (`Pixel.Meadow`, white text readable on it), and its lead and visitors stand in the meadow. A region's colour tints its card in Modules and runs down the edge of its card on Home. Lists, forms and settings inside a region keep the plain look; game moments keep theirs. Heal, Mint, Base, Link and World each have their `RegionLook` too. **Every module has one accent in `ModuleAccent`**, ordered as the Modules menu is (GEAR first, World last; the colours are in the table, not here), and the app's own header bar and tab bar are the neutral `Pixel.Chrome`, not red: the table is in docs/pokelife/FEATURES/DESIGN_SYSTEM.md, section 5.1.
- Crisp scaling: nearest-neighbour, integer scale, no blur.
- Fonts: pixel font for headings only; body text stays a readable font.
- Sound: cries, short jingles; a mute toggle (Settings → Sound and its volume). The buddy calls out when you pet it, when Home celebrates a real event, and when it is made your buddy; every Pokémon cries on its page, in the Pokédex, when it hatches and when it evolves. A few real game sound effects (`assets/sfx`) mark single moments, each kept for one kind: the menu select blip when a task is ticked off, a hop for each rock of a hatching egg, the healing chime when a berry or item is given, the run-away sound when a Pokémon is transferred, and the Pokémon Center jingle after an evolution's cry. None plays on a timer or a plain tap.
- Interface rules: Back sits at the start of every header; on/off settings are switches whose whole row is the target; a choice is a radio row, never an action button; each screen has one solid primary action and the risky ones (Give up, Transfer, Remove) stay quiet until confirmed; Undo lives in one bar floating above the tabs; Focus keeps Start pinned at the bottom; text fields submit from the keyboard's Done key.
- Accessibility: content descriptions on all images, text scaling, colour-blind safe type chips, a reduce-motion option, a dyslexia-friendly font option, and every ceremony skippable.

## Screens and flows (proposal, to confirm)

Navigation is four bottom tabs: **Home / Modules / Pokémon / Me**. Home, Pokémon and Me hold what every module shares (the buddy, the collection, the trainer card, friends, settings); each life module is a section of **Modules**, which opens on a compact list of module rows (with a Jump to field on top and a labelled Gear key in its header; tapping the Modules tab again inside a module returns to this list, and a module header is one row: Back, title, small Sections key) and reopens the module last used. **Plan** is the standard module page (below); its Sections key lists Tasks, Calendar, Focus, Goals and Settings *(PLAN.md section 3)*. Desktop and web have no Focus in Plan.

- **Home** *(docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md)*: the trainer (name, rank and its bar, candy and Poké counters), then **Regions**: one card per region (Plan, and Care while it is on) with its lead idling in a window onto the region's scene and the region's colour down the card's edge, the lead's name and level, how today stands there ("2 of 5 tasks done today", "1 of 3 routines today"), and its one most useful next step with one button (Plan: the next task, the picked one first, with Done, or the running focus session; Care: the next routine with Done, else a calm break). Care with no lead shows the Plan buddy helping out. The top of a card opens the region. Then the streak (with Rest today), mini-quests, and **Recently**: the last few rewards already seen, newest first, a hatch with its sprite (a tap opens the Pokémon). Each lead's scene, petting and berries live in its region's page, and so do the Next up card (Done, Focus on this, "Pick for me") and the planner and focus. *(A streak ring is not built; the streak is a line of text.)*
- **Modules → Plan (owner, 18 November 2026):** Plan's page follows the standard module layout: lead, an overview (today's open tasks), action items (Inbox quick add, the next task with Focus and Pick for me) and the party; it has no menu. The header's Sections key holds Tasks, Calendar, Focus (phone only), Goals and Settings (`PlanSection`); More, Progress, Targets and the weekly review are gone. Tasks is one workspace (`AppModel.tasksView`: Today, Upcoming, All, Done) with a List chooser in the Filter on All and Done; the quick add on Plan's page creates Inbox tasks and the Today view adds to Today. Focus setup offers any open task (Today's first, plus Another task), ends with a session summary and lists the last 5 sessions. The task editor tabs are Schedule, Details and More; a task with no day can carry a nudge ("Remind me at"). Plan settings hold task reminders, repeat until answered, focus halfway and end alerts and the device calendars; quiet hours are in Settings → Notifications and the week start is in General settings. Quick-add is an inline box (title, then "More options" for difficulty, repeat). Completing a task shows the reward and a 10 second undo bar. *(A mission board is not built.)*
- **Modules → Plan → Focus:** optional task to focus on, region picker, presets (15 / 25 / 45 / 60) plus custom, Deep Focus toggle, a short reminder of the strict rules, the egg during the session, the emergency pause button. On finish: hatch ceremony, then a summary card with actions (set as buddy, keep, transfer).
- **Pokémon:** Party: every region's party on one page, Plan's then Care's (while Care is on), each under its region's name with its colour and "x of 6", its lead first (Buddy or Lead), each Pokémon a row of sprite, name and one quiet line (level and types) with Buddy or Lead, ★ Starred and ✨ Shiny at the end, then that party's perks; an empty party says how to fill it. PC boxes: one PC for every region, boxes of 30 with Previous, Next and "Box 2 of 5", each Pokémon a tile of sprite, name and level (✨ ★), search, sort and filters in a quiet button that start again from box 1 when changed; only the Pokémon in no other place, until **Show all Pokémon** lists every one with its place on the tile and a Where filter (`PcView.everyone`, `PcPlace`); Pokédex: an index of one row per species (number, sprite, name, Owned, Caught, Seen or Not found yet), search by name or number (a species not found yet matches by number only), the National Dex by generation, the forms dex on each entry; Bag. The **Pokémon detail** *(docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md)* reads top to bottom: the sprite in its scene with its type plates; **identity** (portrait, National Dex number, species, level and types, Rename with the species name as the placeholder); **Traits**, the specimen (nature with the stat it raises and lowers, ability, gender, Shiny when it is one, and from rank step 3 the IV "judge", qualitative like the games, and its best stat); **Growth** (Rare Candy progress and Feed, friendship and its bond style, and from rank step 3 the stats, the nature's raised and lowered stats marked ▲ and ▼); **Evolution** (what it evolves from, Evolve, or "final form"); **Role** (Plan's buddy or party, Care's lead or party, the PC, or the Guardian slot; which module region's Pokédex it counts for; the party buttons; Star), then its perks and held item; **Origin** (how it joined: the starter, a Focus, Task or Care Egg and the region, befriended on a Care visit, or a quest; and the day it was met; kept in the save as `AppData.origins`, client-side like favourites, so older Pokémon show the day only); and last, Transfer, quiet until confirmed.
- **Modules → Plan → Goals, and Me → Stats:** goals with milestones; the effort trends (Plan progress) are listed on Me.
- **Me:** trainer card (name, buddy, rank), a Stats page (trainer points, tasks finished, minutes focused, streaks; opens Plan progress and Care trends), and Settings, a menu of pages: General (trainer name, sound and touch, look, Reduce motion, week start), Privacy and access (the screen lock for Care, Heal, Base, Link and Mint in one place; Mint's switch writes `MintSettings.gate`), Notifications (permission status with buttons, Quiet hours, one reminders switch per module; Heal has none because its reminders are the times the player adds), Backups (complete backup, a Module exports card, Reset) and About. *Erase this device* also resets Link and every module's settings, clears the Android shared preferences and cancels set alarms. No 12/24-hour setting yet. *(The shops are the Poké Mart in the Pokémon tab and the Kecleon Shop in World; prices in `docs/economy.md`.)*
- **Onboarding:** the Professor's greeting → name the trainer → pick a starter → a **1 minute tutorial focus session** that hatches a free egg → create a first task, which **pays immediately** despite the minimum age (tutorial exemptions). The first reward arrives within about two minutes of opening the app.
- **Offline:** everything works offline; there is no server, so rewards are decided on the phone and nothing is pending.

## Android widgets

1. **Today's tasks and habits:** checklist, tap to complete.
2. **Buddy + streak (2×2).**
3. **Agenda:** upcoming tasks, optional read-only device calendar events. *(Not built as listed: the widgets today are Today's tasks, Buddy, Focus, Care, World, Mint and Mint due; see `docs/code-map.md`.)*
4. **Start focus:** one tap, starts a default session.

Local-only: the app keeps a local database (SQLite on Android) and the widgets read and write the same save as the app. A task finished from a widget is paid when the app next opens, and the app shows what was earned then.

## Platforms

Android is the only product platform (owner, 6 November 2026), with widgets. Desktop and web are development conveniences that gate nothing: they keep compiling from the shared code, have no focus timer (strict focus can't apply to people working in other windows) and no widgets, and there is no sync between devices.

**Priority (owner's, 2026-10-25): Android first; desktop and web are deprioritised.** Features that only serve them (calendars through an iCal link, web push reminders, mouse-specific timeline work) wait, and testing happens on the phone.

## Platform and modules

PokéLife is one app of life modules (PLAN, CARE, HEAL, MINT, BASE, LINK, GEAR and WORLD) over one Pokémon collection, one Bag, one ledger and one currency, Poké.

- **A module:** its own data and screens. It turns its own activities into minimal events (`CareEvent`, `HealEvent`, `MintEvent`, `BaseEvent`, `LinkEvent`) and its store in `app-core` pays them through the `World` interface and the reward engine, idempotently. A module never writes Pokémon or currency rows itself, and an event never carries a figure, a name or a private word.
- **One economy:** a single pool of candies, Poké and items, so effort anywhere strengthens the same Pokémon. The life modules have daily limits on what they pay (`docs/economy.md`, section 3), and every reward is a ledger row recording the module and the source, for auditing (`docs/economy.md`).
- **The buddy works everywhere,** but it is cosmetic; the perks come from each module's party (see "Buddy and perks").
- **Privacy:** health and money data is sensitive. It lives in its own sealed tables on the phone (`docs/code-map.md`), and the game only sees reward events ("workout done"), never the details behind them. The no-health-claims rule applies to every module.
- **Code:** Gradle modules `:pokemon-core` (species data, Pokémon, items, perks, engines), `:plan-core`, `:care-core`, `:heal-core`, `:mint-core`, `:base-core`, `:link-core` and `:world-core` (each module's rules), and `:app-core` (the stores and the rules engine). Nothing in `:pokemon-core` knows about tasks or focus sessions.

## Rewards and the ledger

The phone decides every reward. Every reward row records the rules version (`RewardRules.VERSION`) in the local ledger, and every reward source, use and cap is listed in `docs/economy.md`. Species, base stats, abilities and perk tables are data shipped with the app.

## Data to add

- Per-species base stats, abilities, types, evolutions and forms for **every species through Gen 9**, generated at build time from a public dataset (needs the build environment to reach it). Species use National Dex numbers; each module region keeps its own local numbering.
- Regional dex lists for all regions, as lists of National Dex numbers.
- **Moves:** all 919 standard-type moves (type, category, power, PP, accuracy, priority, generation) and, for each form, what it learns from the newest game it appears in, by level-up (with level), egg, tutor and machine (`Dex.move`, `Dex.learnset`, `Dex.movesOfType`). Mega and Gigantamax forms use their species' set. Nothing in the game uses them yet: they are for move-based skills and GEAR helpers that learn a tool's move.
- A rarity for every species, assigned by rule (see Roster).
- Ability → perk table, type → perk table, item table, all as plain data with tests.

## Risks

- **IP:** names, sprites and cries are Nintendo / Game Freak / The Pokémon Company property. **Distribution is private: the owner and friends** (a sideloaded APK), which keeps exposure low. A public release or store listing would likely be taken down; species stay data so an original-creature skin remains possible.
- **Health claims:** the app must not present itself as therapy or a treatment. Mood tracking and reflection are optional and private.
- **Strict focus** can frustrate users with legitimate interruptions. Calls exempt; revisit after use.
- **Balance:** perks, items and transfer yields compound. Simulate a few months of play before fixing numbers.
- **Planner scope:** a full drag-and-drop day / week planner, lists, recurrence with edit-this-or-all, and device calendar import make the planner a product on its own. It is the part users judge the app by, so it needs its own testing (recurrence and time zones especially).
- **Scope:** stats, items, perks and widgets are a lot. The data pipeline and Pokémon depth should stay behind a clean interface so the UI work can start in parallel.

## Defaults (proposal, change if you disagree)

- Streak day rolls over at 4am local time (`Streaks.streakDay`).
- A recurring task pays at most once per day, and recurrence is never more frequent than daily.
- Completing a task from the widget finishes it at once and the reward is granted the next time the app opens (the undo window has passed by then; `GlanceWidgets.kt`).
- Reminders are silenced during a focus session.
- Settings include a complete backup, per-module exports and Erase this device (there are no accounts).
- One focus session at a time.
- No third-party analytics. Crash reports only, opt-in.

### Planner defaults

- **Time zones:** planned and due times are local wall-clock times. Changing time zone keeps the wall-clock time; recurring tasks keep it across daylight saving. Imported events keep their own zone.
- **Recurrence:** every N days / weeks / months / years; chosen weekdays; monthly on a date, on the nth weekday or on the last day; on a **fixed schedule** or **N days after completion**; ends never, on a date, or after N times; skip one occurrence; edits ask "this one / this and following / all". Never more often than daily. Instances are generated 14 days ahead. A recurring task shows its own streak and completion history (no extra reward beyond the global streak).
- **Reminders:** up to 3 per task, absolute or relative (at the time, 10 minutes, 1 hour or 1 day before); snooze (10 minutes, 1 hour, tomorrow; the notification shows Done, 10 min and 1 hour, Android's limit being three actions); quiet hours default 22:00 to 07:00 (set in Settings → Notifications); silenced during focus; local notifications on the phone (no web push and no multi-device sync).
- **Calendar:** Plan → Calendar has Day, Week and Month. All-day items sit above the timeline. The Day shows today up to 13 days ahead; untimed tasks of the day (on today also the overdue ones) sit in a "To plan" tray above the hours; tap a task, then an hour, then nudge by 15 minutes (the picked task also gets Shorter and Longer). A block lasts its estimate (30 minutes without one) and overlapping blocks share the width; the waking window (08:00 to 22:00) is shown and stretches to fit. Holding a task (a long press on touch) and dragging it from the tray onto the Day or Week sets its planned date and time; dragging a block moves it (across days in the Week); dragging its lower edge (the bottom 12 dp) sets the estimate; everything snaps to 15 minutes, a block starts by 23:45 and never runs past midnight. While dragging, the slot is outlined and the page scrolls near an edge; tap-to-place stays as the accessible route. The Week is the next 7 days from today, blocks show their icon only, and tapping a day name opens that day. The Month is a grid of whole weeks (Monday or Sunday first, from General settings): all-day events are bars across their days (`SpanLayout`), timed events and open tasks are labels, at most four lanes a day then "+n"; tapping a day shows its agenda under the grid.
- **Own events:** an event on Plan's own calendar (title, day, start, length; it starts at the next hour on today, 9:00 on other days, and lasts an hour) is for appointments and blocks of time that are not tasks: never ticked off, never paid. It can be all day (a first and last day, never busy time), has one of the eight list colours (teal by default), and can repeat (Once, Daily, Weekdays, Weekly, Every 2 weeks, Monthly, Yearly; never more often than daily; never ends or ends on an occurrence). Edits from the event panel apply to every occurrence; moving one on the calendar makes it a one-off; Delete asks this one or all; changing how a later occurrence repeats keeps the earlier ones. Saved in the save (`AppData.events`, `PlanEvent.occurrences`). Not written to the phone's calendars.
- **Device calendars (Android):** Settings → Calendars asks for the calendar permission and lists the phone's visible calendars to tick (Google Calendar arrives through them). Events are read from the phone each time the day is shown, never saved or paid; recurring ones are expanded, declined and cancelled ones are left out, private ones show as "Busy" on widgets; the time they take inside the waking window counts as busy. Which calendars show is kept per phone. Desktop and web have no device calendars.
- **Lists:** optional, user-created (name of up to 40 characters, one of eight colours, one of sixteen real item icons). A task has at most one list; archiving a list hides it, deleting it sends its tasks back to no list. Lists carry no reward and are made, edited and archived in Plan settings. Open tasks in no list make up the Inbox. A list groups its open tasks by when they are (`TaskViews.byWhen`: From before, Today, Tomorrow, Next 7 days, Later, No date); `#Name` in quick-add files a task in the list with that name. The Calendar's "To plan" card calls its second group "No date" (`TaskViews.undated`).
- **Search and filters:** text search over title and notes; the Filter on All and Done narrows by list (a List chooser; no list counts as one), rank and status (Open, Done, Any) with open tasks first by their day (`plan-core` `TaskFilter`). There is no date-range filter.
- **Repeating a task:** a task's options have a Repeat row (Never, Daily, Weekdays, Weekly, Monthly), and More options in the quick-add has the same. The task itself stays the one of its own day and pays as a one-off task (with the minimum age); the later ones are made ahead as for any recurring task. Weekly follows the weekday of the first day, Monthly its date. Never stops it and removes the later ones nobody has touched. Every 2 weeks, yearly, ends and "N days after" exist in the engine but have no screen.
- **Quick-add:** one natural-language parser shared by every platform, e.g. `Call mum tomorrow 5pm #Social !B` (`#` list, `!` rank); the box shows "Understood: …" under the field while typing and keeps the parsed time and "tonight". Also available from the widget and the share sheet.
- **Task states:** open, done, **won't do** (no reward, hidden from stats), deleted (recoverable for 30 days). A reward is tied to one completion event; reopening and re-completing a task never pays twice.
- **Task row and card:** every row starts with the check key (a finished task's is ticked in a quiet colour, a won't-do task's is a cross; either reopens it), then the title with the rank as a small framed tag. The open card's When tab holds the day and the list, and one "Time and repeat" row that unfolds the time, reminders and repeat. The Edit tab has a Goal row (None or an unfinished goal): a task counts toward one goal at a time (`GameStore.linkTaskToGoal`), a goal without milestones shows "N of M tasks", and the link pays nothing. A finished task's card has Reopen and Delete.
- **Locale:** week start and 12 / 24 hour follow the system, with an override (the week-start toggle is in General settings). English only for v1.

### Pokémon defaults

- **Gender:** stored per Pokémon, rolled at hatch from the species' ratio (genderless supported). Affects gendered forms and sprites and gender-dependent evolutions only.
- **Friendship:** 0 to 255, starts at 70, never lowered, no decay; the sources and amounts are in `docs/economy.md` (doubled, or +50%, when the care matches the Pokémon's **bond style**, see Ideas adopted). Effects: perk strength scales from ×1.0 at 0 to ×1.2 at 255, and friendship evolutions at 220.
- **Abilities and perks:** about 40 curated abilities have a perk; every other ability falls back to the type perk only.
- **Endgame:** a level 100 Pokémon takes no more candies. Candies still level other Pokémon. Poké sinks: Tera Shards, Mega Stones, form items. Candy holdings cap at 9,999; excess converts to Poké at 1:1 *(tune)*. 
- **Pokédex text:** the latest English entry from the public dataset. **Seen** = appeared on an open quest (`AppData.seenSpecies`, client-side, kept for good); **caught** = ever owned; **owned** = in the party or PC now (`DexState`). Caught species show their sprite and entry, with a Poké Ball on the owned ones; seen ones stay a silhouette but are named when tapped; the rest are a silhouette marked ???. Every tile names its state in words. Each caught species opens its own page: category (genus), height, weight and entry from the dataset, types, abilities (hidden marked), base stats, and its evolution line with how each step works in the app (`EvolutionRules.howTo`); members not found yet stay a silhouette marked ???. Previous and Next skip species not found yet. The list has a completion bar and All, Caught and Not caught filters.
- **Pokédex entry scenes**: a caught species' entry shows it in the real battle scene its first type gets as a buddy on Home (`BuddyScene.of`: Water on the beach, Grass and Bug in the forest, Electric on the thunder plains...); a species only seen keeps the plain wallpaper, so the scene never gives its type away.
- **Hatch and evolution scenes**: a hatch plays full screen, once per new egg wherever it came from (focus, Task Egg, widget): "Oh?", three ever harder wobbles of the egg, a flash, the Pokémon and its cry. An evolution shows "What? X is evolving!", the sprite as a white shape flickering between the two forms ever faster, a flash and "Congratulations!". A tap or Skip jumps to the end; Reduce motion shows only the end. Real sprites and the item egg only; the white shapes are the real sprites tinted.
- **PC and bag:** unlimited PC boxes of 30; bag stacks to 999.

## Open questions

Decided: everything above not marked *(tune)* or *(proposal)*.

- **To confirm:** the Screens and flows proposal; the Defaults above; Mega / G-Max region assignment; S rank staying non-selectable.
- **To tune by simulation before release:** rarity rule parameters across ~1,025 species, Poké amounts, transfer yields, perk magnitudes and cap, catch-up discount, minimum task age, Mega bonus, rank names and thresholds, region unlock order, legendary quest requirements, E to S rank mapping.
- **To check:** Showdown sprite terms and build-time availability, whether the build environment can reach the data sources.
