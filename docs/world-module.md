# PokéLife World: the module in full

Written 1 November 2026, at version 0.101.0. This is the one-stop description of World: what it is, what exists today, what is planned, how it fits with the rest of PokéLife, and its storylines and quests. The design source documents are `docs/world.md` (the original concept and the Kanto, Johto and Hoenn designs), `docs/ecosystem.md` (World regions, the atlas, the decisions), `docs/regional-storylines.md` (the owner's story brief), `docs/economy.md` (every number that pays) and `docs/world-economy-report.md` (the simulation).

---

## 1. What World is

World is the **dungeon game** of PokéLife: a Pokémon Mystery Dungeon style exploration game that runs on its own while you live your life. You build exploration teams from your own Pokémon, send them into dungeons, and they climb floor after floor, online or offline, bringing back berries, Poké, treasure boxes, Rare Candies and wild Pokémon to recruit. You raise an Exploration Rank, take jobs from a mission board, build a small base, and work through storylines that end in a Guardian: a legendary Pokémon.

Name note: **World** is this game. **Explore** is a different, planned module (real-life trips and a travel journal).

The design principle (from the concept): *do not make an idle game where players collect Pokémon; make a Pokémon exploration game that happens to have idle mechanics.* Pokémon are persistent individuals from your collection, never disposable workers; their types, levels and abilities matter.

Tone rules World follows like the rest of the app: gentle, no punishment for missed days (progress is capped, never lost), real game assets only, no chores.

---

## 2. How World slots into PokéLife

PokéLife is a life companion first and a Pokémon game second. Its structure is: **modules** (each a life domain with its own rules) that all meet in a **shared world** (one trainer, one collection, one Bag, one reward log). World is the only module whose job is the game itself.

**What World shares with the rest of the app**
- **The collection.** A team is made of Pokémon you already own, wherever they came from (Focus eggs, Care, Plan, quests). A Pokémon recruited in World is the same persistent Pokémon, added to the shared collection (the PC; it never takes a party slot by itself).
- **The Bag.** Berries, treasure and Rare Candies World finds go straight into the shared Bag. There is no World-only inventory.
- **The reward log.** World's payouts are written to the reward history (`World: exploring`, jobs, research, treasure) like every other source. They are never shown as pop-up reveals.
- **Guardians.** Legendaries World gives join the shared Guardian collection and can be slotted (rank-gated slots) for the shared Guardian perk bucket, like any other Guardian.
- **The Pokédex.** Recruited Pokémon count as caught; species found while exploring count as discovered in World's own research notebook.
- **Home.** Home's "Recently" list and the reward box show World entries ("X joined you", never "hatched").
- **Surfaces.** A World widget on Android (rank, what each team is doing, finds waiting, jobs ready), a job-done notification, and a Place the launcher, shortcuts and notifications can open.

**What World keeps to itself**
- Its own progression (Exploration Rank, XP, skills, facilities), its own currency (Poké), its own storylines and Guardians, its own acquisition method (recruiting Exploration Finds, never eggs).
- Its save: `AppData.world` is client-side data in the save, kept by `GameStore`'s `world*` functions; it is **not** in the server's game state, and World pays through the Bag, not the server reward engine, so the reward rules version is unaffected.

**How it relates to the other modules today**

| Module | Relationship |
|---|---|
| **Plan** (tasks, habits, goals, Focus) | Indirect. Focus and tasks make Pokémon stronger (levels from Candies, evolutions), and a stronger team explores faster. Plan has its own legendary quests for every legendary (see 7.3). Nothing Plan does advances a World journey. |
| **Care** (routines, mood, calm) | Indirect, through the Bag: berries World finds are the berries Care's visitors like and spend. Care's mood and journal never reach World. |
| **Focus eggs / hatching** | None. World never gives eggs and never plays a hatch scene; a recruit is a "joined you" moment. |
| **Pokémon / PC / Party** | World reads the collection to build teams and writes recruits and Guardians into it. A Pokémon on a team is "away" for team purposes only. |
| **Grow, Home (planned modules)** | Not built. Each is expected to get its own side legendary as a Guardian (see 9). |
| **Explore (planned module)** | A different module (real trips); it will share the name space only. |

**A standing decision (owner, 1 November 2026): the journey advances only through World's teams.** Module quests and tasks do **not** feed a World journey or "travel steps". The regional storylines brief proposed that; the owner chose to keep World self-contained. Modules touch World only through the shared Bag, collection, Guardians and the Pokémon's own growth.

---

## 3. Core loop

1. **Assign a team** (up to 4 Pokémon, per rank 2 to 4 teams).
2. **Send it to a dungeon.** It climbs floors on its own; each floor takes time (longer for deeper dungeons, shorter for a stronger or better-suited team).
3. **Come back** (any time). Progress is computed deterministically from the time away, capped at 12 hours per open, so offline progress is exactly as good as staying. A clock set back freezes nothing and loses nothing.
4. **Collect.** Floors give Exploration XP, Poké, berries, treasure boxes, milestone rewards (including Rare Candies), and occasional wild Pokémon waiting to be recruited.
5. **Spend and grow.** Poké buys facilities, training and Rare Candies; XP raises rank (more teams, deeper dungeons); jobs and research pay extra.
6. **Progress the story** by clearing different dungeons at the right rank, ending in a Guardian.

Team speed: a team's power is the sum of its members' levels, with 20% more for each member of a type the dungeon favours. A floor takes the dungeon's base time divided by (1 + power/100), never under a quarter of the base. Rank 4 teams of strong Pokémon clear early dungeons in minutes per floor.

---

## 4. Features: what exists today (0.101.0)

### 4.1 The map and its layers
- A **layer switch** on the Map screen: the **Mystery Dungeon world** (home layer, 9 dungeons), **Kanto**, **Johto**, **Hoenn** and **Sinnoh** (9 dungeons each). All open from the first day. Each layer has its own picture, markers, story and Guardians; teams, rank, Poké and the Bag are shared.
- Maps: the home layer's own Mystery Dungeon world map (revealed in eight stages as you clear dungeons); the **official game maps** for the others (Kanto: FireRed/LeafGreen; Johto: Crystal's Pokégear; Hoenn: Emerald's PokéNav), built from the games' own tiles. Markers are yellow (open), green (cleared) or grey (not yet open).
- Each dungeon card is drawn in real Mystery Dungeon tilesets with its wild Pokémon walking the floor; shows floors, rank needed, favoured types, deepest floor, clears, and a button to send your strongest team. A "Next up" suggestion on the World landing says what to do next and why.

### 4.2 Dungeons, floors and rewards
- 36 dungeons across four layers (listed in section 6). Each has floors, a base floor time, a rank gate, a story-chapter gate, favoured types, a wild table and milestone rewards (a berry mid-way, Rare Candies and Poké at the top).
- Every floor can give: Exploration XP, Poké, a berry (about 70%), a treasure box (about 6%; silver at the middle, gold near the top), and a wild Pokémon (about 3%) drawn from the dungeon's table. Only Pokémon that can hatch are found; **legendaries and mythicals are never found**, they come from quests.
- Dungeon clears are counted per dungeon; "different dungeons cleared" is what opens story chapters (so one cheap dungeon cannot be farmed).

### 4.3 Expedition teams
- Teams and slots by rank: Normal 2, Bronze 2, Silver 3, Gold 3, Platinum 4, Diamond 4.
- Assign up to four Pokémon per team (a Pokémon belongs to one team at a time), send, recall, "Send my strongest" (picks the best free Pokémon).
- Pokémon that leave the collection (transferred) leave their team automatically.
- Abilities help skills: each team member with a helpful ability (for example Pickup or Honey Gather for Foraging, Keen Eye for Scouting, Swift Swim or Run Away for Exploration) counts as a skill level, up to 3.

### 4.4 Exploration Finds and recruiting
- Wild Pokémon found on floors wait in **Finds**. Recruiting one adds it to the collection as that persistent Pokémon with a World origin. No egg, no hatch scene. Finds also count in the research notebook.

### 4.5 Rank, skills and research
- **Exploration Rank** from XP: Normal 0, Bronze 300, Silver 1,500, Gold 6,000, Platinum 20,000, Diamond 60,000. Rank opens dungeons and team slots.
- **Skills** (level 0 to 10, XP of 30 x level squared): **Exploration** (+2% Poké per level), **Foraging** (+1% berry chance per level), **Scouting** (+0.3% find chance per level). Raised by use and by Dojo training.
- **Research:** species discovered while exploring count towards milestones at 3, 6, 10, 15, 25, 35 and 45 species, each paying Poké, berries or Rare Candies.

### 4.6 The mission board (jobs)
Opens at story chapter 2 of the home layer. Up to 3 jobs held at once; a job that can be repeated comes back after a wait (14 days for most, a week for the weekly patrol), the daily berry run every day.
- **Explore** (reach a floor of a dungeon), **Find items** (collect berries), **Rescue** (one-time: a lost Pokémon on a given floor), **Deliver** (hand over berries), **Escort** (needs a team of a size), **Defeat** (needs team power).
- Today 25 jobs, all in the home layer. Jobs are about a third of an engaged player's Poké and XP.
- A **job-done alert** (Android notification, Settings switch, on by default): the finish moment is forecast from the team's pace, one alarm is kept for the earliest; berry-collecting jobs have no forecast.

### 4.7 The base
- **Camp** (an upgrade in Base: five levels, +1% treasure chance per level; its yard shows the Pokémon recruited from finds. The separate Camp screen was folded into Base in 0.112.0, since the PC already lists them).
- **Research Lab** (five levels, +10% Exploration XP per level).
- **Spinda's Café:** three berries from the Bag make a 3-hour brew (+30% XP, +5% treasure chance, +1% find chance).
- **Kecleon Shop:** Poké buys Rare Candies at a price that rises with each one bought (2,500 + 500 each).
- **Croagunk Swap Shop:** three berries of one flavour for one of another.
- **Dojo:** 200 Poké for 60 skill XP in a chosen skill.
- Facility costs triple with each level.

### 4.8 Bag and treasure
- Treasure boxes (plain, silver, gold) are opened into the Bag; berries and Rare Candies land in the shared Bag directly.

### 4.8a Cutscenes
A new rank and each story chapter play a short full-screen scene once (`WorldMoments`, `Cutscene.World`; `WorldData.seenRank` and `seenChapters` remember what was shown, and an old save adopts what it had). A Guardian faced still shows its own card; scenes for the Guardian and the legendary quests are a later addition.

### 4.8b Story archive
A small button on the landing opens the archive (`WorldMoments.reached`): ranks, chapters by layer and legendary quests that joined, each replayable (`Cutscene.World`). A legendary quest has its own scene (`WorldMoment.LegendJoined`, tracked by `WorldData.legendsSeen`, null on older saves so they adopt what they have).

### 4.9 The landing
Rank card (with what the next rank opens), the "since you last looked" report, one primary action (recruit, claim, explore the next dungeon of the layer on show, or look at the teams), all teams at a glance, the layer's story, then Map, Expeditions, Missions and Base, with Finds and Bag, Skills, Camp and Settings as small buttons.

### 4.10 Settings and surfaces
- World animation switch; the job-done alert switch; an Android World widget; the World Place for shortcuts and notifications.
- Reduce motion and large text are respected (130% text is checked in the screenshot tests).

### 4.11 The layers' stories and Guardians (built)
- **Home layer:** six chapters ("A New Explorer" to "The Great Disturbance"), ending in the Temporal Tower and **Primal Dialga**, the Guardian (its own Dex form, separate from Dialga). It gives the **Temporal Mastery**: every expedition climbs 10% faster. A post-story dungeon, the **Distortion Rift**, opens afterwards.
- **Kanto, Johto, Hoenn, Sinnoh:** five chapters each and their legendary quests (section 7).
- A layer's story chapters open by rank and by how many different dungeons **of that layer** have been cleared; clears in one layer do not advance another's.

---

## 5. Features: planned

Ordered roughly by how close they are. Nothing here is built.

### 5.1 More regional layers (decided: all mainline regions, in game order, one layer each)
- ~~**Sinnoh** (Dialga, Palkia, Giratina)~~ (built, 0.115.0), **Unova**, **Kalos**, **Alola**, **Galar**, **Paldea**, then **Hisui** as its own historical layer (Arceus, and the items that unlock the Origin forms of Dialga, Palkia and Giratina). One region per release, each with its own economy simulation first (so new floors never change the pacing of old ones).
- A region's extra areas (Sevii Islands, Isle of Armor, Crown Tundra, Kitakami, Blueberry Academy, Area Zero) are late dungeons of that region. The special spaces (Hyperspace Lumiose, Ultra Space and Ultra Megalopolis, the Battle Zone, Terarium, Area Zero) are Guardian-tier places inside their region, opened by its story, like the Distortion Rift.
- **Mystery Dungeon continents** (Air, Grass, Mist, Water, Sand, Mystery, Sea of Wonders) become layers, one each (decided).
- **Spin-off layer**, starting with the Ranger regions (Fiore, Almia, Oblivia) and Orre; the owner's atlas lists about twenty more settings as a long-term list.

### 5.2 Making a region a journey, not a list
From the owner's regional story brief:
- **Settlements and routes as nodes** (towns with a few characters, a service and a quest board; routes connecting them), geography following the games.
- **Fog of war** and discovery: reaching a place reveals its neighbours; optional paths and landmarks for extra finds.
- **Story and exploration as two tracks:** story unlocks the major destinations in order; exploration reveals optional places and never blocks the story. Finishing a region never requires a full regional Pokédex.
- **Regional jobs** on the mission board (Kanto, Johto and Hoenn have none yet).
- **Postgame** per region (deeper floors, modifiers, rematches, collectibles).
- Fuller story text and scenes (today each chapter is a title and a summary).

### 5.3 Deeper exploration
**Built (0.111.0): side paths with field moves.** One floor in ten has an event that a Pokémon able to learn Cut, Surf, Rock Smash, Strength, Flash, Dig or Fly (level 10 or more) opens for a small bonus; a team without one passes it by at no cost. Deterministic from (dungeon, team, trip, floor), its own random stream, bonuses only, never needed for story. Code: `FloorEvents`, `FieldMove`. **Also built (0.112.0): dungeon modifiers** (`DungeonModifiers`: a third of (dungeon, day) pairs, reward bonuses only, from the day each floor was climbed), and Camp folded into Base. **Hazards and choices (0.114.0):** four type-weathered hazards, and forks and springs that wait 12 hours for an answer and take the first way by themselves (each choice is its own record). Phase 3's plan is complete; team roles, equipment, extra skills and Friend Areas stay in the planned list below.
- Hazards, active choices and floor events (the dungeon is currently a pure simulation).
- **Move-based skills:** moves are in the Dex since 0.96.0 (919 moves and every learnset); the concept lets moves act as exploration abilities (Cut, Surf, Flash, Dig, Fly and so on) that open alternate paths.
- More skills (Mining, Archaeology, Survival, Navigation), held items and moves in the team summary, special jobs and a shared quest engine, Camp as places (Friend Areas), Storage and the Duskull Bank.
- Pokégear helpers (a Pokémon known for a skill helping a tool).

### 5.4 Guardians and modules
- A Guardian's boost applies in its own region; **a Guardian can also help other places depending on the module it is placed in** (decided). The per-module bonuses (for example which Guardian helps Care, Plan, Grow or Home) are designed with each module.
- **Side legendaries** (Mew, the three beasts, Celebi, Latias and Latios, Jirachi, Deoxys, the regis and the like) are *not* regional quests: each is a Guardian featured by a module, as the lake trio are for Plan.
- A new module (Grow, then Home) will get its own featured legendary and its own optional World moments.

### 5.4a Phase 2 status (0.109.0)
Built: team names and six accent colours, saved teams (six presets, applied in one tap), a typed expedition log with a filter. Also built: 24 regional jobs for the three layers, the notebook by region and the Guardian line on the Legendary quests card. Phase 2 is complete apart from device checks; Phase 3 (floor events, move-based skills) and the Sinnoh layer are next.

### 5.5 Quality and unfinished edges
- Marker positions on the three region maps are read by eye and need a device check.
- The job alarm and notification have not been tried on a device.

---

## 6. The dungeons

Columns: floors, rank needed, story chapter needed (of its own layer). "G" marks the Guardian dungeon.

**Home layer, the Mystery Dungeon world** (six chapters)

| Dungeon | Kind | Floors | Rank | Chapter |
|---|---|---|---|---|
| Tiny Woods | Greenlands | 5 | Normal | 1 |
| Thunderwave Cave | Underground | 8 | Normal | 1 |
| Mt. Steel | Mountains | 10 | Bronze | 1 |
| Waterfall Cave | Aquatic | 12 | Silver | 3 |
| Glacier Palace | Frozen | 14 | Gold | 4 |
| Ancient Ruins | Ancient | 16 | Platinum | 5 |
| Sky Tower | Sky | 18 | Platinum | 5 |
| Temporal Tower (G) | Otherworldly | 20 | Diamond | 6 |
| Distortion Rift (after the story) | Otherworldly | 25 | Diamond | 6 |

**Kanto** (Pokémon Red, Blue, FireRed, LeafGreen; wild Pokémon from the Kanto dex)

| Dungeon | Floors | Rank | Chapter |
|---|---|---|---|
| Viridian Forest | 5 | Normal | 1 |
| Mt. Moon | 8 | Normal | 1 |
| Rock Tunnel | 10 | Bronze | 2 |
| Pokémon Tower | 12 | Silver | 3 |
| Power Plant | 12 | Silver | 3 |
| Seafoam Islands | 14 | Gold | 3 |
| Mt. Ember | 16 | Gold | 4 |
| Victory Road | 18 | Platinum | 4 |
| Cerulean Cave (G) | 20 | Diamond | 5 |

**Johto** (Gold, Silver, Crystal, HeartGold, SoulSilver; wild Pokémon from the Johto dex)

| Dungeon | Floors | Rank | Chapter |
|---|---|---|---|
| Sprout Tower | 5 | Normal | 1 |
| Ilex Forest | 8 | Normal | 1 |
| Union Cave | 10 | Bronze | 2 |
| Ruins of Alph | 12 | Silver | 3 |
| Slowpoke Well | 12 | Silver | 3 |
| Mt. Mortar | 14 | Gold | 4 |
| Burned Tower | 16 | Gold | 4 |
| Whirl Islands | 18 | Platinum | 4 |
| Tin Tower (G) | 20 | Diamond | 5 |

**Hoenn** (Ruby, Sapphire, Emerald, Omega Ruby, Alpha Sapphire; wild Pokémon from the Hoenn dex)

| Dungeon | Floors | Rank | Chapter |
|---|---|---|---|
| Petalburg Woods | 5 | Normal | 1 |
| Granite Cave | 8 | Normal | 1 |
| Meteor Falls | 10 | Bronze | 2 |
| Mt. Chimney | 12 | Silver | 3 |
| Shoal Cave | 12 | Silver | 3 |
| Seafloor Cavern | 14 | Gold | 4 |
| Cave of Origin | 16 | Gold | 4 |
| Victory Road (Hoenn) | 18 | Platinum | 4 |
| Sky Pillar (G) | 20 | Diamond | 5 |

Pacing: each region's early dungeons pay about like Tiny Woods and its late ones about like the Temporal Tower, so a new region neither dwarfs nor trivialises the home layer (checked with `./gradlew :world-core:simulateWorldEconomy`).

---

## 7. Storylines and quests

### 7.1 Chapters of the built layers

**Home layer (six chapters)**
1. *A New Explorer.* You form an exploration team and begin to explore.
2. *Requests and Rescues.* Pokémon in need of help come to your door (opens the mission board).
3. *Into the Unknown.* Deeper dungeons and a second team.
4. *Strange Discoveries.* The dungeons behave strangely (research and treasure).
5. *Ancient Secrets.* The source of the disturbances (special expeditions).
6. *The Great Disturbance.* You face the Guardian, Primal Dialga, at the Temporal Tower. Then the Distortion Rift opens.

**Kanto, "The Genetic Crisis"** (five chapters)
1. *Pallet Town.* A new explorer sets out; Pokémon in the wild behave strangely.
2. *Rocket Shadows.* Team Rocket's traces turn up in the caves.
3. *Spirits and Birds.* Pokémon Tower mourns, the Power Plant hums, the Seafoam caves chill; the legendary birds stir.
4. *The League Road.* Mt. Ember burns and Victory Road waits; the road to the truth about the experiments.
5. *The Ultimate Pokémon.* Beyond Cerulean City, a cave holds what Rocket made from Mew.

**Johto, "The Sacred Balance"** (five chapters)
1. *New Bark Town.* The old stories say the sky and the sea are in balance.
2. *Old Stories.* The people of Violet City and Union Cave speak of a time before the towers.
3. *Echoes of Alph.* The Ruins of Alph and the Slowpoke Well hum with a strange power.
4. *The Burned Tower.* Mt. Mortar roars, the Burned Tower smoulders, the Whirl Islands churn; the guardian of the sea stirs.
5. *The Rainbow Wings.* At the top of the Tin Tower the guardian of the sky waits for one who can restore the harmony.

**Hoenn, "The Primal Awakening"** (five chapters)
1. *Littleroot Town.* Pokémon in the woods and caves behave strangely, and the weather is odd.
2. *Signs in the Stone.* Meteor Falls and the old inscriptions speak of a conflict between land and sea.
3. *Magma and Aqua.* Two teams want to remake the world; Mt. Chimney and Shoal Cave hold clues.
4. *The Weather Wars.* The sea and the land clash over the Seafloor Cavern, the Cave of Origin and the road to the League.
5. *The Sky Pillar.* Only one Pokémon can end the fighting, and it waits at the top of the Sky Pillar.

### 7.2 Planned storylines (from the owner's regional brief; nothing built)

| Region | Story | Central legendaries |
|---|---|---|
| Sinnoh | *The Distortion of Creation*: disturbances in space and time, Team Galactic, the Distortion World | Dialga, Palkia, Giratina (Arceus as an overarching myth) |
| Unova | *The Two Ideals*: a movement claiming to liberate Pokémon; truth, ideals, freedom | Reshiram, Zekrom, Kyurem |
| Kalos | *The Cycle of Life*: the ultimate weapon and the balance between life and destruction | Xerneas, Yveltal, Zygarde |
| Alola | *The Light Beyond*: Ultra Wormholes, Ultra Beasts, the disappearance of Alola's light | Solgaleo, Lunala, Necrozma |
| Galar | *The Darkest Day*: unstable Dynamax energy, the ancient heroes | Zacian, Zamazenta, Eternatus |
| Paldea | *The Paradox Expedition*: Area Zero, Paradox Pokémon, the time machine | Koraidon, Miraidon (and Terapagos) |
| Hisui | A historical Sinnoh campaign: the first Pokémon-human relations, the Galaxy Expedition Team, the space-time rift | Arceus (and the items for the Origin forms) |

Spin-off concepts (a later layer): Orre (*The Shadow Pokémon Crisis*), Fiore (*The Guardian's Calling*), Almia (*Shadows Over Almia*), Oblivia (*The Sky Guardians*), Ransei, Ferrum, Lental, Pasio, Aeos Island. Mystery Dungeon continents: Air Continent (*The Sky's Last Hope*, Rayquaza), Grass (*The Lost Future*, Dialga, Darkrai, Shaymin), Mist (*The Dream of a Frozen World*, Kyurem), Water (*The Eternal Balance*, Xerneas, Yveltal), Sand (*The Forgotten Kingdom*), Mystery (*The Unknown Frontier*, Arceus) and the Sea of Wonders (*The Deepest Secret*, Lugia). Assignments of legendaries to places outside their own games are creative adaptations to check before being called canon.

Each region's story follows the brief's five parts (Arrival, Investigation, Awakening, Confrontation, Resolution): the box legendary is felt from the start (signs: strange weather, odd Pokémon behaviour, dreams), then its mythology through travel, then the final expedition. The number of chapters is flexible.

### 7.3 Legendary quests and Guardians

**The rule (decided):** every legendary and mythical can be a Guardian. **Main (box) legendaries guard their home region** (each is a quest of that layer and, once given, a Guardian with a boost there). **Side legendaries are featured in modules**, not regions.

**Built (each given once, by clearing the dungeon enough times; "after the Guardian" means that layer's story must be complete first):**

| Layer | Legendary | Requirement | Boost (in that layer) |
|---|---|---|---|
| Home | **Primal Dialga** (the Guardian) | Reach the top floor of the Temporal Tower | Every expedition climbs 10% faster (all layers) |
| Kanto | Zapdos | Power Plant cleared 3 times | Electric-favouring Kanto dungeons 10% faster |
| Kanto | Articuno | Seafoam Islands cleared 3 times | Ice-favouring dungeons 10% faster |
| Kanto | Moltres | Mt. Ember cleared 3 times | Fire-favouring dungeons 10% faster |
| Kanto | **Mewtwo** (the Guardian) | Reach the top floor of Cerulean Cave | A little more chance of a find |
| Kanto | Mew | Cerulean Cave cleared 3 times, after the Guardian | A little more chance of a treasure box |
| Johto | Lugia | Whirl Islands cleared 3 times | Water- and Flying-favouring dungeons 10% faster |
| Johto | **Ho-Oh** (the Guardian) | Reach the top floor of the Tin Tower | More treasure and finds |
| Hoenn | Groudon | Mt. Chimney cleared 3 times | Ground- and Fire-favouring dungeons 10% faster |
| Hoenn | Kyogre | Seafloor Cavern cleared 3 times | Water-favouring dungeons 10% faster |
| Sinnoh | Dialga | Mt. Coronet cleared 3 times | Steel-favouring dungeons 10% faster |
| Sinnoh | Palkia | Spear Pillar cleared 3 times | Water-favouring dungeons 10% faster |
| Sinnoh | Giratina | The Distortion World, after its Guardian | 3% more treasure boxes (Guardian) |
| Hoenn | **Rayquaza** (the Guardian) | Reach the top floor of the Sky Pillar | Flying- and Dragon-favouring dungeons 10% faster, a little more finds |

**Planned Guardians** (main quests; each a Guardian of its region):

| Region | Main quests |
|---|---|
| Sinnoh | Dialga, Palkia, Giratina |
| Hisui | Arceus, and the items that unlock the Origin forms |
| Unova | Reshiram, Zekrom, Kyurem |
| Kalos | Xerneas, Yveltal, Zygarde |
| Alola | Solgaleo, Lunala, Necrozma |
| Galar | Zacian, Zamazenta, Eternatus |
| Paldea | Koraidon, Miraidon, Terapagos |

**Side legendaries (module Guardians, not regional quests):** Mew (World also uses it as a Kanto quest), the three beasts, Celebi, Latias and Latios, Jirachi, Deoxys, the regis, Victini, the swords of justice, the forces of nature, Darkrai, Shaymin, the lake trio (already Plan's), Diancie, Hoopa, Volcanion, the guardian deities, the Ultra Beasts, the Crown Tundra legendaries, the Treasures of Ruin, Ogerpon. Which module features which one is decided with each module.

**Plan's quests (decided 1 November 2026).** Plan used to have a quest for every legendary and mythical (Deep Focus sessions, hard tasks, active days). From 0.103.0 Plan has **no quest for the species World gives**: Zapdos, Articuno, Moltres, Mewtwo, Mew, Lugia, Ho-Oh, Groudon, Kyogre and Rayquaza (`Legends.coveredSpecies`); a Plan quest already finished stays finished and its Pokémon owned, one in progress for such a species goes away. Every other legendary and mythical keeps its Plan quest until a World layer (or a module's own arc) gives it, and then its Plan quest is removed the same way (the list is `Legends.all`). A World quest also never gives a legendary the player already owns (it still counts, so its boost applies).

---

## 8. The numbers (starting values, all marked *tune* in the code)

- Per floor: XP = (4 + floor) x lab and brew bonus; Poké = (5 + 2 x floor + 0 to 4) x skill; berry 70%; treasure 6%; find 3%.
- Floor time: base seconds (30 minutes for the first dungeons up to 4 hours for the end of a story) divided by (1 + team power / 100), never under a quarter of the base.
- Away cap: 12 hours per open.
- Milestone rewards at the middle floor (a berry) and the top floor (Rare Candies 1 to 8, Poké 100 to 4,000).
- Full simulation of a year of play (jobs, research, facilities, Café, Swap Shop, Dojo, shop): `docs/world-economy-report.md`.

---

## 9. Decisions on record (owner)

1. The Mystery Dungeon world is the main map; regions are layers beside it. Nothing existing is replaced.
2. Every layer open from the first day; each dungeon keeps its own rank gate.
3. All mainline regions, in game order, one layer each; Hisui a separate historical layer after Paldea.
4. A region's Pokémon are found only in that region (its regional dex).
5. Guardians: main legendaries guard their region, side legendaries guard modules; a Guardian helps other places depending on the module it is placed in.
6. Dialga is a Sinnoh Guardian; Primal Dialga is separate (the home layer's).
7. The journey advances only through World's teams; modules do not feed travel.
8. In World, Pokémon come from recruiting, never eggs.
9. Official game map art for each region.
10. A Guardian can join a World team while it is idle (not on a team, not slotted as a Guardian).
11. The team picker shows the 20 strongest idle Pokémon; the landing's next step and a dungeon's locked reasons follow the layer on show and list every unmet requirement.
12. Plan has no quest for a legendary World gives (decided 1 November 2026, option A: removed species by species as each layer is built).

## 10. Open questions

- How the boosts of several Guardians in one region combine (assumed: they add within the existing cap on Guardian effects), and which Guardian helps which module.
- Which module features which side legendary.
- Whether spin-off layers follow the same one-layer-each rule (assumed yes).
- The marker positions on the region maps, and a device test of the job alarm.
