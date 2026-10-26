# PokéPlan design

A fun productivity, growth and self-improvement app for Pokémon lovers: your Pokémon grow as you do. This is the product and game design for the multiplatform rewrite. Numbers marked *(tune)* are starting values to be settled by simulation before release. Everything not marked is decided.

## Pillars

1. **Strict focus.** Leaving the app during a session fails the egg. No loopholes.
2. **More tasks, more productive.** The economy rewards finishing many real tasks, not gaming one big one.
3. **Your partner matters.** A well-raised older Pokémon stays worth using; new hatches never make it obsolete.
4. **Nostalgic pixel.** Gen 3 to 5 look and feel (GBA/DS era), cute and readable.
5. **Gentle by design.** It should quietly help people with ADHD without ever saying so: low friction to start and capture, instant rewards, no shame (see ADHD-friendly design). Strictness applies to distractions, never to the person.
6. **Growth you can see.** Levelling and evolution mirror real progress: goals, habits and reviews feed the same loop as tasks and focus, and the app shows how you are growing.

## Core loop

```
        focus session ──► egg hatches ──► new Pokémon (stats, nature, ability, shiny?)
             ▲                                   │ duplicates ──► transfer ──► Rare Candies
             │                                   ▼
   buddy perks (type / ability)  ◄────  partner raised with candies, items, EV training
             ▲                                   ▲
             └──────── finished tasks ───────────┘  (candies, items, EVs via training plan)
```

Focus time brings new Pokémon. Finished tasks make Pokémon stronger. The buddy's perks feed back into both.

## Focus

- Minimum session 5 minutes.
- **Deep Focus:** the phone must stay still (accelerometer, as in the old app); picking it up or moving it fails the session. In exchange the egg odds go up one band (see `Egg` in `:pokemon-core`).
- **Perks are locked when the session starts:** egg-luck and shiny perks use the buddy at the moment start is pressed, so swapping later does not change that egg. Task perks use the buddy at completion time.
- **Strict mode, always on.** Leaving the app (switching to another app or the home screen) starts a **10 second grace period**. Returning in time keeps the session; otherwise the egg is lost, and a failed session does not count toward the streak. The only pauses are the daily emergency pause and phone calls (below).
- **The focus timer is phone-only.** Desktop and web are companions (see Platforms).
- **Locking the screen does not count as leaving.** The session continues with the screen off; unlocking and opening another app starts the grace period. Face-down on the desk is the intended way to focus. *(Wired up on Android in 0.16.1: when the app goes to the background it checks whether the screen is off or locked; a lock reported just after the leave cancels the grace period.)*
- **A failed session costs only the egg** (plus the streak rules). The buddy's friendship is never reduced.
- **Phone calls pause the timer** (incoming or ongoing); it resumes when the call ends. Android reads this from the audio mode (ringing, in a call, call screening), which needs no permission; app voice chats do not count.
- **The session is watched on every tab**, not only on Focus: leaving the app or moving the phone in Deep Focus counts wherever the player is.
- **Focused time is what counts:** the running timer shows focused minutes, and paused minutes apart from them. The setup screen lists these rules in plain words.
- **Two emergency pauses per day**, up to 3 minutes each, started by the player before leaving. Anything else that takes the player out of the app fails the egg after the grace period.
- **No-blame recovery:** a failed session shows a neutral screen ("That egg went cold. Try again?") with a one-tap 5 minute retry. No red, no scolding.
- Sweet spot is built in: Super Rares per focus-hour peak at about 60 minutes.
- **Egg tickets (offline hatching, shipped 0.10.0):** a signed-in phone keeps a stock of **20** *(tune)* egg tickets, each a random seed the server issued to that device. Starting a session spends the oldest ticket, so the dice are fixed before the egg is laid (like the perks) and failing or abandoning a session never rerolls anything; the egg is rolled from that seed when the session finishes, online or not. Every sync reports the spent tickets (with the session start and the hatched Pokémon) and tops the stock back up. The server accepts a spent ticket only once and only if it issued it to that device before the session started; it refuses anything else. **The egg never waits on a ticket:** a guest, or a phone that ran out offline, hatches with a seed of its own, and a refused ticket takes nothing away from the player. Tickets stay on the device (they are not part of the cloud save) and change no odds, so egg pacing is unchanged. Task Eggs and task rewards keep rolling on the device until the server pays tasks.

## Tasks

- **Difficulty rank E, D, C, B, A** replaces the old priority 1 to 5 (E = old 1 … A = old 5). The player picks how hard a task is; **higher rank pays more, A the most**. Urgency comes from the due date, not the rank. Default rank is C. **S rank is not selectable**: it is reserved for special missions (legendary quest steps, weekly challenges). Reward tables are already ported (`CandyRewards`, indexed 1 to 5). **Every paid task gives Rare Candies** (E 3–5 up to A 7–20); a **Super Candy bonus** comes on top with a chance by rank (E 10%, D 15%, C 20%, B 30%, A 40%, raised by the Super Candy chance perk) *(tune)*. The original app paid one or the other.
- **Minimum task age** scales with the difficulty rank *(tune)*: **E 2 minutes, D 5, C 10, B 15, A 20**, counted from creation. A one-off task only pays after that, so tiny tasks done right away still pay, but big ones cannot be created and instantly ticked. Younger tasks complete without a reward. There is no daily cap, so many real tasks always pay.
- **Recurring tasks are exempt:** their instances are created ahead of time by the schedule, so they pay when their day comes.
- On-time bonus: finishing before the due date adds a bonus *(tune)*. **Overdue tasks pay the normal reward**, just without the bonus, so old tasks get finished rather than abandoned.
- **Subtasks** pay a flat 1 Rare Candy each *(tune)*, for up to 5 subtasks per task. The parent pays in full when it is finished. The minimum age applies to subtasks too.
- **Undo:** completing a task shows its reward and grants it after a 10 second undo window; undo cancels it. After that completion is final.
- **Categories** are for organising only and never change rewards. Six, replacing the old eight (School, Work, Hobby, Leisure, Chores, Health, Social, Others, where Hobby/Leisure overlapped and Others was a dumping ground):

| Category | Covers |
|---|---|
| Work | job, projects, admin |
| School | classes, homework, exams |
| Home | chores, errands, bills |
| Health | exercise, sleep, appointments |
| Social | friends, family, events |
| Personal | hobbies, leisure, anything else (the default) |

  Each has a colour used on the calendar and widgets. A weekly recap shows how the week split across categories.

## ADHD-friendly design (lowkey)

The app never mentions ADHD; it just removes friction and shame.

- **Low friction to start:** one-tap capture into the Inbox (widget, share sheet, quick-add), a **"Just 5 minutes"** button on any task that starts a minimum-length focus session on it, short sessions always welcome.
- **Pick for me:** a button on Today that picks a random task from the list (a small bonus for taking it *(tune)*), for when choosing is the hard part.
- **Time awareness:** after finishing a task you focused on, show **estimate vs actual** time, so over time guesses calibrate.
- **Gradual reveal:** day one is tasks, focus and the buddy only. Other systems appear as the trainer rank rises *(proposal, tune)*:

| Rank step | Appears |
|---|---|
| Start | tasks, habits, planner, focus, buddy, Rare Candy, eggs, the weekly review |
| 1 | Super Candy and evolution, Pokédex, goals, daily mini-quests, the region picker (Kanto opens) |
| 1 | daily mini-quests |
| 2 | items, bag, shop, coins, the Growth screen, Lures, spotlight days |
| 3 | training plans, natures and stats detail, IV judge, Tera, bond styles, mood check-in |
| 4 | legendary quests (regions keep opening, one per rank) |
| 5 | contests, perfect dex, Hall of Fame |

- **No shame:** warm, neutral copy; overdue is shown calmly (no red alarm styling); streaks forgive (see Retention); failing a focus session is recoverable in one tap.
- **Few choices:** every form has sensible defaults (rank C, category Personal, 25 minute session) so the first screen asks for as little as possible.
- **Reminders that persist gently:** up to 3 repeats until acknowledged, then stop.
- **Considered, not included for now:** routines (recurring checklists with timers) and a one-tap "reschedule all overdue". Both can be added later.

## Growth: goals, habits, reviews, insights

- **Goals with milestones.** A goal ("Learn guitar", "Run a 10k") has a category, an optional target date, ordered **milestones**, and linked tasks, habits, lists and focus time, with a progress bar. **Finishing a milestone grants a Super Candy** *(tune)*, so a real achievement evolves a Pokémon; finishing the goal grants a bonus item. Anti-farming: a milestone must exist for at least a day before it pays, and milestone rewards are capped per day *(tune: 3 Super Candies)*.
- **Habits are a recurring-task type** with flexible targets: daily, chosen weekdays, or N times per week / month. Each habit has its own **forgiving streak** (counted in weeks for N-per-week habits) and a simple completion history. Completions pay like recurring tasks (once per day per habit). Habits appear in Today and in the widget.
- **Weekly review.** A short Sunday ritual: what you finished, focus time, habit results, carry forward or reschedule unfinished tasks, and one optional line about how the week went. Completing it earns a small reward once a week *(tune: a berry)*. Notes are private. Not therapy, no diagnosis, no scores about the person.
- **Growth screen** (under Me): a **balance wheel** across the six categories (tasks finished and focus time, last 7 / 30 / 90 days), trends for focus hours and tasks per week, estimate accuracy, goal progress, habit streaks, review history and collection layers. *(0.17.0: the wheel with 7 / 30 / 90 days, each area's tasks and minutes beside its share and only active areas listed; tasks and focus per week with week labels; goal progress apart from tasks and time. Every metric states what it counts and over which days, and the trainer card says "all time" and how the streak counts.)*

## Planner (v1)

In scope for v1: tasks with difficulty rank, category, planned date, due date/time, estimated duration and reminders; **recurring tasks** (daily, weekly, monthly, custom) and **habits**; **goals** with milestones; **subtasks**; an **own calendar** (month, week, day and agenda views) with a **drag-and-drop day / week planner** (time-blocking), which also feeds the agenda widget; **user-created lists** (projects) alongside the six categories; and **read-only import of device calendar events** on Android (needs the calendar permission; shown on the calendar and agenda, never paid as tasks).

### Task model, Today and focus link

- **Fields:** title, notes, difficulty rank, category, optional list, **planned date** (when you intend to do it), **due date/time** (the deadline), estimated duration (optional), recurrence, reminders, subtasks.
- **Views:** **Inbox** (no planned or due date; quick captures land here), **Today** (planned today + due today + overdue + today's habits), **Upcoming**, **Calendar**, **All**, **Goals**.
- **Focus on this task:** a session can be started from a task. The minutes are logged on it, and it shows its total focused time. Finishing a task you focused on gives a small bonus *(tune: +10% candies)*. Egg odds are unchanged. Sessions can also be started without a task.

## Pokémon

Each owned Pokémon has: species, level 1 to 100, nature, ability, 6 IVs, 6 EVs, held item, friendship, shiny flag, nickname.

- **Stats** use the Gen 3+ formula:
  - HP = ⌊(2·Base + IV + ⌊EV/4⌋) · Level / 100⌋ + Level + 10
  - Other = ⌊(⌊(2·Base + IV + ⌊EV/4⌋) · Level / 100⌋ + 5) · Nature⌋, with nature ×1.1 / ×0.9
  - IV 0 to 31 rolled at hatch. EV 0 to 252 per stat, 510 total, gained from finished tasks through the Pokémon's training plan (below).
- **Natures:** the existing 25. Each raises one stat and lowers another (five are neutral).
- **Abilities:** each species has one or two; one is rolled at hatch. Abilities give buddy perks (below).
- **Shiny:** rolled at hatch, base chance **1 in 128** per egg (about a month at ~5 eggs a day), with a small bonus for longer sessions. Shinies get a distinct sprite palette and a hatch animation (a gold flash, twice, instead of the white one).
- **Levelling:** Rare Candies per level grow with the level, the shape of the games' Medium Fast experience group: **2 + level² / 150**, rounded down *(tune)* (2 a level up to level 12, 8 at 30, 18 at 50, 67 at 99; 33 candies from level 1 to 16, 154 to 36, 2,341 to 100). A Pokémon more than 5 levels below your partner needs half the candies, rounded up *(tune)*, so new hatches catch up without outshining the partner.
- **Evolution:** Super Candy at the species' game evolution level (Charmander at 16). Evolution stones for the species that use them.

### Evolution

Every evolution is reachable solo. Each game trigger maps to an in-app one:

| Game trigger | In the app |
|---|---|
| Level | Super Candy once the Pokémon reaches **the species' game level** (Charmander at 16, Charmeleon at 36); there are no battles, so levels only pace progress, and the level curve above keeps that pace |
| Stone / item | the evolution item |
| Trade, trade with an item | a **Link Cord** item (holding the item where the game needs one); a real friend trade also works once trading exists |
| Friendship (with or without day / night) | friendship at 220 or more, then a Super Candy, at the right time of day where it matters |
| Level plus time of day | Super Candy used between 06:00 and 18:00 (day) or otherwise (night), local time |
| Gender-dependent | uses the Pokémon's gender |
| Special conditions (known move, location, party contents, weather, stats, beauty, etc.) | an **Evolution Trial**: a short quest (a number of paid tasks, or one focus session of a given length), flavoured after the original condition |
| Branching (Eevee, Tyrogue, Poliwhirl…) | the trigger picks the branch |
| Random branch (Wurmple) | decided when it hatches, as in the games |

The data pipeline classifies every evolution into one of these.

### Training plan

Stat training is the player's choice, not tied to task type. Each Pokémon has a **training plan**: up to 2 focus stats, changeable at any time. When a task pays out:

- The buddy gains EVs in its focus stats, split evenly. Amount = the task's rank number *(tune)* (E = 1 … A = 5), so an A-rank task gives 5 EVs.
- The rest of the party gains half *(tune)*, so a raised team is possible.
- No plan set: no EVs. The same minimum task age applies, so there is nothing to farm.

At about 5 tasks a day this fills the 510 EV budget in a few weeks; Vitamins are the shortcut.

### Items (so a mid partner is not overshadowed)

| Item | Effect | Source |
|---|---|---|
| Rare Candy, Super Candy | level up, evolve | task rewards |
| Vitamins (HP Up, Protein, Iron, Calcium, Zinc, Carbos) | +10 EV in one stat | task drops, streak rewards |
| Nature Mint | change the nature | rare drop, badge reward |
| Ability Capsule | swap to the other ability | rare drop |
| Bottle Cap | raise one IV to 31 *(tune)* | rare drop, Pokédex milestones |
| Held items (e.g. Leftovers, Choice Band style) | one small buddy perk | drops, shop |
| Tera Shard (one per type) | set a Pokémon's Tera type; its type perk then uses it | shop, rare drops |
| Evolution stones | evolve stone species | drops |
| Link Cord | trade-style evolutions without a trade | shop |
| Ability Patch | switch to the hidden ability | shop (expensive), rare drop |
| Berries | feed one for a Contest condition boost and a little friendship; also a mini-quest reward and the ingredient for Lures | drops from paid tasks, focus sessions, mini-quests |
| Lure (by type) | bias the next hatch toward a type | crafted from berries, shop |
| Form items (Arceus plates, Silvally memories, Rotom appliances, Griseous Orb, DNA splicers, etc.) | change a species' form | shop, drops (generated from species data) |

**Sources (both):** common items drop at random from paid tasks (chance by rank) and come as streak, badge and Pokédex milestone rewards. Rare items (Nature Mint, Ability Capsule, Bottle Cap, Ability Patch, Link Cord, Tera Shards, form items, stones) are bought in a shop with **coins** earned from play. **Coins** are earned from paid tasks (by rank), completed focus sessions (by minutes), and transferring duplicates, plus streak and badge bonuses. Amounts to be tuned. Nothing is sold for real money.

### Duplicates → candy

- Transfer any Pokémon you do not need for Rare Candies. Yields by rarity *(tune)*: Common 8, Uncommon 12, Rare 22, Super Rare 60, plus a fifth of the candies already fed. Perfect-IV or shiny ones show a warning first.
- You cannot transfer your buddy or your last party member, and **legendaries and mythicals cannot be transferred**. Transferring the only copy of a species keeps its Pokédex entry: caught is permanent.
- This is the sink for hatches, so the PC never needs to be a graveyard.

## Roster

**Every species through Gen 9 (about 1,025) with all alternate forms**: regional (Alolan, Galarian, Hisuian, Paldean), gender differences, Mega / Gigantamax, and cosmetic forms (Unown, Vivillon, Alcremie, etc.). The 150-species list in `:pokemon-core` becomes the **Home Region** (below); the rest comes from generated data.

- **Mega / Gigantamax are a temporary focus form.** A buddy holding its Mega Stone (or Dynamax Band for G-Max species) shows that form for the length of a focus session, with a bonus to that session's perk *(tune)*, then reverts. The stones are reusable key items, one per species, from the shop or rare drops.
- **Form dex** *(0.20.0, owner's choice)*: each caught species' Pokédex entry lists all its forms, with a Regular / Shiny switch. A form counts once the player has had a Pokémon in it, as a regular and separately as a shiny: its own form (default or regional), the species' female form for a female (Meowstic, Indeedee, Basculegion, Oinkologne), and a Mega form while it holds that Mega Stone. Forms had stay after a transfer. Forms not had yet are the real sprite in black; Gigantamax and the other alternate forms (Rotom's appliances, Deoxys' formes...) cannot be had yet, so they are shown faded and not counted. Tapping a form the player has had shows it in the entry's scene, with its types, abilities and stats. Saves from before the form dex count a caught species' default form (and a Shiny Dex species' default shiny). Client-side data in the save like the Shiny Dex (`AppData.caughtForms`, `shinyForms`, rules in `FormDex`); the region's list shows its form total. Cosmetic forms (no sprites of their own) are not in it.
- **Cosmetic forms are a side collection** with their own progress bar and badge. 100% Pokédex means one of each species plus regional forms; cosmetic forms still appear when hatching.
- **Rarity by rule** from game data (implemented in `HatchRules`): a species' rarity is the strength of its whole evolution line (the highest base stat total of any default form in it). Lines at 580 or more (the pseudo-legendary families, plus Slakoth and Duraludon) are Super Rare; the rest are ranked and split 30% Common, 40% Uncommon, 30% Rare *(tune)*. Every member of a line shares its rarity. **Legendaries, mythicals, Ultra Beasts and Paradox Pokémon are not in any egg pool** (see below). Ultra Beasts and Paradox Pokémon are not flagged legendary in the source data, so they are listed explicitly *(proposal)*. Hand overrides are not planned, and the rule applies to the Home Region too. **Every region must have at least one hatchable species in every rarity tier**; a data test fails the build otherwise.
- **Art:** the sprites in the public PokeAPI/sprites repository: 96×96 pixel PNGs, front and shiny, for every form (`assets/sprites`, fetched by `tools/sprites/fetch.py`; 2,684 files, about 5 MB). Gen 6 to 9 sprites will not match the Gen 3 to 5 pixel look of the rest, which is accepted for now. Animated Gen 5 sprites can be added later for the species that have them. The same IP caveat as the rest of the project applies (see Risks), and the sprites' own terms should be checked before any public release.

### Legendaries and mythicals

Not hatched from random eggs, which keeps them special and avoids a 4 hour Deep Focus egg being 70% legendary.

- **Legendary quests:** each region has quests for its legendaries (the games' legendaries for that region). A quest unlocks at a trainer rank plus a share of that regional dex *(tune: about 80%)* and asks for a long strict focus session (Deep Focus) and a set number of paid tasks within a time window *(tune)*. Finishing it grants a Legendary Egg that hatches that specific species. There is no extra pacing: the requirements set the pace.
- **Mythical rewards:** milestone based (long streaks, task totals, top rank, full regional dex), one fixed species per milestone. Events can add more later.
- **Every legendary and mythical has a quest** *(decided by the owner)*. Mythicals with no in-game encounter get **long-term quests**: available fairly early, but slow to finish (weeks of steady use, not a single push). Legendaries in the Home Region are tied to its lore: the lake trio (Uxie, Mesprit, Azelf) stand for the core self-improvement ideas of the app, so their quests are written around those ideas (knowledge, emotion, willpower) rather than generic task counts. **Implemented (first pass, *(tune)*):** every legendary-class and mythical species has a quest, generated by rule with the lake trio hand-written.
  - *Legendaries:* three requirements counted from when the quest is taken (Deep Focus sessions, paid tasks of rank B or A, active days), scaled by the strength of the species (tier 1, 2 or 3 at rank 4, 6 or 8) and unlocked by catching a share of their region's ordinary species (50%, 60%, 70%). Uxie, Mesprit and Azelf are written around knowledge (focus sessions, goal milestones, weekly reviews), emotion (habit days, weekly reviews, active days) and willpower (Deep Focus, hard tasks, a 7 day streak), all at rank 4.
  - *Mythicals:* long-term quests from rank 2: 45 active days, 120 paid tasks, 1,200 focus minutes and 6 weekly reviews.
  - Up to three quests at a time, no deadlines. Finishing one lets the player claim the Pokémon, which joins the collection as a Guardian candidate. Progress is derived from the player's history, so nothing extra is tracked.
  - The 26 quest-only species in the Home list are confirmed as quest-only.
- **Species with a tradition of being obtainable in the wild** (the pseudo-legendary lines, Absol, etc.) stay in the egg pools, in the Super Rare tier by the rule.

### Regions

- The **Home Region** is the player's own list of favourites, kept in `tools/species/home-region.txt` (one name per line; edit it and run the generator). It started as the original app's curated 150 and is now the owner's own curated list of 171 species (59 hatchable entry points across all four tiers, plus 26 legendary-class and mythical species earned through quests and milestones). The dex is meant to be completed in about 2 months at about 5 eggs a day; the economy simulator checks that pace. It is the starter dex every player begins in, keeping its local numbers 1 to 150. Its ten legendary-class species (Mew, Celebi, Jirachi, Uxie, Mesprit, Azelf, Manaphy, Shaymin, Victini, Keldeo) leave the egg pool and become quest rewards, so its rarities are re-derived by the rarity rule like every other region.
- Every other region is a real game's regional dex: Kanto, Johto, Hoenn, Sinnoh, Unova, Kalos, Alola, Galar, Paldea. **All are in at launch.** Regional dexes are data (lists pointing at species), generated from a public dataset; other regions use the rarity rule.
- A Pokémon has one record with a **National Dex number**; a region is just a list of those. Regional forms hatch in their home region (Alolan forms in Alola, and so on).
- **Soft pity:** within a rarity, species the player has not caught yet are 3 times as likely to be picked (rarity odds are unchanged), so the last few species do not take forever. "Caught" is permanent, even after a transfer.
- **Where a region's eggs start:** a species can hatch in a region if its pre-evolution is not in that region's dex (a region that has Pikachu but not Pichu can hatch Pikachu), so no region depends on another to be completed. Hisui's dex is merged into Sinnoh, and Hisuian forms hatch there.
- **Region picker:** before a focus session the player chooses which region the egg comes from. Eggs hatch from that region's pool only. Each region has its own dex progress bar, badge and background theme.
- **Unlock by trainer rank** (below). The National Dex is the combination of all regions.
- Mega / Gigantamax forms are available through the region where they were introduced (Kalos for Mega, Galar for G-Max, etc.) *(proposal)*.

## Pacing and collection layers

Collecting is **fast at the start and deep at the end.** The simulator tunes toward these targets (at roughly 5 eggs and 5 tasks a day):

- **Basic dex of the Home Region (every species caught, including evolutions): about 2 months** *(tune)*. Each later region is about the same once unlocked.
- **Region unlocks:** trainer rank opens a new region about every 3 to 4 weeks *(tune)*, so a player is usually collecting in more than one region.
- **Checked by simulation** (`docs/economy-report.md`, regenerate with `./gradlew :plan-core:simulateEconomy`): a typical player (6 days a week, about 5 tasks and 2 sessions a day) completes the basic Home dex at a median of day 62, opens a region every 20 to 28 days, and earns enough Rare Candies to evolve the whole Home Region in 45 to 86 days and enough Super Candies in about 20.
- **The long tail** (months to years) is made of layers, each with its own progress, badge and showcase:

| Layer | Goal |
|---|---|
| Basic dex | one of each species caught |
| Form dex | regional, gendered and Mega / G-Max forms; cosmetic forms are the side collection |
| Shiny dex | a shiny of each species (1 in 128 per egg makes this the longest chase) |
| Perfect dex | a "perfect build" of each species: 31 IVs, an EV spread of 252 / 252 / 6, a favourable nature, a chosen ability. Competitive-style building, and the endgame use for Nature Mints, Bottle Caps, Vitamins and Ability Capsules |
| Ribbons | contest ribbons, legendary quests, mythical milestones |

Perfect builds are shown on a Pokémon's page and in the Hall of Fame; they have no gameplay effect beyond perks and contests.

## Spin-off inspired features

Planned, with build order to be set later.

- **Mission board (Mystery Dungeon):** tasks are shown as a job board using their difficulty rank. Presentation only; rewards stay as designed.
- **Trainer rank (Mystery Dungeon guild ranks):** one rank for the whole account, earned in every app: 2 points per tier of finished work (so 2 for an E task up to 10 for an A task) and 1 point per 5 minutes of effort. Ten ranks (Rookie, Apprentice, Trainer, Ace, Veteran, Expert, Elite, Champion, Master, Legend) at 0, 625, 1,375, 2,250, 3,250, 4,375, 5,625, 7,125, 8,750 and 10,625 points. Each rank opens the next region in order (Home, Kanto, Johto, Hoenn, Sinnoh, Unova, Kalos, Alola, Galar, Paldea) and reveals features (see the table above). Names and thresholds are *(tune)*, aimed at a new region every 3 to 4 weeks.
- **Personality quiz starter (Mystery Dungeon):** onboarding asks a short quiz, which suggests a starter and a nature; the player can override.
- **Poké Assist perks (Pokémon Ranger):** the buddy's type and ability perks are presented as assists. No extra mechanics.
- **Weekly contests (Contests / Pokéathlon):** a friends-only weekly event scored by a buddy's stats, nature and training, with ribbons. Comes with the online release and gives stats and natures a purpose.
- **Secret Base and photo mode (Secret Base, Pokémon-Amie, Snap):** decorate the buddy's home scene (with a berry garden) and take snapshots of the buddy or a hatch. After v1.
- **Focus view for v1 is the egg only** (calm and distraction-free). An expedition view was considered and left out.

## Buddy and perks

The buddy can be **swapped freely at any time**. Perks apply when a reward is earned, so a player can pick an Egg luck buddy before a session. Friendship is per Pokémon, which gives a reason to stick with one.

One party Pokémon is the **buddy**. It lives on the home screen and widget, reacts to the day (sleeps during focus, cheers when a task is done, droops when idle), and gives perks that scale with its level and friendship.

- **Type perk** (one per type, from the buddy's primary type; second type gives half strength *(tune)*). Starting mapping:

| Perk bucket | Types | Effect |
|---|---|---|
| Egg luck | Normal, Flying, Fairy | better rarity odds on hatches |
| Candy yield | Fighting, Rock, Ground, Steel | more Rare Candies from tasks |
| Super Candy chance | Psychic, Dragon, Ghost | higher chance of Super Candy |
| Item finder | Grass, Bug, Dark | chance of an item drop per task |
| Shiny hunter | Electric, Ice | small shiny chance bonus |
| Streak keeper | Water, Fire, Poison | a second missed day forgiven per week (on top of the free one) |

- **Ability perk:** one extra, from the buddy's ability. Real abilities as flavour, data driven. Examples: Pickup (extra item chance), Early Bird (bonus for the first task before 9am), Technician (bonus for 5 to 20 minute sessions), Magic Guard (one failed session a week keeps its egg), Guts (bonus on A-rank tasks), Synchronize (+25% EV gain in the buddy's focus stats).
- **Stats matter:** perk strength scales with the relevant stat (e.g. Attack for candy yield) and nature, so raising and training a partner pays.
- **Caps:** total bonus from perks, held item and party assists stays below +25% *(tune)* so no single buddy breaks the economy.
- **Shown, not hidden:** the Party screen has a **Party perks** card (each bucket's total, and opened, every part of it: the buddy's types, its ability, its held item, each assist and each Guardian, with when a conditional ability applies). Each Pokémon's page shows what it gives as the buddy and as an assist, and its held item with Give, Swap and Take. Items are also given from the Bag; the Bottle Cap and Nature Mint ask which stat or nature *(0.26.0)*.
- **Magic Guard** hatches the egg of a failed session of 5 minutes or more, once a week, paid as a session of the minutes focused; the session still counts as failed. **Streak keeper** at +10% forgives a second missed day a week, for the daily streak and habit streaks *(0.26.0)*.

## Guardians (legendaries and mythicals)

*Direction from the owner: legendaries and mythicals act differently from ordinary Pokémon. They slot into a **Guardian** role and give **big boosts**.* The numbers below are a first proposal *(tune)*.

- **A different role, not a bigger buddy.** Legendary-class and mythical Pokémon (the ones kept out of every egg pool) are **Guardians**. They cannot be the buddy, cannot join the party, cannot be transferred, and do not take Rare Candies or evolve: they are already at full power. They live in their own **Guardians** section of the Pokémon tab. They are earned through quests (see "Legendaries and mythicals"), never hatched.
- **Guardian slots.** The player slots Guardians into a small number of slots, opened by trainer rank: **1 slot at rank 4** (the same rank that reveals legendary quests), **2 at rank 6**, **3 at rank 9** *(tune)*. A slotted Guardian can be swapped at any time, like the buddy.
- **Big boosts.** Each Guardian has a **signature perk** in one of the existing buckets (Egg luck, Candy yield, Super Candy chance, Item finder, Shiny hunter, Streak keeper, Coins) worth **+10%**, plus half of that as a second perk from its second type where it has one. By default the bucket follows the Guardian's primary type, as for ordinary perks; lore gives overrides (Uxie: Super Candy chance for knowledge, Mesprit: Streak keeper for emotion, Azelf: Candy yield for willpower, Jirachi: Egg luck for wishes, Celebi: Streak keeper for time).
- **Outside the cap.** The ordinary perk cap (+25% per bucket from buddy, held item and assists) does not apply to Guardians: their bonus is added on top, up to **+15%** in a bucket from Guardians, so a bucket can reach +40%. A Guardian's perk does not depend on level or friendship, which is what makes it different from the buddy's.
- **Why.** The buddy is the one you grow and bond with; Guardians are the rare, hard-earned reward for sustained effort. A new Guardian changes how you play (which bucket do I want this week?) without adding a second thing to level.

## Retention

- **Forgiving daily streak:** kept alive by one paid task **or** one completed focus session that day. **Everyone gets one free missed day per week** that does not break it, and Home shows "active days this week" as a ring. Weekly goals, and a Pokédex with seen/caught, region completion rewards and a trainer card.
- Badges for milestones (tasks finished, focus hours), shown as gym badges.
- **First online release:** friends and profiles (view a friend's buddy, party and trainer card) and **weekly friends-only leaderboards** (focus minutes, tasks finished, resetting weekly). Trading and co-op focus come later.
- **Daily gifts:** send each friend one berry or small item a day; they open it as a gift. No cheers, comments or activity feed.

## Loop crossover

The two loops feed each other so task-only and focus-only players both progress.

- **Focus sessions also give coins and a berry** (amounts by minutes *(tune)*), not just an egg.
- **Paid tasks fill an Egg meter** (rank-weighted: E counts 1, A counts 5 *(tune)*). Every 10 points *(tune)* produces a **Task Egg** from the player's selected region, hatched instantly with a small ceremony. It rolls on the odds of a short (about 15 minute) focus session, so long focus sessions remain the way to the rarest hatches.

## Ideas adopted from other apps

Studied: Forest and Tiimo (focus and planning), Finch and Pokémon Sleep (self-care and live collecting), Pokémon GO, Habitica, Duolingo, Todoist, Sunsama, SuperBetter. **Avoided on purpose:** HP loss for missed tasks, leagues against strangers, guilt-based notifications, ads, and paywalled core features.

- **Day-load meter and "This evening".** Today shows planned minutes (estimated durations of today's tasks; tasks without an estimate count as 30 minutes *(tune)*) against free time (waking hours default 08:00 to 22:00, minus imported events). It is a calm bar; going over shows a gentle "That's a lot, move something?" and never a red alarm. Tasks planned for 18:00 or later, or flagged evening, sit under a separate **This evening** header.
- **Icon timeline.** The day view shows colour-coded blocks with an icon per task (defaults from the category, changeable). Focus sessions of 15 minutes or more give a quiet **halfway notification** (on by default), and always an end alert, to ease transitions. Both come from the app itself and never count as leaving it.
- **Daily mini-quests.** Three one-minute actions a day from a pool of about 30 system-defined ones (drink water, stand up and stretch, tidy for 2 minutes, text a friend, "open the task you've been avoiding", which starts a Just 5 minutes on your oldest unfinished task). Each is a berry *(tune)*, completing all three adds a small bonus *(tune)*. Honour system: tap when done. One re-roll a day. No streak on mini-quests. Appears at rank step 1.
- **Rest days.** The player marks a day or a range as rest (sick, holiday, burnout), up to 14 days a month *(tune)*. The streak pauses without using up the free missed day, habits skip, reminders are silenced, and the welcome-back gift still applies. No penalty and no reward.
- **Calm break after sessions.** After a completed session of 25 minutes or more, an optional 1 to 2 minute breathing moment with the buddy resting beside you, over a short original ambient loop. Skippable. Built as Care's calm break (`care-core`): friendship and a berry, up to 3 a day *(tune)*; see docs/economy.md.
- **Sleep and steps tie-in (after v1).** On Android, with permission, Health Connect sleep and step data can complete Health habits and goals automatically (for example a 7 hour sleep habit). Off by default, never required, no extra rewards beyond the habit's normal ones; only the result ("habit done") leaves the device.
- **Lures.** A type-specific Lure item (crafted from berries or bought) used before a session makes species of that type 3 times as likely in that egg's pool *(tune)*. It does not touch rarity or shiny odds. The lure is only used up if the egg hatches; a failed session refunds it. Rank step 2.
- **Spotlight days.** Each day a featured type (rotating through all 18) has species weighted 2 times in hatches *(tune)*, announced the day before. Missing one costs nothing. Seasonal events and a featured species can be added later. Rank step 2.
- **Party assists.** Non-buddy party members each add 25% of their type perk strength *(tune)*. Type perks only (no ability perks); the overall perk cap still applies, so the other five slots matter without breaking the economy.
- **Bond styles (from Voidpet Garden).** A Pokémon's nature decides how it likes to bond. **Presence** (Attack and Speed natures): focus sessions with it as buddy give double friendship. **Comfort** (Defense and Sp. Def natures): petting gives double. **Gifts** (Sp. Atk natures): berries give double. The five neutral natures are **balanced** and get +50% from every source. The nature → style table is data *(tune)*. Other care always works; the matching style is just faster. Shown on the Pokémon page. Rank step 3.
- **Weekly reflection prompt.** The weekly review asks one gentle question that rotates through a pool of about 50 ("What felt easier than expected?", "What are you proud of this week?", "What would make next week a little easier?"). Optional, private, no scoring and no analysis; answers are kept in the review history.
- **Optional mood check-in.** A one-tap "how are you feeling?" (five faces) on Home. Never required, no reward, and off by default (a "Track mood" setting). The buddy answers with a line of text. If on, a simple mood trend appears on the Growth screen. Private; never shared with friends.
- **Haptics.** Soft vibration when petting the buddy on Android (a setting turns it off). A **home garden** you grow from berries comes with the Secret Base scene after v1.
- **Not a health product.** Voidpet is built with therapists; PokéPlan borrows the warmth, not the claims. No diagnosis, treatment or scores about the person, and copy never promises a mental-health benefit.
- **Considered, not adopted:** body-double focus rooms (co-op focus stays a later release), a task breakdown helper, growth-profile evolution branching, expeditions.

## Notifications

Beyond task reminders: a **streak-at-risk reminder** in the evening if nothing has counted yet that day (on by default, easy to turn off); a **welcome-back gift** after 7 or more days away, with the streak simply starting fresh and no scolding; and a **weekly recap** on Sundays (tasks, focus time, category split). No buddy messages. All can be switched off in settings, and reminders are silenced during focus.

## Look and feel

- Gen 3 to 5 pixel art: GBA/DS-era text boxes and menus. **Pokémon are drawn with the Pokémon Mystery Dungeon style sprites of the PMD SpriteCollab** *(owner's choice, 0.11.0)*: the idle animation facing the viewer everywhere a Pokémon appears, its first frame where it stands still, and the PokeAPI and Black/White sprites only for the few forms SpriteCollab lacks. The buddy also uses SpriteCollab's Sleep (during a focus session, on Home and the widget), Hop (a pet or an event) and Eat (a berry) animations *(0.13.0)*, and **portraits** show a Pokémon's face beside text the way the Mystery Dungeon games do: on its page (Normal), beside the Focus timer (Determined, Surprised when the player leaves) and the hatch message (Joyous), and in the corner of Home for a real event (Happy for a task, Joyous for a hatch, Inspired for a shiny). Faces are only ever glad, set or surprised, never sad. A shiny without its own recolour of an animation or portrait keeps its idle sprite. Their licence (CC BY-NC 4.0) keeps the app non-commercial and asks for credit, shown in small faint text under each species' Pokédex entry.
- Hatch, level-up and evolution are full ceremonies (egg wobble, text box lines, cry, flash), skippable.
- Crisp scaling: nearest-neighbour, integer scale, no blur.
- Fonts: pixel font for headings only; body text stays a readable font.
- Sound: cries, short jingles; a mute toggle (Settings → Sound and its volume). *(0.23.0)* The buddy calls out when you pet it, when Home celebrates a real event, and when it is made your buddy; every Pokémon cries on its page, in the Pokédex, when it hatches and when it evolves. A few real game sound effects (`assets/sfx`) mark single moments, each kept for one kind: the menu select blip when a task is ticked off, a hop for each rock of a hatching egg, the healing chime when a berry or item is given, the run-away sound when a Pokémon is transferred, and the Pokémon Center jingle after an evolution's cry. None plays on a timer or a plain tap.
- Interface rules from the Laws of UX review (0.19.0, `docs/laws-of-ux-review.md`): Back sits at the start of every header; on/off settings are switches whose whole row is the target; a choice is a radio row, never an action button; each screen has one solid primary action and the risky ones (Give up, Transfer, Remove) stay quiet until confirmed; Undo lives in one bar floating above the tabs; Focus keeps Start pinned at the bottom; text fields submit from the keyboard's Done key.
- Accessibility: content descriptions on all images, text scaling, colour-blind safe type chips, a reduce-motion option, a dyslexia-friendly font option, and every ceremony skippable.

## Screens and flows (proposal, to confirm)

Navigation is five bottom tabs on the phone: **Home / Planner / Focus / Collection / Me**. Desktop and web have no Focus tab (four sections).

- **Home:** a small pixel scene where the buddy wanders, idles and reacts to events, with a speech bubble. Tap the sprite to pet it (friendship, small daily cap); a berry button feeds it. There is no separate close-up screen. Streak ring (active days this week), today's top tasks (tap to complete) and a "Pick for me" button, a "Start focus" button at the default length, small candy and coin counters.
- **Planner:** Inbox, Today, Upcoming, All and Calendar views (lists can show as a mission board; the calendar has drag-and-drop planning), category and list filters, search, quick-add sheet (title, difficulty, due, then "more" for category, recurrence, subtasks, reminder). Completing a task opens a reward sheet with the 10 second undo.
- **Focus:** optional task to focus on, region picker, presets (15 / 25 / 45 / 60) plus custom, Deep Focus toggle, a short reminder of the strict rules, the egg during the session, the emergency pause button. On finish: hatch ceremony, then a summary card with actions (set as buddy, keep, transfer).
- **Collection:** Party (6), PC boxes (30 each), Pokédex (National Dex, seen / caught, forms tab, cosmetic-forms collection), Bag. The Pokémon detail screen shows stats, nature, ability, an IV "judge" (qualitative, like the games), training plan, held item, and actions: feed Rare Candy, evolve, transfer, nickname, set as buddy.
- **Me:** trainer card (badges, streak, totals), the Growth screen, weekly review history, shop, friends and leaderboards (once online), settings (reminders, sound, theme, account linking, data export).
- **Onboarding:** title → start as guest → personality quiz suggests a starter (player can override) → name the trainer → a **1 minute tutorial focus session** that hatches a free egg → create a first task, which **pays immediately** despite the minimum age (tutorial exemptions). The first reward arrives within about two minutes of opening the app.
- **Offline:** everything works offline. Because the server grants rewards, offline completions show as "pending" and resolve on sync; a focus session's start and end times are sent and validated for plausibility.

## Android widgets

1. **Today's tasks and habits:** checklist, tap to complete.
2. **Buddy + streak (2×2).**
3. **Agenda:** upcoming tasks, optional read-only device calendar events.
4. **Start focus:** one tap, starts a default session.

Local-first: the app keeps a local database and syncs to Supabase, so widgets and offline use work. Completions from the widget queue and the server grants the reward on sync; the app shows what was earned next time it opens.

## Platforms

Android (full, plus widgets). Desktop and web are **companions**: planner, calendar, collection, Pokédex, bag, shop, party management, friends and leaderboards, but **no focus timer** (people work in other windows there, so strict focus can't apply). Eggs are hatched on the phone and appear everywhere via sync. No widgets on desktop or web.

**Priority (owner's, 2026-10-25): Android first; desktop and web are deprioritised.** They keep compiling from the shared code (CI checks it on every PR, so they never quietly break), but features that only serve them (calendars through an iCal link, web push reminders, mouse-specific timeline work) wait, and testing happens on the phone.

## Platform and apps

PokéPlan is the first app of a planned **family of personal Pokémon apps** (a health-focused one next, others later) on **one account and one database**. The same Pokémon, items, dex progress and trainer rank appear in every app.

- **The platform (shared by all apps):** account and trainer profile (name, rank, badges), the Pokémon collection and the buddy, the inventory and currencies (candies, coins, berries, items), dex progress and regions, friends, the hatching / evolution / transfer engines, perks, species data and sprites.
- **An app:** its own data and screens. It turns its own activities into **activity events** ("30 minute workout", "task finished at rank C", "7 hours of sleep") and the platform turns each event into rewards (eggs, candies, items, coins) on the server, idempotently. An app never writes Pokémon or currency rows itself.
- **Examples:** PokéPlan maps focus sessions to eggs and tasks, habits and goals to candies and items. A health app could map workouts, steps, sleep, hydration and medication to the same rewards. Mappings are per app; the reward engine is shared.
- **One economy:** a single pool of candies, coins and items, so effort anywhere strengthens the same Pokémon. Each app has a daily reward limit *(tune)* so no app floods the economy, and every reward is a ledger row recording the app and the source, for auditing.
- **The buddy works in every app.** Perk buckets stay the same (egg luck, candy yield, ...) and each app applies them to its own activities.
- **Privacy:** health data is sensitive. It lives in its own schema, is readable only by its owner, and never appears to friends or on leaderboards. The platform only sees reward events ("workout done, 30 minutes"), never the details behind them. The no-health-claims rule applies to every app.
- **Database:** one Supabase project with a schema per area (`platform`, `plan`, `health`, ...), row-level security on every table, and one login shared by all apps (each app is its own client with its own bundle id).
- **Code:** Gradle modules `:pokemon-core` (species data, Pokémon, items, perks, engines) and `:plan-core` (tasks, habits, goals, focus) and `:care-core` (routines, calm breaks, mood; docs/ecosystem.md, section 6), with future apps adding their own `:health-core` and so on. Nothing in `:pokemon-core` knows about tasks or focus sessions.

## Backend (Supabase)

**Accounts:** players start as a guest on the device (Supabase anonymous sign-in) and can later link an email or Google account to sync and keep progress. No passwords are stored in the app.

Server-authoritative for everything that grants value: hatching, candies, items, transfers, shiny rolls. Tables are grouped in schemas per area (see Platform and apps): `platform` holds the shared trainer, Pokémon, inventory and reward ledger; `plan` holds PokéPlan's tasks, goals, habits, sessions and reviews. Clients send intents ("finished task X", "hatch session Y") and the server validates and rolls. Row-level security so a user only reads and writes their own rows. Species, base stats, abilities and perk tables are data shipped with the app and mirrored on the server.

The cloud save is Plan's own data (`plan.saves`, without the game state). Every reward row on the server records the rules version (`RewardRules.VERSION`) that made it; every reward source, use and cap is listed in `docs/economy.md`.

## Data to add

- Per-species base stats, abilities, types, evolutions and forms for **every species through Gen 9**, generated at build time from a public dataset (needs the build environment to reach it). Species use National Dex numbers; the Home Region keeps its own local numbering.
- Regional dex lists for all regions, as lists of National Dex numbers.
- A rarity for every species, assigned by rule (see Roster).
- Ability → perk table, type → perk table, item table, all as plain data with tests.

## Milestones

1. **Shared core (done):** eggs, candy, levels, evolution, Pokédex. Split into `:pokemon-core` and `:plan-core` so more apps can reuse the Pokémon side.
2. **Species data pipeline:** generate species, base stats, abilities, forms, evolutions, regional dex lists and rarity (by rule) for Gen 1 to 9 from a public dataset, plus the sprite fetch step. The 150 become the Home Region; National Dex numbers.
3. **Pokémon depth:** IVs, EVs, natures with stats, gender, abilities, shiny, friendship, items, evolution triggers, transfer yields, perk calculation. Unit tested in `:pokemon-core`.
4. **Planner core:** task model, difficulty ranks, Today / Inbox logic, recurrence expansion (time zones and daylight saving included), habits and their streaks, goals and milestone rewards, reminders schedule, the quick-add parser, task-focus link, review and insight calculations. Pure Kotlin in `:plan-core`, heavily unit tested.
5. **Economy simulator (done):** a JVM tool depending on both cores that plays months of tasks and focus sessions to tune every *(tune)* value against the pacing targets before the UI is built on them.
6. **UI shell:** desktop and web companion screens (no focus timer; needs `dl.google.com` allowed in the environment).
7. **Backend:** Supabase schema, RLS, reward functions, sync.
8. **Android app and widgets** (needs the Android SDK).
9. **Online:** friends and weekly leaderboards first, with weekly contests; then trading and co-op focus.
10. **After v1:** Secret Base decoration, photo mode, and the Health Connect sleep and steps tie-in.

## Risks

- **IP:** names, sprites and cries are Nintendo / Game Freak / The Pokémon Company property. **Distribution is private: the owner and friends** (sideloaded APK, desktop builds, an invite-only web app), which keeps exposure low. A public release or store listing would likely be taken down; species stay data so an original-creature skin remains possible.
- **Health claims:** the app must not present itself as therapy or a treatment. Mood tracking and reflection are optional and private.
- **Strict focus** can frustrate users with legitimate interruptions. Calls exempt; revisit after use.
- **Balance:** perks, items and transfer yields compound. Simulate a few months of play before fixing numbers.
- **Launch scope:** all regions at launch means a large data set to generate and test, and every region needs its theme, music and badge. Region content is data, so the code does not grow with it, but QA does. If time runs short, the order to ship is Home, Kanto, Johto, then the rest.
- **Planner scope:** a full drag-and-drop day / week planner, lists, recurrence with edit-this-or-all, and device calendar import make the planner a product on its own. It is the part users judge the app by, so it needs its own testing (recurrence and time zones especially).
- **Scope:** stats, items, perks and widgets are a lot. The data pipeline and Pokémon depth should stay behind a clean interface so the UI work can start in parallel.

## Defaults (proposal, change if you disagree)

- Streak day rolls over at 4am local time.
- A recurring task pays at most once per day, and recurrence is never more frequent than daily.
- Linking a guest to an account that already has data asks which to keep; no merging.
- Completing a task from the widget uses the same 10 second hold; the app shows a notification with Undo.
- Reminders are silenced during a focus session.
- Settings include account deletion and a data export.
- Cheating: server sanity checks only (plausible durations, one session at a time); friends-only leaderboards make stronger anti-cheat unnecessary.
- Offline hatching uses server-issued "egg tickets" (random seeds spent by the client, verified on the next sync) so an egg still hatches without a connection; the rule is under Focus.
- Game rules run on the server by compiling the shared Kotlin module to JS; a spike confirms this before the backend milestone.

### Planner defaults

- **Time zones:** planned and due times are local wall-clock times. Changing time zone keeps the wall-clock time; recurring tasks keep it across daylight saving. Imported events keep their own zone.
- **Recurrence:** every N days / weeks / months / years; chosen weekdays; monthly on a date, on the nth weekday or on the last day; on a **fixed schedule** or **N days after completion**; ends never, on a date, or after N times; skip one occurrence; edits ask "this one / this and following / all". Never more often than daily. Instances are generated 14 days ahead. A recurring task shows its own streak and completion history (no extra reward beyond the global streak).
- **Reminders:** up to 3 per task, absolute or relative (at the time, 10 minutes, 1 hour or 1 day before); snooze (10 minutes, 1 hour, tomorrow); quiet hours default 22:00 to 07:00; silenced during focus; web push on the web; every signed-in device notifies, and dismissing on one clears the others when synced.
- **Calendar:** all-day items sit above the timeline. **Day timeline (shipped, 0.9.0):** Planner → Day shows today up to 13 days ahead; untimed tasks of the day (on today also the overdue ones) and the inbox sit in a tray above the hours; tap a task, then tap an hour to plan it at that hour, then nudge by 15 minutes; tapping a block picks it to move it or take its time away; a block lasts its estimate (30 minutes without one) and overlapping blocks share the width; the waking window (08:00 to 22:00) is shown and stretches to fit; the day-load line sits above it. **Dragging and the week (shipped, 0.14.0):** the view is Planner → Calendar, with Day and Week. Holding a task (a long press on touch; with a mouse it drags at once) and dragging it from the tray onto the day or week timeline sets its planned date and time; dragging a block moves it (across days in the week); dragging its lower edge (the bottom 12 dp) sets the estimate; everything snaps to 15 minutes, a block starts by 23:45 and never runs past midnight. While dragging, the slot it would land in is outlined, the task rides above the finger with its time, and the page scrolls when the finger nears an edge. Tap-to-place stays as the accessible route, and the picked task gets Shorter and Longer (15 minutes) next to -15 and +15. The week is the next 7 days from today (and the 7 after), all on the same hours; each block shows only its icon, and tapping a day name opens that day. **Own events (shipped, 0.15.0):** New event on Planner → Calendar puts an event on PokéPlan's own calendar (title, day, start, length; it starts at the next hour on today, 9:00 on other days, and lasts an hour; tap an hour or nudge by 15 minutes before Add). Events are for appointments and blocks of time that are not tasks: they are never ticked off and never pay. They are saved and synced with the rest of the save (`AppData.events`), sit on the hours in teal sharing lanes with the tasks and the phone's events, are dragged and resized like tasks (tap one to rename, nudge, change its length or delete it), never run past midnight, and the time they take inside the waking window counts as busy in the day-load line. They are not written to the phone's calendars. Recurring own events shipped in 0.24.0 (below). **Layout (0.19.1, after Fantastical, Apple and Google Calendar and Structured):** the day or week shown and ‹ › arrows on one line, a Day / Week segmented switch with New event, and Today once moved away; a day opens with a strip of the 7 days around it (tap to open, a dot for a day with plans, days past 13 ahead greyed); the untimed tasks and the inbox share one "To plan" card of chips that scroll sideways; the calendar is one panel (week day header, all-day band, then the hours with labels on the lines, dashed half hours and a now-line with a dot); blocks are a soft tint with a solid left strip, a semi-bold title and their start and end time; in a week a block shows its icon and, when its lane is 36 dp or wider, its title on one clipped line. Device calendars: the player picks which to show; events are read-only, recurring events expanded, never paid; private events show as "Busy" on widgets. **Calendar import (shipped, 0.12.0, Android):** Google Calendar reaches the app through the phone's own calendars (its account syncs there), so Settings → Calendars asks for the calendar permission and lists the phone's visible calendars to tick. Their events are read from the phone each time the day is shown, never saved, synced or paid; declined and cancelled ones are left out. Timed events sit on the hours in their calendar's colour, sharing lanes with the task blocks; all-day ones sit above the tray; the time they take inside the waking window counts as busy in the day-load line. Which calendars show is kept per phone (calendar ids differ between devices). Desktop and web have no device calendars; a Google Calendar secret iCal link is the likely route there, deprioritised with the other desktop and web work (see Platforms).
- **Month view and all-day events (0.22.0, after TickTick, Google Calendar and the owner's own calendar use):** Planner → Calendar switches Day / Week / Month. The Month is a grid of whole weeks (Monday or Sunday first, from Settings) for any month (‹ › and Today): an all-day event over several days is one solid bar across them in its colour, white title, longer ones on top (`SpanLayout` lays bars in lanes); one-day all-day events are short bars; timed events and open tasks sit on their day as labels on a soft tint of their colour; at most four lanes a day, then "+n". Every week keeps room for two lanes. Tapping a day shows its agenda under the grid (all-day bars, timed events by start, tasks) with Open day for today and the 13 days after. In the Week, all-day events are bars across their days too, and in the Day they wrap beside "all day". **Own events can be all day** ("Home" over a weekend, "WFH" for a day): New event has At a time / All day, and an all-day one has a first and last day (‹ › each); every own event has one of the eight list colours (teal by default). All-day events are never busy time, never pay, and are synced with the save (`PlanEvent.allDay`, `lastDay`, `color`). Tapping an own event's bar edits it (rename, colour, days, At a time, Delete); a timed one gains All day. New event in the Month starts an all-day event on the chosen day.
- **Recurring own events (0.24.0, after Google Calendar and TickTick):** an own event, timed or all day, can repeat: Once, Daily, Weekdays, Weekly, Every 2 weeks, Monthly or Yearly, each following its first day (Weekly on its weekday, Monthly on its date or the last day of a shorter month, Yearly on its day and month), never more often than daily. New event and the event panel have a Repeats row; a repeating one never ends or ends on one of its occurrences (Set an end keeps ten from the one shown, then ‹ › one occurrence at a time; it shows how many there are in all). Each occurrence shows on the Day, Week and Month with an id of its own (`PlanEvent.occurrences`, the series id plus its date), and an all-day one keeps its length. Edits from the panel (title, colour, time, length, days, All day or At a time) are made to every one; moving one on the calendar (dragging it or tapping another hour) moves just that one, which leaves the series as a one-off event. Delete asks Delete this one (that date is skipped) or Delete all. Changing how a later occurrence repeats keeps the ones before it: the series ends the day before and a new one starts there with the new rule (Once makes it a one-off and the last one). Like every own event, they never pay and are never busy time when all day. Saved and synced in the save (`PlanEvent.repeat`, `until`, `skipped`).
- **Planner menu and lists (0.19.3, after Apple Reminders, Things, Todoist and TickTick):** the menu is a one-field quick-add (the day chips and More options appear once something is typed) above six tiles, two to a row, each with its item icon, its count large at the right, its name and one quiet line (Today: how many are from before; Habits: done today out of all; Done: finished today; Calendar has no count). In a list each group is one panel with rows split by lines, under a heading with its count. Today opens with a thin "x of y done" bar (finished today against all of today), then "From before" (overdue, amber heading, one quiet "Move to today" for the ones planned on an earlier day; a passed deadline stays), "Today" and "This evening" (from 18:00, or marked evening); when nothing is left and something was finished, it says "All clear for today". Soon is grouped by day (Tomorrow, then the date), Done by the day each task was finished (Today, Yesterday, then the date). A row leaves out the day its group is on and shows only its time; a finished row shows no plan.
- **Lists:** optional, user-created (name, colour, icon). A task has at most one list and one category. Archiving a list hides it. Lists carry no reward. **Shipped (0.21.0, after TickTick, owner's choice):** the **Inbox is the tasks in no list**, whatever their date (it was the undated tasks before); the menu's six tiles are the smart lists (Today, Calendar, Inbox, Next 7 days (was Soon), Habits, Done) and below them a **Lists** section: one row per list (its item icon on a soft tint of its colour, its name, its open count), New list, and the archived lists folded under "Archived (n)". A list (and the Inbox) groups its open tasks by when they are: From before, Today, Tomorrow, Next 7 days, Later, No date (`TaskViews.byWhen`). Quick-add inside a list files the task there; `#Name` in quick-add files it in that list when one has the name. A task's options have List chips (Inbox and each list); in the smart lists a row shows its list's colour dot and name. Edit list: name (40 characters), eight colours, sixteen real item icons, Archive, Delete (its tasks go back to the Inbox). The Calendar's "To plan" card calls its second group "No date" (open tasks with neither a planned nor a due date, `TaskViews.undated`). Saved as `AppData.lists` with the rest of the save.
- **Search and filters:** text search over title and notes; filters by category, list, rank, status and date range.
- **Quick-add:** one natural-language parser shared by every platform, e.g. `Call mum tomorrow 5pm #Social !B` (`#` category or list, `!` rank). Also available from the widget and the share sheet.
- **Task states:** open, done, **won't do** (no reward, hidden from stats), deleted (recoverable for 30 days). A reward is tied to one completion event; reopening and re-completing a task never pays twice.
- **Locale:** week start and 12 / 24 hour follow the system, with an override. English only for v1.

### Pokémon defaults

- **Gender:** stored per Pokémon, rolled at hatch from the species' ratio (genderless supported). Affects gendered forms and sprites and gender-dependent evolutions only.
- **Friendship:** 0 to 255, starts at 70. Raised by petting (+2, up to +10 a day), a focus session with that buddy (+3), a paid task finished with that buddy as buddy (+1, up to +10 a day), a berry (+1) and each level gained (+1). Never lowered, no decay. Effects: perk strength scales from ×1.0 at 0 to ×1.2 at 255, friendship evolutions at 220, and the buddy's mood. The amounts above are doubled (or +50%) when the care matches the Pokémon's **bond style** (see Ideas adopted).
- **Contests (come with the online release):** five condition stats (Cool, Beautiful, Cute, Smart, Tough), 0 to 255 each, built from the species' base stats, training and nature, then raised by feeding berries (a berry liked by the nature gives double, a disliked one half; up to 5 a day). Each week one condition is judged; friends' buddies are ranked by it, plus ribbon bonuses. Prizes are coins and ribbons. Numbers *(tune)*.
- **Abilities and perks:** about 40 curated abilities have a perk; every other ability falls back to the type perk only.
- **Endgame:** a level 100 Pokémon takes no more candies. Candies still level other Pokémon. Coin sinks: Tera Shards, Mega Stones, form items, Secret Base decor. Candy holdings cap at 9,999; excess converts to coins at 1:1 *(tune)*. Contests, ribbons and a hall of fame give maxed Pokémon a purpose.
- **Pokédex text:** the latest English entry from the public dataset. **Seen** = appeared on a friend's buddy or on an open quest (`AppData.seenSpecies`, client-side, kept for good); **caught** = ever owned; **owned** = in the party or PC now (`DexState`). Caught species show their sprite and entry, with a Poké Ball on the owned ones; seen ones stay a silhouette but are named when tapped; the rest are a silhouette marked ???. Every tile names its state in words *(0.16.0)*. Each caught species opens its own page *(0.18.0)*: category (genus), height, weight and entry from the dataset, types, abilities (hidden marked), base stats, and its evolution line with how each step works in the app (`EvolutionRules.howTo`); members not found yet stay a silhouette marked ???. Previous and Next skip species not found yet. The list has a completion bar and All, Caught and Not caught filters.
- **Pokédex entry scenes** *(0.19.5)*: a caught species' entry shows it in the real battle scene its first type gets as a buddy on Home (`BuddyScene.of`: Water on the beach, Grass and Bug in the forest, Electric on the thunder plains...); a species only seen keeps the plain wallpaper, so the scene never gives its type away.
- **Hatch and evolution scenes** *(0.18.0)*: a hatch plays full screen, once per new egg wherever it came from (focus, Task Egg, widget): "Oh?", three ever harder wobbles of the egg, a flash, the Pokémon and its cry. An evolution shows "What? X is evolving!", the sprite as a white shape flickering between the two forms ever faster, a flash and "Congratulations!". A tap or Skip jumps to the end; Reduce motion shows only the end. Real sprites and the item egg only; the white shapes are the real sprites tinted.
- **PC and bag:** unlimited PC boxes of 30; bag stacks to 999.

### Platform and operations defaults

- Friends are added with a friend code or invite link; players can block; profiles show the buddy, party, trainer card and aggregate stats only, never task content.
- No third-party analytics. Crash reports only, opt-in.
- Tasks are private to the owner (row-level security); leaderboards expose aggregate numbers only.

## Open questions

Being settled in rounds before any Pokémon-depth code is written. Decided: everything above not marked *(tune)* or *(proposal)*.

- **To confirm:** the Screens and flows proposal; the Defaults above; Mega / G-Max region assignment; S rank staying non-selectable.
- **To tune by simulation before release:** rarity rule parameters across ~1,025 species, coin amounts, transfer yields, perk magnitudes and cap, catch-up discount, minimum task age, Mega bonus, rank names and thresholds, region unlock order, legendary quest requirements, E to S rank mapping.
- **To check:** Showdown sprite terms and build-time availability, whether the build environment can reach the data sources, Android permission flow for calendar import.
