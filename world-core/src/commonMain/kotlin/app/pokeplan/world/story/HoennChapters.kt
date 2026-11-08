package app.pokeplan.world.story

import app.pokeplan.world.WorldLayer
import app.pokeplan.world.story.Expression.*

/** Hoenn, "The Primal Awakening": Cloud the Swablu guides the buddy; Team Magma (Numel, Camerupt) and Team Aqua (Carvanha, Sharpedo); the three ancient Pokémon. */
internal object HoennChapters {
    private val buddy = buddyCharacter()
    private val cloud = npc("cloud", "Cloud", 333)
    private val numel = npc("numel", "Numel", 322)
    private val carvanha = npc("carvanha", "Carvanha", 318)
    private val camerupt = npc("camerupt", "Camerupt", 323)
    private val sharpedo = npc("sharpedo", "Sharpedo", 319)
    private val wingull = npc("wingull", "Wingull", 278)
    private val groudon = npc("big_groudon", "Groudon", 383)
    private val kyogre = npc("big_kyogre", "Kyogre", 382)
    private val rayquaza = npc("big_rayquaza", "Rayquaza", 384)

    fun all(): List<StoryEpisode> = listOf(c1(), c2(), c3(), c4(), c5())

    private fun c1() = StoryEpisode(WorldLayer.HOENN, 1, "Littleroot Town", listOf(
        dialogue("hoenn-1-littleroot", "Littleroot Town", "meadow", listOf(buddy, cloud)) {
            narr("The sky over Littleroot Town is the wrong colour. One day it is scorching, the next it pours.")
            say("cloud", "I am Cloud. I fly over Hoenn every day, and the weather has never been this odd.", NORMAL)
            say("cloud", "The Pokémon in the woods and caves feel it too.", WORRIED)
            say(BUDDY, "Then let us see what they are feeling.", DETERMINED)
        },
        dungeon("hoenn-1-woods", "Petalburg Woods", "petalburg-woods", listOf(buddy, cloud, wingull)) {
            enter(BUDDY, 3.5f, 2.8f)
            enter("cloud", 2.0f, 3.2f)
            narr("In Petalburg Woods the leaves tremble with no wind.")
            show("wingull", 9.0f, 1.8f, Facing.LEFT)
            say("wingull", "Storm! Storm coming! No, wait, sun! No, storm!", SURPRISED)
            say("cloud", "See? Even the Wingull cannot keep up.", NORMAL)
            hide("wingull")
        },
    ))

    private fun c2() = StoryEpisode(WorldLayer.HOENN, 2, "Signs in the Stone", listOf(
        dungeon("hoenn-2-falls", "Meteor Falls", "meteor-falls", listOf(buddy, cloud)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("cloud", 1.8f, 3.2f)
            narr("Behind Meteor Falls, old inscriptions run across the cave wall.")
            emote(BUDDY, "?")
            say(BUDDY, "A great red beast, and a great blue one. They face each other.", SURPRISED)
            say("cloud", "The land and the sea. They quarrelled long ago.", NORMAL)
            say("cloud", "And someone is trying to wake them.", WORRIED)
        },
        dialogue("hoenn-2-elder", "A warning", "dampcave", listOf(buddy, cloud)) {
            say("cloud", "An old scholar told me: the beasts cannot be kept asleep forever.", NORMAL)
            say(BUDDY, "Then someone must reach them first and keep the peace.", DETERMINED)
        },
    ))

    private fun c3() = StoryEpisode(WorldLayer.HOENN, 3, "Magma and Aqua", listOf(
        dungeon("hoenn-3-chimney", "Mt. Chimney", "mt-chimney", listOf(buddy, cloud, numel, camerupt)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("cloud", 1.6f, 3.2f)
            narr("Mt. Chimney smokes. Figures in red crowd around a glowing orb.")
            show("numel", 10.0f, 2.4f, Facing.LEFT); show("camerupt", 10.9f, 3.1f, Facing.LEFT)
            walk("numel", 7.6f to 2.4f); walk("camerupt", 8.6f to 3.1f)
            say("numel", "Team Magma will make more land for every Pokémon. Step aside!", ANGRY)
            say("camerupt", "Waking the great one under the mountain is only the first step.", NORMAL)
            say("cloud", "And what happens to the sea, then?", SURPRISED)
            exit("numel", 2.4f); exit("camerupt", 3.1f)
        },
        dungeon("hoenn-3-shoal", "Shoal Cave", "shoal-cave", listOf(buddy, cloud, carvanha, sharpedo)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("cloud", 1.6f, 3.2f)
            narr("Shoal Cave floods and ebbs with the tide. Blue figures work at its heart.")
            show("carvanha", 10.0f, 2.4f, Facing.LEFT); show("sharpedo", 10.9f, 3.1f, Facing.LEFT)
            walk("carvanha", 7.6f to 2.4f); walk("sharpedo", 8.6f to 3.1f)
            say("carvanha", "Team Aqua will give every Pokémon more sea. Out of our way!", ANGRY)
            say("sharpedo", "The one under the waves will rise soon.", NORMAL)
            say(BUDDY, "Two teams, two plans. And only one world.", DETERMINED)
            exit("carvanha", 2.4f); exit("sharpedo", 3.1f)
        },
    ))

    private fun c4() = StoryEpisode(WorldLayer.HOENN, 4, "The Weather Wars", listOf(
        cinematic("hoenn-4-clash", "Land and sea", "volcanocave", listOf(buddy, groudon, kyogre), dungeonId = "seafloor-cavern") {
            show(BUDDY, 5.0f, 5.2f)
            narr("Beneath the Seafloor Cavern, the two great beasts stir.")
            show("big_groudon", 2.2f, 5.0f, Facing.RIGHT); shake(700); cry("big_groudon"); sound("STORY_RUMBLE")
            show("big_kyogre", 7.8f, 5.0f, Facing.LEFT); flash(); cry("big_kyogre"); sound("STORY_FLOOD")
            narr("Above, the sky roars. The sun burns and the sea rises at once.")
            say(BUDDY, "They are fighting! Everything is being torn apart!", SURPRISED)
            narr("Only the sky itself can end this.")
        },
        dungeon("hoenn-4-road", "The road to the League", "victory-road-hoenn", listOf(buddy, cloud)) {
            enter(BUDDY, 3.5f, 2.8f)
            enter("cloud", 2.0f, 3.2f)
            say("cloud", "There is one more. A Pokémon from the heavens, the one the old stories call the sky's keeper.", NORMAL)
            say(BUDDY, "The Sky Pillar. Let us find it.", DETERMINED)
        },
    ))

    private fun c5() = StoryEpisode(WorldLayer.HOENN, 5, "The Sky Pillar", listOf(
        dungeon("hoenn-5-climb", "The ascent", "sky-pillar", listOf(buddy, cloud)) {
            enter(BUDDY, 3.5f, 2.8f)
            enter("cloud", 2.0f, 3.2f)
            narr("The Sky Pillar is a stair of ancient stone, floor on floor, high above the clouds.")
            say("cloud", "The wind is so strong I cannot fly. You must climb alone from here.", WORRIED)
            say(BUDDY, "Thank you, Cloud. Wait for us.", HAPPY)
            shake(500)
        },
        cinematic("hoenn-5-rayquaza", "Delta Stream", "mountain", listOf(buddy, rayquaza), dungeonId = "sky-pillar") {
            show(BUDDY, 2.0f, 4.8f)
            narr("At the very top, a green coil rises from the clouds.")
            show("big_rayquaza", 6.8f, 4.4f, Facing.LEFT); flash(); shake(800); cry("big_rayquaza"); sound("STORY_WIND_HIGH")
            say("big_rayquaza", "Two quarrelling beasts and two foolish teams. I have watched them long enough.", NORMAL)
            say(BUDDY, "Will you stop them?", DETERMINED)
            say("big_rayquaza", "With you beside me. Hold fast!", HAPPY)
            flash(); shake(600)
            narr("A green light cuts across Hoenn. The storm quiets, and the sun and the sea go back to their places.")
            narr("Rayquaza's blessing stays with your team: Hoenn's expeditions find Pokémon a little more often.")
        },
    ))
}
