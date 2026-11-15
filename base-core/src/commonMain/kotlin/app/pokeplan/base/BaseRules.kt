package app.pokeplan.base

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Why a record was not accepted, in words for the screen. */
sealed interface Check {
    data object Ok : Check
    data class Refused(val reason: String) : Check
}

/**
 * Repeating dates (BASE.md 4.2 and 4.5): pure, deterministic and free of the clock, so the same chore gives the same occurrences after a restart
 * or a change of time zone. Everything is counted from the first date, never from "today".
 */
object Schedules {
    private const val MAX_WEEK_SCAN = 7 * 100 + 7

    private fun monthIndex(d: LocalDate): Int = d.year * 12 + d.monthNumber - 1

    private fun daysInMonth(year: Int, month: Int): Int = LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth

    /** The date in month number [index] (year * 12 + month - 1) that follows [anchor]'s day of the month, shortened to the month's last day. */
    private fun inMonth(index: Int, anchor: LocalDate): LocalDate {
        val year = index.floorDiv(12)
        val month = index - year * 12 + 1
        return LocalDate(year, month, minOf(anchor.dayOfMonth, daysInMonth(year, month)))
    }

    private fun monthStep(r: Recurrence): Int = maxOf(1, r.every) * if (r.kind == RepeatKind.YEARLY) 12 else 1

    private fun weekdays(r: Recurrence, anchor: LocalDate): Set<Int> = r.weekdays.filter { it in 1..7 }.toSet().ifEmpty { setOf(anchor.dayOfWeek.isoDayNumber) }

    private fun mondayOf(d: LocalDate): LocalDate = d.minus(d.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

    /** Whether [day] is one of the occurrences of [r] counted from [anchor]. */
    fun occursOn(r: Recurrence, anchor: LocalDate, day: LocalDate): Boolean = when (r.kind) {
        RepeatKind.ONCE -> day == anchor
        RepeatKind.DAILY -> day >= anchor && anchor.daysUntil(day) % maxOf(1, r.every) == 0
        RepeatKind.WEEKLY -> day >= anchor && day.dayOfWeek.isoDayNumber in weekdays(r, anchor) &&
            (mondayOf(anchor).daysUntil(mondayOf(day)) / 7) % maxOf(1, r.every) == 0
        RepeatKind.MONTHLY, RepeatKind.YEARLY -> day >= anchor && (monthIndex(day) - monthIndex(anchor)) % monthStep(r) == 0 && day == inMonth(monthIndex(day), anchor)
    }

    /** The latest occurrence on or before [day], or null when [day] is before the first one. */
    fun lastOnOrBefore(r: Recurrence, anchor: LocalDate, day: LocalDate): LocalDate? {
        if (day < anchor) return null
        return when (r.kind) {
            RepeatKind.ONCE -> anchor
            RepeatKind.DAILY -> day.minus(anchor.daysUntil(day) % maxOf(1, r.every), DateTimeUnit.DAY)
            RepeatKind.WEEKLY -> generateSequence(day) { it.minus(1, DateTimeUnit.DAY) }.take(MAX_WEEK_SCAN).takeWhile { it >= anchor }.firstOrNull { occursOn(r, anchor, it) }
            RepeatKind.MONTHLY, RepeatKind.YEARLY -> {
                val step = monthStep(r)
                var k = (monthIndex(day) - monthIndex(anchor)).floorDiv(step)
                var c = inMonth(monthIndex(anchor) + k * step, anchor)
                if (c > day) { k -= 1; c = inMonth(monthIndex(anchor) + k * step, anchor) }
                if (k < 0) null else c
            }
        }
    }

    /** The earliest occurrence on or after [day], or null when a one-time schedule is already past. */
    fun firstOnOrAfter(r: Recurrence, anchor: LocalDate, day: LocalDate): LocalDate? {
        if (r.kind != RepeatKind.WEEKLY && day <= anchor) return anchor
        return when (r.kind) {
            RepeatKind.ONCE -> null
            RepeatKind.DAILY -> {
                val n = maxOf(1, r.every)
                day.plus((n - anchor.daysUntil(day) % n) % n, DateTimeUnit.DAY)
            }
            RepeatKind.WEEKLY -> generateSequence(maxOf(day, anchor)) { it.plus(1, DateTimeUnit.DAY) }.take(MAX_WEEK_SCAN).firstOrNull { occursOn(r, anchor, it) }
            RepeatKind.MONTHLY, RepeatKind.YEARLY -> {
                val step = monthStep(r)
                var k = -(-(monthIndex(day) - monthIndex(anchor))).floorDiv(step)
                var c = inMonth(monthIndex(anchor) + k * step, anchor)
                if (c < day) { k += 1; c = inMonth(monthIndex(anchor) + k * step, anchor) }
                c
            }
        }
    }

    /** The next occurrence strictly after [day]. */
    fun nextAfter(r: Recurrence, anchor: LocalDate, day: LocalDate): LocalDate? = firstOnOrAfter(r, anchor, day.plus(1, DateTimeUnit.DAY))
}

/** A quantity is kept in thousandths of its unit, so 1.5 kg is 1500 and nothing is ever a floating-point number. */
object Quantity {
    const val MAX_MILLI = 1_000_000_000L

    /** Reads "12", "1.5" or "0,25" (up to three decimals); null when it is not a quantity. */
    fun parse(text: String): Long? {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty() || t.count { it == '.' } > 1 || !t.all { it.isDigit() || it == '.' } || t == ".") return null
        val whole = t.substringBefore('.').ifEmpty { "0" }
        val frac = t.substringAfter('.', "")
        if (frac.length > 3 || whole.length > 9) return null
        val milli = whole.toLong() * 1000 + frac.padEnd(3, '0').toLong()
        return milli.takeIf { it <= MAX_MILLI }
    }

    /** "12", "1.5", "0.25"; null reads as "Unknown". */
    fun format(milli: Long?): String {
        if (milli == null) return "Unknown"
        val whole = milli / 1000
        val frac = (milli % 1000).toString().padStart(3, '0').trimEnd('0')
        return if (frac.isEmpty()) whole.toString() else "$whole.$frac"
    }
}

enum class WarrantyState(val label: String) { UNDATED("No end date"), ACTIVE("Covered"), ENDING("Ends soon"), EXPIRED("Expired") }

enum class DueState(val label: String) { OVERDUE("Overdue"), DUE("Due today"), SOON("Soon"), LATER("Later"), PAUSED("Paused") }

data class ScheduleView(val schedule: MaintenanceSchedule, val state: DueState, val daysUntil: Int)

enum class HitKind(val label: String) { CHORE("Chore"), SUPPLY("Supply"), ASSET("Belonging"), MAINTENANCE("Maintenance"), WARRANTY("Warranty"), REFERENCE("Note") }

/** One search result: what kind of record it is, its id, a title and a short second line. It opens the canonical record. */
data class Hit(val kind: HitKind, val id: String, val title: String, val detail: String = "")

/** One line of the restocking list: a supply that is low, or something the user wrote down by hand. */
sealed interface RestockLine {
    data class Low(val supply: Supply) : RestockLine
    data class Manual(val entry: RestockEntry, val supply: Supply?) : RestockLine
}

/** What the overview shows: only what needs attention (BASE.md 3.1), never a count of everything. */
data class Overview(val chores: List<ChoreView>, val maintenance: List<ScheduleView>, val low: List<Supply>, val warranties: List<Warranty>) {
    val needsAttention: Boolean get() = chores.isNotEmpty() || maintenance.isNotEmpty() || low.isNotEmpty() || warranties.isNotEmpty()
}

/**
 * HOME's rules, all pure. Principles (BASE.md 1, 4, 5.1): **nothing is judged** (a missed chore is shown, never counted against anyone, and an old
 * backlog is never listed), **unknown is not zero**, **derived facts are not stored** (overdue, low, due soon come from the records and the date),
 * and **only done work can earn** (what pays is decided here and carries no household word).
 */
object BaseRules {
    const val TITLE_MAX = 80
    const val NOTE_MAX = 2000
    const val TEXT_MAX = 8000
    const val EVERY_MAX = 99

    /** How many stock adjustments the document keeps (BASE.md 4.3: lightweight, bounded). */
    const val ADJUSTMENTS_KEPT = 300

    /** A warranty is "ending soon" this many days before its end date. */
    const val WARRANTY_SOON_DAYS = 30

    /** Maintenance shows as "soon" this many days ahead. */
    const val MAINTENANCE_SOON_DAYS = 14

    /** Chores paid in a day, so splitting one job into many small ones can not farm Habitat Discovery (BASE.md 9.2). *(tune)* */
    const val CHORES_PAID_PER_DAY = 5

    /** Pieces of maintenance paid in a day. *(tune)* */
    const val MAINTENANCE_PAID_PER_DAY = 2

    val STARTING_UNITS = listOf("pieces", "packs", "bottles", "boxes", "rolls", "kg", "g", "L", "ml")

    val STARTING_AREAS = listOf("Kitchen", "Bathroom", "Bedroom", "Living area", "Balcony", "Storage", "General household")

    // --- what the app accepts ---------------------------------------------------------------------------------------------

    fun checkTitle(text: String, what: String): Check = when {
        text.isBlank() -> Check.Refused("Give the $what a name.")
        text.trim().length > TITLE_MAX -> Check.Refused("Keep the name under $TITLE_MAX letters.")
        else -> Check.Ok
    }

    fun checkRecurrence(r: Recurrence, firstDue: LocalDate?): Check = when {
        r.every !in 1..EVERY_MAX -> Check.Refused("Repeat every 1 to $EVERY_MAX.")
        r.weekdays.any { it !in 1..7 } -> Check.Refused("A weekday is Monday to Sunday.")
        r.kind != RepeatKind.ONCE && firstDue == null -> Check.Refused("Choose the first date.")
        else -> Check.Ok
    }

    fun checkChore(c: Chore): Check {
        (checkTitle(c.title, "chore") as? Check.Refused)?.let { return it }
        if (c.instructions.length > TEXT_MAX) return Check.Refused("That text is too long.")
        if (c.minutes != null && c.minutes !in 1..1440) return Check.Refused("Minutes can be 1 to 1440.")
        return checkRecurrence(c.recurrence, c.firstDue)
    }

    fun checkQuantity(milli: Long?): Check = if (milli != null && (milli < 0 || milli > Quantity.MAX_MILLI)) Check.Refused("That quantity looks wrong.") else Check.Ok

    fun checkSupply(s: Supply): Check {
        (checkTitle(s.name, "supply") as? Check.Refused)?.let { return it }
        (checkQuantity(s.quantityMilli) as? Check.Refused)?.let { return it }
        (checkQuantity(s.lowMilli) as? Check.Refused)?.let { return it }
        if (s.unit.length > 16 || s.notes.length > NOTE_MAX) return Check.Refused("That text is too long.")
        return Check.Ok
    }

    // --- chores -------------------------------------------------------------------------------------------------------------

    private fun entryFor(entries: List<ChoreEntry>, day: LocalDate): ChoreEntry? = entries.lastOrNull { it.dueDay == day }

    private fun open(chore: Chore, occurrence: LocalDate, dueDay: LocalDate, entry: ChoreEntry?, today: LocalDate): ChoreView = ChoreView(
        chore, if (dueDay < today) ChoreState.OVERDUE else if (dueDay == today) ChoreState.DUE else ChoreState.UPCOMING, occurrence, dueDay, entry,
    )

    /**
     * Where [chore] stands on [today]. For a repeating chore only the latest occurrence up to today is considered, so a month away leaves one overdue
     * chore, not thirty (BASE.md 4.2); one the user did, skipped or put off is read from its entry; an occurrence done early counts too.
     */
    fun view(data: BaseData, chore: Chore, today: LocalDate): ChoreView = view(chore, data.entries.filter { it.choreId == chore.id }, today)

    /** [view] with the chore's own [entries] already picked out, so a list of chores reads the history once and not once per chore. */
    private fun view(chore: Chore, entries: List<ChoreEntry>, today: LocalDate): ChoreView {
        if (chore.status == RecordStatus.ARCHIVED) return ChoreView(chore, ChoreState.ARCHIVED, null, null, null)
        if (chore.status == RecordStatus.PAUSED) return ChoreView(chore, ChoreState.PAUSED, null, null, null)
        val anchor = chore.firstDue
        if (anchor == null) {
            val done = entries.lastOrNull { it.status == EntryStatus.DONE }
            return if (done != null) ChoreView(chore, ChoreState.DONE, done.dueDay, done.dueDay, done) else ChoreView(chore, ChoreState.ANYTIME, null, null, null)
        }
        val r = chore.recurrence
        val after = { entry: ChoreEntry -> if (entry.status == EntryStatus.SKIPPED) ChoreState.SKIPPED else ChoreState.DONE }
        if (r.kind == RepeatKind.ONCE) {
            val e = entryFor(entries, anchor)
            return when (e?.status) {
                EntryStatus.DONE, EntryStatus.SKIPPED -> ChoreView(chore, after(e), anchor, anchor, e)
                EntryStatus.DEFERRED -> open(chore, anchor, e.deferTo ?: anchor, e, today)
                null -> open(chore, anchor, anchor, null, today)
            }
        }
        val next = Schedules.nextAfter(r, anchor, today) ?: anchor
        val last = Schedules.lastOnOrBefore(r, anchor, today)
        val lastEntry = last?.let { entryFor(entries, it) }
        val nextEntry = entryFor(entries, next)
        val doneEarly = nextEntry != null && nextEntry.status != EntryStatus.DEFERRED
        if (last != null && lastEntry?.status == EntryStatus.DEFERRED) return open(chore, last, lastEntry.deferTo ?: last, lastEntry, today)
        if (last != null && lastEntry == null && !doneEarly) return open(chore, last, last, null, today)
        // Nothing is waiting: it was done or skipped for the latest occurrence, or early for the next one, or it has not started.
        val following = if (doneEarly) Schedules.nextAfter(r, anchor, next) ?: next else next
        val settled = if (doneEarly) nextEntry else if (last == today) lastEntry else null
        return if (settled != null) ChoreView(chore, after(settled), settled.dueDay, following, settled) else ChoreView(chore, ChoreState.UPCOMING, following, following, null)
    }

    /** Every chore that is not archived, most pressing first: overdue (oldest first), due today, upcoming by date, whenever, then done and paused. */
    fun views(data: BaseData, today: LocalDate): List<ChoreView> {
        val entries = data.entries.groupBy { it.choreId }
        return data.chores.filter { it.status != RecordStatus.ARCHIVED }.map { view(it, entries[it.id].orEmpty(), today) }
            .sortedWith(compareBy<ChoreView> { order(it.state) }.thenBy { it.dueDay ?: LocalDate(9999, 1, 1) }.thenBy { it.chore.title.lowercase() })
    }

    private fun order(s: ChoreState): Int = when (s) {
        ChoreState.OVERDUE -> 0; ChoreState.DUE -> 1; ChoreState.UPCOMING -> 2; ChoreState.ANYTIME -> 3
        ChoreState.DONE, ChoreState.SKIPPED -> 4; ChoreState.PAUSED -> 5; ChoreState.ARCHIVED -> 6
    }

    /** Chores that need a look today: overdue or due. */
    fun attention(views: List<ChoreView>): List<ChoreView> = views.filter { it.state == ChoreState.OVERDUE || it.state == ChoreState.DUE }

    /** What was done, skipped or put off, newest first (BASE.md 4.2 History). Chores that were later archived keep their history. */
    fun history(data: BaseData, choreId: String? = null): List<ChoreEntry> =
        data.entries.filter { choreId == null || it.choreId == choreId }.sortedWith(compareByDescending<ChoreEntry> { it.atMillis }.thenByDescending { it.dueDay })

    // --- supplies -----------------------------------------------------------------------------------------------------------

    /** Low when the quantity is known, a threshold is set, and the quantity is at or under it. Unknown is never low; no threshold is never inferred. */
    fun isLow(s: Supply): Boolean = !s.archived && s.quantityMilli != null && s.lowMilli != null && s.quantityMilli <= s.lowMilli

    /** Low supplies the user has not quieted (what the overview shows). */
    fun lowSupplies(data: BaseData, includeQuieted: Boolean = false): List<Supply> =
        data.supplies.filter { isLow(it) && (includeQuieted || !it.lowDismissed) }.sortedBy { it.name.lowercase() }

    /** The restocking list: every low supply, then what was written in by hand (BASE.md 4.3). It is not a purchase, a task or a transaction. */
    fun restockList(data: BaseData): List<RestockLine> =
        lowSupplies(data, includeQuieted = true).map { RestockLine.Low(it) } +
            data.restock.map { e -> RestockLine.Manual(e, e.supplyId?.let { id -> data.supplies.firstOrNull { it.id == id } }) }

    // --- maintenance and warranties ---------------------------------------------------------------------------------------

    fun scheduleView(s: MaintenanceSchedule, today: LocalDate): ScheduleView {
        val days = today.daysUntil(s.nextDue)
        val state = when {
            s.status != RecordStatus.ACTIVE -> DueState.PAUSED
            days < 0 -> DueState.OVERDUE
            days == 0 -> DueState.DUE
            days <= MAINTENANCE_SOON_DAYS -> DueState.SOON
            else -> DueState.LATER
        }
        return ScheduleView(s, state, days)
    }

    /** Active schedules, most pressing first. */
    fun schedules(data: BaseData, today: LocalDate): List<ScheduleView> =
        data.schedules.filter { it.status != RecordStatus.ARCHIVED }.map { scheduleView(it, today) }.sortedWith(compareBy<ScheduleView> { it.state == DueState.PAUSED }.thenBy { it.schedule.nextDue })

    /**
     * The schedule after a piece of maintenance was done on [doneDay] (BASE.md 4.5). A one-time schedule is finished (archived). A calendar schedule
     * moves to its next date after the later of the due date and the day it was done, so a late service never leaves a backlog; one set to count
     * "after it is done" starts again from that day. Past records are never touched.
     */
    fun advance(s: MaintenanceSchedule, doneDay: LocalDate): MaintenanceSchedule {
        if (s.recurrence.kind == RepeatKind.ONCE) return s.copy(status = RecordStatus.ARCHIVED)
        val from = maxOf(s.nextDue, doneDay)
        val next = when (s.policy) {
            SchedulePolicy.FIXED -> Schedules.nextAfter(s.recurrence, s.firstDue, from)
            SchedulePolicy.AFTER_DONE -> Schedules.nextAfter(s.recurrence, doneDay, doneDay)
        }
        return s.copy(nextDue = next ?: s.nextDue)
    }

    fun warrantyState(w: Warranty, today: LocalDate): WarrantyState {
        val end = w.expiryDay ?: return WarrantyState.UNDATED
        val days = today.daysUntil(end)
        return when {
            days < 0 -> WarrantyState.EXPIRED
            days <= WARRANTY_SOON_DAYS -> WarrantyState.ENDING
            else -> WarrantyState.ACTIVE
        }
    }

    /** Maintenance records, newest first, for one asset or for all. */
    fun maintenanceHistory(data: BaseData, assetId: String? = null): List<MaintenanceRecord> =
        data.maintenance.filter { assetId == null || it.assetId == assetId }.sortedWith(compareByDescending<MaintenanceRecord> { it.day }.thenByDescending { it.atMillis })

    // --- the overview ---------------------------------------------------------------------------------------------------------

    fun overview(data: BaseData, today: LocalDate): Overview = Overview(
        chores = attention(views(data, today)),
        maintenance = schedules(data, today).filter { it.state == DueState.OVERDUE || it.state == DueState.DUE || it.state == DueState.SOON },
        low = lowSupplies(data),
        warranties = data.warranties.filter { warrantyState(it, today) == WarrantyState.ENDING },
    )

    // --- Habitat Discovery: what may pay (BASE.md section 9) ---------------------------------------------------------------

    /**
     * Whether a chore done on [day] may be sent to the game: Habitat Discovery is on. The daily share ([CHORES_PAID_PER_DAY]) is counted once, by the game
     * (`GameStore.BASE_CAPS`, which reads the constant), because the history is trimmed and a deleted chore takes its entries with it.
     */
    @Suppress("UNUSED_PARAMETER")
    fun chorePays(data: BaseData, day: LocalDate): Boolean = data.settings.progression

    /** Whether a piece of maintenance done on [day] may be sent to the game, on the same terms ([MAINTENANCE_PAID_PER_DAY] is counted by the game). */
    @Suppress("UNUSED_PARAMETER")
    fun maintenancePays(data: BaseData, day: LocalDate): Boolean = data.settings.progression

    /** Chore history kept: entries older than this many days are dropped when one is added (BASE.md 4.2). History shows only the newest 60. */
    const val ENTRY_KEEP_DAYS = 400

    /** [entries] without those done more than [ENTRY_KEEP_DAYS] before [today]. */
    fun trimEntries(entries: List<ChoreEntry>, today: LocalDate): List<ChoreEntry> {
        val cutoff = today.minus(ENTRY_KEEP_DAYS, DateTimeUnit.DAY)
        return if (entries.any { it.day < cutoff }) entries.filter { it.day >= cutoff } else entries
    }

    // --- search ---------------------------------------------------------------------------------------------------------------

    /**
     * Every record whose words contain [text] (case ignored), by kind then title. Local and offline; each hit names its kind and opens its own record,
     * so what no list shows is left out: archived chores, supplies and items, finished one-time maintenance, and the warranties of an archived item.
     */
    fun search(data: BaseData, text: String): List<Hit> {
        val q = text.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        fun has(vararg parts: String?): Boolean = parts.any { it != null && it.lowercase().contains(q) }
        fun area(id: String?): String? = id?.let { a -> data.areas.firstOrNull { it.id == a }?.name }
        fun asset(id: String?): String? = id?.let { a -> data.assets.firstOrNull { it.id == a }?.name }
        val hits = mutableListOf<Hit>()
        data.chores.filter { it.status != RecordStatus.ARCHIVED && has(it.title, it.instructions, it.category, area(it.areaId)) }.forEach { hits += Hit(HitKind.CHORE, it.id, it.title, area(it.areaId) ?: it.category) }
        data.supplies.filter { !it.archived && has(it.name, it.category, it.notes, area(it.areaId)) }.forEach { hits += Hit(HitKind.SUPPLY, it.id, it.name, it.category) }
        data.assets.filter { !it.archived && has(it.name, it.category, it.brand, it.model, it.serial, it.notes, area(it.areaId)) }.forEach { hits += Hit(HitKind.ASSET, it.id, it.name, listOf(it.brand, it.model).filter { p -> p.isNotBlank() }.joinToString(" ")) }
        data.schedules.filter { it.status != RecordStatus.ARCHIVED && has(it.title, it.instructions, asset(it.assetId)) }.forEach { hits += Hit(HitKind.MAINTENANCE, it.id, it.title, asset(it.assetId).orEmpty()) }
        data.warranties.filter { w -> data.assets.none { it.id == w.assetId && it.archived } }.filter { has(it.provider, it.coverage, it.reference, it.notes, asset(it.assetId)) }.forEach { hits += Hit(HitKind.WARRANTY, it.id, asset(it.assetId) ?: "Warranty", it.provider) }
        data.references.filter { has(it.title, it.text, it.category, area(it.areaId), asset(it.assetId)) }.forEach { hits += Hit(HitKind.REFERENCE, it.id, it.title, it.category) }
        return hits.sortedWith(compareBy<Hit> { it.kind.ordinal }.thenBy { it.title.lowercase() })
    }
}
