# PokéLife: World --- The Trickster's Invitation

> **Port status (kept current by each PR).** The owner's rewrite brief, copied here so it lives with the code. Ported to `world-core/.../story/`: **the opening, Mew's Unofficial Welcome** (`IntroMewChapter.kt`, scenes `intro-mew-1` to `7`, 0.122.0; it replaces the old home chapter 1; Mew joins through Finds once, via the `intro-mew-joined` flag and `GameStore.worldEpisodeDone`), **the Rescue Team and Explorers arcs** (`RescueChapters.kt`, `ExplorersChapters.kt`, 0.124.0; scenes on stage backgrounds where the brief's dungeon is not in the game yet). **Not yet ported:** the seven Mythical follow-ups (Hoopa, Victini, Celebi, Jirachi, Marshadow, Shaymin, Darkrai), the arcs' extra dungeons, the Kanto, Johto, Hoenn and Sinnoh adaptations, and removing the old home chapters 2 to 6 (their temporal-mystery story is superseded). Engine gaps this brief assumes: the player's own Pokémon as an on-stage character, "Witness mode" arcs as optional (not gating) layers, tutorial hints that point at real screens, and a recruit confirmation inside a scene.

**Status:** Revised narrative script / implementation draft\
**Source:** Rewritten from the supplied `world-story-script.md` and the
preceding narrative draft.\
**Implementation note:** The generated script is not the source of
truth. Port scenes into the relevant `world-core/.../story/*Chapters.kt`
files. Preserve existing scene IDs where their references are still
valid; new scenes use the IDs documented here.\
**Character tokens:** `{player}` is the player's selected Pokémon;
`{buddy}` is the player's selected story companion. Both are Pokémon.\
**World rule:** PokéLife contains no humans. All characters,
organizations, occupations, conflicts, and regional institutions are
Pokémon-led.

------------------------------------------------------------------------

# Restructure plan (owner decisions, 3 November 2026)

Decisions: **(1) The home layer is deleted completely.** The Mew opening becomes a standalone episode, and the old home dungeons, chapters 2 to 6 and temporal story go. **(2) The first two arcs to build are the two PMD arcs**, Rescue Team (6 chapters) and Explorers (8 chapters), each its own optional layer with its own chapters, dungeons, map and Guardian, in place of home. **(3) Mew and every Mythical are Guardians** (`Guardians.canBe`: slotted by rank, never sent on expeditions). Mew joins the collection at the end of the opening (as now) and is slotted when the rank has a slot (first slot at rank index 4); the line and the recruit card say it joins as the team's Guardian. No change to Guardian numbers.

Work list, in order (one PR each, tests green, the story-engine and Dungeons tests updated alongside):
1. **Layers.** `Layers.kt`: remove `HOME`; add `RESCUE("rescue", "Rescue Team")` and `EXPLORERS("explorers", "Explorers")` with their map pictures (the home layer's eight `map-N` stages become one of them or are reused as a picture). `Story.chaptersOf`, `guardianBonus`, `WorldGuide`, `Missions.layerOf`, `WorldSim` line about the home layer's Guardian, `WorldData.layerDone` (`guardianDone` for home), the World screens' `when (layer)` (map spots), `AppModel.worldLayer`'s default.
2. **Dungeons.** Split `Dungeons.home` between the two arcs by the brief (Rescue: Tiny Woods, Frosty Forest, Sky Tower and the places the six chapters name; Explorers: Beach Cave, Drenched Bluff, Treasure-town dungeons, Temporal Tower and the rest); keep every dungeon id unchanged so clears, missions and teams in old saves still resolve.
3. **Save migration.** `WorldData` keys by layer id: map `clears`, `layersDone`, `guardianDone`, `episodesDone` (`home:N`), `scenesDone` (`home-*`), `storyResume` and `WorldMoments` for the removed layer to the new ids or drop them (never pay a reward twice: rewards come from chapter unlocks, so migrated chapters count as already opened). Old saves must load; add a test that decodes a save with `home` data.
4. **Story catalog.** Delete `MysteryDungeonChapters.kt`; add `RescueChapters.kt` (chapters 1 to 6 from this brief, scene ids `rescue-N-*` as written) and `ExplorersChapters.kt` (chapters 1 to 8, `explorers-N-*`); `IntroMewChapter` loses its layer (a standalone `intro` episode: the catalog and archive need a layer-less entry). Mew's 'witness' framing (a memory echo) is a premise line of each arc's first scene; `{player}` stays a name in text until the engine can put the player's Pokémon on stage.
5. **Guide, map and archive** show the two arcs as optional layers; nothing in them gates ordinary exploration.

Read the Explorers chapters 4 to 8 (lines from "The Thief of Time" on) in full before writing them; chapters 1 to 3 and the Rescue Team arc are above.


# Narrative direction

## The actual premise

PokéLife: World is a Pokémon Mystery Dungeon-style exploration game. Its
story premise is deliberately simple:

**A handful of mischievous Mythical Pokémon take an interest in
`{player}` and `{buddy}`. They keep pulling the pair into unusual
adventures, tests, detours, and dungeon expeditions. When a Mythical
Pokémon is satisfied with how the pair handled its adventure, it chooses
to join them.**

There is no need for a grand prophecy, chosen-one status, universal
catastrophe, or elaborate explanation for why the player is special. The
player and buddy are interesting to these Mythical Pokémon because they
react to their tricks in ways the Mythical Pokémon find entertaining,
admirable, or unexpected.

The Mythical Pokémon are the narrative hook, the introductory cast, and
the tutorial guides for the World module. Their adventures introduce the
player to the world and its systems naturally. The story should feel
like a string of memorable PMD episodes rather than a long main plot
that the player must complete before playing the actual game.

## The opening is the tutorial

The first World experience should begin with a Mythical Pokémon's
trick---not with a long guild orientation, a history lesson, a prophecy,
or a conventional main-story prologue.

The opening sequence should:

1.  Establish `{player}` and `{buddy}` as an existing pair, or bring
    them together quickly without a human-origin premise.
2.  Have a Mythical Pokémon interrupt their ordinary day with a strange
    invitation, prank, challenge, or apparent mishap.
3.  Pull them into a short, playable adventure that introduces the basic
    controls and exploration flow.
4.  Introduce the Frontier and its exploration culture through the
    places they visit and Pokémon they meet.
5.  Teach the practical World systems as part of the adventure: party
    setup, team management, dungeon expeditions, jobs, rewards,
    recruitment, and rank progression.
6.  End with the Mythical Pokémon deciding that the pair passed its test
    or made the adventure worthwhile.
7.  Have that Mythical Pokémon join the player's available
    party/collection as a meaningful story reward.

Do not front-load every system in one dialogue dump. Teach one or two
concepts at the moment they become useful. The player should learn by
doing.

## The Mythical Pokémon are the premise

The recurring Mythical Pokémon are independent tricksters with distinct
motives and personalities. They are not a formal council, a set of
quest-givers, or custodians of a grand mystery. They may know one
another, compete, prank one another, or occasionally cooperate, but they
do not need a shared master plan.

Their adventures can involve: - A fake rescue request that turns into a
real rescue. - A dungeon whose layout has been rearranged as a puzzle. -
A race, scavenger hunt, or challenge with an unexpected objective. - A
portal to a strange place opened without warning. - A memory or dream
recreated for the fun of seeing how the pair reacts. - A small problem
the Mythical Pokémon caused and now wants help fixing. - A test of
teamwork, trust, cleverness, patience, or compassion.

Their tricks should create playable situations, not merely cutscenes. A
Mythical Pokémon should have a reason to be satisfied at the end: the
player solved its puzzle, helped someone, showed good judgment,
persevered, or responded in a way it did not expect.

**Recruitment rule:** Each Mythical Pokémon joins only after its own
adventure reaches a satisfying conclusion. This is a character decision,
not a generic capture. The Mythical Pokémon may announce that it wants
to travel with the team, or casually appear at the team's base
afterward. The scene should make the joining moment explicit and
memorable.

Suggested personalities and adventure styles:

-   **Mew:** playful, curious, capable of disguise and imitation. Best
    for the opening prank/adventure and teaching basic exploration.
-   **Hoopa:** mischievous and boastful. Uses rings to create
    portal-based detours, unusual dungeon layouts, and dimensional
    excursions. Good for teaching navigation and expedition preparation.
-   **Celebi:** curious and compassionate, connected to time. Can create
    a short memory or time-loop challenge, but should not turn the game
    into a historical saga.
-   **Jirachi:** sleepy, sincere, and wish-focused. Good for a dreamlike
    challenge about understanding what someone actually wants.
-   **Victini:** energetic and competitive. Good for a race, battle
    trial, or timed expedition that teaches team composition and
    tactical choices.
-   **Marshadow:** quiet and elusive. Good for stealth, observation, and
    noticing things hidden in plain sight.
-   **Shaymin:** gentle but adventurous. Good for a restoration or
    escort adventure.
-   **Darkrai:** unsettling and misunderstood. Good for a
    dream/nightmare episode where the apparent threat has a more
    personal explanation.

Use only the Mythical Pokémon that fit the current story and available
assets. Do not force all of them into the opening. The first few
introductions should establish the pattern; others can appear as
optional adventures over time.

## The Frontier and the ordinary game

The Frontier is the custom PMD-style home setting. It has a settlement,
guild or expedition center, shops, wilderness, dungeons, residents, and
other exploration teams. It should feel lived-in, but it does not need a
complicated political history or a large central conflict.

The Frontier exists to support the player's ongoing exploration life: -
Build and manage parties from the player's Pokémon collection. - Accept
rescue, retrieval, escort, investigation, delivery, and exploration
jobs. - Send teams into Mystery Dungeons. - Earn rewards, discover
Pokémon, and record findings. - Earn Exploration Points and advance
through the existing rank progression. - Return to the hub, interact
with residents, and take on more adventures.

The Mythical introductions teach these systems, but they do not replace
them. The regular expedition/job/rank loop remains playable after the
tutorial adventures are complete. Story progression must not become a
mandatory bottleneck for ordinary exploration.

Avoid overbuilding the home-world lore before the player has a reason to
care. Introduce locations and recurring NPCs through jobs and
adventures. Add deeper setting details only when they support a
character, gameplay feature, or later episode.

## Other Pokémon game stories are optional adventures

The World module does not need a single overarching retelling of every
Pokémon game. Mainline and PMD stories can exist as optional adventure
arcs, dimensional trips, memory experiences, or special episodes
initiated by Mythical Pokémon.

These arcs are secondary to the player's own exploration life. They
should be added when they offer a good adventure, recognizable
characters, or interesting dungeons---not to satisfy a checklist of
every game.

When adapting an existing game's story: - Preserve its recognizable
characters, central conflict, emotional beats, and defining outcome. -
Keep the original protagonist's accomplishments intact. - Let `{player}`
and `{buddy}` observe, accompany, or handle a parallel task where
appropriate. - Use a memory, dream, portal, or dimensional trip only
when it makes the episode more interesting. Do not force every story
into one explanation. - Adapt human roles into a Pokémon-only society.
No humans, human trainers, human professors, or human-run
organizations. - Keep each arc independently accessible. Do not require
the player to complete other regional stories first.

## No mandatory overarching mystery

Do not make the Convergence, a prophecy, a hidden cosmic threat, or a
single master villain the organizing principle of World.

A loose recurring sense that Mythical Pokémon enjoy sending the pair on
adventures is enough. Occasional strange overlaps between places,
dreams, memories, and dimensions can be used as episode premises without
needing one explanation or final reveal.

The story can remain open-ended indefinitely: a new Mythical Pokémon
appears, starts trouble, gives the pair an adventure, becomes satisfied,
and joins them. The player's ongoing exploration progression is the
continuity that ties the episodes together.

## Dialogue and scene rules

-   Write characters as individuals, not as interchangeable
    quest-givers.
-   Let characters have habits, blind spots, humor, pride, fears, and
    conflicting interpretations.
-   Do not make every line explain the plot. Characters should sometimes
    talk about food, work, weather, other Pokémon, or nothing
    particularly important.
-   Avoid repetitive constructions such as "Then we must go," "Something
    is wrong," and "We have come to restore the balance."
-   Use narration to establish what sprites cannot communicate:
    atmosphere, physical sensations, passing time, and small details.
-   Use sprite blocking for reactions and comedy: looking away,
    shuffling, approaching, backing up, peeking around corners, or
    trying to look brave.
-   Use portraits for meaningful expression changes. Do not change
    expression on every line.
-   Use silence. A pause after a revelation can be stronger than another
    explanatory line.
-   The player may be quiet or react through emotes. Do not force
    `{player}` to deliver long exposition.
-   Choices should express temperament, compassion, curiosity, humor, or
    trust. Avoid choices that merely select the same answer in different
    words.
-   Do not make legendary Pokémon automatically friendly or
    automatically reward the player. Their response should follow their
    character and the events of the story.
-   Canon-inspired adaptations may rearrange details for a Pokémon-only
    society, but should retain the source material's recognizable
    emotional core.

------------------------------------------------------------------------

# Opening tutorial --- Mew's Unofficial Welcome

**Purpose:** Introduce the player, buddy, Frontier, exploration loop,
and the rule that Mythical Pokémon initiate adventures and may join the
party when satisfied.\
**Mode:** A real adventure in the Frontier. No memory framing,
dimensional lore, or main-story mystery is required.\
**Main cast:** `{player}`, `{buddy}`, Mew, Braviary, Pelly, and a small
number of Frontier residents.

This opening should be short, playable, and character-led. Mew is the
first Mythical Pokémon encountered and the first potential recruit. The
player should be playing the World module---not watching a long
introduction to it---within the first few scenes.

## Scene 1 --- A very suspicious invitation

`intro-mew-1` · cinematic/dialogue · background frontier-town · cast:
`{player}`, `{buddy}`, Mew

*`{player}` and `{buddy}` are heading through the settlement when a
small pink Pokémon darts between them, snatches an item, and vanishes
around a corner.*

**{buddy}** (surprised): Hey! That was ours!

*The pair follow the trail. A familiar-looking Pokémon appears at the
end of the lane. It waves, then changes shape.*

**{buddy}** (surprised): Wait. You were the one who took it?

*Mew changes back, holding the item just out of reach.*

**Mew** (happy): Mew!

**{buddy}** (angry): Give it back!

*Mew floats backward toward the edge of town, clearly inviting them to
chase.*

**{buddy}** (determined): It's a trap.

*Pause.*

**{buddy}** (normal): ...We're going, aren't we?

*The player and buddy follow. This establishes Mew as playful and
mischievous, not malicious.*

## Scene 2 --- First steps outside town

`intro-mew-2` · tutorial · background meadow · cast: `{player}`,
`{buddy}`, Mew

*Mew leads them to a small, unfamiliar path. A few wild Pokémon are
gathered around a dropped satchel.*

**{buddy}** (normal): Is this what you wanted to show us?

*Mew points at the satchel, then at a narrow path leading into the
nearby Mystery Dungeon.*

**{buddy}** (worried): You want us to go in there?

*Mew nods enthusiastically.*

**{buddy}** (normal): Fine. But if this is another prank, you're
carrying the bag home.

*Tutorial: basic movement, interaction, and the first small dungeon
encounter. Keep the first dungeon short and forgiving. Teach one
interaction at a time; avoid explanatory paragraphs.*

## Scene 3 --- The first expedition

`intro-mew-3` · dungeon/tutorial · dungeon beginner-woodland · cast:
`{player}`, `{buddy}`, Mew

*Inside the dungeon, the pair discover that the satchel belongs to a
small Pokémon who got separated from its group. The route is not
dangerous, but it includes a simple obstacle and one manageable
encounter.*

**{buddy}** (worried): So this wasn't just about chasing us. Someone
actually needs help.

*Mew watches from a branch, unusually quiet.*

**{buddy}** (determined): Let's get the satchel back to its owner.

*Tutorial beats, delivered through play:* - Move through a dungeon
floor. - Inspect an object or point of interest. - Resolve a simple
encounter. - Reach the objective and leave safely.

*Do not show every dungeon rule at once. Introduce more advanced
mechanics later through Hoopa and Victini's adventures.*

## Scene 4 --- A job becomes a promise

`intro-mew-4` · dialogue · background frontier-town · cast: `{player}`,
`{buddy}`, Pelly, rescued Pokémon, Mew

*Back in town, the satchel's owner is relieved. Pelly, who handles
requests for the local exploration center, has heard what happened.*

**Pelly** (happy): You two went into the woods, found the owner, and
brought the satchel back?

**{buddy}** (normal): We were following a thief.

*Mew looks away innocently.*

**Pelly** (normal): That is not how I would recommend finding your first
job. But it did help someone.

**{buddy}** (normal): So this is what exploration teams do? Take
requests and help Pokémon?

**Pelly** (happy): That's the idea. Rescue, delivery, retrieval, escort,
investigation. Some jobs are simple. Some take a whole team.

*Pelly points to the request board.*

**Pelly** (normal): You can choose what to take. Check the objective and
reward before setting out.

*Tutorial: introduce the job board, job details, and accepting a
request. The first completed rescue/retrieval is recorded as a job.*

## Scene 5 --- The team

`intro-mew-5` · dialogue · background exploration-center · cast:
`{player}`, `{buddy}`, Braviary, Pelly, Mew

*Braviary reviews the team's report.*

**Braviary** (normal): You went into an unfamiliar dungeon without
checking the route, supplies, or threat level.

**{buddy}** (embarrassed): We had a reason.

*Mew raises a paw.*

**Braviary** (normal): I see. The reason appears to be standing right
there.

*Mew grins.*

**Braviary** (normal): If you intend to keep exploring, register as a
team and learn to prepare properly.

*Braviary shows them the expedition board and team setup.*

**Braviary** (normal): Choose who is going, check the team's strengths,
and make sure the assignment suits them. A larger team is not
automatically a better team.

*Tutorial: team formation, party selection, basic team roles, and
expedition preparation. Use the player's current Pokémon collection
where possible; do not force a fixed party beyond `{player}` and
`{buddy}`.*

## Scene 6 --- Mew's real test

`intro-mew-6` · dungeon/tutorial · dungeon beginner-woodland · cast:
`{player}`, `{buddy}`, Mew

*That evening, Mew appears at the edge of the settlement. It creates
several copies of itself and scatters in different directions. One copy
carries a small bundle; another is pretending to be a frightened
Pokémon.*

**{buddy}** (annoyed): Again?

*Mew's copies dart toward the woods. One of them leaves a trail of small
objects behind.*

**{buddy}** (normal): It's testing us. It wants to see if we'll chase
the first thing we notice.

*The actual task is to identify the real Mew's trail, ignore decoys, and
help a Pokémon caught in the confusion. The challenge tests observation
and teamwork rather than combat strength.*

*After the pair resolve the situation, the copies vanish. The real Mew
appears in front of them.*

**Mew** (happy): Mew!

**{buddy}** (normal): Was that your idea of fun?

*Mew nods, then looks at the pair more thoughtfully. It offers the
stolen item back and bows its head.*

**{buddy}** (surprised): You're... asking to come with us?

*Mew gives an eager cry and floats beside the team.*

**{buddy}** (happy): All right. But no stealing our lunch.

*Mew immediately looks interested in the nearby food stall.*

*Recruitment: Mew joins the player's available party/collection. Show
the recruit confirmation and where Mew can be assigned. This is the
first example of the Mythical satisfaction-and-joining rule.*

## Scene 7 --- The adventure board

`intro-mew-7` · dialogue/tutorial · background exploration-center ·
cast: `{player}`, `{buddy}`, Braviary, Pelly, Mew

*The team returns to the exploration center. Their registration is
complete, and the board now shows a range of available assignments.*

**Pelly** (happy): Your first proper expedition board. There are jobs
here for teams of different ranks.

**Braviary** (normal): Your rank reflects your work over time. Complete
assignments, help other Pokémon, and take on greater challenges when you
are ready.

**{buddy}** (happy): So we can choose where to go?

**Braviary** (normal): Within your team's capabilities. The world is
larger than this settlement. You will discover it one expedition at a
time.

*Mew taps a notice with its tail. The notice is upside down.*

**{buddy}** (normal): I don't think that's how you read a job.

*Mew giggles.*

*Tutorial: introduce rank progression, rewards, available expeditions,
and the World home screen. End the guided sequence and hand control to
the player.*

## Tutorial completion criteria

The opening is complete when the player has: - Moved around the Frontier
and interacted with at least one resident. - Completed a short,
forgiving Mystery Dungeon expedition. - Seen a simple objective through
to completion. - Opened the job board and accepted/completed a starter
request. - Set up an expedition party. - Received a first Mythical
recruit (Mew). - Seen where party members are managed. - Seen how
rewards and Exploration Rank progression work.

Do not require the player to finish a long plot, unlock a legendary, or
understand dimensional travel before the ordinary World loop becomes
available.

------------------------------------------------------------------------

# Follow-up Mythical introductions --- Optional tutorial adventures

These are short character episodes, not chapters in a single main story.
Each one teaches a useful system or play pattern and ends with the
Mythical Pokémon joining if the player satisfies its particular
challenge. They can be introduced gradually through the hub, job board,
or spontaneous encounters.

## Hoopa --- The Wrong Door

**Gameplay focus:** portals, expedition selection, unfamiliar dungeon
layouts, returning safely.\
**Adventure:** Hoopa opens several rings around the settlement. Each
shows a different location, but one ring leads somewhere entirely
unexpected. Hoopa insists it knows the route; it does not. The player
and buddy must navigate the strange dungeon, identify the correct exit,
and help Hoopa retrieve something it misplaced.\
**Satisfaction condition:** The player adapts to an unfamiliar route and
helps Hoopa fix the mess without losing their way.\
**Recruitment:** Hoopa decides the pair are entertaining travel
companions and joins. Its rings can then be used as a narrative device
for later optional expeditions.

## Victini --- The Perfect Run

**Gameplay focus:** party composition, combat/tactical choices, optional
objectives, performance feedback.\
**Adventure:** Victini challenges the pair to a friendly expedition
race. It keeps changing the rules because it wants a genuinely exciting
contest, not a predictable win. The route includes optional goals that
reward clever play rather than raw speed.\
**Satisfaction condition:** The player demonstrates teamwork and
adaptability. Winning is not mandatory if the pair show good
sportsmanship and solve the challenge.\
**Recruitment:** Victini joins because it wants to keep testing new team
combinations and exploring with them.

## Celebi --- One More Try

**Gameplay focus:** replaying a short challenge, investigating clues,
understanding a dungeon's pattern.\
**Adventure:** Celebi shows the pair a small time-loop in a forest
dungeon. The same short sequence repeats, but the player can notice
details and change their approach. This is a contained puzzle, not a
historical retelling or a large time-travel plot.\
**Satisfaction condition:** The player notices the pattern and helps the
Pokémon caught in the loop.\
**Recruitment:** Celebi joins because the pair treated the repeated
moment with patience and compassion.

## Jirachi --- A Little Wish

**Gameplay focus:** NPC interaction, optional objectives, choosing how
to help.\
**Adventure:** Jirachi wakes with a wish it cannot quite understand. The
pair visit several Pokémon and learn that the wish is not a treasure or
a powerful item; it is something small that another Pokémon has been
hoping for.\
**Satisfaction condition:** The player discovers what would genuinely
help rather than chasing the most impressive reward.\
**Recruitment:** Jirachi joins after the wish is fulfilled, curious to
see what other small hopes the team encounters.

## Marshadow --- The Unnoticed Path

**Gameplay focus:** observation, hidden routes, stealth or avoidance,
inspecting the environment.\
**Adventure:** Marshadow silently follows the pair through a dungeon,
leaving subtle clues. The objective is to notice the path and help a
lost Pokémon without alerting the dungeon's stronger inhabitants.\
**Satisfaction condition:** The player succeeds through awareness and
restraint rather than defeating everything.\
**Recruitment:** Marshadow joins because the pair noticed what others
overlooked.

## Shaymin --- The Long Way Around

**Gameplay focus:** escort, gathering, environmental interaction,
optional side paths.\
**Adventure:** Shaymin asks the pair to help restore a damaged patch of
wilderness. The direct route is blocked, so the team gathers what is
needed and escorts a small group of Pokémon along a safer path.\
**Satisfaction condition:** The player protects the group and completes
the restoration without rushing.\
**Recruitment:** Shaymin joins because it wants to see more places
recover and flourish.

## Darkrai --- A Dream with Teeth

**Gameplay focus:** dream dungeon, status effects or altered rules,
interpreting NPC dialogue.\
**Adventure:** A frightening dream spreads through a small settlement.
Darkrai appears to be responsible, but the dream is being amplified by a
Pokémon's unresolved fear. The player must navigate the nightmare and
help uncover its source.\
**Satisfaction condition:** The player faces the dream without assuming
that fear proves someone is evil.\
**Recruitment:** Darkrai joins only after the misunderstanding is
resolved and it chooses to trust the pair. Keep the tone eerie but not
needlessly cruel.

------------------------------------------------------------------------

# Optional adventure arc --- Pokémon Mystery Dungeon: Red & Blue Rescue Team

**Setting:** Pokémon Square and the surrounding rescue territories.\
**Tone:** Warm, funny, adventurous, occasionally frightening, and
ultimately bittersweet.\
**Central themes:** Belonging, trust, responsibility, fear of the
unknown, and choosing to help even when the world misunderstands you.\
**Main cast:** `{player}`, `{buddy}`, Caterpie, Whiscash, Gengar, Ekans,
Medicham, Alakazam, Charizard, Tyranitar, Ninetales, Absol, the
legendary birds, and Rayquaza.

This campaign is independent of the Explorers campaign. It uses the
Rescue Team world, not Treasure Town or the Time Gear storyline.

**Witness mode:** Mew brings the Frontier team into a real historical
echo of the Rescue Team era. The player and buddy appear as a secondary
rescue team within the remembered region. They can help residents and
witness private moments, but the original Rescue Team remains
responsible for the defining decisions and the meteor crisis. The echo
is based on events that really happened; it is not a fictional dream.

The original Rescue Team's protagonist species may vary, so the script
refers to them collectively as **the Rescue Team** rather than assigning
them a fixed species or sprite. Where the player team appears beside
them, stage the canonical team only when the asset and
character-selection system can represent it safely.

## Chapter 1 --- A Pokémon Out of Place

*Summary:* `{player}` wakes in a world inhabited entirely by Pokémon,
with no memory of how they arrived. `{buddy}` finds them and decides to
help. Their first rescue request becomes the beginning of a new team.

### Scene: The voice in the grass

`rescue-1-awakening` · dialogue · background meadow · cast: `{player}`,
`{buddy}`, Mew (Mew)

*The Frontier's forest disappears in a wash of green light. The team
lands in soft grass beneath an unfamiliar sky.*

**{buddy}** (surprised): That was not a normal portal.

*Mew floats upside down in front of them, looking delighted.*

**Mew** (happy): Mew!

**{buddy}** (worried): You brought us somewhere without asking.

*Mew tilts its head, then points toward the distant settlement.*

**{buddy}** (normal): Is this a place you want us to see?

*Mew nods. The air shimmers. For a heartbeat, the team sees two versions
of the same landscape: one quiet and empty, the other full of Pokémon
hurrying through a busy day.*

**{buddy}** (surprised): A memory?

*Mew circles them. The image settles into the busy version.*

**{buddy}** (determined): All right. We'll look around. But you're
explaining this when we get back.

*Mew gives a cheerful cry and vanishes into the trees.*

### Scene: Pokémon Square

`rescue-1-square` · stage · background city · cast: `{player}`,
`{buddy}`, Kecleon (Kecleon), Gulpin (Gulpin), Persian (Persian),
Wigglytuff (Wigglytuff)

*Pokémon Square is busy. Kecleon tends his shop, Gulpin arranges items
outside his stall, and several Pokémon argue over the notice board.*

**{buddy}** (happy): Welcome to Pokémon Square! It's not much, but
you'll get used to it.

**Kecleon** (happy): Welcome, welcome! Looking for anything? Supplies? A
bargain? A very reasonable bargain?

**{buddy}** (normal): We just got here, Kecleon.

**Kecleon** (surprised): And? Looking is free!

*Gulpin chuckles from his stall.*

**Gulpin** (happy): Let them breathe, Kecleon. They haven't even had
lunch.

**{buddy}** (worried): Actually... that's a good point. Have you eaten?

*You shake your head.*

**{buddy}** (surprised): You haven't? Oh. Right. We should fix that.

*The square's request board catches your eye. A small Caterpie is
standing in front of it, looking increasingly distressed.*

### Scene: A tiny rescue

`rescue-1-caterpie` · dialogue · background meadow · cast: `{player}`,
`{buddy}`, Caterpie (Caterpie)

**Caterpie** (worried): Excuse me... Are you a rescue team?

**{buddy}** (surprised): Us? Well, not exactly. We're---

**Caterpie** (worried): My friend Metapod went into Tiny Woods and
hasn't come back!

*Caterpie's antennae droop.*

**Caterpie** (worried): I know it's not very far. But what if something
happened? I don't know what to do.

*{buddy} looks at you. This is not the kind of day either of you
expected.*

**{buddy}** (determined): We'll go find them.

**Caterpie** (surprised): You will?

**{buddy}** (happy): Of course. That's what we're here for. ...I think.

*{buddy} gives you a quick, uncertain glance.*

**{buddy}** (normal): Come on. Let's not keep Caterpie waiting.

### Scene: Tiny Woods

`rescue-1-tiny-woods` · dungeon · dungeon tiny-woods · cast: `{player}`,
`{buddy}`, Metapod (Metapod)

*Sunlight filters through the canopy. The woods are peaceful, but
unfamiliar sounds echo between the trees.*

*Near a bend in the path, a small shape is wedged between two roots.*

**Metapod** (worried): ...Metapod.

**{buddy}** (surprised): There you are! Caterpie's been worried sick!

*The two of you help Metapod out. It wobbles, then settles.*

**{buddy}** (happy): There. Not a scratch.

**Metapod** (happy): Metapod!

**{buddy}** (amused): I think that's a thank-you.

*On the way back, {buddy} keeps glancing at you.*

**{buddy}** (normal): You didn't hesitate back there.

*{buddy} looks ahead.*

**{buddy}** (happy): I think we're going to make a good team.

### Scene: A team takes shape

`rescue-1-team` · dialogue · background city · cast: `{player}`,
`{buddy}`, Caterpie (Caterpie), Persian (Persian)

*Back at the square, Caterpie is overjoyed to see Metapod.*

**Caterpie** (joyous): Metapod! You're okay!

*The two friends celebrate. Caterpie turns to you.*

**Caterpie** (happy): Thank you! I won't forget this!

*Persian, who has been watching from the square, approaches.*

**Persian** (normal): A rescue completed without a team badge, a proper
base, or even a plan. Hm.

**{buddy}** (worried): We had a plan.

**Persian** (normal): You followed a path into the woods and brought
someone back.

**{buddy}** (normal): ...A simple plan.

**Persian** (happy): Still. Pokémon who help others are welcome here. If
you intend to keep doing it, you should make it official.

*Persian gestures toward an empty patch of land outside the square.*

**Persian** (normal): There is a place you could use as a base. Nothing
fancy. It will need work.

**{buddy}** (surprised): A base? For us?

**Persian** (happy): Every rescue team needs somewhere to return to.

*{buddy} turns to you. The uncertainty has not entirely gone away, but
there is a little excitement in their expression.*

**{buddy}** (determined): What do you say? Shall we become a rescue
team?

*End chapter. Establish the team's base and unlock rescue requests.*

## Chapter 2 --- The World Needs Helping

*Summary:* The team takes on more requests and becomes known around
Pokémon Square. The disasters are getting worse, but most Pokémon are
too busy with their own problems to notice the pattern.

### Scene: The first morning at the base

`rescue-2-base` · dialogue · background meadow · cast: `{player}`,
`{buddy}`

*Morning light falls across the team's new base. It is small, a little
dusty, and not particularly impressive.*

**{buddy}** (happy): Our very own rescue base!

*{buddy} steps inside. A little dust puffs into the air.*

**{buddy}** (surprised): ...It needs some work.

*{buddy} looks around, then grins.*

**{buddy}** (happy): But it's ours.

*Outside, the ground gives a faint shudder. A few pebbles tumble from
the hillside.*

**{buddy}** (worried): Was that an earthquake?

*The shaking stops.*

**{buddy}** (normal): Maybe a small one. They've been happening more
often lately.

### Scene: Whiscash's warning

`rescue-2-whiscash` · dialogue · background city · cast: `{player}`,
`{buddy}`, Whiscash (Whiscash)

*At the pond, Whiscash floats with his eyes half closed. He looks as
though he has been waiting for someone to ask him a question.*

**Whiscash** (happy): Wooo-hooo! A new rescue team! Come closer, young
ones!

**{buddy}** (normal): Hello, Whiscash. We wanted to ask about the
earthquakes.

**Whiscash** (normal): Ahhh. The ground has been restless. The mountains
rumble, the seas swell, and even the quiet places have begun to tremble.

**{buddy}** (worried): Is it going to get worse?

**Whiscash** (normal): The world has its own rhythms. Sometimes they
change. Sometimes they are warning us.

*Whiscash opens one eye.*

**Whiscash** (normal): There is an older tale about a rescue team that
arrived shortly before the great disasters began.

**{buddy}** (worried): So the team caused them?

**Whiscash** (normal): That is what some Pokémon believed. It was easier
to blame a newcomer than to admit that nobody understood what was
happening.

**{buddy}** (normal): Did the team leave?

**Whiscash** (normal): They kept helping. That is the part worth
remembering.

### Scene: The first signs

`rescue-2-disasters` · montage · backgrounds dungeon · cast: `{player}`,
`{buddy}`, various Pokémon

*Across the region, disasters interrupt ordinary life.*

-   *A sudden tremor cracks the floor of a cave. Diglett scrambles to
    safety.*
-   *A river swells without warning. Wooper and Quagsire help stranded
    Pokémon reach higher ground.*
-   *A gust tears through a forest. Hoothoot cling to branches as leaves
    spiral through the air.*

**{buddy}** (worried): These aren't ordinary accidents.

**{buddy}** (determined): We'll keep taking requests. If Pokémon are in
trouble, we can at least help them.

*The team earns a reputation. Requests arrive faster than they can be
pinned to the board.*

## Chapter 3 --- The Fugitive

*Summary:* Gengar twists an old omen into a rumor that the arrival of
the Rescue Team foretells disaster. The player team witnesses the
accusation and sees how quickly fear spreads.

### Scene: The dream

`rescue-3-dream` · dialogue · background dark · cast: `{player}`,
Gardevoir (Gardevoir)

*You stand in a dark place. A Gardevoir appears, her outline faint and
distant.*

**Gardevoir** (worried): Please... You must leave.

*The ground trembles. The darkness ripples.*

**Gardevoir** (sad): They will come for you. They won't understand.

*She reaches toward you, but the dream begins to break apart.*

**Gardevoir** (worried): I'm sorry. I couldn't protect you.

*You wake.*

### Scene: Gengar's story

`rescue-3-gengar` · dialogue · background city · cast: `{player}`,
`{buddy}`, Gengar (Gengar), Ekans (Ekans), Medicham (Medicham)

*Gengar has gathered a small crowd near the square's notice board.*

**Gengar** (happy): Oh, don't mind me. I was just telling a little
story.

**Ekans** (amused): The old omen? The one about a strange team arriving
before the disasters?

**Gengar** (happy): That's the one! The tale says a newcomer appears
whenever the world is about to turn upside down.

**Medicham** (worried): And you think the Rescue Team is that omen?

**Gengar** (normal): I didn't say that. I only said it's an interesting
coincidence.

*Gengar notices you.*

**Gengar** (surprised): Ah! And here is the visiting team. You arrived
just as the rumors began.

**{buddy}** (angry): We arrived because Mew brought us here. We didn't
cause anything.

**Gengar** (normal): I never said you did. I only wonder why so many
unusual things happen around newcomers.

**{buddy}** (determined): Then investigate the facts. Don't turn
frightened Pokémon against a team that is trying to help.

**Gengar** (happy): If you say so.

*Gengar turns away, smiling.*

### Scene: The square turns

`rescue-3-exile` · dialogue · background city · cast: `{player}`,
`{buddy}`, Alakazam (Alakazam), Charizard (Charizard), Tyranitar
(Tyranitar)

*Whispers follow you through the square. Some Pokémon avoid your eyes.
Others stare openly.*

**{buddy}** (worried): They really believe him...

*Alakazam approaches with Charizard and Tyranitar.*

**Alakazam** (normal): We have heard the rumors. We have also seen your
team's work.

**{buddy}** (worried): Then you know we didn't cause the disasters.

**Alakazam** (normal): We know that there is no proof. But the square is
frightened, and frightened Pokémon are not always reasonable.

**Charizard** (angry): If you stay, this could turn ugly.

**{buddy}** (angry): So we're supposed to leave?

**Tyranitar** (normal): For now. Until we know more.

*{buddy} looks at you, then back at the three rescuers.*

**{buddy}** (sad): Fine. We'll go.

*The two of you leave the square. Nobody follows.*

## Chapter 4 --- Beyond the Village

*Summary:* Branded as a danger, the team travels through harsh
territory. Absol joins them and explains that the disasters may be signs
of a greater catastrophe.

### Scene: A stranger in the snow

`rescue-4-absol` · dungeon · dungeon frosty-forest · cast: `{player}`,
`{buddy}`, Absol (Absol)

*Snow falls over a forest of pale trees. The wind carries a low, distant
rumble.*

**{buddy}** (worried): I don't like this place. The snow is getting
heavier.

*A white shape watches from between the trees.*

**Absol** (normal): You should turn back.

**{buddy}** (surprised): Who are you?

**Absol** (normal): Someone who has learned not to ignore warnings.

**{buddy}** (angry): If you're here to tell us we're causing the
disasters, save your breath.

**Absol** (surprised): I never said that.

*Absol steps into view.*

**Absol** (normal): I can sense disasters before they happen. I have
followed their signs from one region to another.

**{buddy}** (worried): Then you know what's causing them?

**Absol** (sad): No. Only that something is coming. And that Pokémon
often blame the one who appears just before trouble begins.

*Absol looks at you.*

**Absol** (normal): I know what it is like to be mistaken for an omen.
If you will allow it, I would like to travel with you.

**{buddy}** (happy): You can join us. We could use someone who knows
what to look for.

### Scene: A hard night

`rescue-4-camp` · dialogue · background mountain · cast: `{player}`,
`{buddy}`, Absol (Absol)

*The three of you shelter beneath a rocky overhang. Wind howls outside.*

**{buddy}** (sad): Do you think the others will ever believe us?

**Absol** (normal): Some will. Some won't. You cannot build your life
around convincing every Pokémon who misunderstands you.

**{buddy}** (worried): That's easy to say when you're used to being
alone.

*Absol looks away.*

**Absol** (sad): It is not easy. I simply learned that waiting for
others to understand can become its own kind of prison.

*There is a long pause. {buddy} nudges a berry toward Absol.*

**{buddy}** (normal): You should eat. You look like you haven't had a
proper meal in days.

**Absol** (surprised): ...Thank you.

*For the first time, Absol's expression softens.*

## Chapter 5 --- The Sky Is Falling

*Summary:* The team discovers that a meteor is approaching the world.
The disasters are symptoms of its arrival. The legendary birds are
stirred, and the player team helps clear the way while the original
Rescue Team undertakes the defining journey to Rayquaza.

### Scene: The mountain's message

`rescue-5-ninetales` · dialogue · background mountain · cast:
`{player}`, `{buddy}`, Ninetales (Ninetales), Alakazam (Alakazam)

*Atop a remote mountain, Ninetales watches the clouds move below.*

**Ninetales** (normal): So. The one from the old tale has come.

**{buddy}** (worried): You know about the story?

**Ninetales** (normal): I know what happened. A curse was laid upon a
Pokémon long ago. A Gardevoir gave everything to protect her partner.

*Ninetales turns toward you.*

**Ninetales** (normal): But the tale was never a verdict. It was a
warning about how easily fear becomes cruelty.

**{buddy}** (angry): Then tell everyone! Tell them we didn't cause this!

**Ninetales** (sad): They must learn it for themselves.

*The sky darkens. A distant flash lights the clouds.*

**Ninetales** (worried): The disasters are not the work of one Pokémon.
Something is falling from the sky.

### Scene: The legendary birds

`rescue-5-birds` · cinematic · background thunderplains · cast:
`{player}`, `{buddy}`, Articuno (Articuno), Zapdos (Zapdos), Moltres
(Moltres)

*The air turns bitterly cold. A shadow sweeps across the snow.*

**Articuno** (angry): Who trespasses upon my mountain?

**{buddy}** (worried): We need to reach the sky. Something is coming.

**Articuno** (angry): The mountain is restless. The air is disturbed. I
will not allow strangers to pass.

*Lightning splits the clouds. Zapdos descends onto a nearby ridge.*

**Zapdos** (angry): Enough! The whole sky is crackling. I can feel it in
my wings!

*Moltres appears in a burst of heat.*

**Moltres** (angry): And my flames have not burned steadily for days.

*The three birds glare at one another.*

**{buddy}** (worried): You're all feeling the same thing. Whatever is
coming is affecting the whole world!

*Articuno studies the distant sky. For a moment, the anger gives way to
concern.*

**Articuno** (normal): Then prove it. Show us what you have seen.

### Scene: The falling star

`rescue-5-meteor` · cinematic · background sky · cast: `{player}`,
`{buddy}`, Absol (Absol), Rayquaza (Rayquaza)

*A burning point appears beyond the clouds. It grows brighter, larger,
and impossibly close.*

**{buddy}** (surprised): That's no ordinary star...

**Absol** (worried): The disasters. The tremors. The strange weather.
They were warnings.

*The sky tears with a streak of fire.*

**Rayquaza** (angry): Who summons me to the heavens?

**{buddy}** (determined): We need your help. A meteor is on its way!

**Rayquaza** (angry): I guard the skies. I know what approaches them.

**{buddy}** (worried): Then you know it will destroy everything!

*Rayquaza looks toward the approaching meteor.*

**Rayquaza** (normal): I will not let it pass.

### Scene: A path through the clouds

`rescue-5-ascent` · dungeon · dungeon sky-tower · cast: `{player}`,
`{buddy}`, Absol (Absol)

*The climb is exhausting. The air thins, the wind grows sharp, and the
world below becomes a patchwork of clouds and distant mountains.*

**{buddy}** (tired): I thought the sky would be... friendlier.

**Absol** (normal): It is not a place that was made for walking.

**{buddy}** (amused): You could have mentioned that before we started.

**Absol** (normal): You would have come anyway.

*{buddy} pauses, then laughs despite the exhaustion.*

**{buddy}** (happy): ...Yeah. Probably.

### Scene: Rayquaza's decision

`rescue-5-rayquaza` · cinematic · dungeon sky-tower · cast: `{player}`,
`{buddy}`, Rayquaza (Rayquaza)

*At the summit, Rayquaza coils above the clouds. The meteor burns across
the heavens.*

**Rayquaza** (normal): You have climbed far for a request that may cost
you everything.

**{buddy}** (determined): We didn't come this far to watch the world
end.

**Rayquaza** (normal): Brave words. But bravery does not make you
invulnerable.

*Rayquaza turns toward the meteor.*

**Rayquaza** (determined): Stand back.

*Green light gathers around Rayquaza. The sky shakes. A brilliant beam
strikes the meteor, splitting it into blazing fragments.*

*The largest pieces burn away high above the clouds. The shockwave rolls
across the world.*

**{buddy}** (surprised): You did it...

**Rayquaza** (normal): The danger has passed. For now.

*Rayquaza looks down at the two small Pokémon before it.*

**Rayquaza** (normal): You did not come seeking power. You came because
others needed help. Remember that when the world begins to praise you.

## Chapter 6 --- A Place to Return To

*Summary:* The team returns to Pokémon Square. The truth about the
disasters becomes known, and the Pokémon who drove them away must face
what they did.

### Scene: The square again

`rescue-6-return` · dialogue · background city · cast: `{player}`,
`{buddy}`, Alakazam (Alakazam), Charizard (Charizard), Tyranitar
(Tyranitar), Gengar (Gengar)

*The square falls quiet as the team approaches. Alakazam steps forward.*

**Alakazam** (sad): We owe you an apology.

**Charizard** (sad): We should have listened.

**Tyranitar** (normal): You saved us, even after we turned you away.

**{buddy}** (normal): We didn't do it so you'd owe us anything.

*Gengar edges away from the crowd.*

**{buddy}** (angry): And you. You started all of this.

**Gengar** (normal): I told a story. Pokémon believed what they wanted
to believe.

**Alakazam** (angry): You used their fear for your own amusement.

**Gengar** (normal): Hmph.

*Gengar slips away before anyone can stop him.*

**{buddy}** (sad): I don't think he'll ever apologize.

**Alakazam** (normal): Perhaps not. But the rest of us can do better.

### Scene: Back at the base

`rescue-6-home` · dialogue · background meadow · cast: `{player}`,
`{buddy}`, Absol (Absol), Caterpie (Caterpie)

*The base is still small. The roof still needs work. But someone has
left fresh berries by the entrance.*

**Caterpie** (joyous): You're back!

**{buddy}** (happy): We are.

**Caterpie** (happy): I told everyone you'd come back!

*Absol watches the two of you from the doorway.*

**Absol** (normal): You have a place here now.

**{buddy}** (happy): We do.

*{buddy} looks at you.*

**{buddy}** (normal): Funny. At first, I thought we were just helping
you figure out where you came from.

*They glance toward the request board.*

**{buddy}** (happy): I think we found something else along the way.

*Outside, another request arrives.*

*End campaign. Continue with repeatable rescue requests, optional
legendary encounters, and character episodes.*

### Optional post-story episodes --- Rescue Team

-   **Gengar's debt:** A follow-up encounter that gives Gengar a chance
    to show regret without forcing a sudden personality change. His
    redemption, if any, must be earned.
-   **The three birds:** Separate encounters with Articuno, Zapdos, and
    Moltres. Each should have a distinct temperament and reason for
    testing the team.
-   **The buried chamber:** Mew appears rarely in a hidden dungeon. The
    encounter is brief, strange, and never fully explained.
-   **The wish:** Jirachi appears in a special post-story expedition.
    The wish should be a character moment, not a generic reward screen.
-   **The old legend:** Optional conversations with Whiscash and
    Ninetales add context to the Gardevoir story.

------------------------------------------------------------------------

# Optional adventure arc --- Pokémon Mystery Dungeon: Explorers of Time, Darkness & Sky

**Setting:** Treasure Town, the nearby coast, and the Exploration
Guild.\
**Tone:** Cozy guild life, comedy, mystery, adventure, escalating
danger, and a strong emotional conclusion.\
**Central themes:** Friendship, courage, trust, sacrifice, hope, and the
value of a future that has not yet happened.\
**Main cast:** `{player}`, `{buddy}`, Wigglytuff, Chatot, Bidoof,
Sunflora, Loudred, Diglett, Dugtrio, Dusknoir, Grovyle, Celebi, Primal
Dialga, and the guild members.

This campaign is independent of Rescue Team. It must not use Pokémon
Square's rescue-team base, the fugitive plot, Gardevoir's legend, or the
meteor crisis as its central story.

**Witness mode:** Hoopa opens a real dimensional passage to the world of
Treasure Town. The player and buddy arrive as a visiting exploration
team and receive temporary guild affiliation. The original Explorers
protagonists are already part of Wigglytuff's Guild and remain the
central heroes of the Time Gear story. The Witness team joins selected
expeditions, helps with parallel guild work, and is present for
important events without taking over the original duo's defining
decisions.

## Chapter 1 --- The Beach

*Summary:* Hoopa's ring deposits the Frontier team on the beach near
Treasure Town. They are not lost or amnesiac; they have arrived in an
actual neighboring dimension and need to find a safe way to make contact
with the local guild.

### Scene: Washed ashore

`explorers-1-beach` · dialogue · background beach · cast: `{player}`,
`{buddy}`

*Waves roll over the shore. A ring of light closes above the water. The
team lands on the sand in a tangle of limbs and startled cries.*

**{buddy}** (surprised): Hoopa could have warned us about the landing!

*The ring shrinks to a glowing speck and disappears.*

**{buddy}** (worried): We are definitely not on the Frontier coast.

*The beach stretches toward a town. A pair of Koffing drift past,
snickering, with a small satchel between them.*

**{buddy}** (normal): At least the locals are familiar.

*The Koffing notice the team and hurry toward the rocks.*

**{buddy}** (angry): Hey! You two! Give that back!

*The Koffing flee with a small object. {buddy} starts after them, then
looks back at you.*

**{buddy}** (worried): Sorry. I can't just let them get away. Can you
stand?

*The two of you pursue the thieves.*

### Scene: The stolen treasure

`explorers-1-cliff` · dungeon · dungeon beach-cave · cast: `{player}`,
`{buddy}`, Koffing (Koffing), Zubat (Zubat)

*The path narrows into a rocky passage. The Koffing hover near the exit,
laughing.*

**Koffing** (amused): Hehehe! Look what we found!

**Zubat** (amused): Shiny! Shiny!

**{buddy}** (angry): That's not yours! Give it back!

**Koffing** (angry): Finders keepers!

*The Koffing attack. After the encounter, they retreat and drop the
stolen object.*

**{buddy}** (happy): Got it!

*{buddy} picks up the item, then looks at you.*

**{buddy}** (normal): You helped me back there. Thanks.

*A faint ring-shaped mark glimmers in the wet sand where the portal
closed.*

**{buddy}** (surprised): Hoopa's mark. So the passage brought us here
deliberately.

**{buddy}** (normal): We should find the town and ask for directions. If
the ring opens again, I want to know where it leads before we step
through.

### Scene: Treasure Town

`explorers-1-town` · stage · background city · cast: `{player}`,
`{buddy}`, Kecleon (Kecleon), Marill (Marill), Azurill (Azurill),
Duskull (Duskull)

*Treasure Town is lively. Shops line the square, and Pokémon come and go
carrying supplies.*

**Kecleon** (happy): Welcome! Welcome! Fresh stock today!

**{buddy}** (normal): We'll come back later, Kecleon.

**Kecleon** (happy): I'll be here! Unless I sell out!

*Marill and Azurill hurry past.*

**Marill** (worried): Excuse us! We have to find someone!

**Azurill** (worried): Hurry, Marill!

*They disappear around a corner.*

**{buddy}** (normal): Something's always happening here.

*{buddy} points toward a building on the hill.*

**{buddy}** (happy): That's the Exploration Guild. If anyone knows about
that fragment, it'll be someone there.

### Scene: The guild's entrance

`explorers-1-guild` · dialogue · background city · cast: `{player}`,
`{buddy}`, Chatot (Chatot), Wigglytuff (Wigglytuff)

*The guild's entrance is guarded by a Diglett lookout. The two of you
approach the grate.*

**Diglett** (normal): Halt! Who goes there?

**{buddy}** (surprised): It's me! I'm here to join the guild!

**Diglett** (normal): Name?

**{buddy}** (happy): I'm {buddy}. And this is---

*You introduce yourself.*

**Diglett** (normal): Hm. Two applicants. Wait here.

*Diglett disappears underground. A moment later, Chatot appears.*

**Chatot** (normal): So, you're the new applicants. Hm. You look...
enthusiastic.

**{buddy}** (happy): We are!

**Chatot** (normal): Enthusiasm is not a substitute for discipline. The
guild has rules, schedules, and responsibilities.

**{buddy}** (worried): Right. Of course.

*From somewhere inside the guild comes a cheerful voice.*

**Wigglytuff** (happy): Friends!

**Chatot** (surprised): Guildmaster! Please don't interrupt the entrance
examination!

**Wigglytuff** (happy): But they're here!

**Chatot** (angry): I can see that!

*Wigglytuff leans toward the two applicants.*

**Wigglytuff** (happy): Welcome! Do you like apples?

**{buddy}** (surprised): ...Apples?

**Wigglytuff** (happy): Hee-hee! Good answer!

*Chatot sighs.*

**Chatot** (normal): Please ignore that. Come inside. We'll begin your
registration.

## Chapter 2 --- Guild Life

*Summary:* The team joins the guild. Daily work introduces the guild's
residents, the expedition board, and the strange disappearances of
time.\*

### Scene: The guild's morning

`explorers-2-morning` · dialogue · background city · cast: `{player}`,
`{buddy}`, Chatot (Chatot), Wigglytuff (Wigglytuff), Bidoof (Bidoof),
Sunflora (Sunflora), Loudred (Loudred), Diglett (Diglett)

*The guild is already noisy. Loudred's voice shakes dust from the
rafters.*

**Loudred** (happy): GOOD MORNING!

**Chatot** (angry): Loudred! The morning briefing has not started!

**Loudred** (surprised): But it's morning!

**Sunflora** (happy): Oh! The new team is here! Good morning!

**Bidoof** (happy): Hey, hey! Welcome! I'm Bidoof. If you need help with
anything, just ask!

**{buddy}** (happy): Thanks! Everyone seems nice.

**Bidoof** (normal): Heheh. Most of the time.

**Chatot** (normal): This is an exploration guild, not a holiday camp.
You'll learn to read the boards, prepare supplies, and report your
findings.

**Wigglytuff** (happy): And eat plenty!

**Chatot** (angry): Guildmaster!

*Wigglytuff smiles. Chatot takes a long breath.*

**Chatot** (normal): Your first assignment is simple. Accompany Bidoof
on a short expedition.

**Bidoof** (surprised): Me? Oh! Well, sure! I'd be happy to!

### Scene: Bidoof's lesson

`explorers-2-bidoof` · dungeon · dungeon drenched-bluff · cast:
`{player}`, `{buddy}`, Bidoof (Bidoof)

*Bidoof walks ahead, stopping every few steps to point out something
interesting.*

**Bidoof** (happy): See that? A perfect spot for a treasure chest!

**{buddy}** (amused): That's a rock.

**Bidoof** (normal): Aye. But it could be hiding something.

*Bidoof nudges the rock. It does not move.*

**Bidoof** (surprised): Huh.

**{buddy}** (amused): Maybe the treasure is the rock.

**Bidoof** (happy): Heheh! Could be!

*Farther inside, the floor gives a faint pulse. The light seems to dim
for a moment.*

**Bidoof** (worried): Did you feel that?

**{buddy}** (normal): The ground moved.

**Bidoof** (worried): Not like an earthquake. More like... something
skipped.

*Bidoof looks around, suddenly serious.*

**Bidoof** (normal): Let's head back and tell Chatot.

## Chapter 3 --- The Famous Explorer

*Summary:* Dusknoir arrives in Treasure Town. His knowledge of the
future and his interest in the team's discoveries make him an admired
but unsettling figure.

### Scene: A celebrated arrival

`explorers-3-dusknoir` · dialogue · background city · cast: `{player}`,
`{buddy}`, Dusknoir (Dusknoir), Chatot (Chatot), Wigglytuff
(Wigglytuff), guild members

*The town square is crowded. Pokémon whisper excitedly as a
distinguished Dusknoir approaches.*

**Kecleon** (happy): A famous explorer! Here in Treasure Town!

**Sunflora** (happy): I've heard so much about him!

**Loudred** (happy): I BET HE'S REALLY STRONG!

**Chatot** (normal): Everyone, please. Let our guest speak.

**Dusknoir** (happy): Thank you. It is a pleasure to be here.

*Dusknoir's single eye moves across the crowd. It pauses briefly on the
player.*

**Dusknoir** (normal): And who might you be?

**{buddy}** (happy): We're a new team at the guild!

**Dusknoir** (happy): Ah. New explorers. The world always has room for
those willing to venture into the unknown.

*Dusknoir looks again at the Relic Fragment.*

**Dusknoir** (surprised): That fragment...

**{buddy}** (normal): You recognize it?

**Dusknoir** (normal): Perhaps. There are many old relics in this world.
I would be interested to hear where you found it.

**{buddy}** (happy): We found it near the beach.

**Dusknoir** (normal): I see.

*Dusknoir smiles, but the pause lasts a little too long.*

**Dusknoir** (happy): I hope we meet again.

### Scene: A private warning

`explorers-3-warning` · dialogue · background city · cast: `{player}`,
`{buddy}`, Dusknoir (Dusknoir)

*Later, Dusknoir finds the two of you outside the guild.*

**Dusknoir** (normal): May I ask you something?

**{buddy}** (normal): Sure.

**Dusknoir** (normal): Have you noticed anything unusual in the
dungeons? A strange light, perhaps. A place where the air feels...
incomplete?

**{buddy}** (worried): We felt something on our last expedition.

**Dusknoir** (normal): I thought as much.

*Dusknoir looks toward the horizon.*

**Dusknoir** (normal): If you encounter anything unusual, tell me. Time
is more fragile than most Pokémon realize.

*He turns to leave.*

**{buddy}** (worried): That's a strange thing to say.

**Dusknoir** (happy): Is it?

*He disappears into the crowd.*

## Chapter 4 --- The Thief of Time

*Summary:* Time begins to stop in isolated places. Grovyle is blamed for
stealing Time Gears, and the guild becomes involved in the search.

### Scene: The frozen pond

`explorers-4-frozen` · dungeon · dungeon crystal-cave · cast:
`{player}`, `{buddy}`, Bidoof (Bidoof)

*The water in a cavern pond is frozen in the shape of a splash. A drop
hangs in midair.*

**Bidoof** (surprised): That's... not right.

**{buddy}** (worried): The water stopped.

**Bidoof** (normal): Not froze. Stopped. Look at that drop!

*The drop remains suspended.*

**Bidoof** (worried): I've heard stories about Time Gears. They're
hidden in places where time flows strong.

**{buddy}** (normal): Time Gears?

**Bidoof** (normal): Ancient treasures. They're supposed to keep time
moving in the world. Nobody's meant to take them.

*Something moves at the far end of the cavern. A shadow slips between
the rocks.*

**{buddy}** (surprised): Hey! Who's there?

*The figure vanishes.*

### Scene: The accusation

`explorers-4-grovyle` · cinematic · dungeon forest · cast: `{player}`,
`{buddy}`, Grovyle (Grovyle)

*Deep in a forest, a Time Gear rests within an ancient stone structure.
A green Pokémon approaches silently.*

**Grovyle** (normal): One more.

*Grovyle takes the Time Gear. The surrounding light fades.*

**{buddy}** (angry): Stop!

*Grovyle turns. There is no surprise in his expression.*

**Grovyle** (normal): You're too late.

**{buddy}** (determined): Put it back!

**Grovyle** (normal): I can't.

*Grovyle leaps into the shadows and escapes.*

### Scene: The guild's investigation

`explorers-4-briefing` · dialogue · background city · cast: `{player}`,
`{buddy}`, Chatot (Chatot), Dusknoir (Dusknoir), guild members

**Chatot** (angry): A Time Gear has been stolen! This is a serious
matter!

**Loudred** (angry): THEN WE'LL CATCH HIM!

**Sunflora** (worried): But what happens to the forest without its Time
Gear?

**Dusknoir** (normal): Time will stop there. Eventually, the disturbance
may spread.

**Chatot** (normal): Dusknoir, you know more about this than we do. Can
you help us?

**Dusknoir** (normal): I will do what I can.

*Dusknoir looks toward the player.*

**Dusknoir** (normal): You have encountered the thief before. If you see
him again, do not approach alone.

**{buddy}** (determined): We're not going to let him take another one.

**Dusknoir** (normal): Courage is admirable. But it is not always
enough.

## Chapter 5 --- The Hidden Future

*Summary:* The pursuit leads to the truth about Grovyle and Dusknoir.
The player learns that the future is dying and that the Time Gears are
being gathered to prevent a greater catastrophe.

### Scene: The future's shadow

`explorers-5-reveal` · cinematic · background dark · cast: `{player}`,
`{buddy}`, Grovyle (Grovyle), Dusknoir (Dusknoir)

*The dungeon falls silent. Grovyle stands beside a damaged Time Gear
chamber.*

**{buddy}** (angry): Why are you doing this? Do you know what's
happening to these places?

**Grovyle** (normal): Better than you do.

**Dusknoir** (normal): Enough, Grovyle.

*Dusknoir emerges from the darkness. His voice is calm, almost gentle.*

**{buddy}** (surprised): Dusknoir? You followed us?

**Dusknoir** (normal): I have followed you for a very long time.

*Grovyle's expression hardens.*

**Grovyle** (angry): Don't listen to him.

**Dusknoir** (normal): I came from a future where time no longer flows.
A world of darkness. A world that cannot be allowed to continue.

**{buddy}** (worried): Then why are you helping him?

**Dusknoir** (normal): Because Grovyle intends to change the past. He
would undo the future from which I came.

**Grovyle** (angry): That future is already lost!

*Grovyle looks toward the player.*

**Grovyle** (normal): I am gathering the Time Gears to stop the collapse
of time. I know what happens if I fail.

**{buddy}** (worried): And Dusknoir wants to stop you?

**Dusknoir** (normal): I was sent to bring him back.

*Dusknoir's shadow stretches across the floor.*

**Dusknoir** (angry): And I will not fail.

### Scene: Celebi

`explorers-5-celebi` · dialogue · background forest · cast: `{player}`,
`{buddy}`, Grovyle (Grovyle), Celebi (Celebi)

*In a hidden grove, a small pink Pokémon waits beneath a tree. The air
around it shimmers.*

**Celebi** (worried): Grovyle...

**Grovyle** (normal): Celebi. We don't have much time.

**{buddy}** (surprised): You know each other?

**Celebi** (sad): We came from the same future.

*Celebi looks toward the darkened sky.*

**Celebi** (worried): There isn't much left there. The sun never rises.
The wind never changes. Even the flowers have stopped growing.

**Grovyle** (normal): We came here because this world still has a
future.

**{buddy}** (sad): You came all this way to save it?

**Grovyle** (normal): I came because someone had to.

*Celebi moves closer to Grovyle.*

**Celebi** (sad): And because he shouldn't have to do it alone.

## Chapter 6 --- The World's Edge

*Summary:* The team travels to the Hidden Land. The truth of the future
becomes clear, and the player must decide whether to trust Grovyle.

### Scene: The Hidden Land

`explorers-6-hidden-land` · dungeon · dungeon hidden-land · cast:
`{player}`, `{buddy}`, Grovyle (Grovyle), Celebi (Celebi)

*The Hidden Land is quiet. Ancient stone paths wind between cliffs. The
sea below is dark and still.*

**{buddy}** (worried): This place feels like it's been waiting for us.

**Grovyle** (normal): It has.

**{buddy}** (angry): You could have told us the truth earlier.

**Grovyle** (normal): Would you have believed me?

*{buddy} opens their mouth, then stops.*

**Celebi** (sad): He didn't know who he could trust.

**Grovyle** (normal): I still don't.

*Grovyle looks at the player.*

**Grovyle** (normal): But you came this far. That counts for something.

### Scene: The passage to the future

`explorers-6-future` · cinematic · background dark · cast: `{player}`,
`{buddy}`, Grovyle (Grovyle), Celebi (Celebi), Dusknoir (Dusknoir)

*The passage opens. A cold wind comes from the other side.*

**Dusknoir** (normal): You should not have come here.

**{buddy}** (angry): We're not leaving.

**Dusknoir** (normal): You don't understand what awaits you.

**Grovyle** (angry): We understand perfectly.

*Dusknoir's expression shifts. For the first time, he looks tired.*

**Dusknoir** (sad): I remember a world with no dawn. I remember the
silence. I remember what it cost to survive there.

**Celebi** (sad): And you think that makes it right to take this future
away from everyone?

**Dusknoir** (normal): I think I cannot bear to lose it twice.

*The passage shudders. The future waits beyond it.*

## Chapter 7 --- A Future Worth Fighting For

*Summary:* The team reaches the dark future. Dusknoir's allegiance and
the true cause of the collapse are revealed. The final journey to
Temporal Tower begins.

### Scene: The dark future

`explorers-7-dark-future` · cinematic · background dark · cast:
`{player}`, `{buddy}`, Grovyle (Grovyle), Celebi (Celebi), Dusknoir
(Dusknoir)

*The world is still. The sky has no sun. A tree stands without leaves,
its branches frozen in place.*

**{buddy}** (sad): So this is what happens...

**Grovyle** (normal): This is what we're trying to prevent.

**Dusknoir** (sad): I lived here. I knew every path, every shelter,
every place where a little warmth remained.

*Dusknoir looks toward the empty horizon.*

**Dusknoir** (sad): I told myself that if I could preserve this world,
it would mean something. That the suffering would have a purpose.

**Celebi** (worried): But it doesn't have to end this way.

**Dusknoir** (normal): No. It doesn't.

*Dusknoir turns toward the team.*

**Dusknoir** (determined): I was wrong.

*The ground trembles. A distant roar rolls across the darkness.*

**Grovyle** (worried): Dialga.

### Scene: Primal Dialga

`explorers-7-primal-dialga` · cinematic · dungeon temporal-tower · cast:
`{player}`, `{buddy}`, Grovyle (Grovyle), Celebi (Celebi), Dusknoir
(Dusknoir), Primal Dialga (Dialga)

*At the top of Temporal Tower, the structure groans. Gears turn without
rhythm. A vast shape looms beyond the broken arches.*

**Primal Dialga** (angry): GRAAAAAH!

**{buddy}** (worried): It can't hear us!

**Grovyle** (normal): It's lost to the distortion. There may be nothing
left of the guardian we hoped to find.

**Dusknoir** (determined): Then we must reach it.

*The battle begins. Time fractures around the tower.*

*At the turning point, the team creates an opening. Grovyle moves toward
the central mechanism.*

**Celebi** (worried): Grovyle! Be careful!

**Grovyle** (normal): I know.

*Grovyle looks back at the player and buddy.*

**Grovyle** (happy): You gave me a reason to believe this world could be
saved.

*The mechanism begins to turn. Light returns to the tower.*

**Primal Dialga** (surprised): ...Time...

*The red glow fades. Dialga lowers its head.*

**Dialga** (normal): The flow... has returned.

### Scene: The choice to return

`explorers-7-sacrifice` · dialogue · background dark · cast: `{player}`,
`{buddy}`, Grovyle (Grovyle), Celebi (Celebi), Dusknoir (Dusknoir)

*The passage home begins to close. Grovyle and Celebi stand on the far
side.*

**{buddy}** (worried): Come with us!

**Grovyle** (normal): I can't.

**{buddy}** (angry): Don't do this!

**Grovyle** (sad): This world has a future now. Someone has to make sure
it stays that way.

*Celebi takes Grovyle's hand.*

**Celebi** (sad): We knew this might happen.

*Grovyle looks at the player.*

**Grovyle** (happy): Thank you. Both of you.

*The light closes. The two disappear into the restored future.*

*{buddy} stands very still.*

**{buddy}** (sad): ...I wanted them to come home.

*There is no answer.*

## Chapter 8 --- The Morning After

*Summary:* The guild celebrates the restoration of time. The player and
buddy return to ordinary guild life, changed by what they have
experienced.

### Scene: A guild breakfast

`explorers-8-guild` · dialogue · background city · cast: `{player}`,
`{buddy}`, Chatot (Chatot), Wigglytuff (Wigglytuff), Bidoof (Bidoof),
Sunflora (Sunflora), Loudred (Loudred)

*The guild is noisy again. Loudred is arguing with the breakfast pot.
Sunflora is trying to calm him down.*

**Loudred** (angry): IT'S EMPTY!

**Sunflora** (angry): Because you ate all of it!

**Bidoof** (happy): Hey, hey! There's more in the kitchen!

**Chatot** (normal): Please, everyone. We have guests!

*Wigglytuff appears with a large apple.*

**Wigglytuff** (happy): Breakfast!

**Chatot** (angry): Guildmaster, that is your third breakfast!

**Wigglytuff** (happy): I know!

*The guild laughs. {buddy} watches the scene quietly.*

**{buddy}** (sad): It feels strange.

**{buddy}** (normal): Everything is back to normal. But they're not here
to see it.

*Chatot approaches.*

**Chatot** (normal): You two have done more for this guild than we could
have asked.

**Bidoof** (happy): And you're still part of the team! Don't go
forgetting that!

**Wigglytuff** (happy): Friends stay friends!

*{buddy} looks at you.*

**{buddy}** (happy): We still have a lot of exploring to do, don't we?

*Outside, the sun rises over Treasure Town.*

*End main campaign. Unlock post-story episodes and special expeditions.*

### Post-story episodes --- Explorers

-   **The Special Episode: Bidoof's Wish:** A self-contained guild story
    about Bidoof's early days, his admiration for Wigglytuff, and the
    expedition that shaped him.
-   **The Special Episode: Igglybuff the Prodigy:** Wigglytuff's past,
    told through Igglybuff's early adventures with Armaldo. Keep this
    separate from the main campaign's present-day cast.
-   **The Special Episode: Today's "Oh My Gosh":** Sunflora's
    investigation into a mystery in the guild. Let her enthusiasm and
    theatrical reactions drive the comedy.
-   **The Special Episode: In the Future of Darkness:** A short optional
    follow-up focused on Dusknoir, Grovyle, and Celebi after the
    restoration. Do not undo the main ending.
-   **Manaphy's return:** A post-story episode about caring for Manaphy
    and the meaning of family. This can be a warm, lower-stakes contrast
    to the main plot.
-   **The Marine Resort:** A relaxed post-game destination with optional
    treasure hunts and character conversations.

------------------------------------------------------------------------

# Optional dimensional and memory adventures --- Mainline regions

The existing Kanto, Johto, Hoenn, and Sinnoh campaigns remain
independent of the two PMD campaigns. Their central conflicts are
adapted to Pokémon societies. Human characters are removed, not renamed
or represented by human silhouettes.

Each region uses Pokémon as the recognizable faces of its organizations.
A Pokémon may hold a leadership, research, administrative, or strategic
role, but the writing must make that role plausible within a
Pokémon-only world.

The scripts below retain the existing regional premises while replacing
the generic exposition cast and giving each campaign a clearer identity.
They are not continuations of either PMD campaign.

------------------------------------------------------------------------

# Kanto --- The Genetic Crisis

**Premise:** Team Rocket is collecting rare Pokémon and conducting
experiments in abandoned facilities. Its leadership believes Pokémon can
be improved, controlled, and made stronger through artificial means. The
trail leads to Mewtwo.

**Witness mode:** Hoopa opens a physical passage from the Frontier to
Kanto. The player team arrives as an independent visiting exploration
team. A local Pokémon challenger is already following the region's
League route; that challenger remains the central protagonist of the
region's wider journey. The Witness team investigates Rocket's
operations and helps endangered Pokémon, sometimes crossing paths with
the challenger.

**Recurring cast:** Persian, Meowth, Koffing, Weezing, Hypno, Arbok,
Mew, Mewtwo, and a rotating group of wild Pokémon.

## Chapter 1 --- Strange Things in the Forest

### Scene: Viridian Forest

`kanto-1-forest` · dungeon · dungeon viridian-forest · cast: `{player}`,
`{buddy}`, Caterpie (Caterpie), Pikachu (Pikachu)

*The forest is unusually quiet. Caterpie and Weedle have abandoned their
usual feeding grounds. A Pikachu watches from a hollow tree.*

**Pikachu** (worried): Don't go any farther. Something's been
frightening everyone.

**{buddy}** (normal): What kind of something?

**Pikachu** (normal): Pokémon wearing red scarves. They come at night.
They take the strongest Pokémon they can find.

**{buddy}** (surprised): Take them where?

**Pikachu** (worried): Nobody knows. The ones who come back won't talk
about it.

*A rustle comes from the brush. Two Koffing drift into view.*

**Koffing** (angry): You shouldn't be asking questions.

**{buddy}** (determined): Then you shouldn't be sneaking around.

*The Koffing flee after the encounter, leaving behind a marked supply
crate.*

## Chapter 2 --- Rocket's Business

### Scene: The abandoned tunnel

`kanto-2-rocket` · dungeon · dungeon rock-tunnel · cast: `{player}`,
`{buddy}`, Meowth (Meowth), Koffing (Koffing), Persian (Persian)

*Lantern light flickers across a tunnel wall. Meowth is counting
supplies while Koffing guards a stack of crates.*

**Meowth** (normal): Careful with those! The boss wants every sample in
one piece.

**Koffing** (normal): What if one of them escapes?

**Meowth** (angry): Then you catch it! That's why you're here!

*The team steps into view.*

**{buddy}** (angry): What are you doing to those Pokémon?

**Meowth** (surprised): Oh, you've got to be kidding me.

*A heavy pawstep echoes. Persian emerges from the tunnel's shadow.*

**Persian** (normal): Leave them.

**Meowth** (worried): Boss's orders were---

**Persian** (angry): I said leave them.

*Persian studies the team.*

**Persian** (normal): You have wandered into business that does not
concern you. Go home.

**{buddy}** (determined): Not while those Pokémon are in cages.

**Persian** (normal): Then you have made your choice.

*Persian signals the grunts to attack, then withdraws before the fight
is over.*

## Chapter 3 --- The Pokémon Mansion

### Scene: The old laboratory

`kanto-3-mansion` · dungeon · dungeon pokemon-mansion · cast:
`{player}`, `{buddy}`, Hypno (Hypno), Ditto (Ditto)

*Dust covers the laboratory. Broken glass and faded notes lie across the
floor. A Ditto hides beneath a table.*

**Ditto** (worried): Dii... Ditto...

**{buddy}** (soft): It's all right. We're not with Rocket.

*Hypno appears at the end of the corridor.*

**Hypno** (normal): You should not be here.

**{buddy}** (angry): What happened in this place?

**Hypno** (sad): They wanted to create a Pokémon that could never be
defeated. They gathered samples, records, and fragments of an old
legend.

**{buddy}** (worried): Mew?

**Hypno** (normal): Yes. But what they made was not what they expected.

*Hypno looks toward a sealed door.*

**Hypno** (sad): They called it a success. The Pokémon behind that door
called it a prison.

## Chapter 4 --- The One They Made

### Scene: Cerulean Cave

`kanto-4-mewtwo` · cinematic · dungeon cerulean-cave · cast: `{player}`,
`{buddy}`, Mewtwo (Mewtwo)

*At the deepest chamber, a Pokémon stands alone. The air vibrates with
psychic power.*

**Mewtwo** (angry): Leave.

**{buddy}** (worried): We came to stop Team Rocket.

**Mewtwo** (angry): They made me. They named me. They decided what I was
for. Now they want to decide what I should do.

**{buddy}** (normal): We don't want to decide anything for you.

**Mewtwo** (surprised): Then why have you come?

**{buddy}** (sad): Because they're hurting other Pokémon. And because
you shouldn't have to face them alone.

*Mewtwo turns away.*

**Mewtwo** (normal): You speak as though freedom is something that can
be given.

*The cave trembles. Rocket's machines activate deeper underground.*

**Mewtwo** (angry): They have returned.

### Scene: A choice of its own

`kanto-4-escape` · cinematic · dungeon cerulean-cave · cast: `{player}`,
`{buddy}`, Mewtwo (Mewtwo), Persian (Persian)

*Persian enters with a small Rocket unit.*

**Persian** (normal): The experiment belongs to the organization.

**Mewtwo** (angry): I belong to no one.

*The battle begins. Mewtwo destroys the control apparatus, but does not
pursue the fleeing Rocket Pokémon.*

**{buddy}** (surprised): You're letting them go?

**Mewtwo** (normal): I will not become what they wanted me to be.

*Persian retreats. Mewtwo looks toward the cave entrance.*

**Mewtwo** (normal): Do not mistake this for friendship. I have simply
chosen my own path.

*End Kanto main story. Mew and Mewtwo remain available for rare optional
encounters.*

------------------------------------------------------------------------

# Johto --- The Burned Tower and the Sacred Beasts

**Premise:** The Burned Tower's history is resurfacing. The three
legendary beasts have begun moving across Johto, while the old bond
between the towers of sky and sea is weakening.

**Recurring cast:** Xatu, Slowking, Slowpoke, Unown, Suicune, Raikou,
Entei, Ho-Oh, Lugia.

## Chapter 1 --- The Tower That Remembers

### Scene: Sprout Tower

`johto-1-sprout` · dungeon · dungeon sprout-tower · cast: `{player}`,
`{buddy}`, Xatu (Xatu)

*The tower sways in the wind. Xatu stands motionless before an old
carving.*

**{buddy}** (normal): Are you watching the carving?

**Xatu** (normal): I am watching what it remembers.

**{buddy}** (surprised): Can stone remember?

**Xatu** (normal): Stone remembers longer than we do. Pokémon simply
forget.

*Xatu turns slightly.*

**Xatu** (worried): The old tower has begun to stir. Something that was
buried is moving again.

## Chapter 2 --- The Three Who Returned

### Scene: The Burned Tower

`johto-2-burned` · dungeon · dungeon burned-tower · cast: `{player}`,
`{buddy}`, Entei (Entei), Raikou (Raikou), Suicune (Suicune)

*Ash shifts beneath the team's feet. Three shapes move through the
smoke.*

**{buddy}** (surprised): The legendary beasts...

**Entei** (angry): Do not come closer.

**Raikou** (angry): The tower has not forgotten its fire.

**Suicune** (normal): Nor has the world.

**{buddy}** (worried): We aren't here to disturb you.

**Suicune** (normal): Then tell us why the water runs warm and the wind
carries ash.

*The three beasts separate, each following a different trail of strange
energy.*

## Chapter 3 --- The Search for Suicune

### Scene: The quiet lake

`johto-3-lake` · dialogue · background lake · cast: `{player}`,
`{buddy}`, Suicune (Suicune), Slowking (Slowking)

*Slowking studies ripples that move against the wind.*

**Slowking** (normal): The beasts are not angry with one another. They
are responding to different wounds in the land.

**{buddy}** (normal): Can they be brought together?

**Slowking** (normal): Perhaps. But not by chasing them.

*Suicune appears across the lake.*

**Suicune** (normal): The water remembers the tower before it burned.
Follow the path beneath the waves.

## Chapter 4 --- The Sea's Guardian

### Scene: Whirl Islands

`johto-4-lugia` · cinematic · dungeon whirl-islands · cast: `{player}`,
`{buddy}`, Lugia (Lugia)

*The sea grows still. A vast shape rises from the depths.*

**Lugia** (normal): You have come a long way from the burned tower.

**{buddy}** (worried): The sea is changing. The beasts say the land is
wounded.

**Lugia** (normal): The sea and sky do not command one another. They
answer one another.

**{buddy}** (normal): Then what happened?

**Lugia** (sad): A promise was broken when the tower fell. The world
continued, but the balance was never quite the same.

**Lugia** (normal): Go to the tower that still stands. The answer is
waiting above the clouds.

## Chapter 5 --- The Rainbow's Return

### Scene: Tin Tower

`johto-5-hooh` · cinematic · dungeon tin-tower · cast: `{player}`,
`{buddy}`, Ho-Oh (Ho-Oh), Suicune (Suicune)

*Bells ring in a wind that has not touched the tower for years. Ho-Oh
descends through a break in the clouds.*

**Ho-Oh** (normal): The old fire has spoken again.

**{buddy}** (normal): Lugia told us the towers once shared a promise.

**Ho-Oh** (sad): A promise cannot be restored by pretending nothing was
lost.

*Suicune steps forward.*

**Suicune** (normal): Then let the loss be remembered. And let the world
continue.

*Ho-Oh spreads its wings. The light passes over the tower and across
Johto.*

**Ho-Oh** (normal): The sky will answer the sea again. Not as it was. As
it can be.

*End Johto main story. Raikou, Entei, and Suicune remain available for
separate roaming encounters.*

------------------------------------------------------------------------

# Hoenn --- The Land, the Sea, and the Sky

**Premise:** Team Magma and Team Aqua are competing to reshape Hoenn.
Their leaders' ambitions awaken forces neither organization can control.

**Recurring cast:** Camerupt, Mightyena, Houndoom, Sharpedo, Carvanha,
Crawdaunt, Metagross, Milotic, Groudon, Kyogre, Rayquaza, and optional
Deoxys.

## Chapter 1 --- Weather Without Warning

### Scene: Petalburg Woods

`hoenn-1-woods` · dungeon · dungeon petalburg-woods · cast: `{player}`,
`{buddy}`, Wingull (Wingull), Castform (Castform)

*Rain falls beneath a cloudless sky. Castform hurries between the trees,
changing form with each strange gust.*

**Wingull** (worried): The tide came in before the storm. Then the storm
vanished. Then it came back!

**{buddy}** (surprised): Can the weather really change that quickly?

**Castform** (worried): It isn't changing naturally. Something is
pulling it around.

*A distant rumble rolls from the mountains.*

## Chapter 2 --- Red and Blue

### Scene: Mt. Chimney

`hoenn-2-magma` · dungeon · dungeon mt-chimney · cast: `{player}`,
`{buddy}`, Camerupt (Camerupt), Mightyena (Mightyena)

*Red-cloaked Pokémon work around a glowing device.*

**Mightyena** (angry): Keep moving! The boss wants the device ready
before sundown!

**{buddy}** (angry): What are you doing to the mountain?

**Camerupt** (normal): Giving the land room to grow.

**{buddy}** (surprised): By making the volcano erupt?

**Camerupt** (normal): The land has been shrinking beneath the sea for
ages. Someone must correct the balance.

### Scene: Shoal Cave

`hoenn-2-aqua` · dungeon · dungeon shoal-cave · cast: `{player}`,
`{buddy}`, Sharpedo (Sharpedo), Carvanha (Carvanha)

*Blue-cloaked Pokémon haul equipment through the flooded passage.*

**Carvanha** (angry): The sea is where life began! The land has taken
too much!

**Sharpedo** (normal): When the ocean rises, the world will remember its
place.

**{buddy}** (angry): You're going to drown Pokémon who live on land!

**Sharpedo** (normal): And Magma will leave the sea to dry. Neither side
has thought this through.

## Chapter 3 --- The Two Orbs

### Scene: The Seafloor Cavern

`hoenn-3-awakening` · cinematic · dungeon seafloor-cavern · cast:
`{player}`, `{buddy}`, Groudon (Groudon), Kyogre (Kyogre)

*The cavern splits with heat. Water rushes through the cracks. Two
enormous shapes awaken.*

**{buddy}** (worried): They're awake...

*Groudon roars. Kyogre answers from the rising water.*

**Groudon** (angry): The land will rise!

**Kyogre** (angry): The sea will spread!

*The two clash. The cavern begins to collapse.*

**{buddy}** (worried): We can't stop them here!

## Chapter 4 --- The Sky Pillar

### Scene: The meteor's echo

`hoenn-4-sky-pillar` · dungeon · dungeon sky-pillar · cast: `{player}`,
`{buddy}`, Rayquaza (Rayquaza)

*At the top of the pillar, a green shape coils around the ancient
stones.*

**Rayquaza** (angry): The land and sea have disturbed the sky once
again.

**{buddy}** (normal): We need you to stop them.

**Rayquaza** (normal): I have watched their quarrel for ages. They never
learn.

**{buddy}** (determined): Then help us make this time different.

*Rayquaza looks down at the distant storms.*

**Rayquaza** (normal): You ask much of the sky.

**{buddy}** (normal): We know.

*Rayquaza rises.*

**Rayquaza** (determined): Then hold on.

*Rayquaza descends between the two primal forces. Its presence stills
the storm.*

## Chapter 5 --- After the Storm

### Scene: A truce

`hoenn-5-truce` · dialogue · background coast · cast: `{player}`,
`{buddy}`, Camerupt (Camerupt), Sharpedo (Sharpedo), Rayquaza (Rayquaza)

*The two teams have withdrawn. The sea has returned to its shore, and
the mountain has cooled.*

**Camerupt** (sad): We wanted to make the world better.

**Sharpedo** (normal): We wanted to protect our home.

**{buddy}** (normal): You can care about the world and still hurt it.

*Neither responds immediately.*

**Rayquaza** (normal): Remember this quiet. It is not guaranteed.

*End Hoenn main story. Deoxys becomes an optional meteorite expedition.*

------------------------------------------------------------------------

# Sinnoh --- The Distortion of Creation

**Premise:** Team Galactic is attempting to reach the powers that govern
time and space. Its actions disturb the Lake Guardians and open a path
to the Distortion World.

**Witness mode:** A spatial disturbance draws the player team into
Sinnoh. This is a physical journey, not a memory. The team helps the
Lake Guardians and handles parallel objectives while Sinnoh's original
regional challenger remains responsible for the defining confrontation
with Team Galactic. The player may enter the Distortion World, but
Giratina's history and role must not be reduced to a convenient portal
guide.

**Recurring cast:** Purugly, Skuntank, Toxicroak, Golbat, Uxie, Mesprit,
Azelf, Dialga, Palkia, Giratina, and optional Arceus.

## Chapter 1 --- A World Out of Shape

### Scene: Eterna Forest

`sinnoh-1-forest` · dungeon · dungeon eterna-forest · cast: `{player}`,
`{buddy}`, Buneary (Buneary), Shinx (Shinx)

*The forest paths seem longer than they should. A tree appears twice,
though the team has not turned around.*

**Shinx** (worried): I ran this path yesterday. It was half this long.

**Buneary** (worried): And the pond moved. I know it did.

**{buddy}** (normal): Maybe the forest is changing.

**Shinx** (worried): Forests don't move.

*A metallic hum echoes from deeper inside.*

## Chapter 2 --- The New World Society

### Scene: Old Chateau

`sinnoh-2-galactic` · dungeon · dungeon old-chateau · cast: `{player}`,
`{buddy}`, Purugly (Purugly), Golbat (Golbat)

*Blue-clad Pokémon work around a machine built into the floor.*

**Golbat** (normal): The readings are stable.

**Purugly** (normal): Then the commander will be pleased.

**{buddy}** (angry): What is that machine doing?

**Purugly** (angry): Making a better world.

**{buddy}** (normal): Better for whom?

**Purugly** (normal): For those who understand what must be sacrificed.

*Purugly signals the others to attack.*

## Chapter 3 --- The Three Lakes

### Scene: Lake Valor

`sinnoh-3-lakes` · cinematic · background lake · cast: `{player}`,
`{buddy}`, Azelf (Azelf), Mesprit (Mesprit), Uxie (Uxie)

*The lake is unnaturally still. A blue light rises from the water.*

**Azelf** (angry): You should not be here.

**{buddy}** (worried): Team Galactic is trying to reach you.

**Uxie** (normal): They seek knowledge without understanding.

**Mesprit** (sad): They seek power without feeling.

**Azelf** (angry): And they will not stop.

*The three lights flare. The lake trembles.*

## Chapter 4 --- The Summit

### Scene: Mt. Coronet

`sinnoh-4-coronet` · dungeon · dungeon mt-coronet · cast: `{player}`,
`{buddy}`, Skuntank (Skuntank), Toxicroak (Toxicroak)

*The mountain's summit is filled with machinery. The sky bends around a
red chain.*

**Skuntank** (normal): The final preparations are complete.

**Toxicroak** (normal): Once the chain is active, nothing will stand in
our way.

**{buddy}** (angry): You're tearing the mountain apart!

**Skuntank** (normal): A small price for a new world.

*The machine activates. Space folds inward.*

## Chapter 5 --- The Distortion World

### Scene: The Spear Pillar

`sinnoh-5-spear-pillar` · cinematic · dungeon spear-pillar · cast:
`{player}`, `{buddy}`, Dialga (Dialga), Palkia (Palkia)

*Two immense presences appear within the fractured light. The red chain
strains around them.*

**{buddy}** (worried): They're trapped!

*Dialga roars. Palkia's cry tears through the air.*

**{buddy}** (determined): We have to break the chain!

*The team disrupts the machine. The chain shatters, but the mountain
splits open.*

### Scene: The other side

`sinnoh-5-giratina` · cinematic · dungeon distortion-world · cast:
`{player}`, `{buddy}`, Giratina (Giratina)

*Gravity has no meaning here. A river flows upward. Stones float like
leaves.*

**Giratina** (angry): Who enters my domain?

**{buddy}** (worried): We didn't mean to come here.

**Giratina** (normal): Few do.

*Giratina circles the team.*

**Giratina** (normal): The world above has disturbed the boundary again.
You will help mend it.

**{buddy}** (determined): If that's what it takes to get home.

**Giratina** (normal): Then follow. Do not mistake this for an
invitation to wander.

*End Sinnoh main story. Giratina remains available for optional
Distortion World expeditions; Arceus is reserved for a separate,
exceptionally rare post-story encounter.*

------------------------------------------------------------------------

# Optional special episodes --- Strange Places and Strange Days

There is no required Convergence storyline. Use memory echoes, dreams,
portals, and dimensional travel as standalone adventure premises
whenever a Mythical Pokémon's personality makes them fun or meaningful.

Examples: - Hoopa opens a ring to a place it insists is nearby, but the
landscape is completely unfamiliar. - Celebi asks the team to
investigate a repeating moment in a small forest. - Jirachi's dream
creates a temporary dungeon built from a Pokémon's wish. - Darkrai
invites the team into a nightmare to help resolve a fear. - Mew imitates
a local Pokémon and accidentally starts a chain of misunderstandings.

Each episode should work on its own. It may introduce a new dungeon,
character, region, or gameplay mechanic, but it does not need to reveal
a larger secret. The Mythical Pokémon can recur because they enjoy the
team's company, not because they are following a master plan.

# Continuity and implementation notes

## Narrative priority

1.  **Mythical introduction and tutorial:** the first playable
    experience. Mew's opening adventure teaches the basic World loop and
    ends with Mew joining the party.
2.  **Ongoing exploration:** the main activity. Jobs, dungeons, party
    management, rewards, rank progression, Pokémon discovery, and team
    building must remain useful after the tutorial.
3.  **Individual Mythical adventures:** short optional episodes. Each
    introduces a Mythical Pokémon's personality and a gameplay mechanic;
    successful completion can lead to that Pokémon joining the party.
4.  **PMD and regional stories:** optional adventure arcs. They are not
    the central premise and must not gate ordinary exploration.
5.  **Cross-episode mysteries:** entirely optional. Do not create a
    mandatory cosmic plot or universal villain.

## Campaign availability

The player's home setting is the Frontier. The opening tutorial takes
place there and establishes the ongoing exploration loop.

PMD Rescue Team, PMD Explorers, and mainline regional adaptations are
optional adventures. They may be accessed through a Mythical Pokémon, a
dungeon discovery, a request, or a special location. They must not be
mandatory prerequisites for one another.

Do not assume the player has completed any optional arc when writing
another. Do not require every optional arc to unlock the ordinary World
systems.

## Scene ID migration

The previous generated script used `home-*` IDs for a six-chapter home
story that incorrectly centered on a temporal mystery. The new opening
uses `intro-mew-*`.

-   New tutorial scenes use `intro-mew-*`.
-   Hoopa's follow-up uses `intro-hoopa-*` if split into scene records.
-   Victini's follow-up uses `intro-victini-*`.
-   Celebi's follow-up uses `intro-celebi-*`.
-   Jirachi's follow-up uses `intro-jirachi-*`.
-   Marshadow's follow-up uses `intro-marshadow-*`.
-   Shaymin's follow-up uses `intro-shaymin-*`.
-   Darkrai's follow-up uses `intro-darkrai-*`.
-   Existing PMD and regional arc IDs may retain their current prefixes.

Check saved chapter and scene completion state before migration. Do not
silently reuse old `home-*` IDs for the new tutorial. If the old home
campaign was marked complete, migrate only state that semantically
corresponds to the new tutorial; do not duplicate rewards or Mythical
recruits.

## Player and buddy

-   `{player}` and `{buddy}` are always Pokémon.
-   Neither has a human origin, human memories, or a human
    transformation backstory.
-   They are already a pair, or become a team in the first few scenes.
-   The buddy should have opinions, humor, fears, and reactions. Do not
    reduce them to a line-delivery device.
-   The player's chosen Pokémon remains the player character. Avoid
    forcing long spoken lines or a fixed personality onto `{player}`.

## Mythical recruitment

-   Mythical Pokémon are not ordinary dungeon recruits. Each has a
    bespoke introduction and a character-specific reason to join.
-   A Mythical Pokémon's adventure should test something relevant to its
    personality: curiosity, teamwork, cleverness, compassion,
    adaptability, or trust.
-   "Satisfied" must be shown through the Mythical Pokémon's behavior
    and dialogue. Do not implement it as an unexplained hidden affection
    score unless the game already has such a system.
-   Recruitment should be explicit and immediately reflected in
    party/collection management.
-   Joining does not mean the Mythical Pokémon becomes obedient or loses
    its personality. It remains a companion with its own quirks.
-   Do not require the player to recruit every Mythical Pokémon to
    progress in ordinary exploration.

## Existing Pokémon stories

-   Keep PMD Rescue Team and Explorers as separate optional arcs.
-   Preserve their recognizable plots and original protagonists'
    defining accomplishments.
-   The player team may observe, accompany, or complete parallel
    objectives.
-   Mainline human roles must be adapted into believable Pokémon-led
    institutions and relationships. No humans or human-run
    organizations.
-   Do not claim that every Pokémon game shares one exact timeline. Use
    a portal, dream, memory, or separate continuity only when the
    adaptation needs it.
-   Legendary and Mythical Pokémon retain their established
    personalities and agency.

## Gameplay integration

-   The World module remains an exploration game with teams, dungeons,
    missions, rewards, and Exploration Ranks.
-   The tutorial should demonstrate existing systems, not create
    duplicate versions of them.
-   Existing rank, job, expedition, Pokémon recruitment, reward, and
    party systems remain authoritative.
-   Story completion must not grant duplicate expedition rewards when a
    scene is replayed.
-   Mythical introductions can unlock a special companion, an optional
    adventure, or a new utility, but must not replace ordinary rank
    progression.
-   Keep tutorial pacing short. Once the player understands the basic
    loop and has received the first Mythical recruit, hand control to
    the normal World experience.

## Scope of this rewrite

This revision changes the narrative priority and opening premise. The
Mythical Pokémon's trickster adventures are now the main story framework
and the World tutorial. The Frontier is a functional home setting rather
than the beginning of a large central saga. PMD and mainline adaptations
are optional side adventures.

The supplied script's existing PMD Rescue Team, Explorers, Kanto, Johto,
Hoenn, and Sinnoh material is retained as optional story content, with
its Pokémon-only and Witness-mode framing. This document does not yet
provide full scripts for Unova, Kalos, Alola, Galar, Paldea, Pokémon
Ranger, Colosseum/XD, Legends, or other spin-offs. Add those only when
they offer worthwhile optional adventures.
