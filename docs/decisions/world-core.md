# Decision records: world-core

Moved here from code comments in the comment audit (21 November 2026). Each entry is a decision, rule or constraint that
the code alone does not show. Entries are grouped by source file; a line saying "Documented in" means the owning doc
already holds the rule and the entry only points to it.

## `world-core/build.gradle.kts`

### world-core module boundary

Documented in `docs/code-map.md world-core/ entry`.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/AlolaDungeons.kt`

### Alola dungeon wild tables

Documented in `docs/pokelife/MODULES/WORLD.md 14.1 Layers and dungeons`.

### Alola story chapter opening

Documented in `docs/pokelife/MODULES/WORLD.md 14.1 Layers and dungeons`.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/BattleIdentity.kt`

### BattleIdentity

Documented in `docs/pokelife/MODULES/WORLD.md 3.2 Team strength and simplified battle identity`.

### BattleIdentity.VERSION

`BattleIdentity.VERSION` must be bumped whenever any of the battle identity numbers (move, ability or item percentages and the cap) changes, so a report can say which rule version it was computed under.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/Choices.kt`

### ChoiceKind default option

Documented in `docs/pokelife/MODULES/WORLD.md 14.4 Floor events, choices and modifiers`.

### PendingChoice

Documented in `docs/pokelife/MODULES/WORLD.md 14.4 Floor events, choices and modifiers`.

### Choices

Documented in `docs/pokelife/MODULES/WORLD.md 14.4 Floor events, choices and modifiers`.

### Choices.MAX_PENDING

Documented in `docs/pokelife/MODULES/WORLD.md 14.4 Floor events, choices and modifiers`.

### Choices.WAIT_MILLIS

`Choices.WAIT_MILLIS` is 12 hours, deliberately the same as the longest time away World counts (`WorldSim.MAX_AWAY_MILLIS`), so a choice cannot outlive the offline progress that raised it.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/DungeonModifiers.kt`

### DungeonModifier

Documented in `docs/pokelife/MODULES/WORLD.md 14.4 Floor events, choices and modifiers`.

### DungeonModifiers

Documented in `docs/pokelife/MODULES/WORLD.md 14.4 Floor events, choices and modifiers`.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/Dungeons.kt`

### DungeonDef.guardian

Documented in `docs/pokelife/MODULES/WORLD.md 14.6 Story, quests and Guardians`.

### DungeonDef.postStory

Documented in `docs/pokelife/MODULES/WORLD.md 14.1 Layers and dungeons`.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/EvolutionTrialSites.kt`

### EvolutionTrialSites

Documented in `docs/pokelife/MODULES/WORLD.md 14.7 Decisions on record (owner)`.

### EvolutionTrialSites.WALK_FLOORS

`EvolutionTrialSites.WALK_FLOORS` is 5: a steps evolution (Pawmo, Bramblin, Rellor) also counts as walked when a team containing that Pokémon climbs this many floors of any dungeon in one trip, for phones that cannot count steps. The number is a starting value *(tune)*.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/FloorEvents.kt`

### EventReward

Documented in `docs/pokelife/MODULES/WORLD.md 14.4 Floor events, choices and modifiers`.

### FloorEventKind.move

Documented in `docs/pokelife/MODULES/WORLD.md 14.4 Floor events, choices and modifiers`.

### Hazard floor events

Documented in `docs/pokelife/MODULES/WORLD.md 14.4 Floor events, choices and modifiers`.

### FloorEvents

Documented in `docs/pokelife/MODULES/WORLD.md 14.4 Floor events, choices and modifiers`.

### FloorEvents.kindAt floor and index

In `FloorEvents.kindAt` and the matching `Choices.kindAt`, `floor` is the 1-based floor within the dungeon (floors below 2 never have an event) while `index` is the floor's position in the whole trip; the index feeds the seed (`WorldSim.eventSeed`) so a settle gives the same result however it is split.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/GalarDungeons.kt`

### Galar dungeon wild tables

Documented in `docs/pokelife/MODULES/WORLD.md 14.1 Layers and dungeons`.

### Galar story chapter opening

Documented in `docs/pokelife/MODULES/WORLD.md 14.1 Layers and dungeons`.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/HisuiDungeons.kt`

### Hisui dungeon wild tables

Documented in `docs/pokelife/MODULES/WORLD.md 14.1 Layers and dungeons`.

### Hisui story chapter opening

Documented in `docs/pokelife/MODULES/WORLD.md 14.1 Layers and dungeons`.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/HoennDungeons.kt`

### Hoenn dungeon wild tables

Documented in `docs/pokelife/MODULES/WORLD.md 14.1 Layers and dungeons`.

### Hoenn story chapter opening

Documented in `docs/pokelife/MODULES/WORLD.md 14.1 Layers and dungeons`.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/JohtoDungeons.kt`

### Johto dungeon wild tables

Documented in `docs/pokelife/MODULES/WORLD.md 14.1 Layers and dungeons`.

### Johto story chapter opening

Documented in `docs/pokelife/MODULES/WORLD.md 14.1 Layers and dungeons`.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/KalosDungeons.kt`

### Kalos dungeon wild tables

Documented in `docs/pokelife/MODULES/WORLD.md 14.1 Layers and dungeons`.

### Kalos story chapter opening

Documented in `docs/pokelife/MODULES/WORLD.md 14.1 Layers and dungeons`.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/KantoDungeons.kt`

### Kanto dungeon wild tables

Documented in `docs/pokelife/MODULES/WORLD.md 14.1 Layers and dungeons`.

### Kanto story chapter opening

Documented in `docs/pokelife/MODULES/WORLD.md 14.1 Layers and dungeons`.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/KecleonShop.kt`

### KecleonShop

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 20.2.3 Shops, stock and affordability`.

### KecleonShop.shelf

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 20.2.3 Shops, stock and affordability`.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/Layers.kt`

### WorldLayer

Documented in `docs/pokelife/MODULES/WORLD.md 14.1 Layers and dungeons`.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/Legends.kt`

### LegendQuest

Documented in `docs/pokelife/MODULES/WORLD.md 14.6 Story, quests and Guardians`.

### LegendQuest.types

Documented in `docs/pokelife/MODULES/WORLD.md 14.6 Story, quests and Guardians`.

### Legends.coveredSpecies

Documented in `docs/pokelife/MODULES/WORLD.md 14.6 Story, quests and Guardians`.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/Missions.kt`

### MissionDef kinds

Documented in `docs/pokelife/MODULES/WORLD.md 14.5 Missions (jobs)`.

### MissionDef.period

Documented in `docs/pokelife/MODULES/WORLD.md 14.5 Missions (jobs)`.

### Missions.available now parameter

`Missions.available` and `Missions.board` default `now` to `WorldData.lastSeenAt` (the settle time), because a quiet World would not have stored the current clock; screens pass the real clock so repeat periods are judged against it. A job is available when the board is open, the rank is reached, its dungeon is open, and a one-time job is neither done nor already taken.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/Notebook.kt`

### Notebook derivation

The research notebook of a layer is derived from data already saved and has no field of its own: the species its dungeons can show (wild tables plus the Pokémon its jobs rescue), each marked met via `WorldData.discovered` and owned via the collection.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/Outcomes.kt`

### Outcomes

Documented in `docs/pokelife/MODULES/WORLD.md 3.6 Expedition outcomes, defeat and recall`.

### Outcomes.VERSION

`Outcomes.VERSION` must be bumped whenever `CLEAR`, `RETREAT` or the recommended-power numbers change, so a report can say which rule it was settled under.

### Outcomes.recommended

The power `Outcomes.recommended` asks for the top floor of a dungeon, by rank, is set close to (a bit under) what the Defeat jobs ask at Bronze, Gold and Platinum; the Normal value is never used to judge a floor because Normal-rank dungeons always clear, and exists only so the scale is complete.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/PaldeaDungeons.kt`

### Paldea dungeon wild tables

Documented in `docs/pokelife/MODULES/WORLD.md 14.1 Layers and dungeons`.

### Paldea story chapter opening

Documented in `docs/pokelife/MODULES/WORLD.md 14.1 Layers and dungeons`.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/PmdStories.kt`

### Rescue and Explorers story source

The Mystery Dungeon arcs (`RescueStory`, `ExplorersStory`) have no separate script document: the chapter text in code is the source of the story. Rescue Team has six chapters ending at the Sky Tower; each chapter opens by rank and the number of this layer's dungeons cleared.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/Progression.kt`

### WorldSkill

Documented in `docs/pokelife/MODULES/WORLD.md 14.2 Floor rewards and speed`.

### Spinda's Cafe brew

Documented in `docs/pokelife/MODULES/WORLD.md 14.3`.

### AbilityPerks

Documented in `docs/pokelife/MODULES/WORLD.md 14.2`.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/Rank.kt`

### ExplorerRank

Documented in `docs/pokelife/MODULES/WORLD.md 3.3`.

### ExplorerRank.badge

Documented in `docs/assets.md Mystery Dungeon 2 items`.

### PmdArt

Documented in `docs/assets.md Mystery Dungeon 2 items`.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/ReportSummary.kt`

### ReportSummary

ReportSummary is what the teams brought back since the player last looked, built from the events of one or more settles. It is never saved: the shared reward log stays the one record. Rewards are split into three kinds so a chance is never read as a promise: every floor (Exploration XP, Poke), milestones (a fixed reward for reaching a floor the first time) and chance finds (berries, treasure, wild Pokemon). Berries and Poke are already in the Bag when it is shown; wild finds wait to be recruited.

### ReportSummary.memberXp

Documented in `docs/pokelife/MODULES/WORLD.md 5.2`.

### Treasure box tiers

Documented in `docs/assets.md Mystery Dungeon 2 items`.

### Setbacks and retreats

Documented in `docs/pokelife/MODULES/WORLD.md 3.6`.

### ReportSummary.trialsDone

Each entry of ReportSummary.trialsDone is a string of the form `name:evolves into`, so the report can show both the Pokemon and the evolution the trial unlocked.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/SinnohDungeons.kt`

### Mainline layer dungeons

Documented in `docs/pokelife/MODULES/WORLD.md 14.1`.

### SinnohStory

Documented in `docs/pokelife/MODULES/WORLD.md 14.6`.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/Story.kt`

### Primal Dialga Guardian form

Documented in `docs/pokelife/MODULES/WORLD.md 14.6`.

### Story.dungeonsCleared counts distinct dungeons

Documented in `docs/pokelife/MODULES/WORLD.md 14.1`.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/TeamAdvice.kt`

### TeamAdvice ranking

Documented in `docs/pokelife/MODULES/WORLD.md 2.3`.

### TeamAdvice.POOR_MATCH_PERCENT

A team counts as a poor match for a dungeon when fewer than 33 percent of its members have a type the dungeon favours: one of three members is enough, one of four is not. Poor matches only trigger a warning; the expedition formula is untouched.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/UnovaDungeons.kt`

### Mainline layer dungeons

Documented in `docs/pokelife/MODULES/WORLD.md 14.1`.

### UnovaStory

Documented in `docs/pokelife/MODULES/WORLD.md 14.6`.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/WorldData.kt`

### One Pokemon on one expedition team

Documented in `docs/pokelife/MODULES/WORLD.md 2.3`.

### WorldData.pokeEarned is never saved

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 11.2`.

### WorldData.legacyPoke migration

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 11.2`.

### Treasure box tiers

Documented in `docs/assets.md Mystery Dungeon 2 items`.

### WorldData.milestonesPaid

WorldData.milestonesPaid holds keys of the form `dungeon:floor`, one per milestone already paid, so each milestone pays exactly once.

### WorldData.brewUntil

WorldData.brewUntil is an absolute instant in epoch milliseconds until which Spinda's Cafe brew applies; 0 means no brew.

### WorldData.rewoundFrom clock rollback

Documented in `docs/pokelife/MODULES/WORLD.md 3.1`.

### WorldData.seenRank and seenChapters

WorldData.seenRank is the highest rank whose moment was shown; -1 means nothing is recorded yet, so a save from before this existed adopts the rank it already has instead of replaying every rank-up. seenChapters maps a layer id to how many story chapters were shown, with the same adopt-without-replay rule.

### WorldData.legendsSeen

WorldData.legendsSeen is null when no legendary scene has been recorded yet; an older save then adopts the quests it already has instead of replaying their scenes. An empty set means recorded and none seen.

### WorldData.scenesDone and episodesDone

scenesDone and episodesDone (keys `<layer>:<chapter>`) record the story scenes and episodes watched to the end. They feed the Story archive only and never gate a reward.

### WorldData.storyFlags and storyResume

storyFlags holds the answers given in story choices, kept only for later callbacks and never as a reward gate. storyResume maps an episode key to the index of the next scene, so an unfinished episode resumes where it stopped.

### Legendary faster-floor bonus

Documented in `docs/pokelife/MODULES/WORLD.md 14.6`.

### WorldData.recall

WorldData.recall assumes the team was settled just before: the app settles first (worldRecall), so what the team found is already counted and bringing it home loses nothing.

### WorldData.acceptMission day

acceptMission must be given the same UTC offset the mission board and the claim use, which is the player's, otherwise a job the board offers could be refused here.

### World rules do not charge Poke

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 11.2`.

### WorldData.brew

WorldData.brew returns null while a brew is still running. The caller takes the berries only when it gets a result, so berries are never wasted on a brew that would change nothing.

### World rules do not charge Poke

Documented in `docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 11.2`.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/WorldMoments.kt`

### WorldMoment.SpecialEpisode

A SpecialEpisode is an optional episode after a layer's story (chapter 101 and up). It is never queued by itself; it is played only from the Story archive.

### WorldMoments

World moments are derived from saved state (seenRank, seenChapters) against what the save has reached, not from events, so they do not depend on how progress happened (exploring while away, claiming a job, a research reward). A save from before this existed adopts what it already reached without replaying it.

### WorldMoments.migratedFromHome

A save from before the home layer was removed has its opening (episode `home:1`) counted as the new opening; the old home chapters 2 to 6, their episodes and place in the story, and the layer's chapters-seen count are dropped. Dungeon clears, Guardian and everything else are kept, since the dungeons live on in the Rescue Team and Explorers layers, so nothing pays twice. The migration is safe to repeat.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/WorldSim.kt`

### Setback floors

Documented in `docs/pokelife/MODULES/WORLD.md 3.6`.

### SpeedExplanation

SpeedExplanation is built from the same numbers WorldSim.floorMillis uses, so what the player reads is what happens. skillHelp (what members' abilities add to World's skills) is shown but does not change floorMillis.

### Favoured type bonus

Documented in `docs/pokelife/MODULES/WORLD.md 3.2`.

### Floor time minimum

Documented in `docs/pokelife/MODULES/WORLD.md 14.2`.

### WorldReport.memberFloors

Documented in `docs/pokelife/MODULES/WORLD.md 5.2`.

### WorldSim deterministic settlement

Documented in `docs/pokelife/MODULES/WORLD.md 3.1`.

### Exploration XP per floor

Documented in `docs/pokelife/MODULES/WORLD.md 14.2`.

### Team power

Documented in `docs/pokelife/MODULES/WORLD.md 3.2`.

### WorldSim.bestTeam

bestTeam ranks candidates by contribution alone. Ties go to a favoured type, then the higher level, then the id, so the choice is the same every time.

### Floor time minimum

Documented in `docs/pokelife/MODULES/WORLD.md 14.2`.

### Clock rollback

Documented in `docs/pokelife/MODULES/WORLD.md 3.1`.

### Clock rollback

Documented in `docs/pokelife/MODULES/WORLD.md 3.1`.

### WorldSim.settle away cap

When elapsed time exceeds MAX_AWAY_MILLIS, settle sets the settle point to now, so the excess time is dropped instead of carried over. Within the cap the leftover fraction of a floor carries over.

### Expedition retreat

Documented in `docs/pokelife/MODULES/WORLD.md 3.6`.

### nextFloorAt wakes the screen at the instant

Documented in `docs/pokelife/MODULES/WORLD.md 10`.

### WorldSim.jobsDoneAt

jobsDoneAt gives the moment each accepted, unfinished job will be done if its team keeps exploring: when a team in the job's dungeon climbs to the job's floor and meets its team size or power. COLLECT jobs depend on what floors give and have no entry. It is what the job-done alarm is set for; the alarm settles World, and only a job that really is done is announced.

### Jobs and setbacks

Documented in `docs/pokelife/MODULES/WORLD.md 3.6`.

### WorldSim.jobsDoneAt

Investigation jobs depend on what the floors hold and story jobs on the story, so jobsDoneAt gives neither a forecast.

### WorldSim.doneAt

doneAt is only a forecast of when a trip will have climbed N more floors. It follows the same clock-rollback rule as settle (floors before the rewoundFrom mark count on the earlier clock, the rest from the mark), but settle moves the settle point itself.

### Floor outcomes

Documented in `docs/pokelife/MODULES/WORLD.md 3.6`.

### Paldea Gimmighoul Coins dice

On Paldea floors Gimmighoul Coins use their own random generator (the seed xor a constant), so adding or tuning them never changes anything else a floor gives for the same expedition seed. Gimmighoul needs 999 coins.

### WorldSim.findFor wild finds

A floor's wild find in WorldSim.findFor comes only from species that can hatch: never a legendary or mythical (Dex isSpecial), because those are story discoveries (Legends quests), not floor finds.

### WorldSim.eventSeed

Documented in `docs/pokelife/MODULES/WORLD.md 14.4`.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/story/ExplorersChaptersA.kt`

### Explorers story frame

Documented in `docs/world-story-rewrite.md Explorers chapters 1 to 4 (Witness mode)`.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/story/IntroMewChapter.kt`

### IntroMewChapter story anchors

The opening episode (Mew's Unofficial Welcome) ends with Mew added to Finds via the MEW_JOINED flag from GameStore.worldEpisodeDone, so replaying it never gives a second Mew. Other arcs lean on three facts of the opening: what Mew takes is the pair's lunch, Pelly keeps the request board and Braviary the team register, and every arc begins with a Mythical turning up and ends with the pair walking back to this board. Keep these when rewriting.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/story/RescueChapters.kt`

### Rescue story frame

Documented in `docs/world-story-rewrite.md Explorers chapters 1 to 4 (Witness mode)`.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/story/SceneState.kt`

### SceneState final state on skip

Documented in `docs/world-story-engine.md What exists (Runtime)`.

### StoryScene.problems branch check

A Branch event may or may not run, so StoryScene.problems requires that show and hide events inside it cancel out; otherwise who is on stage would depend on a flag.

### Dialogue scene two-character limit

A dialogue scene draws at most two characters at once, so StoryScene.problems rejects a DIALOGUE script that has a third on stage at the same time; a third would not appear.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/story/ScriptDsl.kt`

### Cinematic big_ characters

Documented in `docs/world-story-engine.md Also built (Cinematic scenes)`.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/story/StoryModels.kt`

### Expression fallback

The portraits cover only the first six Expression values (NORMAL, HAPPY, JOYOUS, INSPIRED, DETERMINED, SURPRISED); the remaining ones fall back to NORMAL where a Pokémon has no such face.

### BUDDY story character

Documented in `docs/pokelife/MODULES/WORLD.md 4.2`.

### SceneEvent coordinates and placeholders

Positions in a scene script are grid cells of the scene (x across, y down, floats). A line's text may contain the placeholders {buddy} and {player}, which the player substitutes at run time.

### SceneEvent.Shake

Documented in `docs/world-story-engine.md Also built (Cinematic scenes)`.

### SceneEvent.Choice

Documented in `docs/world-story-engine.md Also built (Choices and flags)`.

### StoryScene.background

A StoryScene's background is a scene image name; for a DUNGEON scene the scene is drawn on the tiles of its dungeonId instead.

### StoryEpisode.key

A StoryEpisode's key is "<layer id>:<chapter number>"; the opening episode, which belongs to no layer (layer null), is "intro:1".

### StoryEpisode.special

An optional post-story episode (chapter number SPECIAL_BASE and up) opens when its layer's story is complete (its Guardian has been faced) and is never played on its own.

### SPECIAL_BASE

Optional post-story episodes are numbered from 101 (SPECIAL_BASE); a layer's regular chapters count up from 1 and never reach that number, which is how special is told from chapter.

## `world-core/src/commonMain/kotlin/app/pokeplan/world/story/StoryPlaces.kt`

### StoryPlaces map names

Documented in `docs/pokelife/FEATURES/ASSETS_AND_AUDIO.md (rescue-maps and sky-maps row)`.

### Explorers of Sky map codes

In the Explorers of Sky map names (map:sky-<code>), the leading letter names the ground map kind: T Treasure Town, G the Guild, V and P story scenes, H cliffs, D dungeon backgrounds.

## `world-core/src/commonTest/kotlin/app/pokeplan/world/FinishTest.kt`

### WORLD rules hold no balance

The WORLD rules (WorldData) hold no Poké balance: callers are given the balance and the rules charge nothing; the app charges the price (for example Dojo.COST), so WorldData.train takes the balance as input.

## `world-core/src/commonTest/kotlin/app/pokeplan/world/HisuiTest.kt`

### Hisui species lookup

Hisui has no Region of its own in the Dex (its species live in the Sinnoh dex or another regional dex), so a check of Hisui's wild species must test against the union of all regional dexes.

## `world-core/src/commonTest/kotlin/app/pokeplan/world/KantoTest.kt`

### Mewtwo's cave asks for real power

Documented in `docs/pokelife/MODULES/WORLD.md 3.6`.

## `world-core/src/commonTest/kotlin/app/pokeplan/world/OutcomesTest.kt`

### Expedition outcomes

Documented in `docs/pokelife/MODULES/WORLD.md 3.6`.

## `world-core/src/commonTest/kotlin/app/pokeplan/world/TeamAdviceTest.kt`

### Team advice

Documented in `docs/pokelife/MODULES/WORLD.md 2.3`.

## `world-core/src/commonTest/kotlin/app/pokeplan/world/WorldTest.kt`

### WorldSim.bestTeam favoured-type test data

In the bestTeam test, favoured types get a 1.2 multiplier: Grass 34 and Bug 33 become 40.8 and 39.6 and beat a plain Water level 40 and Fire level 39, so level alone would pick the wrong team. Moves, abilities and items add a little, so the order inside the team follows the whole contribution, but the three favoured types are in and Fire is out.

## `world-core/src/jvmMain/kotlin/app/pokeplan/world/tools/WorldEconomySimulator.kt`

### WorldEconomySimulator team

The simulated team alternates species 25 (Electric, so it favors Thunderwave Cave) and species 74; the rest of the pool is neutral, so a team of size n has about n/2 favoured members.

### WorldEconomySimulator hourly yield

Hourly yield of one team in one dungeon is measured over 24 hours of staying, settled one hour at a time so the away cap (WorldSim.MAX_AWAY_MILLIS) is never hit.

### WorldEconomySimulator turned-back team

A team that turned back is home (WORLD.md 3.6, nothing is lost), so the simulation has the player send it again at the next hour.

### WORLD rules hold no balance

The WORLD rules hold no Poké balance, so the economy simulation keeps its own Journey.balance; earned Poké is banked after every call that can earn.

### WorldEconomySimulator simulated player

The simulated player opens the app a set number of times a day and keeps one team out (a second team is not played). They take jobs that fit the dungeon they are in, claim jobs and research, build the Camp and Lab (the lower level first), brew at the Café with three berries, and spend the rest of their Poké on shop Rare Candies. Deliver jobs and the Dojo are not played unless asked.

### WorldEconomySimulator best dungeon

The simulated player picks the deepest dungeon that can be started and where the team would not turn back (the team page explains this, WORLD.md 3.6). The roster's levels stay fixed, which is the pessimistic case, since real members gain a level's worth of experience from the floors.

### WorldEconomySimulator job filter

The simulated player only accepts jobs the team can do: an Escort needs enough Pokémon and a Defeat enough power, because a job that cannot be done would sit in one of the three job slots for ever.
