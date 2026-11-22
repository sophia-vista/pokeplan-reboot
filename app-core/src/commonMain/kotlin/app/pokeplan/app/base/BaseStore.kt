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

    fun reload() = document.reload()

    fun sync() = document.sync()

    private fun update(change: (BaseData) -> BaseData) = document.update(change)

    fun restore(snapshot: BaseData) = update { snapshot }

    private fun newId(): String = newId(nowMillis, rng)

    private fun pay(kind: BaseKind, id: String, ref: String): List<Reward>? =
        if (!data.settings.progression) null else world.base(BaseEvent("home:${kind.name.lowercase()}:$id", kind, nowMillis, ref))

    val room: List<Placement> get() = data.room.filter { it.id in world.ownedDecorations() }

    fun inBox(): List<Decoration> = Room.inBox(world.ownedDecorations(), data.room)

    private fun arrange(result: Room.Result): String? = when (result) {
        is Room.Result.Done -> { update { it.copy(room = result.placed) }; null }
        is Room.Result.Refused -> result.reason
    }

    fun placeDecoration(id: String, x: Int, y: Int): String? = arrange(Room.place(world.ownedDecorations(), data.room, id, x, y))

    fun moveDecoration(id: String, x: Int, y: Int): String? = arrange(Room.move(data.room, id, x, y))

    fun removeDecoration(id: String): String? = arrange(Room.remove(data.room, id))

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

    fun setAreaArchived(id: String, archived: Boolean) = update { d -> d.copy(areas = d.areas.map { if (it.id == id) it.copy(archived = archived) else it }) }

    fun moveArea(id: String, up: Boolean) = update { d ->
        val i = d.areas.indexOfFirst { it.id == id }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in d.areas.indices) d else d.copy(areas = d.areas.toMutableList().also { l -> val t = l[i]; l[i] = l[j]; l[j] = t })
    }

    fun deleteArea(id: String) = update { d ->
        d.copy(
            areas = d.areas.filterNot { it.id == id },
            chores = d.chores.map { if (it.areaId == id) it.copy(areaId = null) else it },
            supplies = d.supplies.map { if (it.areaId == id) it.copy(areaId = null) else it },
            assets = d.assets.map { if (it.areaId == id) it.copy(areaId = null) else it },
            references = d.references.map { if (it.areaId == id) it.copy(areaId = null) else it },
        )
    }

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

    fun setChoreStatus(id: String, status: RecordStatus) = update { d -> d.copy(chores = d.chores.map { if (it.id == id) it.copy(status = status, updatedMillis = nowMillis) else it }) }

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

    fun complete(choreId: String, note: String = ""): Logged<ChoreEntry> {
        val (chore, occurrence) = openOccurrence(choreId) ?: return Logged(null, "That chore is not waiting.")
        val before = data
        val entry = ChoreEntry(newId(), chore.id, occurrence, EntryStatus.DONE, today, nowMillis, note.trim().take(BaseRules.NOTE_MAX))
        putEntry(entry)
        val rewards = if (BaseRules.chorePays(before, today)) pay(BaseKind.CHORE, "${chore.id}:$occurrence", entry.id) else null
        if (rewards != null) update { d -> d.copy(entries = d.entries.map { if (it.id == entry.id) it.copy(paid = true) else it }) }
        return Logged(data.entries.first { it.id == entry.id }, rewards = rewards.orEmpty())
    }

    fun skip(choreId: String, note: String = ""): Boolean {
        val (chore, occurrence) = openOccurrence(choreId) ?: return false
        putEntry(ChoreEntry(newId(), chore.id, occurrence, EntryStatus.SKIPPED, today, nowMillis, note.trim().take(BaseRules.NOTE_MAX)))
        return true
    }

    fun defer(choreId: String, to: LocalDate): Boolean {
        if (to <= today) return false
        val (chore, occurrence) = openOccurrence(choreId) ?: return false
        putEntry(ChoreEntry(newId(), chore.id, occurrence, EntryStatus.DEFERRED, today, nowMillis, deferTo = to))
        return true
    }

    fun undo(entryId: String) = update { d -> d.copy(entries = d.entries.filterNot { it.id == entryId }) }

    fun addSupply(name: String, unit: String = "", quantityMilli: Long? = null, lowMilli: Long? = null, category: String = "", areaId: String? = null, notes: String = ""): Logged<Supply> {
        val s = Supply(newId(), name.trim(), category.trim().take(BaseRules.TITLE_MAX), quantityMilli, unit.trim(), lowMilli, areaId, notes.trim(), createdMillis = nowMillis, updatedMillis = nowMillis)
        (BaseRules.checkSupply(s) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { it.copy(supplies = it.supplies + s) }
        return Logged(s)
    }

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

    fun setQuantity(id: String, milli: Long?, reason: String = ""): String? {
        if (data.supplies.none { it.id == id }) return "That supply is gone."
        (BaseRules.checkQuantity(milli) as? Check.Refused)?.let { return it.reason }
        changeQuantity(id, milli, reason)
        return null
    }

    fun adjust(id: String, deltaMilli: Long, reason: String = ""): String? {
        val s = data.supplies.firstOrNull { it.id == id } ?: return "That supply is gone."
        val now = s.quantityMilli ?: return "The quantity is unknown. Set it first."
        val after = now + deltaMilli
        if (after < 0) return "That is more than is left. Set the quantity instead."
        (BaseRules.checkQuantity(after) as? Check.Refused)?.let { return it.reason }
        changeQuantity(id, after, reason)
        return null
    }

    fun dismissLow(id: String) = update { d -> d.copy(supplies = d.supplies.map { if (it.id == id) it.copy(lowDismissed = true) else it }) }

    fun setSupplyArchived(id: String, archived: Boolean) = update { d -> d.copy(supplies = d.supplies.map { if (it.id == id) it.copy(archived = archived, updatedMillis = nowMillis) else it }) }

    fun deleteSupply(id: String) = update { d -> d.copy(supplies = d.supplies.filterNot { it.id == id }, adjustments = d.adjustments.filterNot { it.supplyId == id }, restock = d.restock.filterNot { it.supplyId == id }) }

    fun addRestock(name: String, supplyId: String? = null): RestockEntry? {
        val label = name.trim().take(BaseRules.TITLE_MAX).ifEmpty { data.supplies.firstOrNull { it.id == supplyId }?.name ?: return null }
        if (supplyId != null && data.restock.any { it.supplyId == supplyId }) return null
        val e = RestockEntry(newId(), label, supplyId, nowMillis)
        update { it.copy(restock = it.restock + e) }
        return e
    }

    fun removeRestock(id: String) = update { d -> d.copy(restock = d.restock.filterNot { it.id == id }) }

    fun restocked(supplyId: String, newQuantityMilli: Long?, entryId: String? = null): String? {
        if (newQuantityMilli == null && entryId == null && data.supplies.any { it.id == supplyId && BaseRules.isLow(it) }) return "Enter how much there is now."
        val reason = newQuantityMilli?.let { setQuantity(supplyId, it, "Restocked") }
        if (reason != null) return reason
        update { d -> d.copy(restock = d.restock.filterNot { it.id == entryId || it.supplyId == supplyId }) }
        return null
    }

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

    fun addSchedule(title: String, firstDue: LocalDate?, recurrence: Recurrence = Recurrence(), policy: SchedulePolicy = SchedulePolicy.FIXED, assetId: String? = null, instructions: String = ""): Logged<MaintenanceSchedule> {
        (BaseRules.checkTitle(title, "maintenance") as? Check.Refused)?.let { return Logged(null, it.reason) }
        val first = firstDue ?: defaultFirstDue(recurrence)
        (BaseRules.checkRecurrence(recurrence, first) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val s = MaintenanceSchedule(newId(), title.trim(), assetId, instructions.trim().take(BaseRules.TEXT_MAX), recurrence, policy, first, first, createdMillis = nowMillis)
        update { it.copy(schedules = it.schedules + s) }
        return Logged(s)
    }

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

    fun updateSchedule(s: MaintenanceSchedule): Logged<MaintenanceSchedule> {
        val old = data.schedules.firstOrNull { it.id == s.id } ?: return Logged(null, "That schedule is gone.")
        (BaseRules.checkTitle(s.title, "maintenance") as? Check.Refused)?.let { return Logged(null, it.reason) }
        (BaseRules.checkRecurrence(s.recurrence, s.nextDue) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val next = old.copy(title = s.title.trim(), assetId = s.assetId, instructions = s.instructions.trim().take(BaseRules.TEXT_MAX), recurrence = s.recurrence, policy = s.policy, firstDue = if (s.nextDue != old.nextDue || s.recurrence.kind != old.recurrence.kind) s.nextDue else old.firstDue, nextDue = s.nextDue)
        update { d -> d.copy(schedules = d.schedules.map { if (it.id == s.id) next else it }) }
        return Logged(next)
    }

    fun setScheduleStatus(id: String, status: RecordStatus) = update { d -> d.copy(schedules = d.schedules.map { if (it.id == id) it.copy(status = status) else it }) }

    fun deleteSchedule(id: String) = update { d -> d.copy(schedules = d.schedules.filterNot { it.id == id }, maintenance = d.maintenance.map { if (it.scheduleId == id) it.copy(scheduleId = null) else it }) }

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

    fun updateMaintenanceRecord(r: MaintenanceRecord): Logged<MaintenanceRecord> {
        val old = data.maintenance.firstOrNull { it.id == r.id } ?: return Logged(null, "That record is gone.")
        (BaseRules.checkTitle(r.title, "work") as? Check.Refused)?.let { return Logged(null, it.reason) }
        if (r.day > today) return Logged(null, "That day has not come yet.")
        val next = old.copy(title = r.title.trim(), assetId = r.assetId, day = r.day, notes = r.notes.trim().take(BaseRules.NOTE_MAX), cost = r.cost.trim().take(BaseRules.TITLE_MAX))
        update { d -> d.copy(maintenance = d.maintenance.map { if (it.id == r.id) next else it }) }
        return Logged(next)
    }

    fun deleteMaintenanceRecord(id: String) = update { d -> d.copy(maintenance = d.maintenance.filterNot { it.id == id }) }

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

    fun search(text: String) = BaseRules.search(data, text)

    private fun settings(change: (BaseSettings) -> BaseSettings) = update { it.copy(settings = change(it.settings)) }

    fun setProgression(on: Boolean) = settings { it.copy(progression = on) }

    fun markTutorialSeen() = settings { it.copy(tutorialSeen = true) }

    fun addUnit(unit: String) = settings { s -> unit.trim().take(16).let { u -> if (u.isEmpty() || s.units.any { it.equals(u, ignoreCase = true) }) s else s.copy(units = s.units + u) } }

    fun setSectionHidden(section: String, hidden: Boolean) = settings { s -> s.copy(hidden = if (hidden) (s.hidden + section).distinct() else s.hidden - section) }

    fun setReminders(on: Boolean) = settings { it.copy(reminders = on) }

    fun setReminderHour(hour: Int) = settings { it.copy(reminderHour = hour.coerceIn(0, 23)) }

    fun setMaintenanceLeadDays(days: Int) = settings { it.copy(maintenanceLeadDays = days.coerceIn(0, MAX_LEAD_DAYS)) }

    fun setWarrantyLeadDays(days: Int) = settings { it.copy(warrantyLeadDays = days.coerceIn(0, MAX_WARRANTY_LEAD_DAYS)) }

    fun setReminderNames(on: Boolean) = settings { it.copy(reminderNames = on) }

    fun setChoreRemind(id: String, on: Boolean) = update { d ->
        d.copy(chores = d.chores.map { if (it.id == id) it.copy(remind = on) else it }, settings = if (on && !d.settings.reminders) d.settings.copy(reminders = true) else d.settings)
    }

    fun setScheduleRemind(id: String, on: Boolean) = update { d -> d.copy(schedules = d.schedules.map { if (it.id == id) it.copy(remind = on) else it }) }

    fun setSupplyRemind(id: String, on: Boolean) = update { d -> d.copy(supplies = d.supplies.map { if (it.id == id) it.copy(remind = on) else it }) }

    fun setWarrantyRemind(id: String, on: Boolean) = update { d -> d.copy(warranties = d.warranties.map { if (it.id == id) it.copy(remind = on) else it }) }

    fun export(kinds: Set<BaseKinds> = BaseKinds.entries.toSet()): String = BaseExport.json(data, kinds)

    fun erase(kind: BaseKinds) = update { BaseExport.without(it, kind) }

    fun eraseAll() = update { d -> BaseKinds.entries.fold(d) { acc, k -> BaseExport.without(acc, k) } }

    fun resetAll() = update { BaseData() }

    companion object {
        const val MAX_LEAD_DAYS = 14
        const val MAX_WARRANTY_LEAD_DAYS = 90
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

        internal fun tidy(d: BaseData): BaseData = (if (d.room.isEmpty()) d else d.copy(room = Room.tidy(d.room.map { it.id }, d.room))).let { d -> if (d.settings.areasSeeded) d else d.copy(
            areas = d.areas + BaseRules.STARTING_AREAS.map { name -> BaseArea("area-" + name.lowercase().replace(' ', '-'), name) }.filter { s -> d.areas.none { it.id == s.id } },
            settings = d.settings.copy(areasSeeded = true),
        ) }

        fun milli(text: String): Long? = Quantity.parse(text)
    }
}

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
