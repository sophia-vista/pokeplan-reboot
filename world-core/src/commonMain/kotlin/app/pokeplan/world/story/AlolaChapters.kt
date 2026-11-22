package app.pokeplan.world.story

import app.pokeplan.world.WorldLayer
import app.pokeplan.world.story.Expression.*

internal object AlolaChapters {
    private val buddy = buddyCharacter()
    private val roo = npc("roo", "Roo", 722)
    private val cutiefly = npc("cutiefly", "Cutiefly", 742)
    private val zubat = npc("zubat", "Zubat", 41)
    private val salandit = npc("salandit", "Salandit", 757)
    private val solgaleo = npc("big_solgaleo", "Solgaleo", 791)
    private val lunala = npc("big_lunala", "Lunala", 792)
    private val necrozma = npc("big_necrozma", "Necrozma", 800)

    fun all(): List<StoryEpisode> = listOf(c1(), c2(), c3(), c4(), c5())

    private fun c1() = StoryEpisode(WorldLayer.ALOLA, 1, "Iki Town", listOf(
        dialogue("alola-1-iki", "Iki Town", "beach", listOf(buddy, roo)) {
            narr("Drums echo across Iki Town, and the sea shines behind the village.")
            say("roo", "I am Roo! I will go with you on the island challenge, all four islands.", HAPPY)
            say("roo", "The first trials are Verdant Cavern and Brooklet Hill. Easy, mostly.", NORMAL)
            say(BUDDY, "Mostly?", SURPRISED)
        },
        dungeon("alola-1-cavern", "Verdant Cavern", "verdant-cavern", listOf(buddy, roo)) {
            enter(BUDDY, 3.5f, 2.8f)
            enter("roo", 2.0f, 3.2f)
            narr("Verdant Cavern is dim and damp, and the walls are scratched by someone in a hurry.")
            say("roo", "Those scratches are fresh. Who is in a hurry in a cave like this?", WORRIED)
            emote(BUDDY, "?")
            say(BUDDY, "Boot prints, and a cloth with a skull on it.", SURPRISED)
        },
    ))

    private fun c2() = StoryEpisode(WorldLayer.ALOLA, 2, "The Lush Jungle", listOf(
        dungeon("alola-2-jungle", "Lush Jungle", "lush-jungle", listOf(buddy, roo, cutiefly)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("roo", 1.8f, 3.2f)
            narr("In the Lush Jungle, flowers open and close in time with a strange hum.")
            show("cutiefly", 9.0f, 2.8f, Facing.LEFT)
            say("cutiefly", "Bzzz! I am a Cutiefly. I follow the hum. It tastes like a very sweet sound.", HAPPY)
            say("roo", "It comes from the sky, from a place we cannot see.", NORMAL)
            say(BUDDY, "A sound from above the clouds.", SURPRISED)
            hide("cutiefly")
        },
        dialogue("alola-2-story", "The island guardians", "meadow", listOf(buddy, roo)) {
            say("roo", "Each island has a guardian who watches over its trials.", NORMAL)
            say(BUDDY, "And the guardians are the four who gave Alola its trials?", SURPRISED)
            say("roo", "Yes. But the sun and the moon above them have been silent for a long time.", WORRIED)
        },
    ))

    private fun c3() = StoryEpisode(WorldLayer.ALOLA, 3, "Team Skull", listOf(
        dungeon("alola-3-volcano", "Wela Volcano Park", "wela-volcano-park", listOf(buddy, roo, zubat)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("roo", 1.8f, 3.2f)
            narr("Wela Volcano Park smokes in the heat, and a hooded grunt blasts music across the crater.")
            show("zubat", 10.0f, 2.5f, Facing.LEFT)
            walk("zubat", 7.5f to 2.5f)
            say("zubat", "Yo, yo, yo! Team Skull owns this crater! Move along!", ANGRY)
            say("roo", "You do not own anything here. It belongs to the island.", WORRIED)
            say("zubat", "Whatever. The boss said no one comes through.", NORMAL)
            exit("zubat", 2.5f)
        },
        dungeon("alola-3-canyon", "Vast Poni Canyon", "vast-poni-canyon", listOf(buddy, roo, salandit)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("roo", 1.8f, 3.2f)
            narr("Vast Poni Canyon is huge and wide, and a boss waits at the bend of the road.")
            show("salandit", 9.5f, 2.6f, Facing.LEFT)
            say("salandit", "The boss is not here for you. He is here for what is hidden above the canyon.", NORMAL)
            say(BUDDY, "Then we will go there first.", DETERMINED)
            hide("salandit")
        },
    ))

    private fun c4() = StoryEpisode(WorldLayer.ALOLA, 4, "The Mountain of the Sun", listOf(
        dungeon("alola-4-climb", "Up Mount Lanakila", "mount-lanakila", listOf(buddy, roo)) {
            enter(BUDDY, 3.5f, 2.8f)
            enter("roo", 2.0f, 3.2f)
            narr("The snow on Mount Lanakila squeaks underfoot. The summit hides in a golden haze.")
            say("roo", "My feathers are stiff. But the light up there is warm, like a sunrise that never ends.", SURPRISED)
            say(BUDDY, "The sun is waiting at the top. We are close.", DETERMINED)
            shake(500)
        },
        cinematic("alola-4-solgaleo", "The beast of the sun", "mountain", listOf(buddy, solgaleo), dungeonId = "mount-lanakila") {
            show(BUDDY, 2.0f, 4.8f)
            narr("At the summit, the air shines like gold. A shape of light, white as a lion's mane, steps out of the haze.")
            show("big_solgaleo", 7.0f, 4.8f, Facing.LEFT); flash(); shake(700); cry("big_solgaleo"); sound("STORY_FIRE")
            say("big_solgaleo", "I carry the sun across the sky. Why do you climb so high?", NORMAL)
            say(BUDDY, "To ask you for help against Team Skull and what they are hiding.", DETERMINED)
            say("big_solgaleo", "Then go to the floating paradise, small ones, and bring my sister the moon.", NORMAL)
        },
    ))

    private fun c5() = StoryEpisode(WorldLayer.ALOLA, 5, "The Aether Paradise", listOf(
        cinematic("alola-5-paradise", "The Aether Paradise", "city", listOf(buddy, lunala), dungeonId = "aether-paradise") {
            show(BUDDY, 5.0f, 5.2f)
            narr("In the Aether Paradise, a tall white garden floats above the sea, and a sleeping shape hums in its heart.")
            show("big_lunala", 7.8f, 4.0f, Facing.LEFT); shake(600); cry("big_lunala"); flash()
            say(BUDDY, "The hum. It was her all along!", SURPRISED)
            say("big_lunala", "I sing to the moon. They kept me asleep so the sky would go dark.", SAD)
            flash(); shake(700)
            narr("With a wide, cool song the moon wakes, and a rift of light opens in the sky.")
        },
        cinematic("alola-5-necrozma", "The torn light", "thunderplains", listOf(buddy, necrozma), dungeonId = "ultra-megalopolis") {
            show(BUDDY, 2.0f, 4.8f)
            narr("Through the rift stands a city of ultra light, and in its middle a dark, torn shape that has lost its own glow.")
            show("big_necrozma", 7.0f, 4.6f, Facing.LEFT); flash(); shake(700); cry("big_necrozma")
            say("big_necrozma", "Light. I need light. Where did my light go?", SAD)
            say(BUDDY, "We cannot give it back, but we can share ours.", DETERMINED)
            say("big_necrozma", "Then shine with me, and I will not take more than you give.", HAPPY)
            flash()
            narr("Necrozma's blessing stays with your team: Alola's expeditions find treasure a little more often.")
        },
    ))
}
