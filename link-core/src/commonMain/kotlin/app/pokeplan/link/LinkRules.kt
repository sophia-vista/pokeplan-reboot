package app.pokeplan.link

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlin.io.encoding.Base64

sealed interface Check {
    data object Ok : Check
    data class Refused(val reason: String) : Check
}

enum class Sort { NAME, RECENT, MANUAL, GROUP }

object LinkRules {
    const val NAME_MAX = 80
    const val FIELD_MAX = 120
    const val NOTE_MAX = 4000
    const val TAGS_MAX = 10
    const val TAG_MAX = 24

    const val FOLLOW_UPS_PAID_PER_DAY = 3

    val REMIND_PRESETS = listOf(0, 1, 3, 7)

    const val DATES_SOON_DAYS = 30

    /** The words the main category offers first; the player can still write their own. */
    val STARTING_CATEGORIES = listOf("Family", "Friends", "Work", "School", "Services")

    /** The words a category could have held before 0.240.0 turned categories into groups, used once to give it back. */
    val LEGACY_CATEGORIES = STARTING_CATEGORIES + listOf("Friend", "Colleague", "Service provider")

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

    const val MEMORY_TITLE_MAX = 120

    /** The problem with a partial date, or null when it is a date the player could know. */
    fun dateProblem(d: PartialDate): String? {
        if (d.day != null && d.month == null) return "Choose a month with that day."
        if (d.month != null && d.year == null) return "Choose a year with that month."
        if (d.year != null && d.year !in 1000..2200) return "That year does not look right."
        if (d.month != null && d.month !in 1..12) return "Choose a month."
        if (d.day != null) {
            val month = d.month ?: return "Choose a month with that day."
            val limit = if (month == 2) (if (d.year != null) daysInMonth(d.year, 2) else 29) else daysInMonth(2001, month)
            if (d.day !in 1..limit) return "That day is not in that month."
        }
        return null
    }

    /**
     * A memory may be incomplete (owner's Sprint 1 list): a title, a note, a date or a photo alone is enough, so a
     * photo-only or date-only moment can be kept now and finished later. [photoCount] is how many photos the memory
     * will hold once the save lands. Place and time alone are not enough: they only describe something.
     */
    fun checkMemory(m: Memory, photoCount: Int = 0): Check {
        if (m.title.isBlank() && m.note.isBlank() && !m.date.known && photoCount <= 0) {
            return Check.Refused("Add a title, a note, a date or a photo.")
        }
        if (m.title.length > MEMORY_TITLE_MAX) return Check.Refused("That title is too long.")
        if (m.note.length > NOTE_MAX) return Check.Refused("That note is too long.")
        if (m.place.length > FIELD_MAX || m.time.length > 32) return Check.Refused("One of those lines is too long.")
        dateProblem(m.date)?.let { return Check.Refused(it) }
        return Check.Ok
    }

    /**
     * What to call a memory in a list or a picker when it may have no title: its title, else the first line of its
     * note, else its date, else what it holds. Never empty.
     */
    fun memoryLabel(m: Memory, photoCount: Int = 0): String {
        m.title.trim().takeIf { it.isNotEmpty() }?.let { return it }
        m.note.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.let { return if (it.length > 60) it.take(59).trimEnd() + "…" else it }
        if (m.date.year != null) return dateLabel(m.date)
        return if (photoCount > 0) "Photo memory" else "Untitled memory"
    }

    fun checkEra(e: Era): Check {
        (checkName(e.title, "era") as? Check.Refused)?.let { return it }
        if (e.note.length > NOTE_MAX) return Check.Refused("That note is too long.")
        dateProblem(e.start)?.let { return Check.Refused(it) }
        dateProblem(e.end)?.let { return Check.Refused(it) }
        if (e.start.year != null && e.end.year != null && dateKey(e.start) > dateKey(e.end)) return Check.Refused("The start is after the end.")
        return Check.Ok
    }

    /** A number that orders partial dates by what they know; a blank date sorts after every known one. */
    fun dateKey(d: PartialDate): Int = (d.year ?: 0) * 10_000 + (d.month ?: 0) * 100 + (d.day ?: 0)

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** A partial date as the player knows it ("14 Mar 2024", "Mar 2024", "2024", "Around 2024", "Unknown date"). */
    fun dateLabel(d: PartialDate): String {
        val year = d.year ?: return "Unknown date"
        val body = when {
            d.month == null -> "$year"
            d.day == null -> "${MONTHS[d.month - 1]} $year"
            else -> "${d.day} ${MONTHS[d.month - 1]} $year"
        }
        return if (d.approximate) "Around $body" else body
    }

    fun eraRangeLabel(e: Era): String {
        val start = e.start.known
        val end = e.end.known
        return when {
            !start && !end -> "No dates"
            start && !end -> "${dateLabel(e.start)} - now"
            !start -> "Until ${dateLabel(e.end)}"
            else -> "${dateLabel(e.start)} - ${dateLabel(e.end)}"
        }
    }

    /** A chapter's latest known date: its end, or its start when it has no end; 0 when it knows neither. */
    fun eraKey(e: Era): Int = if (e.end.known) dateKey(e.end) else dateKey(e.start)

    /** Chapters newest first by their latest known date; chapters with no date come last, then by title. */
    fun eraOrder(eras: List<Era>): List<Era> = eras.sortedWith(compareByDescending<Era> { eraKey(it) }.thenBy { it.title.lowercase() })

    /** Memories newest first by what their date knows; a memory with no known year comes after the dated ones. */
    fun memoryOrder(memories: List<Memory>): List<Memory> =
        memories.sortedWith(
            compareByDescending<Memory> { it.date.year ?: Int.MIN_VALUE }
                .thenByDescending { it.date.month ?: 0 }
                .thenByDescending { it.date.day ?: 0 }
                .thenByDescending { it.createdMillis },
        )

    /** The timeline's years, newest first, with the memories that know no year last. */
    fun yearGroups(memories: List<Memory>): List<Pair<Int?, List<Memory>>> {
        val groups = linkedMapOf<Int?, MutableList<Memory>>()
        memoryOrder(memories).forEach { groups.getOrPut(it.date.year) { mutableListOf() } += it }
        return groups.entries.sortedWith(compareBy<Map.Entry<Int?, MutableList<Memory>>> { it.key == null }.thenByDescending { it.key ?: 0 })
            .map { it.key to it.value.toList() }
    }

    fun memoriesOf(data: LinkData, personId: String): List<Memory> = memoryOrder(data.memories.filter { personId in it.personIds })

    fun memoriesInEra(data: LinkData, eraId: String): List<Memory> = memoryOrder(data.memories.filter { eraId in it.eraIds })

    /** The memories from this calendar day in an earlier year, newest first. */
    fun onThisDay(data: LinkData, today: LocalDate): List<Memory> = memoryOrder(
        data.memories.filter { m ->
            m.date.year != null && m.date.month == today.monthNumber && m.date.day == today.dayOfMonth && m.date.year < today.year
        },
    )

    /** The latest day the player recorded a memory with this person, if any memory knows its full date. */
    fun metUpOf(data: LinkData, personId: String): LocalDate? = memoryOrder(memoriesOf(data, personId))
        .firstOrNull { it.date.year != null && it.date.month != null && it.date.day != null }
        ?.let { LocalDate(it.date.year!!, it.date.month!!, it.date.day!!) }

    const val PHOTO_CAPTION_MAX = 200
    const val PHOTOS_PER_MEMORY_MAX = 9

    /** The picker keeps an image at or under this many base64 characters (the same budget as MINT's receive QR). */
    const val PHOTO_MAX_CHARS = 1_400_000

    val PHOTO_MIMES = listOf("image/png", "image/jpeg", "image/webp")

    fun photosOf(data: LinkData, memoryId: String): List<MemoryPhoto> =
        data.photos.filter { it.memoryId == memoryId }.sortedBy { it.addedMillis }

    /** Every photo that belongs to a memory naming this person, in the order it was added. */
    fun photosOfPerson(data: LinkData, personId: String): List<MemoryPhoto> {
        val memories = data.memories.filter { personId in it.personIds }.map { it.id }.toSet()
        return data.photos.filter { it.memoryId in memories }.sortedBy { it.addedMillis }
    }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    fun checkPhoto(p: MemoryPhoto, existing: Int = 0): Check {
        if (existing >= PHOTOS_PER_MEMORY_MAX) return Check.Refused("A memory keeps up to $PHOTOS_PER_MEMORY_MAX photos.")
        if (p.caption.length > PHOTO_CAPTION_MAX) return Check.Refused("That caption is too long.")
        if (p.mime !in PHOTO_MIMES) return Check.Refused("That is not a picture LINK keeps.")
        if (p.data.isBlank()) return Check.Refused("That picture is empty.")
        if (p.data.length > PHOTO_MAX_CHARS) return Check.Refused("That picture is too big to keep.")
        val bytes = runCatching { Base64.decode(p.data) }.getOrNull() ?: return Check.Refused("That picture could not be read.")
        if (bytes.isEmpty()) return Check.Refused("That picture is empty.")
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
            Sort.GROUP -> found.sortedWith(compareBy<Person> { groupRank(data, it) }.thenBy { displayName(it).lowercase() })
        }
        return ordered.filter { it.isMe } + ordered.filterNot { it.isMe }
    }

    /** The first group the person is in, in the player's group order, or null when they are in none. */
    fun groupOf(data: LinkData, p: Person): String? = firstGroupIndex(data, p)?.let { data.settings.groups[it].name }

    private fun firstGroupIndex(data: LinkData, p: Person): Int? =
        p.groupIds.mapNotNull { id -> data.settings.groups.indexOfFirst { it.id == id }.takeIf { it >= 0 } }.minOrNull()

    /** Where the person's first group sits in the player's group order; people in no group come after every group. */
    private fun groupRank(data: LinkData, p: Person): Int = firstGroupIndex(data, p) ?: Int.MAX_VALUE

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
     * Gives the main category back to people a group replaced it for: 0.240.0 turned the old single category into a group
     * once and cleared it. On the first run of this version, a person with no category who is in exactly one group named
     * like a category ("Family", "Friends", "Friend", ...) takes that word as their main category and keeps the group.
     * A person in two such groups, or in none, is left without one. Runs once (the settings flag records it), so a
     * category the player later clears stays cleared.
     */
    fun withCategoriesBack(data: LinkData): LinkData {
        if (data.settings.categoriesRestored) return data
        val people = data.people.map { p ->
            if (p.isMe || p.category.isNotBlank()) p
            else {
                val names = groupNames(data, p).filter { n -> LEGACY_CATEGORIES.any { it.equals(n, ignoreCase = true) } }.distinctBy { it.lowercase() }
                if (names.size == 1) p.copy(category = names.single()) else p
            }
        }
        return data.copy(people = people, settings = data.settings.copy(categoriesRestored = true))
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
        data.memories.filter { m -> has(m.title, m.note, m.place, *m.personIds.map(::person).toTypedArray()) }
            .forEach { m -> hits += Hit(HitKind.MEMORY, m.id, memoryLabel(m, photosOf(data, m.id).size), memoryContext(data, m)) }
        data.eras.filter { has(it.title, it.note) }.forEach { e -> hits += Hit(HitKind.ERA, e.id, e.title, eraRangeLabel(e)) }
        data.photos.filter { it.caption.isNotBlank() && has(it.caption) }.forEach { p ->
            val memory = data.memories.firstOrNull { it.id == p.memoryId } ?: return@forEach
            hits += Hit(HitKind.PHOTO, memory.id, p.caption.take(80), memoryLabel(memory, photosOf(data, memory.id).size))
        }
        return hits.sortedWith(compareBy<Hit> { it.kind.ordinal }.thenBy { it.title.lowercase() })
    }

    /** The line that tells two memories apart in a list: its date, its place and who was there. */
    fun memoryContext(data: LinkData, m: Memory): String {
        val people = m.personIds.mapNotNull { id -> data.people.firstOrNull { it.id == id }?.let(::displayName) }.joinToString(", ")
        return listOf(if (m.date.year != null) dateLabel(m.date) else "", m.place, people).filter { it.isNotBlank() }.joinToString(" · ")
    }

    /** Whether the archive holds anything at all, so LINK shows its search: a memory-only archive counts. */
    fun hasRecords(data: LinkData): Boolean =
        data.people.isNotEmpty() || data.dates.isNotEmpty() || data.followUps.isNotEmpty() || data.memories.isNotEmpty() || data.eras.isNotEmpty()
}
