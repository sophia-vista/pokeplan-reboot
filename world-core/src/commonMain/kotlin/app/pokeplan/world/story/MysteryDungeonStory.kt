package app.pokeplan.world.story

import app.pokeplan.world.WorldLayer
import app.pokeplan.world.story.Expression.*
import app.pokeplan.world.story.SceneEvent.*

/**
 * The Mystery Dungeon world's episodes. The player's buddy is the companion (never a fixed partner); Pelly the Pelipper is the Guild's
 * postmaster, who gives the team its first push. Grid: ten cells across, six down; a floor row in a dungeon is about y = 2.6.
 */
internal object MysteryDungeonStory {
    private val buddy = StoryCharacter(BUDDY, "{buddy}")
    private val pelly = StoryCharacter("pelly", "Pelly", speciesId = 279)
    private val caterpie = StoryCharacter("caterpie", "Caterpie", speciesId = 10)

    fun chapter1() = StoryEpisode(
        WorldLayer.HOME, 1, "A New Explorer",
        listOf(
            StoryScene(
                "home-1-letter", "A letter at dawn", SceneType.DIALOGUE, "city", listOf(buddy, pelly),
                listOf(
                    Show(BUDDY, 0f, 0f, Facing.RIGHT), Show("pelly", 0f, 0f, Facing.LEFT),
                    Say(null, "Dawn over the Explorers' Guild. The first mail of the day arrives."),
                    Say("pelly", "Special delivery! A letter for {buddy} and {player}'s team!", HAPPY),
                    Say(BUDDY, "A letter for us?", SURPRISED),
                    Say("pelly", "The Guild is calling for new explorers. Strange things have been stirring in the dungeons.", NORMAL),
                    Say(BUDDY, "Then let's find out what!", DETERMINED),
                    Say("pelly", "That is the spirit! Meet me in the square and I will show you where to start.", HAPPY),
                    End,
                ),
            ),
            StoryScene(
                "home-1-square", "The square", SceneType.STAGE, "meadow", listOf(buddy, pelly),
                listOf(
                    Show(BUDDY, -1f, 3.4f, Facing.RIGHT), Show("pelly", 7.6f, 2.6f, Facing.LEFT),
                    Move(BUDDY, listOf(3.6f to 3.4f)),
                    Say("pelly", "There you are. Every team starts with a first dungeon.", NORMAL),
                    Say("pelly", "Tiny Woods is quiet, and the Pokémon there are friendly. Start there.", HAPPY),
                    Emote(BUDDY, "!"),
                    Say(BUDDY, "Tiny Woods! We are ready!", JOYOUS),
                    Move("pelly", listOf(10.8f to 2.6f)), Hide("pelly"),
                    Move(BUDDY, listOf(10.8f to 3.4f)), Hide(BUDDY),
                    End,
                ),
            ),
            StoryScene(
                "home-1-woods", "Into Tiny Woods", SceneType.DUNGEON, "tiny-woods", listOf(buddy, caterpie),
                listOf(
                    Show(BUDDY, -1f, 2.8f, Facing.RIGHT),
                    Move(BUDDY, listOf(2.5f to 2.8f, 4.2f to 2.8f)),
                    Say(null, "Tiny Woods. Sunlight comes through the leaves, and something rustles ahead."),
                    Emote(BUDDY, "!"),
                    Show("caterpie", 10.6f, 2.2f, Facing.LEFT),
                    Move("caterpie", listOf(7.4f to 2.2f, 6.6f to 2.9f)),
                    Say(BUDDY, "A Pokémon! Hello?", SURPRISED),
                    Say("caterpie", "Oh! I thought you were a big bird. Please don't be one!", SURPRISED),
                    Say(BUDDY, "We are an exploration team. We are not here to scare anyone.", HAPPY),
                    Say("caterpie", "Then this is for you. I found it near the roots.", HAPPY),
                    Item("pmd2-021", 6.0f, 3.5f),
                    Move(BUDDY, listOf(5.4f to 3.2f)),
                    TakeItem("pmd2-021"),
                    Say(BUDDY, "An apple! Thank you!", JOYOUS),
                    Move("caterpie", listOf(10.8f to 2.4f)), Hide("caterpie"),
                    Say(null, "The little Pokémon say the deeper floors are changing."),
                    End,
                ),
                dungeonId = "tiny-woods",
            ),
            StoryScene(
                "home-1-board", "Back at the Guild", SceneType.DIALOGUE, "city", listOf(buddy, pelly),
                listOf(
                    Show(BUDDY, 0f, 0f, Facing.RIGHT), Show("pelly", 0f, 0f, Facing.LEFT),
                    Say(null, "Back at the Guild at the end of the day."),
                    Say("pelly", "Word travels fast. Your team's first exploration is already on the board.", HAPPY),
                    Say(BUDDY, "Is it always like this?", SURPRISED),
                    Say("pelly", "No. Something is stirring below, and explorers like you will have to find out what.", NORMAL),
                    Say(BUDDY, "Then we will keep exploring.", DETERMINED),
                    Say(null, "Send your team into a dungeon. The deeper they go, the closer you get to the truth."),
                    End,
                ),
            ),
        ),
    )
}
