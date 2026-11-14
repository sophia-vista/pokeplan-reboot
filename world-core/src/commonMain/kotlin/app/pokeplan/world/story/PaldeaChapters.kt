package app.pokeplan.world.story

import app.pokeplan.world.WorldLayer
import app.pokeplan.world.story.Expression.*

/** Paldea, "The Treasure of Area Zero": Sprig the Sprigatito guides the buddy; Team Star is Houndour and Houndoom; Koraidon and Terapagos are the legends of the Crater. */
internal object PaldeaChapters {
    private val buddy = buddyCharacter()
    private val sprig = npc("sprig", "Sprig", 906)
    private val lechonk = npc("lechonk", "Lechonk", 915)
    private val houndour = npc("houndour", "Houndour", 228)
    private val houndoom = npc("houndoom", "Houndoom", 229)
    private val glimmet = npc("glimmet", "Glimmet", 969)
    private val koraidon = npc("big_koraidon", "Koraidon", 1007)
    private val terapagos = npc("big_terapagos", "Terapagos", 1024)

    fun all(): List<StoryEpisode> = listOf(c1(), c2(), c3(), c4(), c5())

    private fun c1() = StoryEpisode(WorldLayer.PALDEA, 1, "Mesagoza", listOf(
        dialogue("paldea-1-mesagoza", "Mesagoza", "city", listOf(buddy, sprig)) {
            narr("The great academy of Mesagoza looks over a country with no fences. Students are free to choose their own treasure.")
            say("sprig", "I am Sprig! I know the Tagtree Thicket and the caves in the south.", HAPPY)
            say("sprig", "They say the Great Crater is the end of every road.", NORMAL)
            say(BUDDY, "Then that is where we are going.", DETERMINED)
        },
        dungeon("paldea-1-thicket", "Tagtree Thicket", "tagtree-thicket", listOf(buddy, sprig, lechonk)) {
            enter(BUDDY, 3.5f, 2.8f)
            enter("sprig", 2.0f, 3.2f)
            narr("The Tagtree Thicket is thick with wild grass and the smell of sun.")
            show("lechonk", 9.0f, 2.8f, Facing.LEFT)
            say("lechonk", "Oink! Careful. Some of the Pokémon here have a glittering crystal on their heads.", SURPRISED)
            say(BUDDY, "A Terastal crystal. Interesting.", NORMAL)
            hide("lechonk")
        },
    ))

    private fun c2() = StoryEpisode(WorldLayer.PALDEA, 2, "Team Star", listOf(
        dungeon("paldea-2-passage", "Dalizapa Passage", "dalizapa-passage", listOf(buddy, sprig, houndour)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("sprig", 1.8f, 3.2f)
            narr("In the Dalizapa Passage, a gang in star-marked jackets has taken over the old tunnels.")
            show("houndour", 10.0f, 2.5f, Facing.LEFT)
            walk("houndour", 7.5f to 2.5f)
            say("houndour", "This base is ours! Turn around or we will make some noise.", ANGRY)
            say("sprig", "Nobody is bullying anyone here.", DETERMINED)
            exit("houndour", 2.5f)
        },
    ))

    private fun c3() = StoryEpisode(WorldLayer.PALDEA, 3, "Lake and Desert", listOf(
        dungeon("paldea-3-lake", "Casseroya Lake", "casseroya-lake", listOf(buddy, sprig, glimmet)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("sprig", 1.8f, 3.2f)
            narr("Casseroya Lake glitters under the evening sky. Crystals stand at its shore like bells.")
            show("glimmet", 9.5f, 2.8f, Facing.LEFT)
            say("glimmet", "The crystals sing at night. They come from the Crater.", NORMAL)
            say("sprig", "From the Crater? Then they are a trail.", SURPRISED)
            hide("glimmet")
        },
        dungeon("paldea-3-desert", "Asado Desert", "asado-desert", listOf(buddy, sprig, houndoom)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("sprig", 1.8f, 3.2f)
            narr("The Asado Desert shimmers with heat. A tall shadow stands on the dune.")
            show("houndoom", 9.5f, 2.6f, Facing.LEFT)
            say("houndoom", "Turn back. The Crater is no place for students.", NORMAL)
            say(BUDDY, "That is for us to decide.", DETERMINED)
            hide("houndoom")
        },
    ))

    private fun c4() = StoryEpisode(WorldLayer.PALDEA, 4, "The Edge of the Crater", listOf(
        dungeon("paldea-4-mountain", "Glaseado Mountain", "glaseado-mountain", listOf(buddy, sprig)) {
            enter(BUDDY, 3.5f, 2.8f)
            enter("sprig", 2.0f, 3.2f)
            narr("Snow squeaks underfoot on Glaseado Mountain. The wind carries a distant roar.")
            say("sprig", "Something is running beside us. Do you see it?", SURPRISED)
            say(BUDDY, "Yes. Something big, and not hostile.", DETERMINED)
            shake(500)
        },
        cinematic("paldea-4-koraidon", "The old partner", "mountain", listOf(buddy, koraidon), dungeonId = "socarrat-trail") {
            show(BUDDY, 2.0f, 4.8f)
            narr("On the Socarrat Trail, a red-and-black giant steps out of the rocks and lowers its head.")
            show("big_koraidon", 7.0f, 4.6f, Facing.LEFT); flash(); shake(700); cry("big_koraidon")
            say("big_koraidon", "Grrr. Rooaar.", NORMAL)
            say(BUDDY, "It wants us to follow it. To the Crater.", HAPPY)
        },
    ))

    private fun c5() = StoryEpisode(WorldLayer.PALDEA, 5, "Area Zero", listOf(
        dungeon("paldea-5-lab", "Zero Lab", "zero-lab", listOf(buddy, sprig)) {
            enter(BUDDY, 3.5f, 2.8f)
            enter("sprig", 2.0f, 3.2f)
            narr("In the Zero Lab, old machines hum on their own and a time machine waits behind glass.")
            say("sprig", "This was someone's life work. And it is still running.", WORRIED)
            say(BUDDY, "Then it must be stopped carefully.", DETERMINED)
        },
        cinematic("paldea-5-terapagos", "The heart of the Crater", "volcanocave", listOf(buddy, terapagos), dungeonId = "area-zero") {
            show(BUDDY, 2.0f, 4.8f)
            narr("At the bottom of the Crater, a vast cavern glitters with crystals of every colour.")
            show("big_terapagos", 7.0f, 4.6f, Facing.LEFT); flash(); shake(600); cry("big_terapagos")
            say("big_terapagos", "Pah! Terapagos.", HAPPY)
            say(BUDDY, "So this is the treasure everyone was looking for.", HAPPY)
            flash()
            narr("Its crown lights your team: Paldea's expeditions find treasure a little more often.")
        },
    ))
}
