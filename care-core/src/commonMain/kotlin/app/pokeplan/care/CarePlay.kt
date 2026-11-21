package app.pokeplan.care

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
enum class PlayActivity(val label: String, val action: CareAction, val taps: Int, val verb: String) {
    GROOMING("Grooming", CareAction.SERVICE, 8, "Brush gently"),
    TOY_PLAY("Toy play", CareAction.TIME, 10, "Wave the toy"),
    TREAT("A treat", CareAction.GIFT, 5, "Offer it"),
    ENCOURAGEMENT("Cheering on", CareAction.WORDS, 6, "Cheer"),
    GENTLE_TOUCH("Gentle touch", CareAction.TOUCH, 8, "Pat softly"),
    QUIET_TIME("Quiet time", CareAction.TIME, 4, "Sit together"),
}

enum class PlayReaction(val line: String) {
    DELIGHTED("is delighted. This is its favourite."),
    HAPPY("is happy. It likes this too."),
    PLEASED("enjoys the time together."),
}

@Serializable
data class PlayRecord(
    val discovered: Set<CareAction> = emptySet(),
    val lastPaidDay: LocalDate? = null,
    val times: Int = 0,
)

@Serializable
data class Friend(
    val speciesId: Int,
    val formIdentifier: String,
    val visitorId: String,
    val befriendedDay: LocalDate,
    val joined: Boolean = false,
)

@Serializable
data class Reunion(val friend: Friend, val arrivedDay: LocalDate)

object CarePlay {
    const val PAID_PER_POKEMON_PER_DAY = 1

    private fun hash(text: String): Long = text.fold(7L) { h, c -> 131 * h + c.code }

    fun preferences(key: String): Pair<CareAction, CareAction> {
        val all = CareAction.entries
        val primary = all[(hash("p:$key") and Long.MAX_VALUE).rem(all.size).toInt()]
        val rest = all.filter { it != primary }
        return primary to rest[(hash("s:$key") and Long.MAX_VALUE).rem(rest.size).toInt()]
    }

    fun reaction(key: String, activity: PlayActivity): PlayReaction {
        val (primary, secondary) = preferences(key)
        return when (activity.action) { primary -> PlayReaction.DELIGHTED; secondary -> PlayReaction.HAPPY; else -> PlayReaction.PLEASED }
    }

    fun discovered(record: PlayRecord?): Set<CareAction> = record?.discovered.orEmpty()

    fun pays(record: PlayRecord?, day: LocalDate): Boolean = record?.lastPaidDay != day

    fun played(record: PlayRecord?, key: String, activity: PlayActivity, day: LocalDate, paid: Boolean): PlayRecord {
        val base = record ?: PlayRecord()
        val shown = if (reaction(key, activity) != PlayReaction.PLEASED) base.discovered + activity.action else base.discovered
        return base.copy(discovered = shown, lastPaidDay = if (paid) day else base.lastPaidDay, times = base.times + 1)
    }

    fun suggestion(key: String, record: PlayRecord?, day: LocalDate): PlayActivity {
        val unknown = PlayActivity.entries.filter { it.action !in discovered(record) }.ifEmpty { PlayActivity.entries }
        return unknown[Random(hash("$key:${day.toEpochDays()}")).nextInt(unknown.size)]
    }

    fun surprise(key: String, seed: Long): PlayActivity = PlayActivity.entries[Random(hash(key) + seed).nextInt(PlayActivity.entries.size)]
}

object Reunions {
    const val DAILY_PERCENT = 20

    const val MIN_DAYS_BETWEEN_VISITS = 3

    fun roll(seed: Int, today: LocalDate, friends: List<Friend>, waiting: Reunion?, lastSeen: Map<String, LocalDate>): Reunion? {
        if (waiting != null || friends.isEmpty()) return null
        val rng = Random(seed.toLong() * 53 + today.toEpochDays())
        if (rng.nextInt(100) >= DAILY_PERCENT) return null
        val ready = friends.filter { f -> lastSeen[f.visitorId]?.let { today.toEpochDays() - it.toEpochDays() >= MIN_DAYS_BETWEEN_VISITS } != false }
        if (ready.isEmpty()) return null
        return Reunion(ready[rng.nextInt(ready.size)], today)
    }

    fun greeting(reunion: Reunion, speciesName: String): String {
        val f = reunion.friend
        val then = "${f.befriendedDay.dayOfMonth} ${f.befriendedDay.month.name.lowercase().replaceFirstChar(Char::uppercase)}"
        return if (f.joined) "$speciesName remembers the day it came to stay ($then) and wanted to say hello."
        else "$speciesName remembers the visit on $then, when you became friends, and came by to say hello."
    }
}
