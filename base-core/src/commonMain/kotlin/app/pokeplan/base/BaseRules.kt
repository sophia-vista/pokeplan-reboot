package app.pokeplan.base

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

sealed interface Check {
    data object Ok : Check
    data class Refused(val reason: String) : Check
}

object Schedules {
    private const val MAX_WEEK_SCAN = 7 * 100 + 7

    private fun monthIndex(d: LocalDate): Int = app.pokeplan.kit.Calendars.monthIndex(d)

    private fun daysInMonth(year: Int, month: Int): Int = app.pokeplan.kit.Calendars.daysInMonth(year, month)

    private fun inMonth(index: Int, anchor: LocalDate): LocalDate {
        val year = index.floorDiv(12)
        val month = index - year * 12 + 1
        return LocalDate(year, month, minOf(anchor.dayOfMonth, daysInMonth(year, month)))
    }

    private fun monthStep(r: Recurrence): Int = maxOf(1, r.every) * if (r.kind == RepeatKind.YEARLY) 12 else 1

    private fun weekdays(r: Recurrence, anchor: LocalDate): Set<Int> = r.weekdays.filter { it in 1..7 }.toSet().ifEmpty { setOf(anchor.dayOfWeek.isoDayNumber) }

    private fun mondayOf(d: LocalDate): LocalDate = d.minus(d.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

    fun occursOn(r: Recurrence, anchor: LocalDate, day: LocalDate): Boolean = when (r.kind) {
        RepeatKind.ONCE -> day == anchor
        RepeatKind.DAILY -> day >= anchor && anchor.daysUntil(day) % maxOf(1, r.every) == 0
        RepeatKind.WEEKLY -> day >= anchor && day.dayOfWeek.isoDayNumber in weekdays(r, anchor) &&
            (mondayOf(anchor).daysUntil(mondayOf(day)) / 7) % maxOf(1, r.every) == 0
        RepeatKind.MONTHLY, RepeatKind.YEARLY -> day >= anchor && (monthIndex(day) - monthIndex(anchor)) % monthStep(r) == 0 && day == inMonth(monthIndex(day), anchor)
    }

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

    fun nextAfter(r: Recurrence, anchor: LocalDate, day: LocalDate): LocalDate? = firstOnOrAfter(r, anchor, day.plus(1, DateTimeUnit.DAY))
}

object Quantity {
    const val MAX_MILLI = 1_000_000_000L

    fun parse(text: String): Long? {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty() || t.count { it == '.' } > 1 || !t.all { it.isDigit() || it == '.' } || t == ".") return null
        val whole = t.substringBefore('.').ifEmpty { "0" }
        val frac = t.substringAfter('.', "")
        if (frac.length > 3 || whole.length > 9) return null
        val milli = whole.toLong() * 1000 + frac.padEnd(3, '0').toLong()
        return milli.takeIf { it <= MAX_MILLI }
    }

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

data class Hit(val kind: HitKind, val id: String, val title: String, val detail: String = "")

/**
 * One line of the restocking list: a low supply, a hand-written entry, or both when the entry
 * points at a supply that is low (one line for one thing to buy).
 */
data class RestockLine(val supply: Supply? = null, val entry: RestockEntry? = null) {
    val name: String get() = supply?.name ?: entry?.name.orEmpty()
    val low: Boolean get() = supply?.let { BaseRules.isLow(it) } == true
}

/** A heading of the restocking list: the line's area, or [BaseRules.RESTOCK_OTHER] when it has none. */
data class RestockGroup(val title: String, val lines: List<RestockLine>)

data class Overview(val chores: List<ChoreView>, val maintenance: List<ScheduleView>, val low: List<Supply>, val warranties: List<Warranty>) {
    val needsAttention: Boolean get() = chores.isNotEmpty() || maintenance.isNotEmpty() || low.isNotEmpty() || warranties.isNotEmpty()
}

object BaseRules {
    const val TITLE_MAX = 80
    const val NOTE_MAX = 2000
    const val TEXT_MAX = 8000
    const val EVERY_MAX = 99

    const val ADJUSTMENTS_KEPT = 300

    /** How many of a supply's stock changes the supply screen shows. */
    const val ADJUSTMENT_HISTORY = 5

    const val REASON_COUNTED = "Counted"
    const val REASON_USED = "Used"
    const val REASON_BOUGHT = "Bought"
    const val REASON_CORRECTION = "Correction"

    /** The reasons offered on the stock adjustment form; the player may also leave it blank. */
    val ADJUSTMENT_REASONS = listOf(REASON_COUNTED, REASON_USED, REASON_BOUGHT, REASON_CORRECTION)

    /** The restocking group for lines with no household area. */
    const val RESTOCK_OTHER = "Other"

    const val WARRANTY_SOON_DAYS = 30

    const val MAINTENANCE_SOON_DAYS = 14

    const val CHORES_PAID_PER_DAY = 5

    const val MAINTENANCE_PAID_PER_DAY = 2

    val STARTING_UNITS = listOf("pieces", "packs", "bottles", "boxes", "rolls", "kg", "g", "L", "ml")

    val STARTING_AREAS = listOf("Kitchen", "Bathroom", "Bedroom", "Living area", "Balcony", "Storage", "General household")

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

    private fun entryFor(entries: List<ChoreEntry>, day: LocalDate): ChoreEntry? = entries.lastOrNull { it.dueDay == day }

    private fun open(chore: Chore, occurrence: LocalDate, dueDay: LocalDate, entry: ChoreEntry?, today: LocalDate): ChoreView = ChoreView(
        chore, if (dueDay < today) ChoreState.OVERDUE else if (dueDay == today) ChoreState.DUE else ChoreState.UPCOMING, occurrence, dueDay, entry,
    )

    fun view(data: BaseData, chore: Chore, today: LocalDate): ChoreView = view(chore, data.entries.filter { it.choreId == chore.id }, today)

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
        val following = if (doneEarly) Schedules.nextAfter(r, anchor, next) ?: next else next
        val settled = if (doneEarly) nextEntry else if (last == today) lastEntry else null
        return if (settled != null) ChoreView(chore, after(settled), settled.dueDay, following, settled) else ChoreView(chore, ChoreState.UPCOMING, following, following, null)
    }

    fun views(data: BaseData, today: LocalDate): List<ChoreView> {
        val entries = data.entries.groupBy { it.choreId }
        return data.chores.filter { it.status != RecordStatus.ARCHIVED }.map { view(it, entries[it.id].orEmpty(), today) }
            .sortedWith(compareBy<ChoreView> { order(it.state) }.thenBy { it.dueDay ?: LocalDate(9999, 1, 1) }.thenBy { it.chore.title.lowercase() })
    }

    private fun order(s: ChoreState): Int = when (s) {
        ChoreState.OVERDUE -> 0; ChoreState.DUE -> 1; ChoreState.UPCOMING -> 2; ChoreState.ANYTIME -> 3
        ChoreState.DONE, ChoreState.SKIPPED -> 4; ChoreState.PAUSED -> 5; ChoreState.ARCHIVED -> 6
    }

    fun attention(views: List<ChoreView>): List<ChoreView> = views.filter { it.state == ChoreState.OVERDUE || it.state == ChoreState.DUE }

    fun history(data: BaseData, choreId: String? = null): List<ChoreEntry> =
        data.entries.filter { choreId == null || it.choreId == choreId }.sortedWith(compareByDescending<ChoreEntry> { it.atMillis }.thenByDescending { it.dueDay })

    fun isLow(s: Supply): Boolean = !s.archived && s.quantityMilli != null && s.lowMilli != null && s.quantityMilli <= s.lowMilli

    fun lowSupplies(data: BaseData, includeQuieted: Boolean = false): List<Supply> =
        data.supplies.filter { isLow(it) && (includeQuieted || !it.lowDismissed) }.sortedBy { it.name.lowercase() }

    /**
     * The restocking list: every low supply (including one the player quieted), then what was
     * written by hand. A hand-written entry linked to a supply that is low is that supply's line,
     * so the same thing to buy is never listed twice.
     */
    fun restockList(data: BaseData): List<RestockLine> {
        val lows = lowSupplies(data, includeQuieted = true)
        val linked = data.restock.filter { it.supplyId != null }.associateBy { it.supplyId }
        val lowIds = lows.map { it.id }.toSet()
        return lows.map { s -> RestockLine(s, linked[s.id]) } +
            data.restock.filter { it.supplyId == null || it.supplyId !in lowIds }
                .map { e -> RestockLine(e.supplyId?.let { id -> data.supplies.firstOrNull { it.id == id } }, e) }
    }

    /**
     * [restockList] under one heading per household area, in the player's area order, with
     * [RESTOCK_OTHER] last. A quieted low supply keeps its area, so it does not merge into Other.
     */
    fun restockGroups(data: BaseData): List<RestockGroup> {
        val lines = restockList(data)
        val named = data.areas.associate { it.id to it.name }
        fun title(line: RestockLine): String = line.supply?.areaId?.let { named[it] } ?: RESTOCK_OTHER
        return (data.areas.map { it.name } + RESTOCK_OTHER).distinct().mapNotNull { name ->
            val inGroup = lines.filter { title(it) == name }
            if (inGroup.isEmpty()) null else RestockGroup(name, inGroup)
        }
    }

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

    fun schedules(data: BaseData, today: LocalDate): List<ScheduleView> =
        data.schedules.filter { it.status != RecordStatus.ARCHIVED }.map { scheduleView(it, today) }.sortedWith(compareBy<ScheduleView> { it.state == DueState.PAUSED }.thenBy { it.schedule.nextDue })

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

    fun maintenanceHistory(data: BaseData, assetId: String? = null): List<MaintenanceRecord> =
        data.maintenance.filter { assetId == null || it.assetId == assetId }.sortedWith(compareByDescending<MaintenanceRecord> { it.day }.thenByDescending { it.atMillis })

    fun overview(data: BaseData, today: LocalDate): Overview = Overview(
        chores = attention(views(data, today)),
        maintenance = schedules(data, today).filter { it.state == DueState.OVERDUE || it.state == DueState.DUE || it.state == DueState.SOON },
        low = lowSupplies(data),
        warranties = data.warranties.filter { warrantyState(it, today) == WarrantyState.ENDING },
    )

    @Suppress("UNUSED_PARAMETER")
    fun chorePays(data: BaseData, day: LocalDate): Boolean = data.settings.progression

    @Suppress("UNUSED_PARAMETER")
    fun maintenancePays(data: BaseData, day: LocalDate): Boolean = data.settings.progression

    const val ENTRY_KEEP_DAYS = 400

    fun trimEntries(entries: List<ChoreEntry>, today: LocalDate): List<ChoreEntry> {
        val cutoff = today.minus(ENTRY_KEEP_DAYS, DateTimeUnit.DAY)
        return if (entries.any { it.day < cutoff }) entries.filter { it.day >= cutoff } else entries
    }

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
