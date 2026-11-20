package app.pokeplan.link

/**
 * Plain-language readings for the Western chart (LINK.md 2.6). Written for fun and for talking points, in the traditional spirit of each sign; none of it is
 * advice, a prediction or a verdict on anyone, and the app says so wherever it shows them. They are fixed text, so nothing here reads or stores a person.
 */
object LoreWestern {
    const val DISCLAIMER = "For fun and conversation, not advice or a verdict on anyone."

    fun sign(s: Sign): String = when (s) {
        Sign.ARIES -> "Bold and quick to start things; direct, competitive, and honest to a fault. Needs room to move."
        Sign.TAURUS -> "Steady, sensual and loyal; loves comfort, good food and keeping promises. Slow to change its mind."
        Sign.GEMINI -> "Curious, chatty and quick; collects ideas, people and jokes. Gets restless without variety."
        Sign.CANCER -> "Caring, protective and deeply sentimental; makes a home out of people. Feels everything, and remembers it."
        Sign.LEO -> "Warm, generous and proud; brings the party and loves to be appreciated. Big heart, big presence."
        Sign.VIRGO -> "Careful, helpful and observant; shows love by fixing things and noticing details. Hard on itself."
        Sign.LIBRA -> "Charming, fair-minded and peace-loving; cares how things look and feel between people. Dislikes choosing sides."
        Sign.SCORPIO -> "Intense, private and fiercely loyal; sees through things and goes deep. Trusts slowly, and means it."
        Sign.SAGITTARIUS -> "Adventurous, frank and optimistic; always chasing the next horizon and the big question. Hates being fenced in."
        Sign.CAPRICORN -> "Disciplined, dry-witted and ambitious; builds things that last. Reserved at first, then reliably there."
        Sign.AQUARIUS -> "Independent, original and a little detached; cares about ideas and fairness. Marches to its own tune."
        Sign.PISCES -> "Dreamy, empathetic and creative; absorbs the mood of a room. Needs quiet time to recover."
    }

    fun element(e: String): String = when (e) {
        "Fire" -> "Fire signs run on enthusiasm and action."
        "Earth" -> "Earth signs run on the practical and the tangible."
        "Air" -> "Air signs run on ideas and conversation."
        else -> "Water signs run on feeling and intuition."
    }

    fun modality(m: String): String = when (m) {
        "Cardinal" -> "Cardinal signs start things."
        "Fixed" -> "Fixed signs keep things going."
        else -> "Mutable signs adapt and finish things."
    }

    /** What each point of the chart stands for. */
    fun body(b: Body): String = when (b) {
        Body.SUN -> "The Sun is the core self: who you are when you are being yourself."
        Body.MOON -> "The Moon is the inner world: moods, needs, and what feels like home."
        Body.MERCURY -> "Mercury is the mind: how you think, talk and learn."
        Body.VENUS -> "Venus is affection and taste: how you love, and what you find beautiful."
        Body.MARS -> "Mars is drive: how you go after things, and how you get angry."
        Body.JUPITER -> "Jupiter is growth and luck: where you expand and feel generous."
        Body.SATURN -> "Saturn is structure: where you work hardest and learn patience."
        Body.URANUS -> "Uranus is change: where you break the mould. It stays in one sign for years, so it describes a generation."
        Body.NEPTUNE -> "Neptune is dreams and intuition. It stays in one sign for over a decade, so it describes a generation."
        Body.PLUTO -> "Pluto is transformation. It stays in one sign for many years, so it describes a generation."
        Body.NORTH_NODE -> "The North Node points to what you are growing toward."
    }

    const val RISING = "The rising sign is the first impression: how you come across and how you meet a new situation. It needs the birth time and place."

    fun house(n: Int): String = when (n) {
        1 -> "House 1: self and appearance."
        2 -> "House 2: money, values and belongings."
        3 -> "House 3: communication, siblings and the neighbourhood."
        4 -> "House 4: home, family and roots."
        5 -> "House 5: play, romance and creativity."
        6 -> "House 6: daily routines, work and health."
        7 -> "House 7: close partnerships."
        8 -> "House 8: shared resources, change and depth."
        9 -> "House 9: travel, study and belief."
        10 -> "House 10: career and public standing."
        11 -> "House 11: friends, groups and hopes."
        else -> "House 12: rest, solitude and the unseen."
    }

    fun aspect(k: AspectKind): String = when (k) {
        AspectKind.CONJUNCTION -> "Conjunct: the two blend into one theme and are felt together."
        AspectKind.SEXTILE -> "Sextile: an easy opening between the two, helpful when used."
        AspectKind.SQUARE -> "Square: friction that pushes you to act."
        AspectKind.TRINE -> "Trine: the two flow together naturally."
        AspectKind.OPPOSITION -> "Opposite: a tug between two sides that asks for balance."
    }

    const val RETROGRADE = "Retrograde means the planet looked, from Earth, as if it were moving backwards. Traditionally it turns the planet's themes inward."
    const val APPROXIMATE = "Positions are worked out on the phone and are accurate to about a third of a degree, so a body right on a sign line may really be in the next sign."
}
