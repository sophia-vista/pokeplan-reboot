package app.pokeplan.world

enum class DungeonModifier(val title: String, val text: String) {
    BOUNTY("Bountiful", "20% more Poké from floors"),
    BERRY_SEASON("Berry season", "berries are easier to find"),
    LUCKY("Lucky day", "treasure boxes turn up more often"),
    CURIOUS("Curious Pokémon", "more Pokémon come to meet the team"),
}

object DungeonModifiers {
    const val PERCENT = 34

    const val BERRY_POINTS = 20
    const val TREASURE_POINTS = 4
    const val FIND_PERMILLE = 10
    const val POKE_PERCENT = 20

    fun day(millis: Long): Long = millis.floorDiv(86_400_000L)

    fun of(dungeonId: String, day: Long): DungeonModifier? {
        val h = ((dungeonId.hashCode().toLong() * 1_000_003L + day) * 2_654_435_761L).let { it xor (it ushr 29) } and 0x7fffffff
        if (h % 100 >= PERCENT) return null
        return DungeonModifier.entries[((h / 100) % DungeonModifier.entries.size).toInt()]
    }

    fun at(dungeonId: String, millis: Long): DungeonModifier? = of(dungeonId, day(millis))
}
