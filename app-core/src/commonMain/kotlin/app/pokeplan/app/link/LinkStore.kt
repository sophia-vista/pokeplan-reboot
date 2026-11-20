package app.pokeplan.app.link

import app.pokeplan.core.reward.LinkEvent
import app.pokeplan.core.reward.LinkMoment
import app.pokeplan.core.reward.Reward
import app.pokeplan.link.Check
import app.pokeplan.link.DateKind
import app.pokeplan.link.FollowUp
import app.pokeplan.link.FollowUpState
import app.pokeplan.link.FollowUpStatus
import app.pokeplan.link.ImportantDate
import app.pokeplan.link.LinkData
import app.pokeplan.link.LinkKinds
import app.pokeplan.link.LinkRules
import app.pokeplan.link.LinkSettings
import app.pokeplan.link.Person
import app.pokeplan.link.Profile
import app.pokeplan.link.ProfileKit
import app.pokeplan.link.birthdayOf
import app.pokeplan.link.PersonNote
import app.pokeplan.link.Sort
import app.pokeplan.plan.toLocalDateTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
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

/**
 * The Link module on this device (docs/pokelife `MODULES/LINK.md`): its own save in its own [storage], apart from every other module's, kept by
 * `LinkTables` in the live database. Everything in it is private: it reaches the shared world only through [world], with a [LinkEvent] that carries a
 * stable id, the module, an opaque record reference, the broad kind, the time and the policy version, and **never** a name, a number, a category, a
 * tag, a note, a date or a follow-up's words, or who it was about. Nothing in it is ever sent to a person.
 *
 * Only a follow-up the user finished can earn, and only while Postcard Trail is on. Adding or importing people, notes, dates, tags and favourites,
 * editing, rescheduling, cancelling or reopening a follow-up never pays, and one follow-up pays once however often it is reopened and finished.
 */
class LinkStore(
    storage: Storage,
    private val clock: Clock,
    private val world: World,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val rng: Random = Random,
) {
    private val document = SavedDocument(storage, LinkData.serializer(), json) { LinkData() }

    val data: LinkData get() = document.value

    fun observe(listener: (LinkData) -> Unit): () -> Unit = document.observe(listener)

    private val nowMillis: Long get() = clock.nowMillis()
    val today: LocalDate get() = nowMillis.toLocalDateTime(zone).date

    /** Picks up a change another process made, without writing anything back; see [SavedDocument.reload]. */
    fun reload() = document.reload()

    /** Picks up what another store in this process (a widget, a notification) wrote; cheap when nothing was. */
    fun sync() = document.sync()

    private fun update(change: (LinkData) -> LinkData) = document.update(change)

    /** Puts back a [snapshot] taken before a delete, for the undo bar. */
    fun restore(snapshot: LinkData) = update { snapshot }

    private fun newId(): String = newId(nowMillis, rng)

    private fun pay(id: String, ref: String): List<Reward>? =
        if (!data.settings.postcardTrail) null else world.link(LinkEvent("link:followup:$id", LinkMoment.FOLLOW_UP, nowMillis, ref))

    // --- people ------------------------------------------------------------------------------------------------------------

    /** Saves a person; only the name is required. Nothing here pays: adding people is never a reward (LINK.md 9.2). */
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
        return Logged(p)
    }

    fun updatePerson(p: Person): Logged<Person> {
        val old = data.people.firstOrNull { it.id == p.id } ?: return Logged(null, "That person is gone.")
        val next = old.copy(
            name = p.name.trim(), preferred = p.preferred.trim(), phone = p.phone.trim(), email = p.email.trim(), organization = p.organization.trim(),
            role = p.role.trim(), address = p.address.trim(), category = p.category.trim(), tags = p.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() },
            favorite = p.favorite, updatedMillis = nowMillis,
        )
        (LinkRules.checkPerson(next) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { d -> d.copy(people = d.people.map { if (it.id == p.id) next else it }) }
        return Logged(next)
    }

    /** The user's own card (LINK.md 2.6): the one person marked [Person.isMe]. Asking again returns the existing one, so there is never a second. Pays nothing. */
    fun createMe(name: String): Logged<Person> {
        data.people.firstOrNull { it.isMe }?.let { return Logged(it) }
        val out = addPerson(name.ifBlank { "Me" }, category = "Me")
        val made = out.record ?: return out
        val me = made.copy(isMe = true)
        update { d -> d.copy(people = d.people.map { if (it.id == me.id) me else it }) }
        return Logged(me)
    }

    /** Saves a person's slambook details, tidied (LINK.md 2.6). Nothing here pays and nothing reaches an event. */
    fun updateProfile(personId: String, profile: Profile): Logged<Person> {
        val old = data.people.firstOrNull { it.id == personId } ?: return Logged(null, "That person is gone.")
        val next = old.copy(profile = ProfileKit.tidy(profile), updatedMillis = nowMillis)
        update { d -> d.copy(people = d.people.map { if (it.id == personId) next else it }) }
        return Logged(next)
    }

    /** Sets a person's birthday (the date their birth chart is worked from): changes the one they have, or adds it. [year] may be null; the chart then needs it. */
    fun setBirthday(personId: String, month: Int, day: Int, year: Int?): Logged<ImportantDate> {
        val old = data.birthdayOf(personId)
        return if (old == null) addDate(DateKind.BIRTHDAY, "Birthday", month, day, year, annual = true, personId = personId)
        else updateDate(old.copy(month = month, day = day, year = year))
    }

    /** A favourite is only a shortcut in the directory, never a ranking (LINK.md 2.1). */
    fun setFavorite(id: String, favorite: Boolean) = update { d -> d.copy(people = d.people.map { if (it.id == id) it.copy(favorite = favorite) else it }) }

    /** People who may be the same as this, to suggest; never merged. */
    fun possibleDuplicates(name: String, phone: String = "", email: String = "", exceptId: String? = null) = LinkRules.possibleDuplicates(data, name, phone, email, exceptId)

    fun directory(query: String = "", category: String? = null, tag: String? = null, favoritesOnly: Boolean = false, sort: Sort = Sort.NAME) =
        LinkRules.directory(data, query, category, tag, favoritesOnly, sort)

    /**
     * Deletes a person and their notes. Their dates and follow-ups are the user's choice (LINK.md 7): kept, with the person taken off them, or deleted
     * with them. Nothing is decided silently.
     */
    fun deletePerson(id: String, keepDates: Boolean, keepFollowUps: Boolean) = update { d ->
        d.copy(
            people = d.people.filterNot { it.id == id },
            notes = d.notes.filterNot { it.personId == id },
            dates = if (keepDates) d.dates.map { if (it.personId == id) it.copy(personId = null) else it } else d.dates.filterNot { it.personId == id },
            followUps = if (keepFollowUps) d.followUps.map { if (it.personId == id) it.copy(personId = null) else it } else d.followUps.filterNot { it.personId == id },
        )
    }

    // --- notes -------------------------------------------------------------------------------------------------------------

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

    /** A person's notes, the newest first. */
    fun notesOf(personId: String): List<PersonNote> = data.notes.filter { it.personId == personId }.sortedByDescending { it.atMillis }

    // --- important dates ---------------------------------------------------------------------------------------------------

    /**
     * Saves a date. [remindDays] (one of [LinkRules.REMIND_PRESETS]) asks for its reminder in the same step, and turns Link's master switch on if it was off,
     * so choosing a reminder is never a second trip to Settings. Null leaves the reminder off (LINK.md 5).
     */
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

    fun upcoming(withinDays: Int = LinkRules.DATES_SOON_DAYS) = LinkRules.upcoming(data, today, withinDays)

    fun allDates() = LinkRules.allDates(data, today)

    // --- importing contacts (LINK.md 3.5 and 6.1) -------------------------------------------------------------------------------

    /** The preview for [cards] the phone offered: which are new, which were imported before, which may be someone already here. Writes nothing. */
    fun planImport(cards: List<app.pokeplan.link.ContactCard>) = app.pokeplan.link.ContactImport.plan(data, cards)

    /**
     * Writes the [picks] the user confirmed in **one** save, so a single [restore] of the snapshot taken before it is the Undo. A pick with `linkTo` attaches
     * the contact to that person and fills only their empty fields; otherwise a new person is made. A contact's birthday becomes a Birthday date for that
     * person unless they already have one, with the reminder [remindDays] when chosen (turning Link's reminders on). Importing never pays, replaces a
     * field the user wrote, or runs by itself, and a second import of the same picks changes nothing.
     */
    fun importContacts(picks: List<app.pokeplan.link.ImportPick>, remindDays: Int? = null): app.pokeplan.link.ImportResult {
        val remind = remindDays?.takeIf { it in LinkRules.REMIND_PRESETS }
        var added = 0; var linked = 0; var birthdays = 0; var skipped = 0
        update { d0 ->
            added = 0; linked = 0; birthdays = 0; skipped = 0 // the change may run again on a clash; count each run afresh
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

    // --- follow-ups --------------------------------------------------------------------------------------------------------

    /** Saves a follow-up; only the title is required. Nothing pays until it is finished (LINK.md 9.2). */
    fun addFollowUp(title: String, personId: String? = null, due: LocalDate? = null, note: String = ""): Logged<FollowUp> {
        val f = FollowUp(newId(), title.trim(), personId?.takeIf { id -> data.people.any { it.id == id } }, due, note.trim(), createdMillis = nowMillis, updatedMillis = nowMillis)
        (LinkRules.checkFollowUp(f) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { it.copy(followUps = it.followUps + f) }
        return Logged(f)
    }

    /** Changes the words, person, day and note. Never pays, and never changes whether it was done. */
    fun updateFollowUp(f: FollowUp): Logged<FollowUp> {
        val old = data.followUps.firstOrNull { it.id == f.id } ?: return Logged(null, "That follow-up is gone.")
        val next = old.copy(title = f.title.trim(), personId = f.personId?.takeIf { id -> data.people.any { it.id == id } }, due = f.due, note = f.note.trim(), updatedMillis = nowMillis)
        (LinkRules.checkFollowUp(next) as? Check.Refused)?.let { return Logged(null, it.reason) }
        update { d -> d.copy(followUps = d.followUps.map { if (it.id == f.id) next else it }) }
        return Logged(next)
    }

    private fun change(id: String, to: (FollowUp) -> FollowUp) = update { d -> d.copy(followUps = d.followUps.map { if (it.id == id) to(it) else it }) }

    /**
     * Marks an open follow-up done and pays Postcard Trail when it is on and today's share is not used. The same follow-up pays once, however often it is
     * reopened and finished again; the event id names it.
     */
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

    /**
     * Hands an open follow-up to PLAN as a task, by the user's choice (LINK.md 2.4). From then on PLAN owns the task, its completion and its reward, once; LINK
     * keeps the person and the words, shows the task's status ([planTask]) and never pays for this follow-up. The same follow-up gives the same task. Null
     * when it is gone, not open, or there is no Plan to receive it.
     */
    fun convertToPlan(id: String): FollowUpTask? {
        val f = data.followUps.firstOrNull { it.id == id }?.takeIf { it.status == FollowUpStatus.OPEN } ?: return null
        val task = world.planFollowUp(f.id, f.title, f.due) ?: return null
        if (!f.converted) change(id) { it.copy(converted = true, updatedMillis = nowMillis) }
        return task
    }

    /** The PLAN task this follow-up became, with its state, or null when it has none (or it was deleted in PLAN: LINK's record is then untouched). */
    fun planTask(id: String): FollowUpTask? = world.followUpTask(id)

    /**
     * Keeps a converted follow-up in line with its PLAN task, without paying: PLAN already did, once. A task finished in PLAN completes the follow-up here,
     * and a task taken back to open in PLAN opens it again. Call it when PLAN's data changes. A deleted or unlinked task leaves the follow-up as it is,
     * owned by nobody but LINK, which still pays nothing for it.
     */
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

    /** Puts a done or cancelled follow-up back to open. What was paid stays paid and is never paid twice. While PLAN owns the task it is reopened there, not here. */
    fun reopen(id: String) = if (world.followUpTask(id) != null) Unit else change(id) { if (it.status == FollowUpStatus.OPEN) it else it.copy(status = FollowUpStatus.OPEN, updatedMillis = nowMillis) }

    /** Cancels an open follow-up. It earns nothing and leaves the open list. */
    fun cancel(id: String) = if (world.followUpTask(id) != null) Unit else change(id) { if (it.status == FollowUpStatus.OPEN) it.copy(status = FollowUpStatus.CANCELLED, completedMillis = nowMillis, updatedMillis = nowMillis) else it }

    fun deleteFollowUp(id: String) = update { d -> d.copy(followUps = d.followUps.filterNot { it.id == id }) }

    fun openFollowUps(personId: String? = null) = LinkRules.open(data, today, personId)

    /** The follow-ups whose day has come and that LINK still owns: what the Overview, the Modules card and Home count. One handed to Plan is Plan's to show. */
    fun dueNow(): List<FollowUp> = openFollowUps().filter { LinkRules.state(it, today) == FollowUpState.DUE && world.followUpTask(it.id) == null }

    fun followUpHistory(personId: String? = null) = LinkRules.history(data, personId)

    // --- search ------------------------------------------------------------------------------------------------------------

    fun search(text: String) = LinkRules.search(data, text)

    // --- settings ----------------------------------------------------------------------------------------------------------

    private fun settings(change: (LinkSettings) -> LinkSettings) = update { it.copy(settings = change(it.settings)) }

    /** Turns Postcard Trail (the reward) on or off. Off never touches a record and never hides a feature (LINK.md 9.1). */
    fun setPostcardTrail(on: Boolean) = settings { it.copy(postcardTrail = on) }

    fun markTutorialSeen() = settings { it.copy(tutorialSeen = true) }

    fun addCategory(name: String) = settings { s ->
        name.trim().take(LinkRules.TAG_MAX).let { n -> if (n.isEmpty() || n.lowercase() in LinkRules.categories(LinkData(settings = s)).map { it.lowercase() }) s else s.copy(categories = s.categories + n) }
    }

    // --- reminders (LINK.md 5): all off until the user asks for one (a date's first chosen reminder turns the master switch on), per record ---------------------------------------------------

    /** The master switch. On, a date or follow-up still asks only when its own reminder is on. Neither pays nor changes a record. */
    fun setReminders(on: Boolean) = settings { it.copy(reminders = on) }

    fun setReminderHour(hour: Int) = settings { it.copy(reminderHour = hour.coerceIn(0, 23)) }

    /** Lets a notification say which date or follow-up once the phone is unlocked; the lock screen never does. */
    fun setReminderNames(on: Boolean) = settings { it.copy(reminderNames = on) }

    /** Reminds [days] before the date ([LinkRules.REMIND_PRESETS]; anything else is ignored), or not at all for null. A reminder chosen turns Link's reminders on. */
    fun setDateRemind(id: String, days: Int?) {
        if (days != null && days !in LinkRules.REMIND_PRESETS) return
        // Choosing a reminder is enough: the first one turns the master switch on, so it is never a second trip to Settings.
        update { d -> d.copy(dates = d.dates.map { if (it.id == id) it.copy(remindDays = days) else it }, settings = if (days != null) d.settings.copy(reminders = true) else d.settings) }
    }

    /** Reminds on the follow-up's day. A follow-up handed to Plan cannot: Plan is its reminder source from then on. */
    fun setFollowUpRemind(id: String, on: Boolean) {
        if (on && data.followUps.firstOrNull { it.id == id }?.converted == true) return
        change(id) { it.copy(remind = on) }
    }

    // --- export and erase (one kind at a time, or all) ----------------------------------------------------------------------

    /** The chosen kinds of record (all by default) as readable JSON, exactly as saved. Nothing of the game is in it. */
    fun export(kinds: Set<LinkKinds> = LinkKinds.entries.toSet()): String = LinkExport.json(data, kinds)

    /** Erases every record of [kind]. Records that pointed at what was erased keep their own words and lose the pointer; nothing else is touched. */
    fun erase(kind: LinkKinds) = update { LinkExport.without(it, kind) }

    /** Erases all of LINK's records (not its settings, not what Postcard Trail paid). */
    fun eraseAll() = update { d -> LinkKinds.entries.fold(d) { acc, k -> LinkExport.without(acc, k) } }

    /** The full-device reset: LINK as new, its records and its settings. */
    fun resetAll() = update { LinkData() }

    companion object {
        /** Why a follow-up that PLAN owns cannot be finished or cancelled here: one real action, one completion. */
        const val IN_PLAN = "This one is in Plan now. Finish it there."

        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    }
}

/** Export and erase by kind, pure and free of the game: one place that knows which fields of the document each kind is. */
object LinkExport {
    private val pretty = Json { encodeDefaults = true; prettyPrint = true }

    private val fields: Map<LinkKinds, List<String>> = mapOf(
        LinkKinds.PEOPLE to listOf("people", "notes"),
        LinkKinds.DATES to listOf("dates"),
        LinkKinds.FOLLOW_UPS to listOf("followUps"),
    )

    fun json(data: LinkData, kinds: Set<LinkKinds>): String {
        val all = LinkStore.json.encodeToJsonElement(LinkData.serializer(), data) as JsonObject
        val chosen = all.filterKeys { key -> fields.any { (kind, names) -> kind in kinds && key in names } }
        return pretty.encodeToString(JsonObject.serializer(), JsonObject(mapOf("format" to JsonPrimitive("pokelife-link-export")) + chosen))
    }

    fun without(d: LinkData, kind: LinkKinds): LinkData = when (kind) {
        LinkKinds.PEOPLE -> d.copy(
            people = emptyList(), notes = emptyList(), dates = d.dates.map { it.copy(personId = null) }, followUps = d.followUps.map { it.copy(personId = null) },
        )
        LinkKinds.DATES -> d.copy(dates = emptyList())
        LinkKinds.FOLLOW_UPS -> d.copy(followUps = emptyList())
    }
}
