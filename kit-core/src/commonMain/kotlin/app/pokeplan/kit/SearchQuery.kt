package app.pokeplan.kit

/**
 * A typed search box's text, trimmed and lower-cased once, matched case-insensitively against any number of
 * fields. Base, Link, Mint and Care each searched this way with their own copy; each module still decides which
 * of its fields to match and what a hit looks like.
 */
class SearchQuery private constructor(val text: String) {
    val isEmpty: Boolean get() = text.isEmpty()

    /** True when any of [parts] contains the query; null parts never match, an empty query matches nothing. */
    fun matchesAny(vararg parts: String?): Boolean = text.isNotEmpty() && parts.any { it != null && it.lowercase().contains(text) }

    fun matchesAny(parts: Iterable<String?>): Boolean = text.isNotEmpty() && parts.any { it != null && it.lowercase().contains(text) }

    companion object {
        fun of(raw: String): SearchQuery = SearchQuery(raw.trim().lowercase())
    }
}
