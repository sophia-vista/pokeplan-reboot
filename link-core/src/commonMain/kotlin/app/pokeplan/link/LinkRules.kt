package app.pokeplan.link

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

sealed interface Check {
    data object Ok : Check
    data class Refused(val reason: String) : Check
}

enum class Sort { NAME, RECENT, MANUAL }

object LinkRules {
    const val NAME_MAX = 80
    const val FIELD_MAX = 120
    const val NOTE_MAX = 4000
    const val TAGS_MAX = 10
    const val TAG_MAX = 24

    const val FOLLOW_UPS_PAID_PER_DAY = 3

    val REMIND_PRESETS = listOf(0, 1, 3, 7)

    const val DATES_SOON_DAYS = 30

    val STARTING_CATEGORIES = listOf("Family", "Friend", "Colleague", "Service provider")

    /** Suggested first groups; none is made until the player taps one. */
    val STARTING_GROUPS = STARTING_CATEGORIES

    const val GROUPS_MAX = 30

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

    fun isLeap(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

    fun daysInMonth(year: Int, month: Int): Int = when (month) {
        2 -> if (isLeap(year)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    fun observed(month: Int, day: Int, year: Int): LocalDate =
        LocalDate(year, month, if (month == 2 && day == 29 && !isLeap(year)) 28 else day)

    fun checkDate(d: ImportantDate): Check {
        (checkName(d.title, "date") as? Check.Refused)?.let { return it }
        if (d.month !in 1..12) return Check.Refused("Choose a month.")
        val limit = if (d.month == 2) (if (d.year != null) daysInMonth(d.year, 2) else 29) else daysInMonth(2001, d.month)
        if (d.day !in 1..limit) return Check.Refused("That day is not in that month.")
        if (d.year != null && d.year !in 1900..2200) return Check.Refused("That year does not look right.")
        if (!d.annual && d.year == null) return Check.Refused("A one-time date needs its year.")
        if (d.note.length > NOTE_MAX) return Check.Refused("That note is too long.")
        return Check.Ok
    }

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

    fun upcoming(data: LinkData, today: LocalDate, withinDays: Int = DATES_SOON_DAYS): List<Upcoming> =
        data.dates.mapNotNull { next(it, today) }.filter { it.daysAway <= withinDays }.sortedWith(compareBy<Upcoming> { it.daysAway }.thenBy { it.date.title.lowercase() })

    fun allDates(data: LinkData, today: LocalDate): List<Pair<ImportantDate, Upcoming?>> {
        val withNext = data.dates.map { it to next(it, today) }
        val coming = withNext.filter { it.second != null }.sortedWith(compareBy<Pair<ImportantDate, Upcoming?>> { it.second!!.daysAway }.thenBy { it.first.title.lowercase() })
        val past = withNext.filter { it.second == null }.sortedByDescending { observed(it.first.month, it.first.day, it.first.year ?: 0) }
        return coming + past
    }

    fun inDays(n: Int): String = when (n) { 0 -> "today"; 1 -> "tomorrow"; else -> "in $n days" }

    fun state(f: FollowUp, today: LocalDate): FollowUpState = when (f.status) {
        FollowUpStatus.COMPLETED -> FollowUpState.COMPLETED
        FollowUpStatus.CANCELLED -> FollowUpState.CANCELLED
        FollowUpStatus.OPEN -> if (f.due != null && f.due <= today) FollowUpState.DUE else FollowUpState.UPCOMING
    }

    fun open(data: LinkData, today: LocalDate, personId: String? = null): List<FollowUp> =
        data.followUps.filter { it.status == FollowUpStatus.OPEN && (personId == null || it.personId == personId) }
            .sortedWith(compareBy<FollowUp> { it.due == null }.thenBy { it.due ?: today }.thenBy { it.createdMillis })

    fun history(data: LinkData, personId: String? = null): List<FollowUp> =
        data.followUps.filter { it.status != FollowUpStatus.OPEN && (personId == null || it.personId == personId) }
            .sortedByDescending { it.completedMillis ?: it.updatedMillis }

    fun pays(data: LinkData, f: FollowUp, day: LocalDate): Boolean =
        data.settings.postcardTrail && !f.paid && !f.converted && data.followUps.count { it.paid && it.completedDay == day } < FOLLOW_UPS_PAID_PER_DAY

    const val SAFARI_PEOPLE_CAP = 20

    fun safariSpread(data: LinkData): Int = data.people.count { !it.isMe }.coerceAtMost(SAFARI_PEOPLE_CAP)

    fun safariRef(personId: String): String {
        var h = -3750763034362895579L
        for (c in "safari:$personId") { h = h xor c.code.toLong(); h *= 1099511628211L }
        return h.toULong().toString(16)
    }

    fun displayName(p: Person): String = p.preferred.ifBlank { p.name }

    fun directory(
        data: LinkData, query: String = "", category: String? = null, tag: String? = null, favoritesOnly: Boolean = false, sort: Sort = Sort.NAME,
        group: String? = null,
    ): List<Person> {
        val q = query.trim().lowercase()
        val found = data.people.filter { p ->
            (group == null || group in p.groupIds) &&
                (category == null || p.category.equals(category, ignoreCase = true)) &&
                (tag == null || p.tags.any { it.equals(tag, ignoreCase = true) }) &&
                (!favoritesOnly || p.favorite) &&
                (q.isEmpty() || matches(p, q))
        }
        val ordered = when (sort) {
            Sort.RECENT -> found.sortedByDescending { it.updatedMillis }
            Sort.MANUAL -> manual(data, found)
            Sort.NAME -> found.sortedBy { displayName(it).lowercase() }
        }
        return ordered.filter { it.isMe } + ordered.filterNot { it.isMe }
    }

    /** The player's own order: listed people first in their listed order, the rest by name. Only the player moves anyone. */
    private fun manual(data: LinkData, people: List<Person>): List<Person> {
        val rank = data.settings.order.withIndex().associate { it.value to it.index }
        return people.sortedWith(compareBy<Person> { rank[it.id] ?: Int.MAX_VALUE }.thenBy { displayName(it).lowercase() })
    }

    /** The full "My order" list: the saved order for people who still exist, then everyone else by name. */
    fun fullOrder(data: LinkData): List<String> = manual(data, data.people).map { it.id }

    /**
     * The saved order after moving [id] one place up (`-1`) or down (`+1`) among [visible] (the people the screen
     * shows, in screen order): it swaps places with its neighbour there and everyone else keeps their place.
     */
    fun moved(data: LinkData, visible: List<String>, id: String, step: Int): List<String> {
        val full = fullOrder(data).toMutableList()
        val at = visible.indexOf(id)
        val to = at + step
        if (at < 0 || to !in visible.indices || step == 0) return full
        val a = full.indexOf(id)
        val b = full.indexOf(visible[to])
        if (a < 0 || b < 0) return full
        full[a] = visible[to]
        full[b] = id
        return full
    }

    fun checkGroup(data: LinkData, name: String, exceptId: String? = null): Check = when {
        name.isBlank() -> Check.Refused("Give the group a name.")
        name.trim().length > TAG_MAX -> Check.Refused("That group name is too long.")
        data.settings.groups.any { it.id != exceptId && it.name.equals(name.trim(), ignoreCase = true) } -> Check.Refused("There is already a group with that name.")
        exceptId == null && data.settings.groups.size >= GROUPS_MAX -> Check.Refused("That is plenty of groups.")
        else -> Check.Ok
    }

    fun groupNames(data: LinkData, p: Person): List<String> =
        p.groupIds.mapNotNull { id -> data.settings.groups.firstOrNull { it.id == id }?.name }

    /**
     * Turns the older single category into groups, once: every distinct category (and each one the player added) becomes a
     * group, and each person keeps the group of their category. "Me" is not a group. Safe to run again: it changes nothing
     * when no person has a category left and no category is waiting.
     */
    fun withGroupsFromCategories(data: LinkData): LinkData {
        val wanted = (data.people.filter { !it.isMe }.map { it.category } + data.settings.categories).map { it.trim() }.filter { it.isNotEmpty() }
        if (wanted.isEmpty() && data.people.none { it.category.isNotBlank() && !it.isMe }) return data
        val groups = data.settings.groups.toMutableList()
        fun groupFor(name: String): LinkGroup {
            groups.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return it }
            val slug = name.lowercase().filter { it.isLetterOrDigit() }.take(20).ifEmpty { "group" }
            var id = "g-$slug"
            var n = 2
            while (groups.any { it.id == id }) { id = "g-$slug-$n"; n++ }
            return LinkGroup(id, name.take(TAG_MAX)).also { groups += it }
        }
        wanted.forEach { groupFor(it) }
        val people = data.people.map { p ->
            if (p.isMe || p.category.isBlank()) p
            else p.copy(groupIds = (p.groupIds + groupFor(p.category.trim()).id).distinct(), category = "")
        }
        return data.copy(people = people, settings = data.settings.copy(groups = groups.toList(), categories = emptyList()))
    }

    fun categories(data: LinkData): List<String> =
        (STARTING_CATEGORIES + data.settings.categories + data.people.map { it.category }).filter { it.isNotBlank() }.distinctBy { it.lowercase() }

    private fun digits(s: String) = s.filter { it.isDigit() }

    private fun matches(p: Person, q: String): Boolean {
        if (listOf(p.name, p.preferred, p.organization, p.role, p.category, p.email, p.address, p.phone, p.tags.joinToString(" ")).any { it.lowercase().contains(q) }) return true
        val pr = p.profile
        if ((pr.socials.flatMap { listOf(it.platform, it.handle) } + pr.likes + pr.dislikes + pr.answers.map { it.answer } + pr.mbti).any { it.lowercase().contains(q) }) return true
        val d = digits(q)
        return d.length >= 3 && digits(p.phone).contains(d)
    }

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
