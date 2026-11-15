package app.pokeplan.app.base

import app.pokeplan.core.reward.BaseEvent
import app.pokeplan.core.reward.BaseKind
import app.pokeplan.core.reward.Reward
import app.pokeplan.base.Asset
import app.pokeplan.base.Check
import app.pokeplan.base.Chore
import app.pokeplan.base.ChoreEntry
import app.pokeplan.base.ChoreState
import app.pokeplan.base.ChoreView
import app.pokeplan.base.EntryStatus
import app.pokeplan.base.BaseArea
import app.pokeplan.base.BaseData
import app.pokeplan.base.BaseKinds
import app.pokeplan.base.Decoration
import app.pokeplan.base.Placement
import app.pokeplan.base.Room
import app.pokeplan.base.BaseRules
import app.pokeplan.base.BaseSettings
import app.pokeplan.base.MaintenanceRecord
import app.pokeplan.base.MaintenanceSchedule
import app.pokeplan.base.Quantity
import app.pokeplan.base.RecordStatus
import app.pokeplan.base.Recurrence
import app.pokeplan.base.Reference
import app.pokeplan.base.RepeatKind
import app.pokeplan.base.RestockEntry
import app.pokeplan.base.SchedulePolicy
import app.pokeplan.base.StockAdjustment
import app.pokeplan.base.Supply
import app.pokeplan.base.Warranty
import app.pokeplan.plan.toLocalDateTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random
import app.pokeplan.app.World
import app.pokeplan.app.heal.Logged
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.SavedDocument
import app.pokeplan.app.storage.Storage
import app.pokeplan.app.storage.newId

/**
 * The Home module on this device (docs/pokelife `MODULES/BASE.md`): its own save in its own [storage], apart from every other module's, kept by
 * `BaseTables` in the live database. Everything in it is private: it reaches the shared world only through [world], with a [BaseEvent] that carries a
 * stable id, the module, an opaque record reference, the broad kind and the time, and **never** a title, an area, a supply, an asset, a serial, a cost
 * or a note.
 *
 * Only finished work can earn, and only while Habitat Discovery is on: a chore done and a piece of maintenance done. Creating or editing anything,
 * changing a quantity, a warranty, a note or the restocking list never pays. A chore occurrence is paid once however many times it is undone and done
 * again: the event id names the occurrence, and the game remembers it.
 */
class BaseStore(
    storage: Storage,
    private val clock: Clock,
    private val world: World,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val rng: Random = Random,
) {
    private val document = SavedDocument(storage, BaseData.serializer(), json, tidy = { tidy(it) }) { tidy(BaseData()) }

    val data: BaseData get() = document.value

    fun observe(listener: (BaseData) -> Unit): () -> Unit = document.observe(listener)

    private val nowMillis: Long get() = clock.nowMillis()
    val today: LocalDate get() = nowMillis.toLocalDateTime(zone).date

    /** Picks up a change another process made, without writing anything back; see [SavedDocument.reload]. */
    fun reload() = document.reload()

    /** Picks up what another store in this process (a widget, a notification) wrote; cheap when nothing was. */
    fun sync() = document.sync()

    private fun update(change: (BaseData) -> BaseData) = document.update(change)

    /** Puts back a [snapshot] taken before a delete, for the undo bar. */
    fun restore(snapshot: BaseData) = update { snapshot }

    private fun newId(): String = newId(nowMillis, rng)

    private fun pay(kind: BaseKind, id: String, ref: String): List<Reward>? =
        if (!data.settings.progression) null else world.base(BaseEvent("home:${kind.name.lowercase()}:$id", kind, nowMillis, ref))

    // --- the Secret Base room (BASE.md 9.5) ----------------------------------------------------------------------------------

    /** What stands on the room's floor, from decorations the player owns. */
    val room: List<Placement> get() = data.room.filter { it.id in world.ownedDecorations() }

    /** The decorations owned and not placed (the box), in catalogue order. */
    fun inBox(): List<Decoration> = Room.inBox(world.ownedDecorations(), data.room)

    private fun arrange(result: Room.Result): String? = when (result) {
        is Room.Result.Done -> { update { it.copy(room = result.placed) }; null }
        is Room.Result.Refused -> result.reason
    }

    /** Puts an owned decoration in the box on an empty cell; null when done, else why not. Free, and never pays. */
    fun placeDecoration(id: String, x: Int, y: Int): String? = arrange(Room.place(world.ownedDecorations(), data.room, id, x, y))

    /** Moves a placed decoration to another empty cell; null when done, else why not. */
    fun moveDecoration(id: String, x: Int, y: Int): String? = arrange(Room.move(data.room, id, x, y))

    /** Takes a decoration off the floor into the box. It is never lost. */
    fun removeDecoration(id: String): String? = arrange(Room.remove(data.room, id))

    // --- areas -------------------------------------------------------------------------------------------------------------

    fun addArea(name: String): BaseArea? {
        val n = name.trim().take(BaseRules.TITLE_MAX)
        if (n.isEmpty() || data.areas.any { it.name.equals(n, ignoreCase = true) && !it.archived }) return null
        val a = BaseArea(newId(), n)
        update { it.copy(areas = it.areas + a) }
        return a
    }

    fun renameArea(id: String, name: String) {
        val n = name.trim().take(BaseRules.TITLE_MAX)
        if (n.isNotEmpty()) update { d -> d.copy(areas = d.areas.map { if (it.id == id) it.copy(name = n) else it }) }
    }

    /** Archiving hides an area from the pickers; every record that names it keeps it (BASE.md 4.1). */
    fun setAreaArchived(id: String, archived: Boolean) = update { d -> d.copy(areas = d.areas.map { if (it.id == id) it.copy(archived = archived) else it }) }

    /** Moves an area one place up or down the list. */
    fun moveArea(id: String, up: Boolean) = update { d ->
        val i = d.areas.indexOfFirst { it.id == id }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in d.areas.indices) d else d.copy(areas = d.areas.toMutableList().also { l -> val t = l[i]; l[i] = l[j]; l[j] = t })
    }

    /** Deletes an area; the records that named it become unassigned. */
    fun deleteArea(id: String) = update { d ->
        d.copy(
            areas = d.areas.filterNot { it.id == id },
            chores = d.chores.map { if (it.areaId == id) it.copy(areaId = null) else it },
            supplies = d.supplies.map { if (it.areaId == id) it.copy(areaId = null) else it },
            assets = d.assets.map { if (it.areaId == id) it.copy(areaId = null) else it },
            references = d.references.map { if (it.areaId == id) it.copy(areaId = null) else it },
        )
    }

    // --- chores ------------------------------------------------------------------------------------------------------------

    /**
     * Saves a chore. A repeating chore with no first date starts today; a one-time chore with none is a "whenever" chore. Nothing here pays: making a
     * list of chores is never a reward (BASE.md 9.2).
     */
    fun addChore(
        title: String, recurrence: Recurrence = Recurrence(), firstDue: LocalDate? = null, instructions: String = "", areaId: String? = null,
        category: String = "", minutes: Int? = null,
    ): Logged<Chore> {
        val due = firstDue ?: if (recurrence.kind != RepeatKind.ONCE) today else null
        val c = Chore(newId(), title.trim(), instructions.trim(), areaId, category.trim().take(BaseRules.TITLE_MAX), recurrence, due, minutes, createdMillis = nowMillis, updatedMillis = nowMillis)
        (BaseRules.checkChore(c) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { it.copy(chores = it.chores + c) }
        return Logged(c)
    }

    /**
     * Changes a chore's words, area, category, minutes, repeat and first date. Entries are never touched, so what was done stays done: a new repeat only
     * decides which occurrences come next (BASE.md 4.2: editing never rewrites completed history).
     */
    fun updateChore(c: Chore): Logged<Chore> {
        val old = data.chores.firstOrNull { it.id == c.id } ?: return Logged(null, "That chore is gone.")
        val due = c.firstDue ?: if (c.recurrence.kind != RepeatKind.ONCE) old.firstDue ?: today else null
        val next = old.copy(
            title = c.title.trim(), instructions = c.instructions.trim(), areaId = c.areaId, category = c.category.trim().take(BaseRules.TITLE_MAX),
            recurrence = c.recurrence, firstDue = due, minutes = c.minutes, updatedMillis = nowMillis,
        )
        (BaseRules.checkChore(next) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { d -> d.copy(chores = d.chores.map { if (it.id == c.id) next else it }) }
        return Logged(next)
    }

    /** Active, paused (kept, not shown as due) or archived (out of the active views, history kept). */
    fun setChoreStatus(id: String, status: RecordStatus) = update { d -> d.copy(chores = d.chores.map { if (it.id == id) it.copy(status = status, updatedMillis = nowMillis) else it }) }

    /** Deletes a chore and what was done about it. Prefer archiving, which keeps the history. */
    fun deleteChore(id: String) = update { d -> d.copy(chores = d.chores.filterNot { it.id == id }, entries = d.entries.filterNot { it.choreId == id }) }

    fun choreViews(): List<ChoreView> = BaseRules.views(data, today)

    fun choreView(id: String): ChoreView? = data.chores.firstOrNull { it.id == id }?.let { BaseRules.view(data, it, today) }

    private fun openOccurrence(id: String): Pair<Chore, LocalDate>? {
        val chore = data.chores.firstOrNull { it.id == id } ?: return null
        val v = BaseRules.view(data, chore, today)
        if (v.state == ChoreState.DONE || v.state == ChoreState.SKIPPED || v.state == ChoreState.PAUSED || v.state == ChoreState.ARCHIVED) return null
        return chore to (v.occurrence ?: today)
    }

    private fun putEntry(e: ChoreEntry) = update { d -> d.copy(entries = BaseRules.trimEntries(d.entries.filterNot { it.choreId == e.choreId && it.dueDay == e.dueDay } + e, today)) }

    /**
     * Marks the chore's current occurrence done (one action, BASE.md 4.2), with an optional [note]. An occurrence not yet due counts too ("done early").
     * Pays Habitat Discovery when it is on and the day's share is not used; the same occurrence pays once, however often it is undone and done again.
     */
    fun complete(choreId: String, note: String = ""): Logged<ChoreEntry> {
        val (chore, occurrence) = openOccurrence(choreId) ?: return Logged(null, "That chore is not waiting.")
        val before = data
        val entry = ChoreEntry(newId(), chore.id, occurrence, EntryStatus.DONE, today, nowMillis, note.trim().take(BaseRules.NOTE_MAX))
        putEntry(entry)
        val rewards = if (BaseRules.chorePays(before, today)) pay(BaseKind.CHORE, "${chore.id}:$occurrence", entry.id) else null
        if (rewards != null) update { d -> d.copy(entries = d.entries.map { if (it.id == entry.id) it.copy(paid = true) else it }) }
        return Logged(data.entries.first { it.id == entry.id }, rewards = rewards.orEmpty())
    }

    /** Skips the current occurrence: it leaves the due list and the schedule goes on (BASE.md 4.2). Nothing is paid. */
    fun skip(choreId: String, note: String = ""): Boolean {
        val (chore, occurrence) = openOccurrence(choreId) ?: return false
        putEntry(ChoreEntry(newId(), chore.id, occurrence, EntryStatus.SKIPPED, today, nowMillis, note.trim().take(BaseRules.NOTE_MAX)))
        return true
    }

    /** Puts the current occurrence off to [to] (after today); the schedule is not shifted. */
    fun defer(choreId: String, to: LocalDate): Boolean {
        if (to <= today) return false
        val (chore, occurrence) = openOccurrence(choreId) ?: return false
        putEntry(ChoreEntry(newId(), chore.id, occurrence, EntryStatus.DEFERRED, today, nowMillis, deferTo = to))
        return true
    }

    /** Takes an entry back (a mistaken tick), so the occurrence waits again. What Habitat Discovery paid stays paid and is not paid twice. */
    fun undo(entryId: String) = update { d -> d.copy(entries = d.entries.filterNot { it.id == entryId }) }

    // --- supplies ----------------------------------------------------------------------------------------------------------

    fun addSupply(name: String, unit: String = "", quantityMilli: Long? = null, lowMilli: Long? = null, category: String = "", areaId: String? = null, notes: String = ""): Logged<Supply> {
        val s = Supply(newId(), name.trim(), category.trim().take(BaseRules.TITLE_MAX), quantityMilli, unit.trim(), lowMilli, areaId, notes.trim(), createdMillis = nowMillis, updatedMillis = nowMillis)
        (BaseRules.checkSupply(s) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { it.copy(supplies = it.supplies + s) }
        return Logged(s)
    }

    /** Changes a supply's name, unit, threshold, category, area and notes. The quantity changes only through [setQuantity] and [adjust], which leave a trace. */
    fun updateSupply(s: Supply): Logged<Supply> {
        val old = data.supplies.firstOrNull { it.id == s.id } ?: return Logged(null, "That supply is gone.")
        val next = old.copy(name = s.name.trim(), unit = s.unit.trim(), lowMilli = s.lowMilli, category = s.category.trim().take(BaseRules.TITLE_MAX), areaId = s.areaId, notes = s.notes.trim(), updatedMillis = nowMillis)
        (BaseRules.checkSupply(next) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { d -> d.copy(supplies = d.supplies.map { if (it.id == s.id) next else it }) }
        return Logged(next)
    }

    private fun changeQuantity(id: String, after: Long?, reason: String) = update { d ->
        val old = d.supplies.firstOrNull { it.id == id } ?: return@update d
        val adj = StockAdjustment(newId(), id, old.quantityMilli, after, reason.trim().take(BaseRules.TITLE_MAX), nowMillis)
        d.copy(
            supplies = d.supplies.map { if (it.id == id) it.copy(quantityMilli = after, lowDismissed = false, updatedMillis = nowMillis) else it },
            adjustments = (d.adjustments + adj).takeLast(BaseRules.ADJUSTMENTS_KEPT),
        )
    }

    /** Sets the quantity directly; null makes it unknown (which is not zero). Never pays. */
    fun setQuantity(id: String, milli: Long?, reason: String = ""): String? {
        if (data.supplies.none { it.id == id }) return "That supply is gone."
        (BaseRules.checkQuantity(milli) as? Check.Refused)?.let { return it.reason }
        changeQuantity(id, milli, reason)
        return null
    }

    /**
     * Adds ([deltaMilli] above zero) or takes away (below zero) from the quantity. A quantity that is unknown can not be changed by a difference, and one
     * that would go below zero is refused, so the entry is put right instead of silently clamped (BASE.md 4.3). Returns the reason, or null.
     */
    fun adjust(id: String, deltaMilli: Long, reason: String = ""): String? {
        val s = data.supplies.firstOrNull { it.id == id } ?: return "That supply is gone."
        val now = s.quantityMilli ?: return "The quantity is unknown. Set it first."
        val after = now + deltaMilli
        if (after < 0) return "That is more than is left. Set the quantity instead."
        (BaseRules.checkQuantity(after) as? Check.Refused)?.let { return it.reason }
        changeQuantity(id, after, reason)
        return null
    }

    /** Quiets the current low-stock notice. Neither the quantity nor the threshold changes. */
    fun dismissLow(id: String) = update { d -> d.copy(supplies = d.supplies.map { if (it.id == id) it.copy(lowDismissed = true) else it }) }

    fun setSupplyArchived(id: String, archived: Boolean) = update { d -> d.copy(supplies = d.supplies.map { if (it.id == id) it.copy(archived = archived, updatedMillis = nowMillis) else it }) }

    fun deleteSupply(id: String) = update { d -> d.copy(supplies = d.supplies.filterNot { it.id == id }, adjustments = d.adjustments.filterNot { it.supplyId == id }, restock = d.restock.filterNot { it.supplyId == id }) }

    /** Writes something on the restocking list by hand: a [supplyId] the user chose, or any [name]. Not a purchase, a task or a transaction. */
    fun addRestock(name: String, supplyId: String? = null): RestockEntry? {
        val label = name.trim().take(BaseRules.TITLE_MAX).ifEmpty { data.supplies.firstOrNull { it.id == supplyId }?.name ?: return null }
        if (supplyId != null && data.restock.any { it.supplyId == supplyId }) return null
        val e = RestockEntry(newId(), label, supplyId, nowMillis)
        update { it.copy(restock = it.restock + e) }
        return e
    }

    fun removeRestock(id: String) = update { d -> d.copy(restock = d.restock.filterNot { it.id == id }) }

    /**
     * Marks a restocking entry bought: a supply gets its new quantity, and the entry leaves the list. A supply that is on the list because it is low
     * needs the quantity (nothing else takes it off). Pays nothing.
     */
    fun restocked(supplyId: String, newQuantityMilli: Long?, entryId: String? = null): String? {
        if (newQuantityMilli == null && entryId == null && data.supplies.any { it.id == supplyId && BaseRules.isLow(it) }) return "Enter how much there is now."
        val reason = newQuantityMilli?.let { setQuantity(supplyId, it, "Restocked") }
        if (reason != null) return reason
        update { d -> d.copy(restock = d.restock.filterNot { it.id == entryId || it.supplyId == supplyId }) }
        return null
    }

    // --- belongings (durable items), warranties ---------------------------------------------------------------------------

    fun addAsset(name: String, category: String = "", brand: String = "", model: String = "", serial: String = "", purchaseDay: LocalDate? = null, areaId: String? = null, notes: String = ""): Logged<Asset> {
        (BaseRules.checkTitle(name, "item") as? Check.Refused)?.let { return Logged(null, it.reason) }
        val a = Asset(newId(), name.trim(), category.trim().take(BaseRules.TITLE_MAX), brand.trim().take(BaseRules.TITLE_MAX), model.trim().take(BaseRules.TITLE_MAX), serial.trim().take(BaseRules.TITLE_MAX), purchaseDay, areaId, notes.trim().take(BaseRules.NOTE_MAX), createdMillis = nowMillis, updatedMillis = nowMillis)
        update { it.copy(assets = it.assets + a) }
        return Logged(a)
    }

    fun updateAsset(a: Asset): Logged<Asset> {
        val old = data.assets.firstOrNull { it.id == a.id } ?: return Logged(null, "That item is gone.")
        (BaseRules.checkTitle(a.name, "item") as? Check.Refused)?.let { return Logged(null, it.reason) }
        val next = old.copy(
            name = a.name.trim(), category = a.category.trim().take(BaseRules.TITLE_MAX), brand = a.brand.trim().take(BaseRules.TITLE_MAX), model = a.model.trim().take(BaseRules.TITLE_MAX),
            serial = a.serial.trim().take(BaseRules.TITLE_MAX), purchaseDay = a.purchaseDay, areaId = a.areaId, notes = a.notes.trim().take(BaseRules.NOTE_MAX), updatedMillis = nowMillis,
        )
        update { d -> d.copy(assets = d.assets.map { if (it.id == a.id) next else it }) }
        return Logged(next)
    }

    fun setAssetArchived(id: String, archived: Boolean) = update { d -> d.copy(assets = d.assets.map { if (it.id == id) it.copy(archived = archived, updatedMillis = nowMillis) else it }) }

    /**
     * Deletes an item and its warranties. Its maintenance schedules, records and the notes that named it stay, as general household records
     * (BASE.md 4.4); archive an item to keep everything together.
     */
    fun deleteAsset(id: String) = update { d ->
        d.copy(
            assets = d.assets.filterNot { it.id == id },
            warranties = d.warranties.filterNot { it.assetId == id },
            schedules = d.schedules.map { if (it.assetId == id) it.copy(assetId = null) else it },
            maintenance = d.maintenance.map { if (it.assetId == id) it.copy(assetId = null) else it },
            references = d.references.map { if (it.assetId == id) it.copy(assetId = null) else it },
        )
    }

    fun addWarranty(assetId: String, provider: String = "", coverage: String = "", startDay: LocalDate? = null, expiryDay: LocalDate? = null, reference: String = "", notes: String = ""): Logged<Warranty> {
        if (data.assets.none { it.id == assetId }) return Logged(null, "That item is gone.")
        if (startDay != null && expiryDay != null && expiryDay < startDay) return Logged(null, "The end date is before the start.")
        val w = Warranty(newId(), assetId, provider.trim().take(BaseRules.TITLE_MAX), coverage.trim().take(BaseRules.NOTE_MAX), startDay, expiryDay, reference.trim().take(BaseRules.TITLE_MAX), notes.trim().take(BaseRules.NOTE_MAX), nowMillis)
        update { it.copy(warranties = it.warranties + w) }
        return Logged(w)
    }

    fun updateWarranty(w: Warranty): Logged<Warranty> {
        val old = data.warranties.firstOrNull { it.id == w.id } ?: return Logged(null, "That warranty is gone.")
        val start = w.startDay; val end = w.expiryDay
        if (start != null && end != null && end < start) return Logged(null, "The end date is before the start.")
        val next = old.copy(provider = w.provider.trim().take(BaseRules.TITLE_MAX), coverage = w.coverage.trim().take(BaseRules.NOTE_MAX), startDay = w.startDay, expiryDay = w.expiryDay, reference = w.reference.trim().take(BaseRules.TITLE_MAX), notes = w.notes.trim().take(BaseRules.NOTE_MAX))
        update { d -> d.copy(warranties = d.warranties.map { if (it.id == w.id) next else it }) }
        return Logged(next)
    }

    fun deleteWarranty(id: String) = update { d -> d.copy(warranties = d.warranties.filterNot { it.id == id }) }

    // --- maintenance -------------------------------------------------------------------------------------------------------

    /** A schedule for an item or for the household. Making one never pays; only doing the work does. */
    fun addSchedule(title: String, firstDue: LocalDate?, recurrence: Recurrence = Recurrence(), policy: SchedulePolicy = SchedulePolicy.FIXED, assetId: String? = null, instructions: String = ""): Logged<MaintenanceSchedule> {
        (BaseRules.checkTitle(title, "maintenance") as? Check.Refused)?.let { return Logged(null, it.reason) }
        val first = firstDue ?: defaultFirstDue(recurrence)
        (BaseRules.checkRecurrence(recurrence, first) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val s = MaintenanceSchedule(newId(), title.trim(), assetId, instructions.trim().take(BaseRules.TEXT_MAX), recurrence, policy, first, first, createdMillis = nowMillis)
        update { it.copy(schedules = it.schedules + s) }
        return Logged(s)
    }

    /** The first due date of a schedule added by name only: today plus one interval (a week for a one-time one), so it never starts due or overdue. */
    private fun defaultFirstDue(r: Recurrence): LocalDate {
        val n = maxOf(1, r.every)
        return when (r.kind) {
            RepeatKind.ONCE -> today.plus(7, DateTimeUnit.DAY)
            RepeatKind.DAILY -> today.plus(n, DateTimeUnit.DAY)
            RepeatKind.WEEKLY -> today.plus(n, DateTimeUnit.WEEK)
            RepeatKind.MONTHLY -> today.plus(n, DateTimeUnit.MONTH)
            RepeatKind.YEARLY -> today.plus(n, DateTimeUnit.YEAR)
        }
    }

    /**
     * Changes a schedule's words, item, repeat, policy and next date. Records of work already done are never touched. The date it counts from
     * ([MaintenanceSchedule.firstDue]) moves only when the next date or the kind of repeat changed, so renaming a schedule that started on the 31st
     * does not make it drift to the 28th.
     */
    fun updateSchedule(s: MaintenanceSchedule): Logged<MaintenanceSchedule> {
        val old = data.schedules.firstOrNull { it.id == s.id } ?: return Logged(null, "That schedule is gone.")
        (BaseRules.checkTitle(s.title, "maintenance") as? Check.Refused)?.let { return Logged(null, it.reason) }
        (BaseRules.checkRecurrence(s.recurrence, s.nextDue) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val next = old.copy(title = s.title.trim(), assetId = s.assetId, instructions = s.instructions.trim().take(BaseRules.TEXT_MAX), recurrence = s.recurrence, policy = s.policy, firstDue = if (s.nextDue != old.nextDue || s.recurrence.kind != old.recurrence.kind) s.nextDue else old.firstDue, nextDue = s.nextDue)
        update { d -> d.copy(schedules = d.schedules.map { if (it.id == s.id) next else it }) }
        return Logged(next)
    }

    fun setScheduleStatus(id: String, status: RecordStatus) = update { d -> d.copy(schedules = d.schedules.map { if (it.id == id) it.copy(status = status) else it }) }

    /** Deletes a schedule; the work it recorded stays in the history as one-off records. */
    fun deleteSchedule(id: String) = update { d -> d.copy(schedules = d.schedules.filterNot { it.id == id }, maintenance = d.maintenance.map { if (it.scheduleId == id) it.copy(scheduleId = null) else it }) }

    /**
     * Records the schedule's work as done on [day] (default today) and moves it on by its policy (BASE.md 4.5). Pays Habitat Discovery when it is on and the
     * day's share is not used; the same due date of the same schedule pays once. [cost] is informational text: it becomes no transaction.
     */
    fun completeMaintenance(scheduleId: String, notes: String = "", cost: String = "", day: LocalDate = today): Logged<MaintenanceRecord> {
        val s = data.schedules.firstOrNull { it.id == scheduleId } ?: return Logged(null, "That schedule is gone.")
        if (s.status == RecordStatus.ARCHIVED) return Logged(null, "That schedule is finished.")
        if (day > today) return Logged(null, "That day has not come yet.")
        val before = data
        val r = MaintenanceRecord(newId(), s.title, s.assetId, s.id, day, notes.trim().take(BaseRules.NOTE_MAX), cost.trim().take(BaseRules.TITLE_MAX), nowMillis)
        update { d -> d.copy(maintenance = d.maintenance + r, schedules = d.schedules.map { if (it.id == s.id) BaseRules.advance(s, day) else it }) }
        val rewards = if (BaseRules.maintenancePays(before, day)) pay(BaseKind.MAINTENANCE, "${s.id}:${s.nextDue}", r.id) else null
        if (rewards != null) update { d -> d.copy(maintenance = d.maintenance.map { if (it.id == r.id) it.copy(paid = true) else it }) }
        return Logged(data.maintenance.first { it.id == r.id }, rewards = rewards.orEmpty())
    }

    /** Records one piece of work done that is on no schedule. Pays like [completeMaintenance]; it can be paid once per record. */
    fun logMaintenance(title: String, assetId: String? = null, notes: String = "", cost: String = "", day: LocalDate = today): Logged<MaintenanceRecord> {
        (BaseRules.checkTitle(title, "work") as? Check.Refused)?.let { return Logged(null, it.reason) }
        if (day > today) return Logged(null, "That day has not come yet.")
        val before = data
        val r = MaintenanceRecord(newId(), title.trim(), assetId, null, day, notes.trim().take(BaseRules.NOTE_MAX), cost.trim().take(BaseRules.TITLE_MAX), nowMillis)
        update { it.copy(maintenance = it.maintenance + r) }
        val rewards = if (BaseRules.maintenancePays(before, day)) pay(BaseKind.MAINTENANCE, r.id, r.id) else null
        if (rewards != null) update { d -> d.copy(maintenance = d.maintenance.map { if (it.id == r.id) it.copy(paid = true) else it }) }
        return Logged(data.maintenance.first { it.id == r.id }, rewards = rewards.orEmpty())
    }

    /** Corrects a record of work: its words, item, day, notes and cost. What it paid stays as it was: an edit never pays and never takes back. */
    fun updateMaintenanceRecord(r: MaintenanceRecord): Logged<MaintenanceRecord> {
        val old = data.maintenance.firstOrNull { it.id == r.id } ?: return Logged(null, "That record is gone.")
        (BaseRules.checkTitle(r.title, "work") as? Check.Refused)?.let { return Logged(null, it.reason) }
        if (r.day > today) return Logged(null, "That day has not come yet.")
        val next = old.copy(title = r.title.trim(), assetId = r.assetId, day = r.day, notes = r.notes.trim().take(BaseRules.NOTE_MAX), cost = r.cost.trim().take(BaseRules.TITLE_MAX))
        update { d -> d.copy(maintenance = d.maintenance.map { if (it.id == r.id) next else it }) }
        return Logged(next)
    }

    fun deleteMaintenanceRecord(id: String) = update { d -> d.copy(maintenance = d.maintenance.filterNot { it.id == id }) }

    // --- reference notes ---------------------------------------------------------------------------------------------------

    fun addReference(title: String, text: String = "", category: String = "", areaId: String? = null, assetId: String? = null): Logged<Reference> {
        (BaseRules.checkTitle(title, "note") as? Check.Refused)?.let { return Logged(null, it.reason) }
        if (text.length > BaseRules.TEXT_MAX) return Logged(null, "That note is too long.")
        val r = Reference(newId(), title.trim(), text.trim(), category.trim().take(BaseRules.TITLE_MAX), areaId, assetId, nowMillis, nowMillis)
        update { it.copy(references = it.references + r) }
        return Logged(r)
    }

    fun updateReference(r: Reference): Logged<Reference> {
        val old = data.references.firstOrNull { it.id == r.id } ?: return Logged(null, "That note is gone.")
        (BaseRules.checkTitle(r.title, "note") as? Check.Refused)?.let { return Logged(null, it.reason) }
        if (r.text.length > BaseRules.TEXT_MAX) return Logged(null, "That note is too long.")
        val next = old.copy(title = r.title.trim(), text = r.text.trim(), category = r.category.trim().take(BaseRules.TITLE_MAX), areaId = r.areaId, assetId = r.assetId, updatedMillis = nowMillis)
        update { d -> d.copy(references = d.references.map { if (it.id == r.id) next else it }) }
        return Logged(next)
    }

    fun deleteReference(id: String) = update { d -> d.copy(references = d.references.filterNot { it.id == id }) }

    // --- search ------------------------------------------------------------------------------------------------------------

    fun search(text: String) = BaseRules.search(data, text)

    // --- settings ------------------------------------------------------------------------------------------------------------

    private fun settings(change: (BaseSettings) -> BaseSettings) = update { it.copy(settings = change(it.settings)) }

    /** Turns Habitat Discovery (the reward) on or off. Off never touches a record and never hides a feature. */
    fun setProgression(on: Boolean) = settings { it.copy(progression = on) }

    fun markTutorialSeen() = settings { it.copy(tutorialSeen = true) }

    fun addUnit(unit: String) = settings { s -> unit.trim().take(16).let { u -> if (u.isEmpty() || s.units.any { it.equals(u, ignoreCase = true) }) s else s.copy(units = s.units + u) } }

    /** Folds an Overview section away or brings it back ("chores", "maintenance", "supplies"). */
    fun setSectionHidden(section: String, hidden: Boolean) = settings { s -> s.copy(hidden = if (hidden) (s.hidden + section).distinct() else s.hidden - section) }

    // --- reminders (BASE.md 6): all off until the user turns them on, per record --------------------------------------------------

    /** The master switch. On, a record still asks only when its own reminder is on. Neither pays nor changes a record. */
    fun setReminders(on: Boolean) = settings { it.copy(reminders = on) }

    fun setReminderHour(hour: Int) = settings { it.copy(reminderHour = hour.coerceIn(0, 23)) }

    fun setMaintenanceLeadDays(days: Int) = settings { it.copy(maintenanceLeadDays = days.coerceIn(0, MAX_LEAD_DAYS)) }

    fun setWarrantyLeadDays(days: Int) = settings { it.copy(warrantyLeadDays = days.coerceIn(0, MAX_WARRANTY_LEAD_DAYS)) }

    /** Lets a notification name the chore, supply or work once the phone is unlocked; the lock screen never does. */
    fun setReminderNames(on: Boolean) = settings { it.copy(reminderNames = on) }

    /** Turns a chore's own reminder on or off. The first reminder set also turns Base's reminder switch (Base → Settings → Reminders) on, so "Remind me" is always offered (REP-029 a). */
    fun setChoreRemind(id: String, on: Boolean) = update { d ->
        d.copy(chores = d.chores.map { if (it.id == id) it.copy(remind = on) else it }, settings = if (on && !d.settings.reminders) d.settings.copy(reminders = true) else d.settings)
    }

    fun setScheduleRemind(id: String, on: Boolean) = update { d -> d.copy(schedules = d.schedules.map { if (it.id == id) it.copy(remind = on) else it }) }

    fun setSupplyRemind(id: String, on: Boolean) = update { d -> d.copy(supplies = d.supplies.map { if (it.id == id) it.copy(remind = on) else it }) }

    fun setWarrantyRemind(id: String, on: Boolean) = update { d -> d.copy(warranties = d.warranties.map { if (it.id == id) it.copy(remind = on) else it }) }

    // --- export and erase (one kind at a time, or all) ----------------------------------------------------------------------

    /** The chosen kinds of record (all by default) as readable JSON, exactly as saved. Nothing of the game is in it. */
    fun export(kinds: Set<BaseKinds> = BaseKinds.entries.toSet()): String = BaseExport.json(data, kinds)

    /**
     * Erases every record of [kind]. Records that pointed at what was erased keep their own words and lose the pointer; nothing else is touched: other
     * kinds, the settings, Pokémon, Poké, the Bag and the other modules stay.
     */
    fun erase(kind: BaseKinds) = update { BaseExport.without(it, kind) }

    /** Erases all of HOME's records (not its settings, not what Habitat Discovery paid). */
    fun eraseAll() = update { d -> BaseKinds.entries.fold(d) { acc, k -> BaseExport.without(acc, k) } }

    companion object {
        const val MAX_LEAD_DAYS = 14
        const val MAX_WARRANTY_LEAD_DAYS = 90
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

        /** A document that was never offered the starting areas gets them once, with stable ids, so a fresh install can file a chore at once. */
        internal fun tidy(d: BaseData): BaseData = (if (d.room.isEmpty()) d else d.copy(room = Room.tidy(d.room.map { it.id }, d.room))).let { d -> if (d.settings.areasSeeded) d else d.copy(
            areas = d.areas + BaseRules.STARTING_AREAS.map { name -> BaseArea("area-" + name.lowercase().replace(' ', '-'), name) }.filter { s -> d.areas.none { it.id == s.id } },
            settings = d.settings.copy(areasSeeded = true),
        ) }

        /** "1.5" as thousandths (see [Quantity]); a convenience for screens. */
        fun milli(text: String): Long? = Quantity.parse(text)
    }
}

/** Export and erase by kind, pure and free of the game: one place that knows which fields of the document each kind is. */
object BaseExport {
    private val pretty = Json { encodeDefaults = true; prettyPrint = true }

    private val fields: Map<BaseKinds, List<String>> = mapOf(
        BaseKinds.AREAS to listOf("areas"),
        BaseKinds.CHORES to listOf("chores", "entries"),
        BaseKinds.SUPPLIES to listOf("supplies", "adjustments", "restock"),
        BaseKinds.ASSETS to listOf("assets", "schedules", "maintenance", "warranties"),
        BaseKinds.REFERENCES to listOf("references"),
        BaseKinds.ROOM to listOf("room"),
    )

    fun json(data: BaseData, kinds: Set<BaseKinds>): String {
        val all = BaseStore.json.encodeToJsonElement(BaseData.serializer(), data) as JsonObject
        val chosen = all.filterKeys { key -> fields.any { (kind, names) -> kind in kinds && key in names } }
        return pretty.encodeToString(JsonObject.serializer(), JsonObject(mapOf("format" to JsonPrimitive("pokelife-home-export"), "unit" to JsonPrimitive("quantities are in thousandths of their unit")) + chosen))
    }

    fun without(d: BaseData, kind: BaseKinds): BaseData = when (kind) {
        BaseKinds.AREAS -> d.copy(
            areas = emptyList(), chores = d.chores.map { it.copy(areaId = null) }, supplies = d.supplies.map { it.copy(areaId = null) },
            assets = d.assets.map { it.copy(areaId = null) }, references = d.references.map { it.copy(areaId = null) },
        )
        BaseKinds.CHORES -> d.copy(chores = emptyList(), entries = emptyList())
        BaseKinds.SUPPLIES -> d.copy(supplies = emptyList(), adjustments = emptyList(), restock = emptyList())
        BaseKinds.ASSETS -> d.copy(
            assets = emptyList(), schedules = emptyList(), maintenance = emptyList(), warranties = emptyList(),
            references = d.references.map { it.copy(assetId = null) },
        )
        BaseKinds.REFERENCES -> d.copy(references = emptyList())
        BaseKinds.ROOM -> d.copy(room = emptyList())
    }
}
