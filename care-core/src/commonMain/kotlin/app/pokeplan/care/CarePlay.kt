package app.pokeplan.care

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Care Play and reunions (docs/pokelife `MODULES/CARE.md` sections 4 and 6.7). Care Play is time spent with a Pokémon: the buddy, a
 * visitor or any owned one invited from the collection, with no quota and no neglect. Each Pokémon likes two ways of being cared for,
 * a primary and a secondary, found out by trying; nothing is wrong, and nothing is lost by not trying. A reunion is a befriended
 * visitor dropping by to say hello: a social moment that takes no meadow place and pays nothing.
 */

/** The short activities, each one a kind of care ([CareAction]) in the spec's words (grooming, toy play, treats, encouragement, gentle touch, shared quiet time). */
@Serializable
enum class PlayActivity(val label: String, val action: CareAction, val taps: Int, val verb: String) {
    GROOMING("Grooming", CareAction.SERVICE, 8, "Brush gently"),
    TOY_PLAY("Toy play", CareAction.TIME, 10, "Wave the toy"),
    TREAT("A treat", CareAction.GIFT, 5, "Offer it"),
    ENCOURAGEMENT("Cheering on", CareAction.WORDS, 6, "Cheer"),
    GENTLE_TOUCH("Gentle touch", CareAction.TOUCH, 8, "Pat softly"),
    QUIET_TIME("Quiet time", CareAction.TIME, 4, "Sit together"),
}

/** How a Pokémon took an activity. No reaction is a wrong answer. */
enum class PlayReaction(val line: String) {
    DELIGHTED("is delighted. This is its favourite."),
    HAPPY("is happy. It likes this too."),
    PLEASED("enjoys the time together."),
}

/** What the player has found out about one Pokémon, and when time with it last counted. Kept per owned Pokémon (or visitor) id. */
@Serializable
data class PlayRecord(
    val discovered: Set<CareAction> = emptySet(),
    val lastPaidDay: LocalDate? = null,
    val times: Int = 0,
)

/** A visitor the player befriended: kept so it can come back to say hello (never a regular visitor again, never another egg). */
@Serializable
data class Friend(
    val speciesId: Int,
    val formIdentifier: String,
    val visitorId: String,
    val befriendedDay: LocalDate,
    /** The visitor itself joined (rather than leaving an egg). */
    val joined: Boolean = false,
)

/** A befriended visitor that has come back and has not been greeted yet. It waits as long as it takes and holds no meadow place. */
@Serializable
data class Reunion(val friend: Friend, val arrivedDay: LocalDate)

object CarePlay {
    /** Time with a Pokémon pays at most once per Pokémon per day; further time is free to enjoy and pays nothing. */
    const val PAID_PER_POKEMON_PER_DAY = 1

    private fun hash(text: String): Long = text.fold(7L) { h, c -> 131 * h + c.code }

    /** The primary and the secondary way [key] (an owned Pokémon's id or a visitor's) likes best: the same every time, and different from each other. */
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

    /** What the player knows of [key]'s likes: only what it has shown. */
    fun discovered(record: PlayRecord?): Set<CareAction> = record?.discovered.orEmpty()

    /** Whether time with [key] on [day] would pay: the first time that day. */
    fun pays(record: PlayRecord?, day: LocalDate): Boolean = record?.lastPaidDay != day

    /** After doing [activity] with [key]: the record with the preference it showed (a primary or secondary one) now known, and the day marked when [paid]. */
    fun played(record: PlayRecord?, key: String, activity: PlayActivity, day: LocalDate, paid: Boolean): PlayRecord {
        val base = record ?: PlayRecord()
        val shown = if (reaction(key, activity) != PlayReaction.PLEASED) base.discovered + activity.action else base.discovered
        return base.copy(discovered = shown, lastPaidDay = if (paid) day else base.lastPaidDay, times = base.times + 1)
    }

    /** A suggestion that never expires: an activity the Pokémon has not shown a preference for yet, else any (the same for the same day and Pokémon). */
    fun suggestion(key: String, record: PlayRecord?, day: LocalDate): PlayActivity {
        val unknown = PlayActivity.entries.filter { it.action !in discovered(record) }.ifEmpty { PlayActivity.entries }
        return unknown[Random(hash("$key:${day.toEpochDays()}")).nextInt(unknown.size)]
    }

    /** "Surprise me": any activity, the same for the same Pokémon and moment. */
    fun surprise(key: String, seed: Long): PlayActivity = PlayActivity.entries[Random(hash(key) + seed).nextInt(PlayActivity.entries.size)]
}

/** Befriended visitors coming back. */
object Reunions {
    /** The chance of a reunion on a day when none is waiting. *(tune)* */
    const val DAILY_PERCENT = 20

    /** A friend may return again only after this many days (so the same face does not keep coming). *(tune)* */
    const val MIN_DAYS_BETWEEN_VISITS = 3

    /**
     * The reunion for [today], or null: none while one waits to be greeted, none for a day already rolled, one in [DAILY_PERCENT] days
     * from the friends, never the one that came last just now. The same for the same device [seed] and day. [lastSeen] maps a friend's visitor
     * id to the last day it returned.
     */
    fun roll(seed: Int, today: LocalDate, friends: List<Friend>, waiting: Reunion?, lastSeen: Map<String, LocalDate>): Reunion? {
        if (waiting != null || friends.isEmpty()) return null
        val rng = Random(seed.toLong() * 53 + today.toEpochDays())
        if (rng.nextInt(100) >= DAILY_PERCENT) return null
        val ready = friends.filter { f -> lastSeen[f.visitorId]?.let { today.toEpochDays() - it.toEpochDays() >= MIN_DAYS_BETWEEN_VISITS } != false }
        if (ready.isEmpty()) return null
        return Reunion(ready[rng.nextInt(ready.size)], today)
    }

    /** What the friend says on greeting: it remembers the first visit. */
    fun greeting(reunion: Reunion, speciesName: String): String {
        val f = reunion.friend
        val then = "${f.befriendedDay.dayOfMonth} ${f.befriendedDay.month.name.lowercase().replaceFirstChar(Char::uppercase)}"
        return if (f.joined) "$speciesName remembers the day it came to stay ($then) and wanted to say hello."
        else "$speciesName remembers the visit on $then, when you became friends, and came by to say hello."
    }
}
