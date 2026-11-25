package app.pokeplan.app.link

import app.pokeplan.core.reward.LinkEvent
import app.pokeplan.core.reward.LinkMoment
import app.pokeplan.core.reward.Reward
import app.pokeplan.core.reward.SafariEvent
import app.pokeplan.core.reward.SafariRules
import app.pokeplan.core.reward.OutbreakOption
import app.pokeplan.app.SafariDay
import app.pokeplan.link.Check
import app.pokeplan.link.DateKind
import app.pokeplan.link.Era
import app.pokeplan.link.FollowUp
import app.pokeplan.link.FollowUpState
import app.pokeplan.link.FollowUpStatus
import app.pokeplan.link.ImportantDate
import app.pokeplan.link.LinkData
import app.pokeplan.link.LinkGroup
import app.pokeplan.link.LinkKinds
import app.pokeplan.link.LinkRules
import app.pokeplan.link.LinkSettings
import app.pokeplan.link.Memory
import app.pokeplan.link.PartialDate
import app.pokeplan.link.Person
import app.pokeplan.link.Profile
import app.pokeplan.link.ProfileKit
import app.pokeplan.link.birthdayOf
import app.pokeplan.link.PersonNote
import app.pokeplan.link.Sort
import app.pokeplan.plan.toLocalDateTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random
import app.pokeplan.app.FollowUpTask
import app.pokeplan.app.World
import app.pokeplan.app.heal.Logged
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.SavedDocument
import app.pokeplan.app.storage.Storage
import app.pokeplan.app.storage.newId

class LinkStore(
    storage: Storage,
    private val clock: Clock,
    private val world: World,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val rng: Random = Random,
) {
    private val document = SavedDocument(storage, LinkData.serializer(), json) { LinkData() }

    val data: LinkData get() = document.value

    /** Gives the main category back to people a group replaced it for, once (0.243.0); nothing to do after that. */
    private fun migrate() {
        val next = LinkRules.withCategoriesBack(document.value)
        if (next != document.value) document.update { next }
    }

    init { migrate() }

    fun observe(listener: (LinkData) -> Unit): () -> Unit = document.observe(listener)

    private val nowMillis: Long get() = clock.nowMillis()
    val today: LocalDate get() = nowMillis.toLocalDateTime(zone).date

    fun reload() = document.reload()

    fun sync() = document.sync()

    private fun update(change: (LinkData) -> LinkData) = document.update(change)

    fun restore(snapshot: LinkData) = update { snapshot }

    private fun newId(): String = newId(nowMillis, rng)

    private fun pay(id: String, ref: String): List<Reward>? =
        if (!data.settings.postcardTrail) null else world.link(LinkEvent("link:followup:$id", LinkMoment.FOLLOW_UP, nowMillis, ref))

    val safariOn: Boolean get() = data.settings.postcardTrail && data.settings.safari

    fun safariPool(personId: String): List<OutbreakOption> =
        if (data.people.none { it.id == personId }) emptyList() else SafariRules.pool(LinkRules.safariRef(personId), LinkRules.safariSpread(data))

    private fun safariEvent(personId: String) =
        SafariEvent("link:safari:${LinkRules.safariRef(personId)}:${today.toEpochDays()}", nowMillis, LinkRules.safariRef(personId), LinkRules.safariSpread(data))

    fun safariToday(personId: String): SafariDay = world.safariDay(safariEvent(personId).id)

    fun visitSafari(personId: String): List<Reward>? =
        if (!safariOn || data.people.none { it.id == personId }) null else world.safari(safariEvent(personId))

    fun addPerson(
        name: String, preferred: String = "", phone: String = "", email: String = "", organization: String = "", role: String = "", address: String = "",
        category: String = "", tags: List<String> = emptyList(), favorite: Boolean = false,
    ): Logged<Person> {
        val p = Person(
            newId(), name.trim(), preferred.trim(), phone.trim(), email.trim(), organization.trim(), role.trim(), address.trim(), category.trim(),
            tags.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }, favorite, nowMillis, nowMillis,
        )
        (LinkRules.checkPerson(p) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { it.copy(people = it.people + p) }
        migrate()
        return Logged(data.people.firstOrNull { it.id == p.id } ?: p)
    }

    fun updatePerson(p: Person): Logged<Person> {
        val old = data.people.firstOrNull { it.id == p.id } ?: return Logged(null, "That person is gone.")
        val next = old.copy(
            name = p.name.trim(), preferred = p.preferred.trim(), phone = p.phone.trim(), email = p.email.trim(), organization = p.organization.trim(),
            role = p.role.trim(), address = p.address.trim(), category = p.category.trim(), tags = p.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() },
            favorite = p.favorite, updatedMillis = nowMillis,
            groupIds = p.groupIds.filter { id -> data.settings.groups.any { it.id == id } }.distinct(),
        )
        (LinkRules.checkPerson(next) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { d -> d.copy(people = d.people.map { if (it.id == p.id) next else it }) }
        migrate()
        return Logged(data.people.firstOrNull { it.id == p.id } ?: next)
    }

    fun createMe(name: String): Logged<Person> {
        data.people.firstOrNull { it.isMe }?.let { return Logged(it) }
        val out = addPerson(name.ifBlank { "Me" }, category = "Me")
        val made = out.record ?: return out
        val me = made.copy(isMe = true)
        update { d -> d.copy(people = d.people.map { if (it.id == me.id) me else it }) }
        return Logged(me)
    }

    fun updateProfile(personId: String, profile: Profile): Logged<Person> {
        val old = data.people.firstOrNull { it.id == personId } ?: return Logged(null, "That person is gone.")
        val next = old.copy(profile = ProfileKit.tidy(profile), updatedMillis = nowMillis)
        update { d -> d.copy(people = d.people.map { if (it.id == personId) next else it }) }
        return Logged(next)
    }

    fun setBirthday(personId: String, month: Int, day: Int, year: Int?): Logged<ImportantDate> {
        val old = data.birthdayOf(personId)
        return if (old == null) addDate(DateKind.BIRTHDAY, "Birthday", month, day, year, annual = true, personId = personId)
        else updateDate(old.copy(month = month, day = day, year = year))
    }

    fun setAvatar(id: String, speciesId: Int) = update { d -> d.copy(people = d.people.map { if (it.id == id) it.copy(avatar = speciesId.coerceAtLeast(0)) else it }) }

    fun setFavorite(id: String, favorite: Boolean) = update { d -> d.copy(people = d.people.map { if (it.id == id) it.copy(favorite = favorite) else it }) }

    fun possibleDuplicates(name: String, phone: String = "", email: String = "", exceptId: String? = null) = LinkRules.possibleDuplicates(data, name, phone, email, exceptId)

    fun directory(query: String = "", category: String? = null, tag: String? = null, favoritesOnly: Boolean = false, sort: Sort = Sort.NAME, group: String? = null) =
        LinkRules.directory(data, query, category, tag, favoritesOnly, sort, group)

    /** Makes a group (a suggested one or the player's own); a name already used is refused. */
    fun addGroup(name: String): Logged<LinkGroup> {
        (LinkRules.checkGroup(data, name) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val group = LinkGroup(newId(), name.trim())
        settings { it.copy(groups = it.groups + group) }
        return Logged(group)
    }

    fun renameGroup(id: String, name: String): Logged<LinkGroup> {
        val old = data.settings.groups.firstOrNull { it.id == id } ?: return Logged(null, "That group is gone.")
        (LinkRules.checkGroup(data, name, exceptId = id) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val next = old.copy(name = name.trim())
        settings { s -> s.copy(groups = s.groups.map { if (it.id == id) next else it }) }
        return Logged(next)
    }

    /** Deletes the group only: nobody is deleted, they just stop being in it. */
    fun deleteGroup(id: String) = update { d ->
        d.copy(
            people = d.people.map { p -> if (id in p.groupIds) p.copy(groupIds = p.groupIds - id) else p },
            settings = d.settings.copy(groups = d.settings.groups.filterNot { it.id == id }),
        )
    }

    fun setGroups(personId: String, groupIds: List<String>) = update { d ->
        val known = d.settings.groups.map { it.id }.toSet()
        d.copy(people = d.people.map { if (it.id == personId) it.copy(groupIds = groupIds.filter { g -> g in known }.distinct(), updatedMillis = nowMillis) else it })
    }

    /** "My order": move a person one place up or down among the people the screen shows. Only the player arranges anyone. */
    fun moveInOrder(personId: String, visible: List<String>, step: Int) {
        val order = LinkRules.moved(data, visible, personId, step)
        if (order != data.settings.order) settings { it.copy(order = order) }
    }

    fun deletePerson(id: String, keepDates: Boolean, keepFollowUps: Boolean) = update { d ->
        d.copy(
            people = d.people.filterNot { it.id == id },
            notes = d.notes.filterNot { it.personId == id },
            dates = if (keepDates) d.dates.map { if (it.personId == id) it.copy(personId = null) else it } else d.dates.filterNot { it.personId == id },
            followUps = if (keepFollowUps) d.followUps.map { if (it.personId == id) it.copy(personId = null) else it } else d.followUps.filterNot { it.personId == id },
            memories = d.memories.map { m -> if (id in m.personIds) m.copy(personIds = m.personIds - id) else m },
        )
    }

    fun addNote(personId: String, text: String): Logged<PersonNote> {
        if (data.people.none { it.id == personId }) return Logged(null, "That person is gone.")
        (LinkRules.checkNote(text) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val n = PersonNote(newId(), personId, text.trim(), nowMillis)
        update { it.copy(notes = it.notes + n, people = it.people.map { p -> if (p.id == personId) p.copy(updatedMillis = nowMillis) else p }) }
        return Logged(n)
    }

    fun updateNote(id: String, text: String): Logged<PersonNote> {
        val old = data.notes.firstOrNull { it.id == id } ?: return Logged(null, "That note is gone.")
        (LinkRules.checkNote(text) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val next = old.copy(text = text.trim())
        update { d -> d.copy(notes = d.notes.map { if (it.id == id) next else it }) }
        return Logged(next)
    }

    fun deleteNote(id: String) = update { d -> d.copy(notes = d.notes.filterNot { it.id == id }) }

    fun notesOf(personId: String): List<PersonNote> = data.notes.filter { it.personId == personId }.sortedByDescending { it.atMillis }

    fun addDate(kind: DateKind, title: String, month: Int, day: Int, year: Int? = null, annual: Boolean = true, personId: String? = null, note: String = "", remindDays: Int? = null): Logged<ImportantDate> {
        val remind = remindDays?.takeIf { it in LinkRules.REMIND_PRESETS }
        val d = ImportantDate(newId(), kind, title.trim(), month, day, year, annual, personId?.takeIf { id -> data.people.any { it.id == id } }, note.trim(), nowMillis, remind)
        (LinkRules.checkDate(d) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { it.copy(dates = it.dates + d, settings = if (remind != null) it.settings.copy(reminders = true) else it.settings) }
        return Logged(d)
    }

    fun updateDate(d: ImportantDate): Logged<ImportantDate> {
        val old = data.dates.firstOrNull { it.id == d.id } ?: return Logged(null, "That date is gone.")
        val next = old.copy(
            kind = d.kind, title = d.title.trim(), month = d.month, day = d.day, year = d.year, annual = d.annual,
            personId = d.personId?.takeIf { id -> data.people.any { it.id == id } }, note = d.note.trim(),
        )
        (LinkRules.checkDate(next) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { dd -> dd.copy(dates = dd.dates.map { if (it.id == d.id) next else it }) }
        return Logged(next)
    }

    fun deleteDate(id: String) = update { d -> d.copy(dates = d.dates.filterNot { it.id == id }) }

    fun memories(): List<Memory> = LinkRules.memoryOrder(data.memories)

    fun memoriesOf(personId: String): List<Memory> = LinkRules.memoriesOf(data, personId)

    fun memoriesInEra(eraId: String): List<Memory> = LinkRules.memoriesInEra(data, eraId)

    fun eras(): List<Era> = data.eras

    fun onThisDay(day: LocalDate = today): List<Memory> = LinkRules.onThisDay(data, day)

    fun metUp(personId: String): LocalDate? = LinkRules.metUpOf(data, personId)

    fun addMemory(
        title: String, date: PartialDate = PartialDate(), note: String = "", time: String = "", place: String = "",
        personIds: List<String> = emptyList(), eraIds: List<String> = emptyList(),
    ): Logged<Memory> {
        val m = Memory(
            newId(), title.trim(), note.trim(), date, time.trim(), place.trim(),
            personIds.filter { id -> data.people.any { it.id == id } }.distinct(),
            eraIds.filter { id -> data.eras.any { it.id == id } }.distinct(),
            nowMillis, nowMillis,
        )
        (LinkRules.checkMemory(m) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { it.copy(memories = it.memories + m) }
        return Logged(m)
    }

    fun updateMemory(m: Memory): Logged<Memory> {
        val old = data.memories.firstOrNull { it.id == m.id } ?: return Logged(null, "That memory is gone.")
        val next = old.copy(
            title = m.title.trim(), note = m.note.trim(), date = m.date, time = m.time.trim(), place = m.place.trim(),
            personIds = m.personIds.filter { id -> data.people.any { it.id == id } }.distinct(),
            eraIds = m.eraIds.filter { id -> data.eras.any { it.id == id } }.distinct(),
            updatedMillis = nowMillis,
        )
        (LinkRules.checkMemory(next) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { d -> d.copy(memories = d.memories.map { if (it.id == m.id) next else it }) }
        return Logged(next)
    }

    fun deleteMemory(id: String) = update { d -> d.copy(memories = d.memories.filterNot { it.id == id }) }

    fun addEra(title: String, start: PartialDate = PartialDate(), end: PartialDate = PartialDate(), note: String = ""): Logged<Era> {
        val e = Era(newId(), title.trim(), note.trim(), start, end, nowMillis, nowMillis)
        (LinkRules.checkEra(e) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { it.copy(eras = it.eras + e) }
        return Logged(e)
    }

    fun updateEra(e: Era): Logged<Era> {
        val old = data.eras.firstOrNull { it.id == e.id } ?: return Logged(null, "That era is gone.")
        val next = old.copy(title = e.title.trim(), note = e.note.trim(), start = e.start, end = e.end, updatedMillis = nowMillis)
        (LinkRules.checkEra(next) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { d -> d.copy(eras = d.eras.map { if (it.id == e.id) next else it }) }
        return Logged(next)
    }

    /** Deletes the era only: its memories stay, without it. */
    fun deleteEra(id: String) = update { d ->
        d.copy(
            eras = d.eras.filterNot { it.id == id },
            memories = d.memories.map { m -> if (id in m.eraIds) m.copy(eraIds = m.eraIds - id) else m },
        )
    }

    fun upcoming(withinDays: Int = LinkRules.DATES_SOON_DAYS) = LinkRules.upcoming(data, today, withinDays)

    fun allDates() = LinkRules.allDates(data, today)

    fun planImport(cards: List<app.pokeplan.link.ContactCard>) = app.pokeplan.link.ContactImport.plan(data, cards)

    fun importContacts(picks: List<app.pokeplan.link.ImportPick>, remindDays: Int? = null): app.pokeplan.link.ImportResult {
        val remind = remindDays?.takeIf { it in LinkRules.REMIND_PRESETS }
        var added = 0; var linked = 0; var birthdays = 0; var skipped = 0
        update { d0 ->
            added = 0; linked = 0; birthdays = 0; skipped = 0
            var d = d0
            for (pick in picks) {
                val c = pick.card
                val existing = (pick.linkTo ?: d.people.firstOrNull { c.sourceId.isNotBlank() && it.sourceId == c.sourceId }?.id)?.let { id -> d.people.firstOrNull { it.id == id } }
                if (pick.linkTo != null && existing == null) { skipped++; continue }
                val person = if (existing != null) {
                    val filled = app.pokeplan.link.ContactImport.fill(existing, c, nowMillis)
                    if ((LinkRules.checkPerson(filled) as? Check.Refused) != null) { skipped++; continue }
                    if (filled != existing) { d = d.copy(people = d.people.map { if (it.id == existing.id) filled else it }); linked++ } else skipped++
                    filled
                } else {
                    val p = app.pokeplan.link.ContactImport.newPerson(c, newId(), nowMillis)
                    if ((LinkRules.checkPerson(p) as? Check.Refused) != null) { skipped++; continue }
                    d = d.copy(people = d.people + p); added++
                    p
                }
                val b = c.birthday
                if (b != null && d.dates.none { it.personId == person.id && it.kind == DateKind.BIRTHDAY }) {
                    val date = ImportantDate(newId(), DateKind.BIRTHDAY, "Birthday", b.month, b.day, b.year, true, person.id, "", nowMillis, remind)
                    if (LinkRules.checkDate(date) == Check.Ok) { d = d.copy(dates = d.dates + date); birthdays++ }
                }
            }
            if (remind != null && birthdays > 0) d = d.copy(settings = d.settings.copy(reminders = true))
            d
        }
        return app.pokeplan.link.ImportResult(added, linked, birthdays, skipped)
    }

    fun addFollowUp(title: String, personId: String? = null, due: LocalDate? = null, note: String = ""): Logged<FollowUp> {
        val f = FollowUp(newId(), title.trim(), personId?.takeIf { id -> data.people.any { it.id == id } }, due, note.trim(), createdMillis = nowMillis, updatedMillis = nowMillis)
        (LinkRules.checkFollowUp(f) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { it.copy(followUps = it.followUps + f) }
        return Logged(f)
    }

    fun updateFollowUp(f: FollowUp): Logged<FollowUp> {
        val old = data.followUps.firstOrNull { it.id == f.id } ?: return Logged(null, "That follow-up is gone.")
        val next = old.copy(title = f.title.trim(), personId = f.personId?.takeIf { id -> data.people.any { it.id == id } }, due = f.due, note = f.note.trim(), updatedMillis = nowMillis)
        (LinkRules.checkFollowUp(next) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { d -> d.copy(followUps = d.followUps.map { if (it.id == f.id) next else it }) }
        return Logged(next)
    }

    private fun change(id: String, to: (FollowUp) -> FollowUp) = update { d -> d.copy(followUps = d.followUps.map { if (it.id == id) to(it) else it }) }

    fun complete(id: String): Logged<FollowUp> {
        val f = data.followUps.firstOrNull { it.id == id } ?: return Logged(null, "That follow-up is gone.")
        if (f.status != FollowUpStatus.OPEN) return Logged(null, "That follow-up is not open.")
        if (world.followUpTask(f.id) != null) return Logged(null, IN_PLAN)
        val before = data
        val pays = LinkRules.pays(before, f, today)
        change(id) { it.copy(status = FollowUpStatus.COMPLETED, completedMillis = nowMillis, completedDay = today, updatedMillis = nowMillis) }
        val rewards = if (pays) pay(f.id, f.id) else null
        if (rewards != null) change(id) { it.copy(paid = true) }
        return Logged(data.followUps.first { it.id == id }, rewards = rewards.orEmpty())
    }

    fun convertToPlan(id: String): FollowUpTask? {
        val f = data.followUps.firstOrNull { it.id == id }?.takeIf { it.status == FollowUpStatus.OPEN } ?: return null
        val task = world.planFollowUp(f.id, f.title, f.due) ?: return null
        if (!f.converted) change(id) { it.copy(converted = true, updatedMillis = nowMillis) }
        return task
    }

    fun planTask(id: String): FollowUpTask? = world.followUpTask(id)

    fun syncFromPlan() {
        val tasks = data.followUps.filter { it.converted }.mapNotNull { f -> world.followUpTask(f.id)?.let { f.id to it } }.toMap()
        if (tasks.none { (id, t) -> data.followUps.first { it.id == id }.status.let { s -> (t.done && s == FollowUpStatus.OPEN) || (!t.done && s == FollowUpStatus.COMPLETED) } }) return
        update { d ->
            d.copy(followUps = d.followUps.map { f ->
                val t = tasks[f.id] ?: return@map f
                when {
                    t.done && f.status == FollowUpStatus.OPEN -> {
                        val at = t.doneAtMillis ?: nowMillis
                        f.copy(status = FollowUpStatus.COMPLETED, completedMillis = at, completedDay = at.toLocalDateTime(zone).date, updatedMillis = nowMillis)
                    }
                    !t.done && f.status == FollowUpStatus.COMPLETED -> f.copy(status = FollowUpStatus.OPEN, updatedMillis = nowMillis)
                    else -> f
                }
            })
        }
    }

    fun reopen(id: String) = if (world.followUpTask(id) != null) Unit else change(id) { if (it.status == FollowUpStatus.OPEN) it else it.copy(status = FollowUpStatus.OPEN, updatedMillis = nowMillis) }

    fun cancel(id: String) = if (world.followUpTask(id) != null) Unit else change(id) { if (it.status == FollowUpStatus.OPEN) it.copy(status = FollowUpStatus.CANCELLED, completedMillis = nowMillis, updatedMillis = nowMillis) else it }

    fun deleteFollowUp(id: String) = update { d -> d.copy(followUps = d.followUps.filterNot { it.id == id }) }

    fun openFollowUps(personId: String? = null) = LinkRules.open(data, today, personId)

    fun dueNow(): List<FollowUp> = openFollowUps().filter { LinkRules.state(it, today) == FollowUpState.DUE && world.followUpTask(it.id) == null }

    fun followUpHistory(personId: String? = null) = LinkRules.history(data, personId)

    fun search(text: String) = LinkRules.search(data, text)

    private fun settings(change: (LinkSettings) -> LinkSettings) = update { it.copy(settings = change(it.settings)) }

    fun setPostcardTrail(on: Boolean) = settings { it.copy(postcardTrail = on) }

    fun setSafari(on: Boolean) = settings { it.copy(safari = on) }

    fun markTutorialSeen() = settings { it.copy(tutorialSeen = true) }

    /** Adds a word of the player's own to the main category list, beside the starting ones. */
    fun addCategory(name: String) = settings { s ->
        name.trim().take(LinkRules.TAG_MAX).let { n -> if (n.isEmpty() || n.lowercase() in LinkRules.categories(LinkData(settings = s)).map { it.lowercase() }) s else s.copy(categories = s.categories + n) }
    }

    fun setReminders(on: Boolean) = settings { it.copy(reminders = on) }

    fun setReminderHour(hour: Int) = settings { it.copy(reminderHour = hour.coerceIn(0, 23)) }

    fun setReminderNames(on: Boolean) = settings { it.copy(reminderNames = on) }

    fun setDateRemind(id: String, days: Int?) {
        if (days != null && days !in LinkRules.REMIND_PRESETS) return
        update { d -> d.copy(dates = d.dates.map { if (it.id == id) it.copy(remindDays = days) else it }, settings = if (days != null) d.settings.copy(reminders = true) else d.settings) }
    }

    fun setFollowUpRemind(id: String, on: Boolean) {
        if (on && data.followUps.firstOrNull { it.id == id }?.converted == true) return
        change(id) { it.copy(remind = on) }
    }

    fun export(kinds: Set<LinkKinds> = LinkKinds.entries.toSet()): String = LinkExport.json(data, kinds)

    fun erase(kind: LinkKinds) = update { LinkExport.without(it, kind) }

    fun eraseAll() = update { d -> LinkKinds.entries.fold(d) { acc, k -> LinkExport.without(acc, k) } }

    fun resetAll() = update { LinkData() }

    companion object {
        const val IN_PLAN = "This one is in Plan now. Finish it there."

        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    }
}

object LinkExport {
    private val pretty = Json { encodeDefaults = true; prettyPrint = true }

    private val fields: Map<LinkKinds, List<String>> = mapOf(
        LinkKinds.PEOPLE to listOf("people", "notes"),
        LinkKinds.DATES to listOf("dates"),
        LinkKinds.FOLLOW_UPS to listOf("followUps"),
        LinkKinds.MEMORIES to listOf("memories", "eras"),
    )

    fun json(data: LinkData, kinds: Set<LinkKinds>): String {
        val all = LinkStore.json.encodeToJsonElement(LinkData.serializer(), data) as JsonObject
        val chosen = all.filterKeys { key -> fields.any { (kind, names) -> kind in kinds && key in names } }
        // The group names go with the people, since each person's groups are ids into this list.
        val groups = if (LinkKinds.PEOPLE in kinds) mapOf("groups" to LinkStore.json.encodeToJsonElement(ListSerializer(LinkGroup.serializer()), data.settings.groups)) else emptyMap()
        return pretty.encodeToString(JsonObject.serializer(), JsonObject(mapOf("format" to JsonPrimitive("pokelife-link-export")) + chosen + groups))
    }

    fun without(d: LinkData, kind: LinkKinds): LinkData = when (kind) {
        LinkKinds.PEOPLE -> d.copy(
            people = emptyList(), notes = emptyList(), dates = d.dates.map { it.copy(personId = null) }, followUps = d.followUps.map { it.copy(personId = null) },
            memories = d.memories.map { it.copy(personIds = emptyList()) },
            settings = d.settings.copy(groups = emptyList(), order = emptyList(), categories = emptyList()),
        )
        LinkKinds.DATES -> d.copy(dates = emptyList())
        LinkKinds.FOLLOW_UPS -> d.copy(followUps = emptyList())
        LinkKinds.MEMORIES -> d.copy(memories = emptyList(), eras = emptyList())
    }
}
