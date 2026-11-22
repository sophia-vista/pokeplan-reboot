package app.pokeplan.world.story

import app.pokeplan.world.WorldLayer
import app.pokeplan.world.story.Expression.*

internal object GalarChapters {
    private val buddy = buddyCharacter()
    private val scor = npc("scor", "Scor", 813)
    private val wooloo = npc("wooloo", "Wooloo", 831)
    private val impidimp = npc("impidimp", "Impidimp", 859)
    private val yamask = npc("yamask", "Yamask", 562)
    private val copperajah = npc("copperajah", "Copperajah", 879)
    private val zacian = npc("big_zacian", "Zacian", 888)
    private val zamazenta = npc("big_zamazenta", "Zamazenta", 889)
    private val eternatus = npc("big_eternatus", "Eternatus", 890)

    fun all(): List<StoryEpisode> = listOf(c1(), c2(), c3(), c4(), c5())

    private fun c1() = StoryEpisode(WorldLayer.GALAR, 1, "The Wild Area", listOf(
        dialogue("galar-1-postwick", "Postwick", "meadow", listOf(buddy, scor)) {
            narr("Wooloo graze on the hills above Postwick. A long road leads out to the Wild Area.")
            say("scor", "I am Scor! I know the way to the Wild Area and the Galar Mine.", HAPPY)
            say("scor", "The weather is changing too fast, though. Even the Wooloo look up.", WORRIED)
            say(BUDDY, "Then let us see why.", DETERMINED)
        },
        dungeon("galar-1-wild", "The Wild Area", "wild-area", listOf(buddy, scor, wooloo)) {
            enter(BUDDY, 3.5f, 2.8f)
            enter("scor", 2.0f, 3.2f)
            narr("The Wild Area is wide and loud. Storms roll over the grass in minutes.")
            show("wooloo", 9.0f, 2.8f, Facing.LEFT)
            say("wooloo", "Baaah! Bigger Pokémon than usual lately. Careful.", WORRIED)
            emote(BUDDY, "?")
            hide("wooloo")
        },
    ))

    private fun c2() = StoryEpisode(WorldLayer.GALAR, 2, "Team Yell", listOf(
        dungeon("galar-2-tangle", "Glimwood Tangle", "glimwood-tangle", listOf(buddy, scor, impidimp)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("scor", 1.8f, 3.2f)
            narr("In the Glimwood Tangle, glowing mushrooms light a path. A crowd in loud scarves has blocked it.")
            show("impidimp", 10.0f, 2.5f, Facing.LEFT)
            walk("impidimp", 7.5f to 2.5f)
            say("impidimp", "Nobody passes! We cheer for our favourite, and the rest must wait!", ANGRY)
            say("scor", "Cheering is fine. Blocking is not.", NORMAL)
            say(BUDDY, "Move, or we move you.", DETERMINED)
            exit("impidimp", 2.5f)
        },
    ))

    private fun c3() = StoryEpisode(WorldLayer.GALAR, 3, "Dynamax", listOf(
        dungeon("galar-3-bowl", "Dusty Bowl", "dusty-bowl", listOf(buddy, scor, yamask)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("scor", 1.8f, 3.2f)
            narr("The Dusty Bowl is full of old stones that never forget a wind.")
            show("yamask", 9.5f, 2.8f, Facing.LEFT)
            say("yamask", "Long ago a red light fell here. It made the giants.", NORMAL)
            say("scor", "Giants? You mean the Dynamax Pokémon?", SURPRISED)
            say("yamask", "The light is still burning, deep below.", WORRIED)
            hide("yamask")
        },
        dungeon("galar-3-mine", "Galar Mine No. 2", "galar-mine-2", listOf(buddy, scor, copperajah)) {
            enter(BUDDY, 3.0f, 2.8f)
            enter("scor", 1.8f, 3.2f)
            narr("In the deep mine, the Chairman's company hauls glowing stones up from the dark.")
            show("copperajah", 9.5f, 2.6f, Facing.LEFT)
            say("copperajah", "The Chairman needs this energy. Galar will be fed forever.", NORMAL)
            say(BUDDY, "At what price?", WORRIED)
            hide("copperajah")
        },
    ))

    private fun c4() = StoryEpisode(WorldLayer.GALAR, 4, "The Sword and the Shield", listOf(
        dungeon("galar-4-pass", "Route 10", "route-10", listOf(buddy, scor)) {
            enter(BUDDY, 3.5f, 2.8f)
            enter("scor", 2.0f, 3.2f)
            narr("Snow drifts across Route 10, and the cold does not feel natural.")
            say("scor", "My fire is almost out. The air itself is heavy.", SURPRISED)
            say(BUDDY, "The old heroes are close. Keep going.", DETERMINED)
            shake(500)
        },
        cinematic("galar-4-heroes", "The old heroes", "mountain", listOf(buddy, zacian, zamazenta), dungeonId = "giants-seat") {
            show(BUDDY, 5.0f, 5.2f)
            narr("At Giant's Seat, two shapes step out of the snow, one with a sword in its jaws, one with a shield on its chest.")
            show("big_zacian", 2.2f, 4.0f, Facing.RIGHT); flash(); cry("big_zacian")
            show("big_zamazenta", 7.8f, 4.0f, Facing.LEFT); shake(600); cry("big_zamazenta")
            say("big_zacian", "Long ago we stopped the darkness. Will you?", NORMAL)
            say(BUDDY, "We will.", DETERMINED)
            say("big_zamazenta", "Then take our strength, and go to the Energy Plant.", HAPPY)
        },
    ))

    private fun c5() = StoryEpisode(WorldLayer.GALAR, 5, "The Darkest Day", listOf(
        dungeon("galar-5-weald", "Slumbering Weald", "slumbering-weald", listOf(buddy, scor)) {
            enter(BUDDY, 3.5f, 2.8f)
            enter("scor", 2.0f, 3.2f)
            narr("The Slumbering Weald is quiet, and the sky above it is turning red.")
            say("scor", "Everything is wrong. The Wishing Stars have woken it.", SURPRISED)
            say(BUDDY, "The plant is straight ahead.", DETERMINED)
        },
        cinematic("galar-5-eternatus", "The Darkest Day", "volcanocave", listOf(buddy, eternatus), dungeonId = "energy-plant") {
            show(BUDDY, 2.0f, 4.8f)
            narr("In the Energy Plant, the red light has taken a shape, and the sky itself cracks.")
            show("big_eternatus", 7.0f, 4.4f, Facing.LEFT); flash(); shake(800); cry("big_eternatus")
            say("big_eternatus", "Everything burns, so everything may start again.", NORMAL)
            say(BUDDY, "Not today. Not like this.", DETERMINED)
            flash()
            narr("When the red light fades, its blessing stays with your team: Galar's expeditions find treasure a little more often.")
        },
    ))
}
