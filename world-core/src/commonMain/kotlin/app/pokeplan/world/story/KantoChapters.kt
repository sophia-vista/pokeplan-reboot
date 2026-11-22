package app.pokeplan.world.story

import app.pokeplan.world.WorldLayer
import app.pokeplan.world.story.Expression.*

internal object KantoChapters {
    private val buddy = buddyCharacter()
    private val nia = npc("nia", "Nia", 133)
    private val meowth = npc("meowth", "Meowth", 52)
    private val koffing = npc("koffing", "Koffing", 109)
    private val clefairy = npc("clefairy", "Clefairy", 35)
    private val gastly = npc("gastly", "Gastly", 92)
    private val zapdos = npc("big_zapdos", "Zapdos", 145)
    private val articuno = npc("big_articuno", "Articuno", 144)
    private val moltres = npc("big_moltres", "Moltres", 146)
    private val mewtwo = npc("big_mewtwo", "Mewtwo", 150)
    private val mew = npc("big_mew", "Mew", 151)

    fun all(): List<StoryEpisode> = listOf(c1(), c2(), c3(), c4(), c5())

    private fun c1() = StoryEpisode(WorldLayer.KANTO, 1, "Pallet Town", listOf(
        dialogue("kanto-1-pallet", "Pallet Town", "meadow", listOf(buddy, nia)) {
            narr("A quiet morning in Pallet Town. The grass is bright, but the wild Pokémon seem on edge.")
            say("nia", "Hi! I am Nia. I know every path from here to Viridian Forest.", HAPPY)
            say("nia", "But the forest Pokémon have been acting strange. They run from nothing.", NORMAL)
            say(BUDDY, "Then something is scaring them. Let's take a look.", DETERMINED)
        },
        dungeon("kanto-1-forest", "Viridian Forest", "viridian-forest", listOf(buddy, nia, clefairy)) {
            enter(BUDDY, 3.5f, 2.8f)
            enter("nia", 2.0f, 3.2f)
            narr("Sunlight breaks through the trees. Then, a rustle.")
            show("clefairy", 9.0f, 2.4f, Facing.LEFT)
            walk("clefairy", 7.0f to 2.5f)
            say("clefairy", "Please stay back! The shadows in the cave on the mountain... they took my friends.", SURPRISED)
            say("nia", "Mt. Moon. That is where the strange behavior starts.", NORMAL)
            say(BUDDY, "Then that is where we go next.", DETERMINED)
        },
    ))

    private fun c2() = StoryEpisode(WorldLayer.KANTO, 2, "Rocket Shadows", listOf(
        dungeon("kanto-2-rocktunnel", "Signs in the dark", "rock-tunnel", listOf(buddy, nia, meowth, koffing)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("nia", 1.6f, 3.2f)
            narr("In the Rock Tunnel, a faint light flickers, and voices carry through the dark.")
            show("meowth", 10.0f, 2.4f, Facing.LEFT)
            show("koffing", 10.8f, 3.1f, Facing.LEFT)
            walk("meowth", 7.5f to 2.4f); walk("koffing", 8.3f to 3.1f)
            say("meowth", "Nobody is supposed to be down here, so scram!", ANGRY)
            say("koffing", "Boss said to keep the cave quiet. The experiment must not be disturbed.", NORMAL)
            say("nia", "Experiment? So the rumors were true.", SURPRISED)
            exit("meowth", 2.4f); exit("koffing", 3.1f)
        },
        dialogue("kanto-2-rumors", "Whispers in town", "city", listOf(buddy, nia)) {
            say("nia", "Team Rocket. They said the experiments were stopped years ago.", WORRIED)
            say(BUDDY, "Not stopped. Hidden.", DETERMINED)
            say("nia", "Then we follow the tracks. They will lead us to whoever is behind it.", HAPPY)
        },
    ))

    private fun c3() = StoryEpisode(WorldLayer.KANTO, 3, "Spirits and Birds", listOf(
        dungeon("kanto-3-tower", "Pokémon Tower", "pokemon-tower", listOf(buddy, nia, gastly)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("nia", 1.6f, 3.2f)
            narr("Pokémon Tower is hushed. Candles burn in rows, and the air is cold.")
            show("gastly", 9.0f, 1.8f, Facing.LEFT)
            say("gastly", "Hee hee. Not many visitors. Not many alive ones, anyway.", HAPPY)
            say("nia", "Please, just let us pass. We are not here to bother anyone.", NORMAL)
            say("gastly", "Fine. But listen: the birds are restless. Something woke them.", SURPRISED)
            hide("gastly")
        },
        cinematic("kanto-3-birds", "Three wings", "thunderplains", listOf(buddy, zapdos, articuno, moltres)) {
            show(BUDDY, 5.0f, 5.0f)
            narr("Far above Kanto, three shapes cross the sky: lightning, ice and flame.")
            show("big_zapdos", 2.2f, 3.4f, Facing.RIGHT); cry("big_zapdos"); sound("STORY_ELECTRICITY"); flash()
            show("big_articuno", 5.0f, 2.8f, Facing.DOWN); cry("big_articuno")
            show("big_moltres", 8.0f, 3.4f, Facing.LEFT); cry("big_moltres"); sound("STORY_FIRE"); shake(500)
            say(BUDDY, "The legendary birds. They are heading to Cerulean.", SURPRISED)
            narr("Whatever stirs them, it is stronger than any one of them.")
        },
    ))

    private fun c4() = StoryEpisode(WorldLayer.KANTO, 4, "The League Road", listOf(
        dungeon("kanto-4-road", "Up the mountain", "mt-ember", listOf(buddy, nia)) {
            enter(BUDDY, 3.5f, 2.8f)
            enter("nia", 2.0f, 3.2f)
            narr("Mt. Ember burns with a low red glow. Beyond it, the road to Victory Road begins.")
            say("nia", "The League is at the end of this road. Every trainer dreams of it.", HAPPY)
            say(BUDDY, "We are not here to battle. We are here for the truth about the experiments.", DETERMINED)
            say("nia", "Then keep your head up. Rocket's trail goes all the way to Cerulean Cave.", NORMAL)
        },
        dialogue("kanto-4-camp", "A night on the road", "mountain", listOf(buddy, nia)) {
            narr("They rest by a small fire halfway up Victory Road.")
            say("nia", "Do you ever wonder what we would do if we were as strong as the legends?", HAPPY)
            choice("What do you say?", "Protect everyone." to "protect", "Find the truth." to "truth")
            branch("protect") { say("nia", "That sounds like you. Good.", JOYOUS) }
            branch("truth") { say("nia", "That sounds like you too. Let us find it.", DETERMINED) }
        },
    ))

    private fun c5() = StoryEpisode(WorldLayer.KANTO, 5, "The Ultimate Pokémon", listOf(
        cinematic("kanto-5-cave", "Cerulean Cave", "dampcave", listOf(buddy, nia, mewtwo), dungeonId = "cerulean-cave") {
            show(BUDDY, 2.0f, 4.4f); show("nia", 1.0f, 4.6f)
            narr("In the deepest chamber of Cerulean Cave, a single Pokémon waits in the dark.")
            show("big_mewtwo", 7.2f, 5.2f, Facing.LEFT); flash(); shake(600); cry("big_mewtwo"); sound("STORY_OMINOUS")
            say("big_mewtwo", "I was made, not born. I did not ask for this power.", NORMAL)
            say(BUDDY, "We came to find the truth. Not to hurt you.", DETERMINED)
            say("big_mewtwo", "Then you are the first. Go. I will watch over this place.", NORMAL)
            flash()
        },
        dialogue("kanto-5-end", "After the cave", "meadow", listOf(buddy, nia)) {
            say("nia", "Mewtwo is free. The experiments are over for good.", JOYOUS)
            say(BUDDY, "And the shadows are gone from Mt. Moon.", HAPPY)
            say("nia", "Something is still strange, though. Mewtwo came from somewhere.", WORRIED)
            narr("Mewtwo's blessing stays with your team: Kanto's expeditions find Pokémon a little more often.")
        },
    ))

}
