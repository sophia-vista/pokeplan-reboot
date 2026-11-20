package app.pokeplan.link

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * The slambook half of a person (LINK.md 2.6): what the user chooses to write down about someone, and about themselves (the person marked
 * [Person.isMe]). Every field is optional and typed in by the user; nothing is inferred and nothing here ever leaves LINK. A reading in [Insights] is
 * worked out from these when it is shown and never stored.
 */
@Serializable
data class Profile(
    val socials: List<Social> = emptyList(),
    val likes: List<String> = emptyList(),
    val dislikes: List<String> = emptyList(),
    /** Slambook questions and the person's answers, in the order written. */
    val answers: List<Answer> = emptyList(),
    /** Four letters such as "INFP", or empty. */
    val mbti: String = "",
    /** The Enneagram type 1 to 9, or 0 for none, and the wing (also 1 to 9; 0 for none). */
    val enneagram: Int = 0,
    val enneagramWing: Int = 0,
    /** Love languages, most meaningful first (up to five, no repeats). */
    val loveLanguages: List<LoveLanguage> = emptyList(),
    val attachment: Attachment? = null,
    /** DISC style letters, the main one first (one or two). */
    val disc: List<Disc> = emptyList(),
    val bigFive: Map<Trait, Level> = emptyMap(),
    val birth: Birth = Birth(),
)

/** A place online: the platform's own name or the user's, and the handle or link, kept as typed. */
@Serializable
data class Social(val platform: String, val handle: String)

@Serializable
data class Answer(val question: String, val answer: String)

/**
 * What a birth chart needs besides the date (which is the person's Birthday date, so it is kept once): the clock time as written on the birth record
 * ([minutes] after midnight), the birthplace for the rising sign, and the UTC offset that clock was on. [kuaFormula] picks the traditional formula for
 * the Kua number; empty means none was chosen.
 */
@Serializable
data class Birth(
    val minutes: Int? = null,
    val utcOffsetMinutes: Int = 480,
    val placeName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val kuaFormula: String = "",
) {
    val place: Place? get() = if (latitude != null && longitude != null) Place(placeName, latitude, longitude, utcOffsetMinutes) else null
    val kua: BaZi.KuaFormula? get() = BaZi.KuaFormula.entries.firstOrNull { it.name == kuaFormula }
}

@Serializable
enum class LoveLanguage(val label: String) {
    WORDS("Words of affirmation"), TIME("Quality time"), GIFTS("Receiving gifts"), SERVICE("Acts of service"), TOUCH("Physical touch"),
}

@Serializable
enum class Attachment(val label: String) { SECURE("Secure"), ANXIOUS("Anxious"), AVOIDANT("Avoidant"), FEARFUL("Fearful-avoidant") }

@Serializable
enum class Disc(val label: String) { D("Dominance"), I("Influence"), S("Steadiness"), C("Conscientiousness") }

@Serializable
enum class Trait(val label: String) {
    OPENNESS("Openness"), CONSCIENTIOUSNESS("Conscientiousness"), EXTRAVERSION("Extraversion"), AGREEABLENESS("Agreeableness"), NEUROTICISM("Sensitivity"),
}

@Serializable
enum class Level(val label: String) { LOW("Lower"), MID("Middle"), HIGH("Higher") }

/** What the profile sheet offers as starting points; the user can always type their own. */
object ProfileKit {
    val socialPlatforms = listOf("Instagram", "Facebook", "TikTok", "X", "Snapchat", "Discord", "YouTube", "LinkedIn", "Telegram", "Messenger", "Website")

    /** The classic slambook questions, to tap and answer. */
    val questions = listOf(
        "Nickname", "Favourite colour", "Favourite food", "Favourite song", "Favourite movie or show", "Favourite Pokémon", "Hobby",
        "Dream job", "Dream place to visit", "Best memory with me", "Motto", "Pet peeve", "Hidden talent", "Biggest fear", "Comfort food", "One word for me",
    )

    val mbtiTypes = listOf(
        "INTJ", "INTP", "ENTJ", "ENTP", "INFJ", "INFP", "ENFJ", "ENFP", "ISTJ", "ISFJ", "ESTJ", "ESFJ", "ISTP", "ISFP", "ESTP", "ESFP",
    )

    const val TEXT_MAX = 120
    const val ANSWER_MAX = 300
    const val MAX_ITEMS = 40

    /** Cleans a typed list: trimmed, no blanks, no repeats, at most [MAX_ITEMS]. */
    fun clean(items: List<String>): List<String> = items.map { it.trim().take(TEXT_MAX) }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(MAX_ITEMS)

    /** A UTC offset typed as hours ("8", "+5.5", "-3:30"), in minutes, or null if it is not one. Offsets run from -12:00 to +14:00. */
    fun parseOffset(text: String): Int? {
        val t = text.trim().removePrefix("UTC").removePrefix("GMT").trim()
        if (t.isEmpty()) return null
        val sign = if (t.startsWith("-")) -1 else 1
        val body = t.removePrefix("+").removePrefix("-")
        val minutes = if (':' in body) {
            val (h, m) = body.split(':', limit = 2)
            (h.toIntOrNull() ?: return null) * 60 + (m.toIntOrNull() ?: return null)
        } else (((body.toDoubleOrNull() ?: return null) * 60.0) + 0.5).toInt()
        val v = sign * minutes
        return if (v in -720..840) v else null
    }

    fun offsetText(minutes: Int): String {
        val a = kotlin.math.abs(minutes)
        return (if (minutes < 0) "-" else "+") + (a / 60) + if (a % 60 != 0) ":" + (a % 60).toString().padStart(2, '0') else ""
    }

    /** "13:05" → 785, or null. */
    fun parseTime(text: String): Int? {
        val parts = text.trim().split(':', '.')
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return if (h in 0..23 && m in 0..59) h * 60 + m else null
    }

    fun timeText(minutes: Int) = (minutes / 60).toString().padStart(2, '0') + ":" + (minutes % 60).toString().padStart(2, '0')

    /** A tidy profile: lists cleaned, repeats dropped. */
    fun tidy(p: Profile): Profile = p.copy(
        socials = p.socials.map { Social(it.platform.trim().take(40), it.handle.trim().take(TEXT_MAX)) }.filter { it.handle.isNotEmpty() }.take(MAX_ITEMS),
        likes = clean(p.likes), dislikes = clean(p.dislikes),
        answers = p.answers.map { Answer(it.question.trim().take(TEXT_MAX), it.answer.trim().take(ANSWER_MAX)) }.filter { it.question.isNotEmpty() && it.answer.isNotEmpty() }.take(MAX_ITEMS),
        mbti = p.mbti.trim().uppercase().takeIf { it in mbtiTypes } ?: "",
        enneagram = p.enneagram.takeIf { it in 1..9 } ?: 0,
        enneagramWing = if (p.enneagram in 1..9 && p.enneagramWing in 1..9 && (p.enneagramWing - p.enneagram).let { it == 1 || it == -1 || it == 8 || it == -8 }) p.enneagramWing else 0,
        loveLanguages = p.loveLanguages.distinct().take(5), disc = p.disc.distinct().take(2),
    )
}

/** Where a person's birth date lives: the Birthday date record that has them, so the date and its reminder are kept once. */
fun LinkData.birthdayOf(personId: String): ImportantDate? = dates.firstOrNull { it.personId == personId && it.kind == DateKind.BIRTHDAY }

/** The birth date with its year, or null if there is no birthday or its year is unknown. */
fun ImportantDate.birthDate(): LocalDate? = year?.let { y -> runCatching { LocalDate(y, month, day) }.getOrNull() }
