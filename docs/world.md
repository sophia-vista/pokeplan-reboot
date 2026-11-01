# PokéLife World --- Game Design Concept

> **Naming (owner, 0.83.0):** **World** is the dungeon game described here. **Explore** stays the PokéLife module for real-life trips, places and a travel journal (docs/ecosystem.md); it is a different thing. Where this document says "Explore" or "exploration" it means exploring dungeons in World. The overworld map is World's first screen (section 17).

> **Working direction:** A Pokémon Mystery Dungeon-inspired
> idle/incremental exploration RPG.
>
> **Core fantasy:** Build an exploration team, accept requests, explore
> mysterious dungeons, rescue and recruit Pokémon, discover treasure,
> and become a renowned exploration organization.

## 1. Product identity

PokéLife contains several life-oriented modules for productivity,
routines, health, home, finance, and social connection. **Explore is
intentionally different:** it is the most game-like module and should
feel like a standalone Pokémon adventure that happens to use idle and
incremental mechanics.

The guiding principle:

**Do not make an idle game where players collect Pokémon. Make a Pokémon
exploration game that happens to have idle and incremental mechanics.**

Use Pokémon Mystery Dungeon (PMD) as the primary creative reference: -
*Explorers of Sky* for exploration, guild, missions, dungeons, story,
and mystery. - *Rescue Team* / *Rescue Team DX* for rescue jobs,
recruitment, Friend Areas, and Pokémon camps. - Other PMD titles for
additional facilities, missions, and exploration ideas. - *Legends of
IdleOn* as a reference for interconnected idle systems, parallel
progression, specialization, account-wide bonuses, and long-term
incremental depth---not for its setting or generic worker framing.

Keep the implementation recognizably Pokémon. Prefer established Pokémon
concepts and terminology over generic RPG abstractions wherever
practical.

## 2. Relationship to the wider PokéLife system

Explore should use the shared PokéLife infrastructure where
appropriate: - Shared Trainer identity and profile. - Shared Pokémon
collection / PC. - Shared Pokédex. - Shared Bag and item inventory. -
Shared Pokémon levels, moves, abilities, forms, and evolution systems
where compatible. - Shared quest framework. - Shared reward ledger and
module completion tracking.

A Pokémon discovered in Explore should be the same persistent Pokémon in
the user's wider collection, not a disposable module-specific worker.

However, Explore must retain its own: - Progression and currencies. -
Storyline and Guardian. - Exploration-based Pokémon acquisition. -
Dungeon and expedition progression. - Module-specific upgrades and
mastery bonus.

Other modules retain their own acquisition methods. Explore should not
simply reuse Focus Eggs, Care Eggs, or another module's reward loop. Its
signature acquisition method is **Exploration Finds**: Pokémon
discovered through dungeons, missions, tracking, habitats, rescues,
research, and special expeditions.

## 3. Core gameplay pillars

1.  **Dungeon exploration** --- the primary idle progression engine.
2.  **Expedition teams** --- multiple Pokémon teams progressing in
    parallel.
3.  **Missions and rescues** --- recognizable PMD-style objectives and
    rewards.
4.  **Pokémon recruitment and development** --- collecting, leveling,
    evolving, and using Pokémon.
5.  **Items and treasure** --- useful discoveries that support further
    exploration.
6.  **Exploration rank and research** --- long-term account/module
    progression.
7.  **Story and Guardian** --- a narrative questline culminating in a
    major encounter and permanent module boost.
8.  **Post-story exploration** --- repeatable endgame content after the
    storyline is complete.

## 4. Main gameplay loop

1.  Choose a dungeon, mission, or special expedition.
2.  Assign a Pokémon team suited to the destination and objective.
3.  Start the expedition.
4.  The team automatically explores floors and makes progress while the
    player is away.
5.  Discover items, treasure, Pokémon encounters, resources, and new
    routes.
6.  Collect XP, items, Poké, research progress, and exploration rewards.
7.  Improve Pokémon, moves, equipment, facilities, and research.
8.  Unlock more dungeons, deeper floors, missions, and expedition
    capacity.
9.  Recruit more Pokémon and create additional teams.
10. Repeat with increasingly capable teams and more rewarding
    destinations.

The loop should create a satisfying incremental snowball: exploration
unlocks resources; resources improve teams and equipment; stronger teams
reach deeper areas; deeper areas reveal more Pokémon and treasure;
discoveries improve the wider account.

## 5. Dungeon exploration

Dungeons should be the defining feature of Explore.

PMD dungeon concepts to preserve: - Floors. - Wild Pokémon. - Items on
the floor. - Stairs and deeper levels. - Treasure. - Hazards and
environmental conditions. - Special rooms, events, and encounters. -
Dungeon completion and milestones.

### Idle adaptation

The player selects a dungeon and assigns a team. The team automatically
progresses through floors over time. The game simulates ordinary
movement and exploration rather than requiring manual navigation for
every floor.

Each dungeon can track: - Current floor and deepest floor reached. -
Exploration progress / speed. - Encounter opportunities. - Item and
treasure discovery. - Dungeon-specific objectives. - Milestone
rewards. - Environmental or type-related modifiers. - Maximum depth or
progression gates, where appropriate.

### Active play opportunities

Active interactions should accelerate or enrich exploration, not be
mandatory repetitive tapping: - Boost exploration speed. - Use an item
or Orb. - Choose a route at a branching path. - Investigate a suspicious
location. - Trigger a special encounter. - Respond to a rescue or hazard
event.

Offline progress should be meaningful. Players should return to
accumulated progress and discoveries without needing constant check-ins.

### Example early dungeon

**Tiny Woods** (illustrative PMD-inspired implementation): - Early
floors: common berries and basic items. - Middle floors: ordinary wild
Pokémon encounters and treasure. - Later floors: less common discoveries
and a dungeon milestone. - Repeated exploration can continue to provide
useful rewards after initial completion.

Exact floor tables and species should follow the selected source game's
canon where a location is represented as canon. New locations should be
clearly treated as original PMD-inspired content.

## 6. Expedition teams

Multiple teams progressing simultaneously are a high-priority
IdleOn-inspired feature.

-   Start with one team and unlock additional permanent expedition slots
    through progression.
-   Assign collected Pokémon to teams.
-   Teams can work on different dungeons or missions simultaneously.
-   Each team has its own assignment, progress, and rewards.
-   Pokémon assigned to a team are unavailable for conflicting
    assignments until recalled or the activity ends.
-   Additional teams should create more meaningful parallel progress,
    not merely multiply an unbalanced reward faucet.

Example assignments: - Team 1: explore a forest dungeon. - Team 2:
complete a rescue request. - Team 3: gather information in a cave. -
Team 4: investigate a special location.

The player should be able to choose a team based on its Pokémon, moves,
abilities, types, levels, and held items.

## 7. Pokémon utility: use canon traits, not generic jobs

Avoid turning Pokémon into generic idle workers with arbitrary
professions. Their utility should come from recognizable Pokémon
characteristics: - Species and biology. - Type. - Ability. - Moves. -
Level and evolution. - Held items. - Potentially individual traits, if
the wider PokéLife system supports them.

Examples of exploration interactions: - Bulbasaur: Vine Whip or
plant-related interactions. - Geodude: Rock Smash or rocky obstacles. -
Lapras: water travel. - Diglett: underground passages and buried
items. - Abra: Teleport-related navigation or escape. - Magnemite:
electrical mechanisms. - Pokémon with tracking-related abilities or
senses: improve certain encounter clues.

These examples are illustrative. Do not force a species into a role
solely because it fits a generic job category.

### Moves as exploration abilities

Moves can affect exploration without requiring a full battle system at
launch.

Examples: - Cut --- clear vegetation. - Rock Smash --- break
obstacles. - Surf --- traverse water. - Strength --- move heavy
objects. - Dig --- uncover buried items or passages. - Flash --- reveal
hidden paths. - Teleport --- escape or shorten a return. - Sweet Scent
--- influence encounter opportunities. - False Swipe --- improve certain
recruitment encounters.

Move-based gates should create useful team-building choices. They should
not make a single rare Pokémon mandatory or block ordinary progression
indefinitely.

### Types, abilities, and levels

-   Type can influence environmental compatibility and dungeon bonuses.
-   Abilities can provide passive expedition effects.
-   Moves provide active or conditional utility.
-   Level provides general expedition capability.
-   Evolution can improve capabilities and unlock new moves or
    interactions.
-   Held items provide additional bonuses.

Keep these systems consistent with Pokémon rules wherever possible.
Avoid arbitrary stat systems that conflict with the wider PokéLife
Pokémon model.

## 8. Missions and rescue requests

Use the shared PokéLife quest engine, but give Explore PMD-style quest
content and presentation.

Mission types: - **Rescue:** Find and help a lost Pokémon. - **Escort:**
Accompany a Pokémon through a dungeon. - **Find:** Locate a requested
item. - **Deliver:** Bring an item to a Pokémon. - **Explore:** Reach a
specified floor or destination. - **Defeat:** Overcome a target Pokémon
or threat, if combat is implemented. - **Special:** Complete a unique
discovery or investigation.

Mission categories: - Ordinary jobs --- repeatable requests. - Special
jobs --- unusual objectives and discoveries. - Story missions ---
sequential, persistent narrative progression. - Rescue missions ---
rescue-focused requests. - Daily/weekly requests --- optional repeatable
activities.

Missions can be assigned to available teams and completed passively
where appropriate. Short jobs should provide quick rewards; longer jobs
should offer larger rewards or meaningful discoveries.

**Finished-job notification (0.97.0, Android):** when a team that is exploring a job's dungeon will climb to the job's floor,
an alarm is set for that moment (`WorldSim.jobsDoneAt`, `WorldAlerts.nextJobDone`; Settings → "World job alert", on by
default). When it goes off World is settled and a notification names the finished job (or says how many are ready) and opens
World. Berry jobs depend on what the floors give, so they have no forecast and are only seen when World next settles.

### Shared quest framework requirements

The common quest engine should support: - Prerequisites and
dependencies. - Sequential story chapters. - Optional side quests. -
Repeatable daily/weekly quests. - Multi-stage objectives. - Progress
counters. - Dungeon/floor objectives. - Discovery and collection
objectives. - Guardian encounters. - Permanent completion flags. -
Rewards and unlocks.

Possible objective types: - Accumulate a quantity. - Complete a number
of expeditions. - Discover specified Pokémon. - Reach a dungeon floor. -
Collect items. - Use a move or ability. - Upgrade a skill or facility. -
Unlock a destination. - Complete a rescue. - Defeat or overcome a
Guardian encounter.

Story quests must remain distinct from daily quests and routine module
tasks. Missing a day must not reset or invalidate story progress.

## 9. Exploration rank and long-term progression

Use PMD's Exploration Team Rank as the main progression identity rather
than inventing a generic guild level.

Possible rank names should be selected consistently from one PMD
reference title if reproducing canon terminology. Rank progression can
be earned through: - Completing missions. - Exploring new dungeon
floors. - Discovering locations. - Finding rare treasure. - Completing
rescue requests. - Achieving exploration milestones.

Rank unlocks should be meaningful: - New dungeon destinations. -
Additional expedition teams. - New mission categories. - Better
equipment and items. - Remote or difficult dungeons. - Special
expeditions. - Advanced or post-story content.

Keep rank separate from individual Pokémon levels. A player's team can
be powerful while the exploration organization is still a lower rank,
and vice versa.

## 10. Skills and interconnected incremental systems

Use IdleOn as a reference for multiple independent but connected
progression tracks.

Possible Explore skills: - **Exploration:** general discovery and
dungeon progression. - **Foraging:** berry and plant discovery. -
**Mining:** ore and mineral discovery. - **Scouting:** location and
encounter discovery. - **Archaeology:** fossils and ancient treasures. -
**Research:** species data and discoveries. - **Survival:** expedition
endurance and hazard handling. - **Navigation:** access to difficult
routes.

Skills should level through relevant gameplay. For example,
mining-related expeditions improve Mining, which can unlock richer
deposits or better mining outcomes.

These are optional design tracks, not a requirement to turn Pokémon into
workers. Tie them to team activities, Pokémon moves/abilities, and
expedition outcomes.

### Account-wide progression

Discoveries and upgrades should benefit the wider Explore system: -
Species discoveries contribute to research. - Completing a region
improves navigation in that region. - Research unlocks bonuses for
multiple teams. - Achievements grant permanent bonuses. - Certain
species or Pokémon families can unlock special exploration
interactions. - Dungeon milestones can improve future expeditions.

Older regions should remain relevant through useful crafting materials,
collection goals, missions, or completion bonuses.

## 11. Items, treasure, and rewards

Use recognizable PMD and Pokémon items wherever appropriate.

  -----------------------------------------------------------------------
  Category                Examples                Possible use
  ----------------------- ----------------------- -----------------------
  Berries                 Oran, Sitrus, Pecha     Recovery and utility

  Seeds                   Reviver Seed, Sleep     Expedition effects
                          Seed, Blast Seed        

  Gummis                  Colored Gummis          Pokémon development

  Evolution items         Evolution stones /      Evolution
                          relevant evolution      
                          items                   

  Orbs                    Escape Orb, Scanner     Exploration effects
                          Orb, other PMD Orbs     

  TMs                     Existing Pokémon TMs    Teach moves

  Held items              Scarves, ribbons,       Expedition bonuses
                          bands, and canon held   
                          items                   

  Treasure                Treasure boxes and rare Collection and rewards
                          finds                   

  Poké                    PMD-style currency      Purchases and services
  -----------------------------------------------------------------------

Items should have actual uses, not all become generic currency: -
Berries provide useful recovery or utility. - Seeds and Orbs affect
expedition outcomes. - TMs connect to Pokémon moves. - Evolution items
integrate with the shared evolution system. - Treasure includes
collectibles and discoveries, not only sellable materials.

### Treasure Boxes

Expeditions can uncover treasure boxes containing: - Ordinary and rare
items. - TMs. - Evolution materials. - Special collectibles. - Rare
encounter opportunities. - Region-specific discoveries.

Treasure can have tiers or quality levels. Opening can be an active
return interaction or handled through an automatic Bag workflow.

### Reward hierarchy

1.  **XP and level-ups:** frequent feedback for meaningful activity.
2.  **Items and materials:** regular rewards that enable upgrades and
    interactions.
3.  **Pokémon discoveries:** major collection rewards, but frequent
    enough to keep collecting enjoyable.
4.  **Milestones:** new destinations, facilities, mechanics, and story
    progression.

Use generous progression. Ordinary Pokémon should be regularly
discoverable; rarer or legendary Pokémon can require more elaborate
conditions. Avoid low-probability-only acquisition with no visible
progress.

Rare Candy should be useful and reasonably available, supporting Pokémon
leveling and evolution-related progression rather than becoming a
resource players are afraid to spend.

## 12. Pokémon recruitment and camps

Use Rescue Team's Friend Areas as inspiration.

When a Pokémon is encountered: 1. The player completes the relevant
encounter, rescue, or recruitment condition. 2. The Pokémon joins the
persistent shared collection. 3. It can appear in the Explore camp or
relevant habitat. 4. It becomes available for future teams, subject to
assignment rules.

### Camps / Friend Areas

Possible environments: - Forest Camp. - Mountain Camp. - Waterfall
Camp. - Cave Camp. - Grassland Camp. - Sky Camp. - Ancient Ruins Camp.

Camps can have: - Distinct visual environments. - Roaming Pokémon. -
Species-specific interactions. - Camp upgrades. - Small passive
bonuses. - Habitat-based attraction and discovery opportunities.

Camps make collection tangible and provide a place to see Pokémon
outside the PC or roster screens.

## 13. Exploration headquarters and PMD facilities

The hub should feel like a PMD exploration base, not a generic
management dashboard.

Possible facilities drawn from different PMD games: - **Job Bulletin
Board:** missions and rescue requests. - **Kecleon Shop:** buy and sell
items. - **Kangaskhan Storage:** shared item storage. - **Duskull
Bank:** store Poké. - **Spinda's Café:** use berries and ingredients for
drinks or temporary expedition benefits. - **Croagunk Swap Shop:**
exchange duplicate items. - **Marowak / Makuhita Dojo:** training
challenges and Pokémon development. - **Research facility:** species
data, discoveries, and permanent upgrades. - **Friend Areas / Camps:**
Pokémon residents and habitat development.

These facilities appear across different PMD games. Treat them as a
unified adaptation, not as a claim that every facility exists together
in one canonical location.

### Spinda's Café-inspired activity

Use berries and ingredients from expeditions to prepare refreshments.
Possible temporary effects: - Faster exploration. - Better treasure
discovery. - Better recruitment opportunities. - Increased Pokémon XP. -
Increased rare-item discoveries.

### Dojo-inspired activity

Repeatable training content: - Type-themed challenges. - Physical or
special training. - Survival challenges. - Move development. - Pokémon
XP and training rewards.

This provides active gameplay for players who want more than passive
expeditions.

## 14. World map and dungeon regions

Use a connected world map of Pokémon environments rather than a generic
grid of resource nodes.

Possible broad regions: - Greenlands --- forests and plains. - Mountains
--- rocky terrain and peaks. - Aquatic --- rivers, waterfalls, and
oceans. - Underground --- caves and ruins. - Frozen --- snowfields and
glaciers. - Ancient --- ruins and relic sites. - Sky --- floating
islands and towers. - Otherworldly --- distorted or mysterious spaces.

Use recognizable PMD locations where appropriate, and original
PMD-inspired locations where needed. Do not present original locations
as canon.

### Unlock structure

-   Begin with a few accessible dungeons.
-   Completing missions reveals routes.
-   Discovering landmarks reveals neighboring destinations.
-   Pokémon moves or abilities can unlock alternate paths.
-   Story missions reveal hidden areas.
-   Region completion can unlock deeper or more difficult destinations.

World progression should feel like discovery rather than a static list
of locked levels.

## 15. Module storyline and Guardian

All PokéLife modules should use a shared narrative architecture:

**Module introduction → Story quests → Regional milestones → Guardian
encounter → Permanent mastery bonus → Post-story content.**

The story system should be shared technically, while each module has its
own theme, characters, objectives, and Guardian.

### Explore story premise

A series of mysterious disturbances has appeared across the Pokémon
world. Dungeons are behaving strangely, familiar routes are changing,
Pokémon are disappearing, and unusual phenomena are affecting
exploration.

The player's exploration team investigates: 1. Establish the team and
begin exploring. 2. Accept requests and rescue Pokémon. 3. Investigate
increasingly unusual dungeons. 4. Discover ancient ruins and hidden
locations. 5. Encounter legendary Pokémon and uncover clues. 6. Discover
the source of the disturbances. 7. Face the Explore Guardian.

### Suggested chapter structure

  -----------------------------------------------------------------------
  Chapter                 Story focus             Gameplay unlock
  ----------------------- ----------------------- -----------------------
  1\. A New Explorer      Establish the team      Basic expeditions

  2\. Requests and        Help Pokémon in need    Quest board
  Rescues                                         

  3\. Into the Unknown    Explore deeper dungeons Multiple teams

  4\. Strange Discoveries Investigate anomalies   Research and treasure

  5\. Ancient Secrets     Discover the source     Special expeditions

  6\. The Great           Face the Guardian       Story completion
  Disturbance                                     
  -----------------------------------------------------------------------

Each chapter should introduce a mechanic, meaningful unlock, or
substantial narrative development.

### Guardian candidates

-   **Primal Dialga:** time distortion and disappearing routes;
    strongest direct reference to *Explorers of Time/Darkness/Sky*.
-   **Rayquaza:** disturbances in the atmosphere and sky; connects to
    Rescue Team and Sky Tower.
-   **Giratina:** dimensional distortions and unstable dungeons;
    supports mysterious and otherworldly exploration.

Primal Dialga is the strongest PMD reference. If originality is more
important than direct reference, use an original mystery involving a
Guardian whose influence is affecting the region.

Legendary and Mythical Pokémon should remain special, story-driven
discoveries rather than ordinary random recruits.

### Guardian completion reward

Completing the story grants a permanent, module-specific mastery bonus.
It should be a meaningful passive enhancement, accompanied by cosmetic
and collection rewards.

Example: - **Temporal Mastery:** all Explore expeditions progress 10%
faster. - Additional rewards: Guardian-themed camp decoration, title,
achievement, and special collectible or Pokédex entry.

The percentage is illustrative and must be balanced against actual
progression rates. Prefer one signature bonus plus secondary rewards
over granting every possible bonus at once.

### Other modules

The same story architecture can apply across PokéLife: - Focus ---
restore order to a chaotic world; possible Guardian: Cobalion. - Care
--- restore balance and nurture life; possible Guardian: Xerneas. - Grow
--- reawaken a dormant ecosystem; possible Guardian: Shaymin. - Home ---
restore and organize a neglected home; possible Guardian: Regigigas. -
Health --- rebuild vitality and balance; possible Guardian: Mewtwo. -
Finance --- restore a broken economy or treasure network; possible
Guardian: Gholdengo. - Connect --- reconnect isolated communities;
possible Guardian: Celebi. - Explore --- investigate mysterious dungeons
and disturbances; possible Guardian: Primal Dialga.

These are thematic candidates, not final assignments. Choose Guardians
based on lore and narrative fit, not type matching alone.

## 16. Post-story and endgame

Completing the Guardian storyline should be a gateway, not the end of
the module.

Explore can unlock **Distortion Dungeons** or **Special Expeditions**
after story completion: - Repeatable endgame exploration. - Higher-tier
treasure. - Rare Pokémon discoveries. - Special materials. - More
challenging objectives. - Dungeon modifiers. - Long-term collection
achievements.

The ordinary dungeons, missions, and collection systems should also
remain useful after story completion.

## 17. Suggested navigation and UI

Keep the main navigation simple and make the overworld the visual
identity.

### World

-   Pixel-art overworld and connected map.
-   Regions, dungeons, routes, and landmarks.
-   Discovery indicators and visible progression.

### Expeditions

-   Active teams and assignments.
-   Dungeon floor and progress.
-   Missions, events, and collected rewards.
-   Recall and reward-claim interactions.

### Camp

-   Pokémon residents and team management.
-   Friend Areas / habitats.
-   Facilities, shops, and upgrades.

### Bag & Collections

-   Items, treasure, and equipment.
-   Pokédex and discoveries.
-   Collection achievements.

The management screens should support the world, not replace it with a
spreadsheet-like interface. Pokémon and locations should feel alive
through pixel-art environments, roaming sprites, and contextual
interactions.

## 18. MVP and implementation priorities

Avoid implementing every system immediately. Establish the core loop
first.

### MVP --- essential

  Priority   Feature               Initial scope
  ---------- --------------------- ---------------------------------------------
  P0         World map             3 explorable dungeons / biomes
  P0         Dungeon simulation    Floor progression and discoveries
  P0         Expedition teams      2 simultaneous teams
  P0         Pokémon roster        Assignable persistent Pokémon
  P0         Offline progression   Accumulated progress while away
  P0         Exploration XP        Guild/rank and Pokémon progression
  P0         Exploration Finds     Encounter and recruit Pokémon
  P0         Shared quest engine   Story and basic mission objectives
  P0         Items and rewards     Basic berries, items, treasure, and Poké
  P0         Story foundation      First chapter and Guardian progression data

### Phase 2 --- depth

-   Mission board with rescue, escort, find, deliver, and explore jobs.
-   Exploration rank unlocks.
-   Foraging, mining, scouting, and research tracks.
-   Pokémon moves and abilities as exploration utilities.
-   Basic crafting and equipment.
-   Research tree and permanent upgrades.
-   Additional expedition slots.
-   Camps / Friend Areas.
-   More dungeons and regions.

### Phase 3 --- advanced content

-   Multi-stage dungeons and special rooms.
-   Boss encounters and more substantial PMD-style combat.
-   Fossil restoration.
-   Spinda's Café-inspired activity.
-   Dojo challenges.
-   Story expeditions and legendary discoveries.
-   Advanced collections and achievements.
-   Distortion Dungeons / post-story content.
-   Additional parallel idle activities such as voyages or treasure
    hunting.

## 19. Design rules

1.  **Pokémon terminology first.** Prefer dungeon floors, missions,
    rescue teams, exploration rank, treasure, moves, abilities, and
    recruitment over generic RPG terminology.
2.  **Pokémon are not generic workers.** Their species, moves,
    abilities, types, evolution, and held items should determine
    utility.
3.  **Not every activity is a timer.** Combine passive progression,
    milestone discoveries, and optional active decisions.
4.  **Keep acquisition generous.** Ordinary Pokémon should be regularly
    discoverable; special Pokémon can require more involved quests.
5.  **Preserve shared Pokémon identity.** A Pokémon found in Explore
    remains the same persistent individual across the wider PokéLife
    system.
6.  **Make the world persistent.** Dungeons, camps, facilities, and
    discoveries should visibly reflect progress.
7.  **Keep the quest engine shared.** Explore uses PMD-style quest
    content, not a separate incompatible quest framework.
8.  **Story completion is not module completion.** The Guardian grants
    mastery and unlocks post-story content.
9.  **Keep early content relevant.** Older regions should retain value
    through materials, missions, collections, and completion bonuses.
10. **Avoid mandatory constant check-ins.** Offline progress should be
    meaningful; active play should accelerate or enrich rather than be
    required.
11. **Maintain canon coherence.** Use established PMD and Pokémon
    concepts when possible. Clearly distinguish original PMD-inspired
    content from canon locations, characters, and events.
12. **Make progression interconnected.** Every major system should feed
    at least one other system.

## 20. Final recommended direction

Build Explore as a **PMD-inspired exploration RPG with IdleOn-style
interconnected incremental progression**.

The essential experience: - Operate an exploration team. - Accept
requests from Pokémon. - Explore Mystery Dungeons. - Send multiple teams
on autonomous expeditions. - Find treasure and useful Pokémon items. -
Discover, rescue, and recruit Pokémon. - Use species, moves, abilities,
and held items to improve exploration. - Increase Exploration Team
Rank. - Develop camps and headquarters facilities. - Follow a module
storyline and confront its Guardian. - Earn permanent mastery bonuses. -
Continue into post-story dungeons and discoveries.

The emotional reward is discovering and collecting Pokémon. The
mechanical reward is watching teams, skills, rank, and exploration
capabilities grow. The narrative reward is uncovering the mystery of the
region and overcoming its Guardian.

**Primary reference:** *Pokémon Mystery Dungeon: Explorers of Sky*.\
**Secondary references:** *Pokémon Mystery Dungeon: Rescue Team DX* and
other PMD games.\
**Incremental design reference:** *Legends of IdleOn*.


## Implementation status (0.89.0)

What the code does now, against this concept (details: `CHANGELOG.md`, rules: `docs/economy.md`, numbers: `docs/world-economy-report.md`).

| Section | Built | Not built |
|---|---|---|
| 5 Dungeon exploration | Nine dungeons across all eight regions, each with its own tiles, floors, offline settlement (bounded to 12 hours), finds, milestones, treasure | Hazards, active choices, floor events |
| 6 Expedition teams | Up to four teams by rank, four Pokémon each, "Send my strongest" | Held items and moves in the team summary |
| 7 Pokémon utility | Type bonus to speed, ability helpers for skills | Moves (the Dex has none) |
| 8 Missions | Explore, Find items, Rescue, Deliver, Escort, Defeat; daily, weekly and ordinary jobs | Special jobs, the shared quest engine |
| 9 and 10 Rank, skills | Six ranks, three skills, research | Mining, Archaeology, Survival, Navigation |
| 11 Items and treasure | Berries, boxes, Rare Candies, milestone rewards, Bag staging | Item effects specific to World |
| 12 and 13 Camp, facilities | Camp (yard), Research Lab, Café, Kecleon Shop, Swap Shop, Dojo | Storage, Duskull Bank |
| 14 Map | The world map with markers, eight reveal stages | Regions beyond the nine dungeons |
| 15 Story and Guardian | Six chapters, Temporal Tower, Dialga and the 10% mastery, Distortion Rift | Story text and scenes |
| 16 Post-story | One Distortion Rift | Modifiers, more dungeons |


## Regions of the games (decided in outline, 1 November 2026)

The Mystery Dungeon world is the main map. Beside it, as layers behind a switch on the Map screen: the nine mainline regions (Kanto, Johto, Hoenn, Sinnoh, Unova, Kalos, Alola, Galar, Paldea; eight to nine dungeons each, wild tables from the region's regional dex, Guardians per region that are its main legendaries, each reached by a quest, one layer each, built in release order, the special spaces as Guardian-tier places in their region), then the spin-off settings, starting with the Ranger regions and Orre. The owner's atlas, the decisions and what is still open are in `docs/ecosystem.md`, "World regions". Not built; the dungeons that exist (Tiny Woods to the Distortion Rift) are the home layer.

## Kanto, the first region layer (design, 1 November 2026)

Kanto is the first mainline layer beside the Mystery Dungeon home layer (decisions in `docs/ecosystem.md`, "World regions"). Everything below is a starting design; numbers marked *(tune)* are checked with `:world-core:simulateWorldEconomy` before the release.

### Layer rules
- **A layer is data, not a new game.** `WorldLayer` (home, Kanto, ...) is a new field on `DungeonDef`, and each layer has its own map picture, its own story chapters and its own Guardians. One layer is shown at a time behind a switch on the Map screen; teams, the Bag, rank, Poké and the Camp stay shared (one trainer).
- **Kanto opens when the home story reaches its second chapter *(tune)*,** so nobody meets it before they know the loop. It stays open once reached. Its dungeons have their own rank gates, starting at Normal.
- **Wild tables follow the Kanto regional dex** (species 1 to 151). A Kanto species is found only in Kanto, so the collection fills region by region. The tables are written by hand per dungeon from the species' habitats in the games, not generated.
- **Recruiting, not eggs:** finds join through the same recruit flow as the home layer.
- **No change to the home layer** and nothing existing moves.

### The nine dungeons
Floors and floor time rise from route to endgame like the home layer's (Tiny Woods 5 floors at 30 min to the Temporal Tower's 20 at 4 h) *(tune)*. Favoured types decide who explores faster. "Quest" means a legendary quest ends there.

| # | Dungeon | Kind | Floors | Rank | Favoured | Notes |
|---|---|---|---|---|---|---|
| 1 | Viridian Forest | Greenlands | 5 | Normal | Bug, Grass, Flying | Weedle, Caterpie lines, Pikachu from floor 4 |
| 2 | Mt. Moon | Underground | 8 | Normal | Rock, Normal, Ground | Zubat, Geodude, Clefairy; moon-stone items on the last floors |
| 3 | Rock Tunnel | Underground | 10 | Bronze | Rock, Ground, Fighting | Dark cave, Machop and Onix |
| 4 | Pokémon Tower | Ancient | 12 | Silver | Ghost, Psychic | Gastly line; the Pokémon Tower's spirits |
| 5 | Power Plant | Underground | 12 | Silver | Electric, Steel | **Zapdos quest**, Voltorb, Magnemite |
| 6 | Seafoam Islands | Aquatic / Frozen | 14 | Gold | Water, Ice | **Articuno quest**, Seel, Dewgong, Slowpoke |
| 7 | Mt. Ember (Pokémon Mansion upstairs) | Mountains | 16 | Gold | Fire, Rock | **Moltres quest**, Growlithe, Ponyta, Magmar |
| 8 | Victory Road | Mountains | 18 | Platinum | Fighting, Rock, Dragon | The road to the League; Dragonair from floor 12 |
| 9 | Cerulean Cave | Otherworldly | 20 | Diamond | Psychic, Ghost, Dragon | **Mewtwo, the Guardian**; post-story floors beyond |

Five more can follow from the regional dex without changing the shape: Diglett's Cave, Safari Zone (a Camp-like place), Cinnabar's Lab, the Sevii Islands (a late area, Kanto's "extra" region) and the Power Plant's deeper floors.

### Story and quests
Kanto's story is a chain of chapters like the home layer's (`Story.chapters`, one list per layer), each opened by rank and by dungeons cleared:
1. **The League road:** clear the forest, Mt. Moon and Rock Tunnel.
2. **Spirits:** Pokémon Tower. The ghost Gastly line and Marowak's spirit give the first chapter text.
3. **The birds:** Power Plant, Seafoam Islands and Mt. Ember. Each ends in its **legendary quest** (Zapdos, Articuno, Moltres), which uses the shared quest framework (docs/world.md section 8): a requirement (clear it a number of times, field a team of a given type) then the Pokémon joins the Guardian collection.
4. **Victory Road:** the way to Cerulean Cave.
5. **The Guardian:** Cerulean Cave to its last floor, where Mewtwo waits. Mewtwo is a Guardian of Kanto and joins the Guardian collection once, like Primal Dialga does for the home layer. Mew stays a **side legendary** (a module's Guardian, not Kanto's).

All four are Guardians of Kanto (decision 8); the boost of each is **Kanto-wide only** and small, so they add up inside the existing cap on Guardian effects *(tune)*:
- Mewtwo, Psychic Mastery: expeditions in Kanto find recruits slightly more often.
- Articuno, Zapdos, Moltres: their types explore Kanto faster (ice and water, electric, fire).

### Art
- **Map picture:** one for Kanto, in the style of the `map-1..8` stages. Real assets only (the art rule): the town map of the games or a Mystery Dungeon style crop. This is the main thing the owner supplies or we find. Until then the layer ships with the home layer's picture and markers laid on a scene backdrop.
- **Tilesets:** the Explorers of Sky sheets already in `assets/tiles/` cover every kind needed (Apple Woods or Lush Prairie for the forest; Crystal Cave for Mt. Moon; Beach Cave for Seafoam; Deep Sealed Ruin for the tower). No new sheet is required for the first release.
- **Sprites, cries and portraits:** SpriteCollab and the existing assets, as for every Pokémon.

### Build order (one PR each, combined where small)
1. `WorldLayer`, `DungeonDef.layer`, the layer switch and per-layer story; no Kanto content yet. Saves load unchanged.
2. The nine dungeons, wild tables and milestone rewards; economy run (`simulateWorldEconomy`, compare Kanto with the home layer: Poké and Rare Candy per hour must not exceed the home layer's endgame).
3. The story chapters, the bird quests and the Mewtwo Guardian with its boost; tests (a settle that reaches each quest, the Guardian granted once).
4. Map picture and tiles, and the 130% text size check on the new screens.
5. `docs/economy.md`, `docs/world.md` status and `CHANGELOG.md`; bump `RewardRules.VERSION` only if World pays through a new source.

### Open questions for the owner
- Is the Kanto map picture to be taken from the games, or supplied by the owner?
- Should Kanto's quests need the home-layer story first (assumed: chapter 2 of the home story), or open from the first day?
- Kanto-only boosts as above, or may a Kanto Guardian also help other layers a little?
