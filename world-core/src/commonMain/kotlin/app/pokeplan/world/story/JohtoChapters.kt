package app.pokeplan.world.story

import app.pokeplan.world.WorldLayer
import app.pokeplan.world.story.Expression.*

internal object JohtoChapters {
    private val buddy = buddyCharacter()
    private val marin = npc("marin", "Marin", 183)
    private val amp = npc("amp", "Elder Amp", 181)
    private val slowpoke = npc("slowpoke", "Slowpoke", 79)
    private val unown = npc("unown", "Unown", 201)
    private val houndoom = npc("houndoom", "Houndoom", 229)
    private val lugia = npc("big_lugia", "Lugia", 249)
    private val hooh = npc("big_hooh", "Ho-Oh", 250)

    fun all(): List<StoryEpisode> = listOf(c1(), c2(), c3(), c4(), c5())

    private fun c1() = StoryEpisode(WorldLayer.JOHTO, 1, "New Bark Town", listOf(
        dialogue("johto-1-bark", "New Bark Town", "meadow", listOf(buddy, marin)) {
            narr("The wind from New Bark Town carries the smell of the sea.")
            say("marin", "Hello! I am Marin. The old tales say the sky and the sea keep each other in balance.", HAPPY)
            say("marin", "People forget those tales. But the towers remember.", NORMAL)
            say(BUDDY, "Then let us visit the towers.", DETERMINED)
        },
        dungeon("johto-1-sprout", "Sprout Tower", "sprout-tower", listOf(buddy, marin)) {
            enter(BUDDY, 3.5f, 2.8f)
            enter("marin", 2.0f, 3.2f)
            narr("Sprout Tower sways gently on its great pillar. The sages here pray for balance.")
            say("marin", "Listen. The tower hums in time with the wind.", HAPPY)
            say(BUDDY, "It feels alive.", SURPRISED)
            say("marin", "Come. Ilex Forest is next, and then Violet City's elder.", NORMAL)
        },
    ))

    private fun c2() = StoryEpisode(WorldLayer.JOHTO, 2, "Old Stories", listOf(
        dialogue("johto-2-elder", "The elder's tale", "city", listOf(buddy, amp)) {
            narr("In Violet City, the elder keeps a lamp lit through the night.")
            say("amp", "Long ago two towers stood: one for the sea, one for the sky. One of them burned.", NORMAL)
            say("amp", "A fire swept through and the sky tower fell. Three beasts rose from the ashes.", SURPRISED)
            say(BUDDY, "And the balance was lost?", WORRIED)
            say("amp", "Lost, but not broken. Someone must find it again.", HAPPY)
        },
        dungeon("johto-2-union", "Union Cave", "union-cave", listOf(buddy, marin)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("marin", 1.8f, 3.2f)
            narr("In Union Cave, drips echo like footsteps.")
            say("marin", "The elder says the old paths pass through here.", NORMAL)
            emote(BUDDY, "?")
            say(BUDDY, "Carvings on the wall. Wings and waves.", SURPRISED)
        },
    ))

    private fun c3() = StoryEpisode(WorldLayer.JOHTO, 3, "Echoes of Alph", listOf(
        dungeon("johto-3-alph", "The Ruins of Alph", "ruins-of-alph", listOf(buddy, marin, unown)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("marin", 1.8f, 3.2f)
            narr("Carved stone fills the Ruins of Alph. Strange marks float in the air.")
            show("unown", 8.0f, 2.0f, Facing.LEFT)
            say("unown", "...!... ...?...", SURPRISED)
            say("marin", "The Unown. They have a message but I cannot read it.", WORRIED)
            say(BUDDY, "They are pointing down. Toward the Slowpoke Well.", DETERMINED)
        },
        dialogue("johto-3-well", "The Slowpoke Well", "dampcave", listOf(buddy, slowpoke)) {
            say("slowpoke", "...Oh... hello... it is raining... no... it is not...", NORMAL)
            say(BUDDY, "Are you all right?", WORRIED)
            say("slowpoke", "...Something is off... the water is moving... the wrong way...", SURPRISED)
            narr("Even the slowest of Pokémon can feel it: the balance is slipping.")
        },
    ))

    private fun c4() = StoryEpisode(WorldLayer.JOHTO, 4, "The Burned Tower", listOf(
        dungeon("johto-4-burned", "The Burned Tower", "burned-tower", listOf(buddy, marin, houndoom)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("marin", 1.8f, 3.2f)
            narr("Charred beams, dark ash, and an uneasy heat.")
            show("houndoom", 9.0f, 2.5f, Facing.LEFT)
            say("houndoom", "This place is mine. Turn back, small ones.", ANGRY)
            say(BUDDY, "We are only listening. The fire remembers something.", DETERMINED)
            say("houndoom", "...Then listen. Under the floor lies the old well of the beasts.", NORMAL)
            hide("houndoom")
        },
        cinematic("johto-4-lugia", "The guardian of the sea", "beach", listOf(buddy, lugia), dungeonId = "whirl-islands") {
            show(BUDDY, 2.0f, 4.6f)
            narr("In the deepest cave of the Whirl Islands, the water parts.")
            show("big_lugia", 7.0f, 5.2f, Facing.LEFT); flash(); shake(600); cry("big_lugia"); sound("STORY_WIND_HIGH")
            say("big_lugia", "You have walked through fire and ash. The sea knows your name.", NORMAL)
            say(BUDDY, "We came to restore the balance.", DETERMINED)
            say("big_lugia", "Then go to the Tin Tower. My sister of the sky waits there.", NORMAL)
        },
    ))

    private fun c5() = StoryEpisode(WorldLayer.JOHTO, 5, "The Rainbow Wings", listOf(
        dungeon("johto-5-ascent", "The Tin Tower", "tin-tower", listOf(buddy, marin)) {
            enter(BUDDY, 3.5f, 2.8f)
            enter("marin", 2.0f, 3.2f)
            narr("The Tin Tower rises in tier after tier of bells.")
            say("marin", "The elder said nobody has reached the top in a hundred years.", WORRIED)
            say(BUDDY, "Then let us be the first.", DETERMINED)
            shake(400)
        },
        cinematic("johto-5-hooh", "Rainbow Wings", "mountain", listOf(buddy, hooh), dungeonId = "tin-tower") {
            show(BUDDY, 2.0f, 4.6f)
            narr("At the top of the tower, the clouds part.")
            show("big_hooh", 7.0f, 4.0f, Facing.LEFT); flash(); shake(600); cry("big_hooh"); sound("STORY_HUMMING")
            say("big_hooh", "Sea and sky, fire and ash, they have been apart too long.", NORMAL)
            say(BUDDY, "Lugia sent us. We bring what was lost.", DETERMINED)
            say("big_hooh", "Then I will fly with you. The balance is whole once more.", HAPPY)
            flash()
        },
    ))
}
