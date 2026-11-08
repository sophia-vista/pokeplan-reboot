package app.pokeplan.world.story

import app.pokeplan.world.WorldLayer
import app.pokeplan.world.story.Expression.*

/** Sinnoh, "The Distortion of Creation": Pip the Piplup guides the buddy; Team Galactic is Stunky and Skuntank; Dialga, Palkia and Giratina are the three creators. */
internal object SinnohChapters {
    private val buddy = buddyCharacter()
    private val pip = npc("pip", "Pip", 393)
    private val stunky = npc("stunky", "Stunky", 434)
    private val skuntank = npc("skuntank", "Skuntank", 435)
    private val gible = npc("gible", "Gible", 443)
    private val dialga = npc("big_dialga", "Dialga", 483)
    private val palkia = npc("big_palkia", "Palkia", 484)
    private val giratina = npc("big_giratina", "Giratina", 487)

    fun all(): List<StoryEpisode> = listOf(c1(), c2(), c3(), c4(), c5())

    private fun c1() = StoryEpisode(WorldLayer.SINNOH, 1, "Twinleaf Town", listOf(
        dialogue("sinnoh-1-twinleaf", "Twinleaf Town", "meadow", listOf(buddy, pip)) {
            narr("Snow melts on the roofs of Twinleaf Town. The forests and mines are quiet, but the sky feels strange.")
            say("pip", "I am Pip! I know the way to Eterna Forest and the Oreburgh Mine.", HAPPY)
            say("pip", "Have you noticed the clouds? They hang still, like paint.", NORMAL)
            say(BUDDY, "Then we will find out why.", DETERMINED)
        },
        dungeon("sinnoh-1-forest", "Eterna Forest", "eterna-forest", listOf(buddy, pip)) {
            enter(BUDDY, 3.5f, 2.8f)
            enter("pip", 2.0f, 3.2f)
            narr("Eterna Forest is quiet. Too quiet. Even the leaves hesitate to fall.")
            say("pip", "I have never heard it so silent.", WORRIED)
            emote(BUDDY, "?")
            say(BUDDY, "Tracks. Heavy boots, and not from any Pokémon.", SURPRISED)
        },
    ))

    private fun c2() = StoryEpisode(WorldLayer.SINNOH, 2, "Whispers of Creation", listOf(
        dungeon("sinnoh-2-cave", "Wayward Cave", "wayward-cave", listOf(buddy, pip, gible)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("pip", 1.8f, 3.2f)
            narr("In Wayward Cave, old carvings of three great figures cover the walls.")
            show("gible", 9.0f, 2.8f, Facing.LEFT)
            say("gible", "Rawr! Oh, hello. Sorry. I am only a little Gible.", HAPPY)
            say("pip", "These carvings show three creators: time, space and a shadow behind them.", NORMAL)
            say(BUDDY, "Three creators who made the world.", SURPRISED)
            say("gible", "Grandma says the third one hides where nobody can see.", NORMAL)
            hide("gible")
        },
        dialogue("sinnoh-2-story", "The old tale", "dampcave", listOf(buddy, pip)) {
            say("pip", "Time made the days, space made the places, and the third kept everything in balance.", NORMAL)
            say(BUDDY, "And if one of them is disturbed?", WORRIED)
            say("pip", "Then the world frays at its edges.", WORRIED)
        },
    ))

    private fun c3() = StoryEpisode(WorldLayer.SINNOH, 3, "Team Galactic", listOf(
        dungeon("sinnoh-3-chateau", "Old Chateau", "old-chateau", listOf(buddy, pip, stunky)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("pip", 1.8f, 3.2f)
            narr("In the Old Chateau, a figure in bright blue peers into a glowing machine.")
            show("stunky", 10.0f, 2.5f, Facing.LEFT)
            walk("stunky", 7.5f to 2.5f)
            say("stunky", "Team Galactic is making a new world, one without spirit or feeling!", ANGRY)
            say("pip", "Without feeling? That sounds terrible.", WORRIED)
            say("stunky", "The old creators will be our tools. Step aside.", NORMAL)
            exit("stunky", 2.5f)
        },
        dungeon("sinnoh-3-island", "Iron Island", "iron-island", listOf(buddy, pip, skuntank)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("pip", 1.8f, 3.2f)
            narr("Iron Island rings with the sound of machines, and a commander waits on its landing.")
            show("skuntank", 9.5f, 2.6f, Facing.LEFT)
            say("skuntank", "The machines are ready. Even three creators will not stop us.", NORMAL)
            say(BUDDY, "We will, Commander.", DETERMINED)
            hide("skuntank")
        },
    ))

    private fun c4() = StoryEpisode(WorldLayer.SINNOH, 4, "The Peak of Mt. Coronet", listOf(
        dungeon("sinnoh-4-climb", "Up Mt. Coronet", "mt-coronet", listOf(buddy, pip)) {
            enter(BUDDY, 3.5f, 2.8f)
            enter("pip", 2.0f, 3.2f)
            narr("The slopes of Mt. Coronet are steeper than anything so far. Clouds crowd the peak.")
            say("pip", "My ears pop. And everything looks bent, like through water.", SURPRISED)
            say(BUDDY, "Time and space tremble up there. We are close.", DETERMINED)
            shake(500)
        },
        cinematic("sinnoh-4-dialga", "Time itself", "mountain", listOf(buddy, dialga), dungeonId = "mt-coronet") {
            show(BUDDY, 2.0f, 4.8f)
            narr("At the summit, the air shimmers. Clocks tick backwards.")
            show("big_dialga", 7.0f, 4.8f, Facing.LEFT); flash(); shake(700); cry("big_dialga"); sound("STORY_TEMPORAL")
            say("big_dialga", "Those who bend time will find it bends back.", NORMAL)
            say(BUDDY, "We want to stop them, not to command you.", DETERMINED)
            say("big_dialga", "Then go to the Spear Pillar, small ones, and do not be late.", NORMAL)
        },
    ))

    private fun c5() = StoryEpisode(WorldLayer.SINNOH, 5, "The Distortion World", listOf(
        cinematic("sinnoh-5-pillar", "The Spear Pillar", "mountain", listOf(buddy, dialga, palkia), dungeonId = "spear-pillar") {
            show(BUDDY, 5.0f, 5.2f)
            narr("At the Spear Pillar, time and space are bound and caught in Team Galactic's machine.")
            show("big_dialga", 2.2f, 4.0f, Facing.RIGHT); shake(600); cry("big_dialga")
            show("big_palkia", 7.8f, 4.0f, Facing.LEFT); flash(); cry("big_palkia")
            say(BUDDY, "Free them! The machine is tearing them apart!", SURPRISED)
            flash(); shake(800)
            narr("With a roar, both creators break free and the sky splits open.")
        },
        cinematic("sinnoh-5-giratina", "The other side", "volcanocave", listOf(buddy, giratina), dungeonId = "distortion-world") {
            show(BUDDY, 2.0f, 4.8f)
            narr("Beyond the break lies a world turned inside out, where floors hang in the air and rivers run upward.")
            show("big_giratina", 7.0f, 4.6f, Facing.LEFT); flash(); shake(700); cry("big_giratina")
            say("big_giratina", "Who steps into the world behind the world?", NORMAL)
            say(BUDDY, "Someone who wants to put it back.", DETERMINED)
            say("big_giratina", "Then walk through it with me, and I will send you home.", HAPPY)
            flash()
            narr("Giratina's blessing stays with your team: Sinnoh's expeditions find treasure a little more often.")
        },
    ))
}
