package app.pokeplan.world.story

import app.pokeplan.world.WorldLayer
import app.pokeplan.world.story.Expression.*

internal object UnovaChapters {
    private val buddy = buddyCharacter()
    private val osh = npc("osh", "Osh", 501)
    private val yamask = npc("yamask", "Yamask", 562)
    private val purrloin = npc("purrloin", "Purrloin", 509)
    private val liepard = npc("liepard", "Liepard", 510)
    private val reshiram = npc("big_reshiram", "Reshiram", 643)
    private val zekrom = npc("big_zekrom", "Zekrom", 644)
    private val kyurem = npc("big_kyurem", "Kyurem", 646)

    fun all(): List<StoryEpisode> = listOf(c1(), c2(), c3(), c4(), c5())

    private fun c1() = StoryEpisode(WorldLayer.UNOVA, 1, "Nuvema Town", listOf(
        dialogue("unova-1-nuvema", "Nuvema Town", "meadow", listOf(buddy, osh)) {
            narr("Wind turns the bridges of Nuvema Town. Everyone in the square is talking about setting Pokémon free.")
            say("osh", "I am Osh! I know the paths through Pinwheel Forest and Wellspring Cave.", HAPPY)
            say("osh", "People say Pokémon and trainers should part. I do not think so.", WORRIED)
            say(BUDDY, "Then we will see for ourselves.", DETERMINED)
        },
        dungeon("unova-1-forest", "Pinwheel Forest", "pinwheel-forest", listOf(buddy, osh)) {
            enter(BUDDY, 3.5f, 2.8f)
            enter("osh", 2.0f, 3.2f)
            narr("Pinwheel Forest is full of rope bridges and rustling leaves, but no one answers when you call.")
            say("osh", "Strange. The Pokémon here usually come to say hello.", WORRIED)
            emote(BUDDY, "?")
            say(BUDDY, "Footprints of trainers in matching boots, all leading deeper in.", SURPRISED)
        },
    ))

    private fun c2() = StoryEpisode(WorldLayer.UNOVA, 2, "Truth and Ideals", listOf(
        dungeon("unova-2-castle", "Relic Castle", "relic-castle", listOf(buddy, osh, yamask)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("osh", 1.8f, 3.2f)
            narr("Sand pours through the halls of the Relic Castle, over murals of two great dragons.")
            show("yamask", 9.0f, 2.8f, Facing.LEFT)
            say("yamask", "I remember the face on my mask. It was a person who loved a dragon.", SAD)
            say("osh", "These murals show one dragon of truth and one of ideals.", NORMAL)
            say(BUDDY, "A dragon for each kind of hero.", SURPRISED)
            hide("yamask")
        },
        dialogue("unova-2-story", "The old legend", "dampcave", listOf(buddy, osh)) {
            say("osh", "Long ago one dragon split in two, because two brothers could not agree.", NORMAL)
            say(BUDDY, "And when the brothers agreed?", WORRIED)
            say("osh", "Then the dragon was whole again. The legend does not say what came after.", WORRIED)
        },
    ))

    private fun c3() = StoryEpisode(WorldLayer.UNOVA, 3, "Team Plasma", listOf(
        dungeon("unova-3-cave", "Chargestone Cave", "chargestone-cave", listOf(buddy, osh, purrloin)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("osh", 1.8f, 3.2f)
            narr("Floating stones hum with charge in Chargestone Cave, and a knight in a grey robe blocks the way.")
            show("purrloin", 10.0f, 2.5f, Facing.LEFT)
            walk("purrloin", 7.5f to 2.5f)
            say("purrloin", "Team Plasma sets Pokémon free from the cruelty of trainers!", ANGRY)
            say("osh", "Free? Nobody here is being kept against their will.", WORRIED)
            say("purrloin", "Pokémon belong to themselves. Step aside.", NORMAL)
            exit("purrloin", 2.5f)
        },
        dungeon("unova-3-mountain", "Twist Mountain", "twist-mountain", listOf(buddy, osh, liepard)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("osh", 1.8f, 3.2f)
            narr("Twist Mountain is cold and dark, and a sage in a robe watches from a ledge.")
            show("liepard", 9.5f, 2.6f, Facing.LEFT)
            say("liepard", "Our king will open the way to a new Unova.", NORMAL)
            say(BUDDY, "A world where nobody chooses a friend is no world at all.", DETERMINED)
            hide("liepard")
        },
    ))

    private fun c4() = StoryEpisode(WorldLayer.UNOVA, 4, "The Dragonspiral Tower", listOf(
        dungeon("unova-4-climb", "Up the Dragonspiral Tower", "dragonspiral-tower", listOf(buddy, osh)) {
            enter(BUDDY, 3.5f, 2.8f)
            enter("osh", 2.0f, 3.2f)
            narr("The Dragonspiral Tower winds up into the clouds. Every step shakes dust from the old stone.")
            say("osh", "My fur stands on end. Something big is waking up.", SURPRISED)
            say(BUDDY, "The white dragon of truth. It is up there.", DETERMINED)
            shake(500)
        },
        cinematic("unova-4-reshiram", "The dragon of truth", "mountain", listOf(buddy, reshiram), dungeonId = "dragonspiral-tower") {
            show(BUDDY, 2.0f, 4.8f)
            narr("At the top, the air burns white. The roof of the tower opens to the sky.")
            show("big_reshiram", 7.0f, 4.8f, Facing.LEFT); flash(); shake(700); cry("big_reshiram")
            say("big_reshiram", "Truth is a flame. It warms those who tend it and burns those who hide it.", NORMAL)
            say(BUDDY, "We came to see the truth, not to hide it.", DETERMINED)
            say("big_reshiram", "Then go to the castle, small ones, and see what is built on a lie.", NORMAL)
        },
    ))

    private fun c5() = StoryEpisode(WorldLayer.UNOVA, 5, "The Giant Chasm", listOf(
        cinematic("unova-5-castle", "N's Castle", "mountain", listOf(buddy, zekrom), dungeonId = "n-castle") {
            show(BUDDY, 5.0f, 5.2f)
            narr("In N's Castle, a black dragon rises from the floor, crackling with lightning, and the walls of the castle fall away.")
            show("big_zekrom", 7.8f, 4.0f, Facing.LEFT); flash(); shake(800); cry("big_zekrom")
            say("big_zekrom", "Ideals are a storm. They strike where the sky is clearest.", NORMAL)
            say(BUDDY, "Then strike with us, not against us!", DETERMINED)
            flash(); shake(600)
            narr("The storm bows to your team, and the sky above Unova clears.")
        },
        cinematic("unova-5-kyurem", "The frozen chasm", "icecave", listOf(buddy, kyurem), dungeonId = "giant-chasm") {
            show(BUDDY, 2.0f, 4.8f)
            narr("Past the castle lies the Giant Chasm, a crater of ice where the third dragon was left behind.")
            show("big_kyurem", 7.0f, 4.6f, Facing.LEFT); flash(); shake(700); cry("big_kyurem")
            say("big_kyurem", "Nobody came for the empty part of the dragon. Why do you?", SAD)
            say(BUDDY, "Because nobody should be left in the cold.", DETERMINED)
            say("big_kyurem", "Then take my cold with you, and let it guard your road.", HAPPY)
            flash()
            narr("Kyurem's blessing stays with your team: Unova's expeditions find treasure a little more often.")
        },
    ))
}
