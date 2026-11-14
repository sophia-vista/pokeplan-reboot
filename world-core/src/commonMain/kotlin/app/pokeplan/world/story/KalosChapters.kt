package app.pokeplan.world.story

import app.pokeplan.world.WorldLayer
import app.pokeplan.world.story.Expression.*

/** Kalos, "The Beauty and the Weapon": Fenn the Fennekin guides the buddy; Team Flare is Houndour and Houndoom; Xerneas, Yveltal and Zygarde are the legends of life, death and order. */
internal object KalosChapters {
    private val buddy = buddyCharacter()
    private val fenn = npc("fenn", "Fenn", 653)
    private val pancham = npc("pancham", "Pancham", 674)
    private val goomy = npc("goomy", "Goomy", 704)
    private val houndour = npc("houndour", "Houndour", 228)
    private val houndoom = npc("houndoom", "Houndoom", 229)
    private val xerneas = npc("big_xerneas", "Xerneas", 716)
    private val yveltal = npc("big_yveltal", "Yveltal", 717)
    private val zygarde = npc("big_zygarde", "Zygarde", 718)

    fun all(): List<StoryEpisode> = listOf(c1(), c2(), c3(), c4(), c5())

    private fun c1() = StoryEpisode(WorldLayer.KALOS, 1, "Vaniville Town", listOf(
        dialogue("kalos-1-vaniville", "Vaniville Town", "meadow", listOf(buddy, fenn)) {
            narr("Petals drift over Vaniville Town. In the far north, a tall tower on the horizon glows red.")
            say("fenn", "I am Fenn! I know the way to Santalune Forest and Connecting Cave.", HAPPY)
            say("fenn", "Have you seen the tower in the north? It was never red before.", NORMAL)
            say(BUDDY, "Then we will find out why.", DETERMINED)
        },
        dungeon("kalos-1-forest", "Santalune Forest", "santalune-forest", listOf(buddy, fenn)) {
            enter(BUDDY, 3.5f, 2.8f)
            enter("fenn", 2.0f, 3.2f)
            narr("Santalune Forest is bright, and every bug in it has flown away at once.")
            say("fenn", "Something frightened them. I smell smoke, but not from a campfire.", WORRIED)
            emote(BUDDY, "?")
            say(BUDDY, "Scorch marks on the trunks. Someone was testing a machine.", SURPRISED)
        },
    ))

    private fun c2() = StoryEpisode(WorldLayer.KALOS, 2, "The Mega Evolution", listOf(
        dungeon("kalos-2-cave", "Glittering Cave", "glittering-cave", listOf(buddy, fenn, pancham)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("fenn", 1.8f, 3.2f)
            narr("In Glittering Cave, crystals reflect a strange light that seems to come from inside the stones.")
            show("pancham", 9.0f, 2.8f, Facing.LEFT)
            say("pancham", "Hmph. I am a tough Pancham. Who is coming into my cave?", ANGRY)
            say("fenn", "We just want to look at the crystals.", NORMAL)
            say("pancham", "They sing to stones like the one on your bracelet. Very strange.", HAPPY)
            hide("pancham")
        },
        dialogue("kalos-2-story", "The old stones", "dampcave", listOf(buddy, fenn)) {
            say("fenn", "A bond between a trainer and a Pokémon can shine so brightly that the Pokémon changes.", NORMAL)
            say(BUDDY, "Only for a moment?", SURPRISED)
            say("fenn", "Yes. But somebody wants to make it last forever.", WORRIED)
        },
    ))

    private fun c3() = StoryEpisode(WorldLayer.KALOS, 3, "Team Flare", listOf(
        dungeon("kalos-3-way", "Grande Vallée Way", "grande-vallee-way", listOf(buddy, fenn, goomy, houndour)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("fenn", 1.8f, 3.2f)
            narr("Rain falls steadily on Grande Vallée Way. A small, slimy Pokémon peers out of the reeds.")
            show("goomy", 8.5f, 3.0f, Facing.LEFT)
            say("goomy", "Hello, wet friends. The men in red suits scared my pond.", SAD)
            show("houndour", 11.0f, 2.5f, Facing.LEFT)
            walk("houndour", 7.0f to 2.5f)
            say("houndour", "Team Flare needs a quiet marsh to hide a machine. Step aside, little one.", ANGRY)
            say(BUDDY, "You scared a pond for that?", DETERMINED)
            exit("houndour", 2.5f)
            hide("goomy")
        },
        dungeon("kalos-3-village", "Pokémon Village", "pokemon-village", listOf(buddy, fenn, houndoom)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("fenn", 1.8f, 3.2f)
            narr("The Pokémon Village is hidden behind a veil of mist, and a commander of Team Flare waits at its gate.")
            show("houndoom", 9.5f, 2.6f, Facing.LEFT)
            say("houndoom", "A world made for the beautiful is a world that will not end. Even a little beauty is worth saving.", NORMAL)
            say(BUDDY, "Not if everyone else is thrown away.", DETERMINED)
            hide("houndoom")
        },
    ))

    private fun c4() = StoryEpisode(WorldLayer.KALOS, 4, "The Frost Cavern", listOf(
        dungeon("kalos-4-climb", "Through the Frost Cavern", "frost-cavern", listOf(buddy, fenn)) {
            enter(BUDDY, 3.5f, 2.8f)
            enter("fenn", 2.0f, 3.2f)
            narr("The Frost Cavern is bluer than the sea. Every breath hangs in the air like a small cloud.")
            say("fenn", "I cannot feel my paws. But something up ahead feels warm, like a heartbeat.", SURPRISED)
            say(BUDDY, "The tree of life. We are close.", DETERMINED)
            shake(500)
        },
        cinematic("kalos-4-xerneas", "The life tree", "meadow", listOf(buddy, xerneas), dungeonId = "victory-road-kalos") {
            show(BUDDY, 2.0f, 4.8f)
            narr("Beyond Victory Road a tree of crystal stands in the rain, and its roots shine like a fountain.")
            show("big_xerneas", 7.0f, 4.8f, Facing.LEFT); flash(); shake(700); cry("big_xerneas")
            say("big_xerneas", "I give life without asking. Why does someone try to take it back?", SAD)
            say(BUDDY, "We want to stop him, not to take anything from you.", DETERMINED)
            say("big_xerneas", "Then walk down, small ones, and do not be afraid of the dark.", NORMAL)
        },
    ))

    private fun c5() = StoryEpisode(WorldLayer.KALOS, 5, "The Ultimate Weapon", listOf(
        cinematic("kalos-5-labs", "Lysandre Labs", "volcanocave", listOf(buddy, yveltal), dungeonId = "lysandre-labs") {
            show(BUDDY, 5.0f, 5.2f)
            narr("Deep under Lumiose City, the lab hums. A machine drains the life from the land to wake the weapon.")
            show("big_yveltal", 7.8f, 4.0f, Facing.LEFT); shake(800); cry("big_yveltal"); flash()
            say(BUDDY, "Stop it! The machine is draining everything!", SURPRISED)
            say("big_yveltal", "Life ends, and I take it. That is all I was made to do.", NORMAL)
            flash(); shake(800)
            narr("With a roar the wings fold, and the lab goes dark.")
        },
        cinematic("kalos-5-zygarde", "The order of the land", "dampcave", listOf(buddy, zygarde), dungeonId = "terminus-cave") {
            show(BUDDY, 2.0f, 4.8f)
            narr("In Terminus Cave, far below the roots of the land, countless small lights gather into one.")
            show("big_zygarde", 7.0f, 4.6f, Facing.LEFT); flash(); shake(700); cry("big_zygarde")
            say("big_zygarde", "I watch the balance between life and death. Who disturbs the order?", NORMAL)
            say(BUDDY, "We came to help put it back.", DETERMINED)
            say("big_zygarde", "Then walk it with me, and I will keep this land in your care.", HAPPY)
            flash()
            narr("Zygarde's blessing stays with your team: Kalos's expeditions find treasure a little more often.")
        },
    ))
}
