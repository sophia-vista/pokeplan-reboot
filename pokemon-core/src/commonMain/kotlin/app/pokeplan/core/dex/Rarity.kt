package app.pokeplan.core.dex

enum class Rarity(val label: String) {
    COMMON("Common"),
    UNCOMMON("Uncommon"),
    RARE("Rare"),
    SUPER_RARE("Super Rare"),
    ;

    /** One tier up, or this tier if it is already the top. */
    fun next(): Rarity = entries[minOf(ordinal + 1, entries.lastIndex)]
}
