# World story and scene engine

Status: first slice built (0.118.0). The owner's specification (the "Story & Scene Engine Implementation Specification", 2 November 2026) is the plan; this file says what exists, how to author a scene, and what is still to do.

## What exists

- **Model** (`world-core`, package `app.pokeplan.world.story`): `StoryEpisode` (a chapter's scenes), `StoryScene` (`SceneType` DIALOGUE, STAGE, DUNGEON or CINEMATIC; a background or, for a dungeon walk, the `dungeonId` whose own tiles it is drawn on; a cast; an event script), `StoryCharacter` and a typed `SceneEvent` set: `Show`, `Hide`, `Express`, `Face`, `Move` (a path of grid cells), `Say` (a line, or narration when the speaker is null), `Wait`, `Fade`, `Background`, `Item`, `TakeItem`, `Emote`, `Flag`, `End`.
- **Runtime** (`SceneState`): what each event leaves behind (who is where, what lies on the floor, the line on screen). `SceneState.finalOf(scene)` applies every event at once, which is what Skip lands on, so a skipped scene is never left half done. `StoryScene.problems()` lists mistakes in a script (a speaker not in the cast, someone used before being shown, a line over 160 characters, no End); the tests run it over every authored scene.
- **Player** (`composeApp`, `screens/StoryPlayer.kt`): a dialogue view (the place with the speakers standing in it as sprites, and the speaker's small framed Mystery Dungeon portrait at its side, over the scene, above the dark dialogue box with the speaker's name in yellow), and a stage or dungeon view (ten by six cells, sprites drawn at their cells and walking with their own walk animations, items on the floor, an exclamation mark over a head). Tap to advance (a tap while text is typing completes it), Skip scene, a close button, a typewriter that Reduce motion turns off. A scene watched to its end is recorded in `WorldData.scenesDone`, a finished episode in `episodesDone` (a record for the archive; replaying grants nothing).
- **The companion** is always the player's buddy (`BUDDY` in a script; whoever is chosen, portrait and sprite from their own form). Text may use `{buddy}` and `{player}`. A character with no portrait for an expression shows its normal face.
- **Where it plays:** a chapter with an authored episode (`StoryCatalog.episodeFor(layer, chapter)`) plays it when the chapter opens and from the Story archive's play button; every other chapter plays its title card as before.
- **Authored so far:** Mystery Dungeon, chapter 1, "A New Explorer": a letter at dawn (dialogue with Pelly the Pelipper, the Guild's postmaster), the square (a stage), Into Tiny Woods (a scripted dungeon walk with a Caterpie and an apple on the floor) and back at the Guild. The Guild and the Pelipper are our own: the setting is Pokémon Mystery Dungeon in spirit, not a copy of its story.

## How to write a chapter

Add a function to a region's story object (see `MysteryDungeonStory`) that returns a `StoryEpisode`, and list it in `StoryCatalog`. A line is `Say("pelly", "Special delivery!", HAPPY)`; narration is `Say(null, "...")`. A walk is `Move(BUDDY, listOf(3.6f to 3.4f))` (cells across and down; a dungeon floor row is about y = 2.6 to 3.4). `StoryEngineTest` fails if a script is unsound.

## Art and the specification

The specification asks for original pixel art and warns against ripped game assets. The project's own rule (the owner's, in CLAUDE.md and docs/assets.md) is the opposite: real game assets only, the PMD SpriteCollab portraits and sprites first, never hand-drawn or generated art. The engine follows the project's rule: portraits, walking sprites, scene backgrounds and dungeon tiles are the existing SpriteCollab and game assets, credited as they are today. Nothing in the engine depends on that choice; a different asset source would only change the resolvers.

## Built since the first slice (0.119.0)

- **Cinematic scenes** (`SceneType.CINEMATIC`): black bars, characters whose id starts with `big_` drawn large, `Shake`, `Flash`, `Letterbox`; Reduce motion skips the motion.
- **Sound:** `Cry(who)` plays a Pokémon's real cry and `Sound(name)` any game effect from the app's list. There is no music yet: the project has no music assets.
- **Choices and flags:** `Choice(prompt, options)` (two or three answers that set a flag; skipped, the first is taken) and `Branch(flag, events)`. Flags are kept in `WorldData.storyFlags` and carry into later scenes; they are for callbacks only and never gate a reward or an unlock.
- **Resume:** `WorldData.storyResume` holds the next scene's index per episode; a watched episode replays from its start.
- **People you have met** in the Story archive, from the casts of the scenes watched (`StoryCatalog.peopleMet`).
- **Every chapter written** (first pass): `MysteryDungeonStory` and `MysteryDungeonChapters`, `KantoChapters`, `JohtoChapters`, `HoennChapters`, `SinnohChapters`, built with the writing aid in `ScriptDsl.kt` (`dialogue`, `stage`, `dungeon`, `cinematic` blocks of `say`, `walk`, `shake`, `choice`...). A test checks that every chapter of every layer has an episode with the chapter's number and title, and that every script is sound.

## Not built yet

- Music and ambience (no music assets in the project); a particle-effects set beyond shake and flash.
- A Tiled map pipeline and a story asset manifest: scenes use the existing dungeon tile sheets and scene images (the dungeon tiles for the non-home layers are the generic sheet).
- A scene preview tool beyond the Story archive and the screenshot test; a character index with lore text.
- The rewrite of the first-pass writing, and the new regions' chapters (Unova to Paldea, Hisui): scripts only, no engine work.
