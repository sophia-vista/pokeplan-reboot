package app.pokeplan.world.story

import app.pokeplan.world.WorldLayer
import app.pokeplan.world.story.Expression.*

/** The Mystery Dungeon world, chapters 2 to 6 (chapter 1 is [IntroMewChapter]). Pelly the Pelipper is the Guild's postmaster; the buddy is the companion. */
internal object MysteryDungeonChapters {
    private val buddy = buddyCharacter()
    private val pelly = npc("pelly", "Pelly", 279)
    private val vulpix = npc("vulpix", "Vulpix", 37)
    private val hoot = npc("hoot", "Professor Hoot", 164)
    private val rival = npc("rival", "Glim", 362)
    private val big = npc("big_dialga", "Primal Dialga", 483)
    private val voice = npc("voice", "A voice", 201)

    fun all(): List<StoryEpisode> = listOf(chapter2(), chapter3(), chapter4(), chapter5(), chapter6())

    private fun chapter2() = StoryEpisode(WorldLayer.HOME, 2, "Requests and Rescues", listOf(
        dialogue("home-2-board", "The request board", "city", listOf(buddy, vulpix)) {
            narr("A crowd gathers at the Guild's request board. Someone is waiting for the team.")
            say("vulpix", "Please, are you the new exploration team? I need help.", HAPPY)
            say("vulpix", "My friend wandered into the woods and has not come back.", NORMAL)
            say(BUDDY, "Do not worry. We will bring them home.", DETERMINED)
            choice("How do you answer?", "We will go right now." to "rush", "Tell us where to look." to "plan")
            branch("rush") { say("vulpix", "Thank you! You are so quick!", JOYOUS) }
            branch("plan") { say("vulpix", "Thank you! The trail starts at the old oak.", HAPPY) }
        },
        dungeon("home-2-rescue", "A lost friend", "tiny-woods", listOf(buddy, npc("lost", "Lost Pokémon", 37))) {
            enter(BUDDY, 3.0f, 2.8f)
            narr("Deeper into the woods, the paths twist and the light fades.")
            show("lost", 8.0f, 2.4f, Facing.LEFT)
            emote(BUDDY)
            say("lost", "Over here! My paw is stuck in the roots.", SURPRISED)
            walk(BUDDY, 6.8f to 2.6f)
            say(BUDDY, "Hold still. There. You are free.", HAPPY)
            say("lost", "I thought no one would find me. Thank you!", JOYOUS)
            narr("Every request the Guild takes in is a small promise. Keep them, and the board will keep growing.")
        },
    ))

    private fun chapter3() = StoryEpisode(WorldLayer.HOME, 3, "Into the Unknown", listOf(
        dialogue("home-3-rival", "A rival team", "city", listOf(buddy, rival)) {
            narr("The Guild is busier than ever. A second team has been admitted, and another explorer team is eyeing the board.")
            say("rival", "So you are the new team everyone talks about. I am Glim. We are exploring too.", NORMAL)
            say(BUDDY, "Then we will see each other in the dungeons.", HAPPY)
            say("rival", "Be careful out there. The deep floors have been changing.", NORMAL)
            say("rival", "Floors that were safe yesterday are not safe today.", SURPRISED)
        },
        dungeon("home-3-deeper", "The deeper floors", "thunderwave-cave", listOf(buddy, rival)) {
            enter(BUDDY, 3.5f, 2.8f)
            enter("rival", 1.5f, 3.2f)
            narr("In Thunderwave Cave the walls hum, and the floor feels different from how it did last week.")
            say("rival", "Feel that? The cave is rearranging itself.", SURPRISED)
            say(BUDDY, "Stay close. We map it and go back.", DETERMINED)
            shake(500)
            say("rival", "Together, then. For now.", HAPPY)
        },
    ))

    private fun chapter4() = StoryEpisode(WorldLayer.HOME, 4, "Strange Discoveries", listOf(
        dialogue("home-4-hoot", "The research room", "city", listOf(buddy, hoot)) {
            narr("Professor Hoot's research room smells of old paper and warm tea.")
            say("hoot", "Your finds are fascinating. See here: the same Pokémon turn up in places they should not.", HAPPY)
            say(BUDDY, "Like the dungeons are mixing things up?", SURPRISED)
            say("hoot", "Exactly. Something in the dungeons is pulling at the world like a thread.", NORMAL)
            say("hoot", "Bring me more. Every berry and every treasure helps me understand it.", HAPPY)
        },
        dungeon("home-4-glow", "Strange tiles", "mt-steel", listOf(buddy)) {
            enter(BUDDY, 3.0f, 2.8f)
            narr("In Mt. Steel the stone glows faintly where no light should reach.")
            emote(BUDDY, "?")
            say(BUDDY, "These tiles are warm. And they are humming.", SURPRISED)
            walk(BUDDY, 6.0f to 2.8f)
            item("pmd2-072", 7.0f, 3.2f)
            walk(BUDDY, 7.0f to 3.0f)
            take("pmd2-072")
            say(BUDDY, "Poké coins, glowing with the same light. The professor will want to see this.", HAPPY)
        },
    ))

    private fun chapter5() = StoryEpisode(WorldLayer.HOME, 5, "Ancient Secrets", listOf(
        dungeon("home-5-ruins", "The ancient hall", "ancient-ruins", listOf(buddy, hoot)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("hoot", 1.4f, 3.2f)
            narr("In the Ancient Ruins, carved pictures cover every wall: a clock, a tower and a great beast of metal.")
            say("hoot", "Look at the carvings. They describe a place where time itself lives.", SURPRISED)
            say(BUDDY, "A tower of time. Is that where the disturbance comes from?", DETERMINED)
            say("hoot", "I believe so. And I believe it has been calling to us.", NORMAL)
            shake(600)
            narr("Far off, something answers the call.")
        },
        dialogue("home-5-clue", "A message from the stone", "dampcave", listOf(buddy, voice)) {
            say("voice", "...the one who walks the floors... time frays...", NORMAL)
            say(BUDDY, "Who is that?", SURPRISED)
            say("voice", "...the tower... before it is too late...", SURPRISED)
            narr("The voice fades. Whatever it is, it wants the team to go to the Temporal Tower.")
        },
    ))

    private fun chapter6() = StoryEpisode(WorldLayer.HOME, 6, "The Great Disturbance", listOf(
        cinematic("home-6-tower", "The Temporal Tower", "mountain", listOf(buddy, big), dungeonId = "temporal-tower") {
            show(BUDDY, 2.0f, 4.2f)
            narr("At the top of the Temporal Tower, time stands still. Clocks without hands. Leaves frozen in the air.")
            show("big_dialga", 7.2f, 5.2f, Facing.LEFT)
            flash(); shake(700); cry("big_dialga")
            say("big_dialga", "You have come far, small explorers. I am what time turned into when it broke.", NORMAL)
            say(BUDDY, "We have come to set it right.", DETERMINED)
            say("big_dialga", "Then show me that your steps are true.", NORMAL)
            flash(); shake(600)
            say("big_dialga", "...It is enough. The thread mends. Go on, and keep the dungeons well.", HAPPY)
        },
        dialogue("home-6-home", "Time restored", "city", listOf(buddy, pelly)) {
            narr("The Guild glows in the morning light. The clock in the square ticks again.")
            say("pelly", "The whole town felt it. Time is running right again!", JOYOUS)
            say(BUDDY, "And the dungeons?", NORMAL)
            say("pelly", "Quieter. But the Guild will never run out of requests for a team like yours.", HAPPY)
            narr("Primal Dialga's blessing stays with your team: every expedition climbs ten percent faster.")
        },
    ))

}
