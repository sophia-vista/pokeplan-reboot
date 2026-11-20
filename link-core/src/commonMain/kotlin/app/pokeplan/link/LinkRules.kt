package app.pokeplan.link

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** Why a record was not accepted, in words for the screen. */
sealed interface Check {
    data object Ok : Check
    data class Refused(val reason: String) : Check
}

enum class Sort { NAME, RECENT }

/**
 * LINK's rules (docs/pokelife `MODULES/LINK.md`): pure and free of the clock and of the game. They never rank a person, never count contact, and never
 * judge a relationship; they only keep records valid, work out what is due or coming from a date, search, and say whether a completed follow-up may
 * be paid (Postcard Trail, section 9).
 */
object LinkRules {
    const val NAME_MAX = 80
    const val FIELD_MAX = 120
    const val NOTE_MAX = 4000
    const val TAGS_MAX = 10
    const val TAG_MAX = 24

    /** The most follow-ups Postcard Trail pays in one day *(tune)*: a backstop so making and ticking small items is never a way to earn (LINK.md 9.4). */
    const val FOLLOW_UPS_PAID_PER_DAY = 3

    /** The days-before choices a date's reminder offers (LINK.md 5): the day itself, one day, three days or a week before. */
    val REMIND_PRESETS = listOf(0, 1, 3, 7)

    /** How far ahead the Overview shows dates. */
    const val DATES_SOON_DAYS = 30

    /** The relationship words offered at the start; the user can add more and none is required (LINK.md 2.1). */
    val STARTING_CATEGORIES = listOf("Family", "Friend", "Colleague", "Service provider")

    // --- records -----------------------------------------------------------------------------------------------------------

    fun checkName(name: String, what: String = "person"): Check = when {
        name.isBlank() -> Check.Refused("Give the $what a name.")
        name.trim().length > NAME_MAX -> Check.Refused("That name is too long.")
        else -> Check.Ok
    }

    fun checkPerson(p: Person): Check {
        (checkName(p.name) as? Check.Refused)?.let { return it }
        if (listOf(p.preferred, p.phone, p.email, p.organization, p.role, p.address, p.category).any { it.length > FIELD_MAX }) return Check.Refused("One of those lines is too long.")
        if (p.tags.size > TAGS_MAX || p.tags.any { it.length > TAG_MAX }) return Check.Refused("Keep to $TAGS_MAX short tags.")
        return Check.Ok
    }

    /** Tags as typed: trimmed, no empties, no duplicates (case ignored), at most [TAGS_MAX]. */
    fun tags(text: String): List<String> =
        text.split(',', '#', '\n').map { it.trim().take(TAG_MAX) }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(TAGS_MAX)

    fun checkNote(text: String): Check = when {
        text.isBlank() -> Check.Refused("Write something first.")
        text.length > NOTE_MAX -> Check.Refused("That note is too long.")
        else -> Check.Ok
    }

    fun checkFollowUp(f: FollowUp): Check {
        (checkName(f.title, "follow-up") as? Check.Refused)?.let { return it }
        if (f.note.length > NOTE_MAX) return Check.Refused("That note is too long.")
        return Check.Ok
    }

    // --- important dates ---------------------------------------------------------------------------------------------------

    fun isLeap(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

    fun daysInMonth(year: Int, month: Int): Int = when (month) {
        2 -> if (isLeap(year)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    /**
     * The rule for February 29 (LINK.md 2.3): a date kept as 29 February is **observed on 28 February** in a year with no 29th, and on the 29th in a
     * leap year. Every other date is observed on its own day.
     */
    fun observed(month: Int, day: Int, year: Int): LocalDate =
        LocalDate(year, month, if (month == 2 && day == 29 && !isLeap(year)) 28 else day)

    fun checkDate(d: ImportantDate): Check {
        (checkName(d.title, "date") as? Check.Refused)?.let { return it }
        if (d.month !in 1..12) return Check.Refused("Choose a month.")
        // The 29th of February is allowed without a year (it is kept as it is), and with a year only when that year has one.
        val limit = if (d.month == 2) (if (d.year != null) daysInMonth(d.year, 2) else 29) else daysInMonth(2001, d.month)
        if (d.day !in 1..limit) return Check.Refused("That day is not in that month.")
        if (d.year != null && d.year !in 1900..2200) return Check.Refused("That year does not look right.")
        if (!d.annual && d.year == null) return Check.Refused("A one-time date needs its year.")
        if (d.note.length > NOTE_MAX) return Check.Refused("That note is too long.")
        return Check.Ok
    }

    /**
     * The next time [d] is observed on or after [today]: an annual date this year or the next, a one-time date only if it has not passed (null once it
     * has). [Upcoming.years] is the age or anniversary number it marks when the year is known.
     */
    fun next(d: ImportantDate, today: LocalDate): Upcoming? {
        val year = d.year
        if (!d.annual) {
            val day = observed(d.month, d.day, year ?: return null)
            return if (day < today) null else Upcoming(d, day, today.daysUntil(day), null)
        }
        var y = today.year
        var day = observed(d.month, d.day, y)
        if (day < today) { y += 1; day = observed(d.month, d.day, y) }
        return Upcoming(d, day, today.daysUntil(day), year?.let { y - it }?.takeIf { it > 0 })
    }

    /** Dates coming within [withinDays] (default [DATES_SOON_DAYS]), soonest first, ties by title. */
    fun upcoming(data: LinkData, today: LocalDate, withinDays: Int = DATES_SOON_DAYS): List<Upcoming> =
        data.dates.mapNotNull { next(it, today) }.filter { it.daysAway <= withinDays }.sortedWith(compareBy<Upcoming> { it.daysAway }.thenBy { it.date.title.lowercase() })

    /** Every date, in the order it next comes (a one-time date already past comes last, newest first). */
    fun allDates(data: LinkData, today: LocalDate): List<Pair<ImportantDate, Upcoming?>> {
        val withNext = data.dates.map { it to next(it, today) }
        val coming = withNext.filter { it.second != null }.sortedWith(compareBy<Pair<ImportantDate, Upcoming?>> { it.second!!.daysAway }.thenBy { it.first.title.lowercase() })
        val past = withNext.filter { it.second == null }.sortedByDescending { observed(it.first.month, it.first.day, it.first.year ?: 0) }
        return coming + past
    }

    /** "today", "tomorrow", "in 5 days". */
    fun inDays(n: Int): String = when (n) { 0 -> "today"; 1 -> "tomorrow"; else -> "in $n days" }

    // --- follow-ups --------------------------------------------------------------------------------------------------------

    /** A follow-up's state today: due once its day has come (it stays Due, with no escalation), upcoming before that or with no day. */
    fun state(f: FollowUp, today: LocalDate): FollowUpState = when (f.status) {
        FollowUpStatus.COMPLETED -> FollowUpState.COMPLETED
        FollowUpStatus.CANCELLED -> FollowUpState.CANCELLED
        FollowUpStatus.OPEN -> if (f.due != null && f.due <= today) FollowUpState.DUE else FollowUpState.UPCOMING
    }

    /** The open follow-ups, those with a day first (soonest first), then the ones with none, oldest first. */
    fun open(data: LinkData, today: LocalDate, personId: String? = null): List<FollowUp> =
        data.followUps.filter { it.status == FollowUpStatus.OPEN && (personId == null || it.personId == personId) }
            .sortedWith(compareBy<FollowUp> { it.due == null }.thenBy { it.due ?: today }.thenBy { it.createdMillis })

    /** Completed and cancelled follow-ups, the latest first: a factual record, never a score (LINK.md 2.2). */
    fun history(data: LinkData, personId: String? = null): List<FollowUp> =
        data.followUps.filter { it.status != FollowUpStatus.OPEN && (personId == null || it.personId == personId) }
            .sortedByDescending { it.completedMillis ?: it.updatedMillis }

    /**
     * Whether completing [f] on [day] may be paid by Postcard Trail (LINK.md 9.2): it is on, this follow-up was never paid, and today's share is not used.
     * Cancelling, reopening, editing and rescheduling never pay.
     */
    fun pays(data: LinkData, f: FollowUp, day: LocalDate): Boolean =
        data.settings.postcardTrail && !f.paid && !f.converted && data.followUps.count { it.paid && it.completedDay == day } < FOLLOW_UPS_PAID_PER_DAY

    // --- people ------------------------------------------------------------------------------------------------------------

    fun displayName(p: Person): String = p.preferred.ifBlank { p.name }

    /** The directory (LINK.md 2.1): filtered, then sorted by name or by what changed last. Never ranked by anything else. */
    fun directory(
        data: LinkData, query: String = "", category: String? = null, tag: String? = null, favoritesOnly: Boolean = false, sort: Sort = Sort.NAME,
    ): List<Person> {
        val q = query.trim().lowercase()
        val found = data.people.filter { p ->
            (category == null || p.category.equals(category, ignoreCase = true)) &&
                (tag == null || p.tags.any { it.equals(tag, ignoreCase = true) }) &&
                (!favoritesOnly || p.favorite) &&
                (q.isEmpty() || matches(p, q))
        }
        // The user's own card is pinned first, whatever the sort; it is a place, not a rank.
        val ordered = if (sort == Sort.RECENT) found.sortedByDescending { it.updatedMillis } else found.sortedBy { displayName(it).lowercase() }
        return ordered.filter { it.isMe } + ordered.filterNot { it.isMe }
    }

    /** Every category in use or added, for the filter. */
    fun categories(data: LinkData): List<String> =
        (STARTING_CATEGORIES + data.settings.categories + data.people.map { it.category }).filter { it.isNotBlank() }.distinctBy { it.lowercase() }

    private fun digits(s: String) = s.filter { it.isDigit() }

    /** Whether a person's words contain [q] (already lower case): name, alias, organization, role, category, tags, email, address, and a phone number however it is spaced. */
    private fun matches(p: Person, q: String): Boolean {
        if (listOf(p.name, p.preferred, p.organization, p.role, p.category, p.email, p.address, p.phone, p.tags.joinToString(" ")).any { it.lowercase().contains(q) }) return true
        val pr = p.profile
        // The slambook details the user wrote are searchable too (LINK.md 2.1 "user-entered searchable detail"); a birth chart is never searched.
        if ((pr.socials.flatMap { listOf(it.platform, it.handle) } + pr.likes + pr.dislikes + pr.answers.map { it.answer } + pr.mbti).any { it.lowercase().contains(q) }) return true
        val d = digits(q)
        return d.length >= 3 && digits(p.phone).contains(d)
    }

    /**
     * People who may be the same as ([name], [phone], [email]), to suggest and never to merge (LINK.md 2.1): the same name or preferred name with case
     * ignored, the same phone digits (at least 7), or the same email. [exceptId] leaves out the person being edited.
     */
    fun possibleDuplicates(data: LinkData, name: String, phone: String = "", email: String = "", exceptId: String? = null): List<Person> {
        val n = name.trim().lowercase()
        val d = digits(phone)
        val e = email.trim().lowercase()
        return data.people.filter { p ->
            p.id != exceptId && (
                (n.isNotEmpty() && (p.name.trim().lowercase() == n || p.preferred.trim().lowercase() == n)) ||
                    (d.length >= 7 && digits(p.phone) == d) ||
                    (e.isNotEmpty() && p.email.trim().lowercase() == e)
                )
        }
    }

    // --- search ------------------------------------------------------------------------------------------------------------

    /** Every record whose words contain [text] (case ignored): people, notes, dates and follow-ups. A note's hit opens its person. Local and offline. */
    fun search(data: LinkData, text: String): List<Hit> {
        val q = text.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        fun has(vararg parts: String?): Boolean = parts.any { it != null && it.lowercase().contains(q) }
        fun person(id: String?): String? = id?.let { i -> data.people.firstOrNull { it.id == i }?.let(::displayName) }
        val hits = mutableListOf<Hit>()
        data.people.filter { matches(it, q) }
            .forEach { hits += Hit(HitKind.PERSON, it.id, displayName(it), it.organization.ifBlank { it.category }) }
        data.notes.filter { has(it.text) }.forEach { n -> hits += Hit(HitKind.NOTE, n.personId, person(n.personId) ?: "Note", n.text.lineSequence().first().take(80)) }
        data.dates.filter { has(it.title, it.note, person(it.personId)) }.forEach { hits += Hit(HitKind.DATE, it.id, it.title, person(it.personId).orEmpty()) }
        data.followUps.filter { has(it.title, it.note, person(it.personId)) }.forEach { hits += Hit(HitKind.FOLLOW_UP, it.id, it.title, person(it.personId).orEmpty()) }
        return hits.sortedWith(compareBy<Hit> { it.kind.ordinal }.thenBy { it.title.lowercase() })
    }
}
