package app.pokeplan.link

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val socials: List<Social> = emptyList(),
    val likes: List<String> = emptyList(),
    val dislikes: List<String> = emptyList(),
    val answers: List<Answer> = emptyList(),
    val mbti: String = "",
    val enneagram: Int = 0,
    val enneagramWing: Int = 0,
    val loveLanguages: List<LoveLanguage> = emptyList(),
    val attachment: Attachment? = null,
    val disc: List<Disc> = emptyList(),
    val bigFive: Map<Trait, Level> = emptyMap(),
    val birth: Birth = Birth(),
)

@Serializable
data class Social(val platform: String, val handle: String)

@Serializable
data class Answer(val question: String, val answer: String)

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

object ProfileKit {
    val socialPlatforms = listOf("Instagram", "Facebook", "TikTok", "X", "Snapchat", "Discord", "YouTube", "LinkedIn", "Telegram", "Messenger", "Website")

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

    fun clean(items: List<String>): List<String> = items.map { it.trim().take(TEXT_MAX) }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(MAX_ITEMS)

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

    fun parseTime(text: String): Int? {
        val parts = text.trim().split(':', '.')
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return if (h in 0..23 && m in 0..59) h * 60 + m else null
    }

    fun timeText(minutes: Int) = (minutes / 60).toString().padStart(2, '0') + ":" + (minutes % 60).toString().padStart(2, '0')

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

fun LinkData.birthdayOf(personId: String): ImportantDate? = dates.firstOrNull { it.personId == personId && it.kind == DateKind.BIRTHDAY }

fun ImportantDate.birthDate(): LocalDate? = year?.let { y -> runCatching { LocalDate(y, month, day) }.getOrNull() }
