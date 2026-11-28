package app.pokeplan.kit

/**
 * How a player-typed name (an area, a category, a unit, a tag) is cleaned and compared. Base, Mint and Link each
 * had their own copy of "trim, cap, refuse blank, refuse a repeat ignoring case"; each module still owns its
 * limit and which existing names count as taken.
 */
object Names {
    /** [raw] without surrounding space, cut to [max] characters. */
    fun tidy(raw: String, max: Int): String = raw.trim().take(max)

    /** Whether [name] is already one of [existing], ignoring case. */
    fun isTaken(name: String, existing: Iterable<String>): Boolean = existing.any { it.equals(name, ignoreCase = true) }

    /** [list] with [raw] appended when it is a new, non-blank name; the same list otherwise. */
    fun addTo(list: List<String>, raw: String, max: Int): List<String> {
        val n = tidy(raw, max)
        return if (n.isEmpty() || isTaken(n, list)) list else list + n
    }

    /** Every non-blank part, tidied, repeats (ignoring case) dropped, at most [limit] kept. */
    fun distinct(parts: Iterable<String>, max: Int, limit: Int): List<String> =
        parts.map { tidy(it, max) }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(limit)
}
