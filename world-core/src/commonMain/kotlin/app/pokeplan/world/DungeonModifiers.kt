package app.pokeplan.world

/** What a dungeon is like today: a small bonus on its rewards. Never a penalty and never a change in speed, so being away costs nothing. */
enum class DungeonModifier(val title: String, val text: String) {
    BOUNTY("Bountiful", "20% more Poké from floors"),
    BERRY_SEASON("Berry season", "berries are easier to find"),
    LUCKY("Lucky day", "treasure boxes turn up more often"),
    CURIOUS("Curious Pokémon", "more Pokémon come to meet the team"),
}

/**
 * Dungeon modifiers: about a third of the (dungeon, day) pairs have one, picked from the dungeon's id and the day number (UTC) alone, so
 * every floor of a settle, however it is split or whenever the app is opened, gets the same answer. Each floor uses the day it was
 * climbed on. They add to rewards only.
 */
object DungeonModifiers {
    /** Percent of (dungeon, day) pairs that have a modifier. *(tune)* */
    const val PERCENT = 34

    const val BERRY_POINTS = 20
    const val TREASURE_POINTS = 4
    const val FIND_PERMILLE = 10
    const val POKE_PERCENT = 20

    /** The day number of [millis] (UTC). */
    fun day(millis: Long): Long = millis.floorDiv(86_400_000L)

    /** The modifier of [dungeonId] on day number [day], or null. */
    fun of(dungeonId: String, day: Long): DungeonModifier? {
        val h = ((dungeonId.hashCode().toLong() * 1_000_003L + day) * 2_654_435_761L).let { it xor (it ushr 29) } and 0x7fffffff
        if (h % 100 >= PERCENT) return null
        return DungeonModifier.entries[((h / 100) % DungeonModifier.entries.size).toInt()]
    }

    /** The modifier at the moment [millis]. */
    fun at(dungeonId: String, millis: Long): DungeonModifier? = of(dungeonId, day(millis))
}
