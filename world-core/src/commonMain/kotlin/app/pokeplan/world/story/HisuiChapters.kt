package app.pokeplan.world.story

import app.pokeplan.world.WorldLayer
import app.pokeplan.world.story.Expression.*

/** Hisui, "The Ancient Survey": Row the Rowlet guides the buddy; the frenzied noble is Kleavor; Dialga, Palkia and Arceus are the powers of the rift. */
internal object HisuiChapters {
    private val buddy = buddyCharacter()
    private val row = npc("row", "Row", 722)
    private val bidoof = npc("bidoof", "Bidoof", 399)
    private val goomy = npc("goomy", "Goomy", 704)
    private val kleavor = npc("kleavor", "Kleavor", 900)
    private val wyrdeer = npc("wyrdeer", "Wyrdeer", 899)
    private val dialga = npc("big_dialga", "Dialga", 483)
    private val palkia = npc("big_palkia", "Palkia", 484)
    private val arceus = npc("big_arceus", "Arceus", 493)

    fun all(): List<StoryEpisode> = listOf(c1(), c2(), c3(), c4(), c5())

    private fun c1() = StoryEpisode(WorldLayer.HISUI, 1, "The Fieldlands", listOf(
        dialogue("hisui-1-fields", "Jubilife Village", "meadow", listOf(buddy, row)) {
            narr("Hisui is a wild land, long before Sinnoh had its name. Villagers watch the fields with care.")
            say("row", "I am Row! I know the Obsidian Fieldlands and the Crimson Mirelands.", HAPPY)
            say("row", "People say a crack in the sky brought you here. I believe it.", NORMAL)
            say(BUDDY, "Then let us see the land first.", DETERMINED)
        },
        dungeon("hisui-1-mire", "Crimson Mirelands", "crimson-mirelands", listOf(buddy, row, goomy)) {
            enter(BUDDY, 3.5f, 2.8f)
            enter("row", 2.0f, 3.2f)
            narr("In the Crimson Mirelands the rain never stops, and the mud sings.")
            show("goomy", 9.0f, 2.8f, Facing.LEFT)
            say("goomy", "Mmmoo! Hello. Do not step in the deep mud, please.", HAPPY)
            say("row", "It likes a visitor. That is rare in Hisui.", NORMAL)
            hide("goomy")
        },
    ))

    private fun c2() = StoryEpisode(WorldLayer.HISUI, 2, "The Survey Corps", listOf(
        dungeon("hisui-2-coast", "Cobalt Coastlands", "cobalt-coastlands", listOf(buddy, row, bidoof)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("row", 1.8f, 3.2f)
            narr("On the Cobalt Coastlands, the Survey Corps sets up tents beside the surf.")
            show("bidoof", 10.0f, 2.8f, Facing.LEFT)
            say("bidoof", "Bwah! Everyone wants to catch everyone here. I am keeping my head down.", WORRIED)
            say(BUDDY, "We want to study, not to catch.", DETERMINED)
            hide("bidoof")
        },
        dialogue("hisui-2-camp", "The camp", "beach", listOf(buddy, row)) {
            say("row", "The Corps writes everything down. That is how a land learns its own names.", NORMAL)
            say(BUDDY, "And the sky? Does it write that down too?", WORRIED)
            say("row", "Look up. It cracks a little more each night.", WORRIED)
        },
    ))

    private fun c3() = StoryEpisode(WorldLayer.HISUI, 3, "The Frenzied Lords", listOf(
        dungeon("hisui-3-ruins", "Celestica Ruins", "celestica-ruins", listOf(buddy, row, wyrdeer)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("row", 1.8f, 3.2f)
            narr("In the Celestica Ruins, old stones keep the memory of a noble Pokémon that has gone wild.")
            show("wyrdeer", 9.5f, 2.6f, Facing.LEFT)
            say("wyrdeer", "The lords are frenzied. Seek the plates, quiet them.", NORMAL)
            say("row", "We need to calm them, not fight them.", WORRIED)
            hide("wyrdeer")
        },
        dungeon("hisui-3-highlands", "Coronet Highlands", "coronet-highlands", listOf(buddy, row, kleavor)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("row", 1.8f, 3.2f)
            narr("High in the Coronet Highlands, a lord of the mountain stands in the wind with burning eyes.")
            show("kleavor", 9.5f, 2.6f, Facing.LEFT)
            say("kleavor", "Graaah!", ANGRY)
            say(BUDDY, "It is in pain. Hold steady.", DETERMINED)
            hide("kleavor")
        },
    ))

    private fun c4() = StoryEpisode(WorldLayer.HISUI, 4, "Time and Space", listOf(
        dungeon("hisui-4-ice", "Alabaster Icelands", "alabaster-icelands", listOf(buddy, row)) {
            enter(BUDDY, 3.5f, 2.8f)
            enter("row", 2.0f, 3.2f)
            narr("The Alabaster Icelands are white and still. The air bends above the snow.")
            say("row", "Look: the sky has a seam. And something golden stirs inside it.", SURPRISED)
            say(BUDDY, "Time and space are both in pain.", WORRIED)
            shake(500)
        },
        cinematic("hisui-4-rift", "Time and space", "mountain", listOf(buddy, dialga, palkia), dungeonId = "turnback-cave") {
            show(BUDDY, 5.0f, 5.2f)
            narr("At the mouth of Turnback Cave, two giants are bound in a rage that does not belong to them.")
            show("big_dialga", 2.2f, 4.0f, Facing.RIGHT); shake(600); cry("big_dialga")
            show("big_palkia", 7.8f, 4.0f, Facing.LEFT); flash(); cry("big_palkia")
            say(BUDDY, "Easy now. We are here to help you.", DETERMINED)
            flash(); shake(800)
            narr("Slowly, both calm, and the rift in the sky stops growing.")
        },
    ))

    private fun c5() = StoryEpisode(WorldLayer.HISUI, 5, "The Original One", listOf(
        dungeon("hisui-5-tower", "Hallowed Tower", "hallowed-tower", listOf(buddy, row)) {
            enter(BUDDY, 3.5f, 2.8f)
            enter("row", 2.0f, 3.2f)
            narr("The Hallowed Tower rises to the torn sky. Beyond the seam, a pale shrine floats in nothing.")
            say("row", "That is the Temple of Sinnoh. Whoever made this land waits there.", SURPRISED)
            say(BUDDY, "Then we finish the survey.", DETERMINED)
        },
        cinematic("hisui-5-arceus", "The Original One", "volcanocave", listOf(buddy, arceus), dungeonId = "temple-of-sinnoh") {
            show(BUDDY, 2.0f, 4.8f)
            narr("In the Temple of Sinnoh, a white shape unfolds from the light, with a golden wheel around its body.")
            show("big_arceus", 7.0f, 4.4f, Facing.LEFT); flash(); shake(700); cry("big_arceus")
            say("big_arceus", "Little one, you have walked all my land. Why?", NORMAL)
            say(BUDDY, "To know it, and to keep it safe.", DETERMINED)
            say("big_arceus", "Then I will send you home, and my land will remember.", HAPPY)
            flash()
            narr("Arceus's blessing stays with your team: Hisui's expeditions find treasure a little more often.")
        },
    ))
}
