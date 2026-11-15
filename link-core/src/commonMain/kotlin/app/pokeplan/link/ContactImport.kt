package app.pokeplan.link

/** A birthday as the phone's contact stores it: the year only when the contact has one that looks real. */
data class ContactBirthday(val month: Int, val day: Int, val year: Int? = null)

/**
 * One contact as the phone's address book offers it for import (LINK.md 3.5, 6.1). It holds only the fields LINK asks to read: a name, phones, emails,
 * an organization and a birthday. [sourceId] is the contact's lookup key. Nothing here is a call, a message or a photo.
 */
data class ContactCard(
    val sourceId: String,
    val name: String,
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val organization: String = "",
    val birthday: ContactBirthday? = null,
)

/** What an import row is, to the person choosing: new, already imported once, or possibly someone already in LINK. */
enum class ImportKind(val label: String) { NEW("New"), KNOWN("Already in Link"), MAYBE("Maybe already in Link") }

/** One contact with what LINK found for it. [matches] are the people it may be (empty for a new one); a [ImportKind.KNOWN] row has the one it was imported as. */
data class ImportRow(val card: ContactCard, val kind: ImportKind, val matches: List<Person> = emptyList())

/** The preview before anything is written. [unnamed] counts contacts with no name, which cannot become a person; they are said, not dropped silently. */
data class ImportPlan(val rows: List<ImportRow>, val unnamed: Int = 0)

/**
 * A choice made in the preview: import [card] as a new person (`linkTo` null), or attach it to the person already in LINK (`linkTo`), where only empty fields
 * are filled. Nothing is merged or overwritten without this choice (LINK.md 6.1).
 */
data class ImportPick(val card: ContactCard, val linkTo: String? = null)

/** What one import did, in numbers, for the one line after it. */
data class ImportResult(val added: Int = 0, val linked: Int = 0, val birthdays: Int = 0, val skipped: Int = 0)

/** The pure rules of the optional contact import (LINK.md 3.5 and 6.1): parsing a birthday, classifying rows, and filling empty fields. */
object ContactImport {
    /**
     * A birthday from Android's contact event text: `yyyy-MM-dd`, `--MM-dd` (no year) or `--MMdd`. A year before 1900 or after 2200 (phones keep 1604 or 0000
     * for "unknown") is dropped and the date kept; anything else that is not a real day is null, and the person is imported without a date.
     */
    fun parseBirthday(raw: String): ContactBirthday? {
        val t = raw.trim()
        val parts: List<String> = when {
            t.startsWith("--") -> {
                val rest = t.removePrefix("--")
                if (rest.contains('-')) listOf("") + rest.split('-') else if (rest.length == 4) listOf("", rest.take(2), rest.drop(2)) else return null
            }
            Regex("""\d{4}-\d{1,2}-\d{1,2}.*""").matches(t) -> t.take(10).split('-')
            else -> return null
        }
        if (parts.size != 3) return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].take(2).toIntOrNull() ?: return null
        val year = parts[0].toIntOrNull()?.takeIf { it in 1900..2200 }
        val probe = ImportantDate("", DateKind.BIRTHDAY, "Birthday", month, day, year)
        return if (LinkRules.checkDate(probe) == Check.Ok) ContactBirthday(month, day, year) else null
    }

    /** Sorts the offered [cards] against [data]: already imported (by [Person.sourceId]), possibly someone already there (name, any phone or any email), or new. */
    fun plan(data: LinkData, cards: List<ContactCard>): ImportPlan {
        val named = cards.filter { it.name.isNotBlank() }.distinctBy { it.sourceId.ifBlank { it.name + it.phones } }
        val rows = named.map { c ->
            val known = c.sourceId.isNotBlank().let { has -> if (has) data.people.firstOrNull { it.sourceId == c.sourceId } else null }
            if (known != null) ImportRow(c, ImportKind.KNOWN, listOf(known))
            else {
                val maybe = (listOf(LinkRules.possibleDuplicates(data, c.name)) +
                    c.phones.map { LinkRules.possibleDuplicates(data, "", phone = it) } +
                    c.emails.map { LinkRules.possibleDuplicates(data, "", email = it) }).flatten().distinctBy { it.id }
                if (maybe.isEmpty()) ImportRow(c, ImportKind.NEW) else ImportRow(c, ImportKind.MAYBE, maybe)
            }
        }
        return ImportPlan(rows, cards.count { it.name.isBlank() })
    }

    /** A new person from a contact: the first phone and email only, long lines cut to what LINK keeps. */
    fun newPerson(c: ContactCard, id: String, nowMillis: Long): Person = Person(
        id = id, name = c.name.trim().take(LinkRules.NAME_MAX), phone = c.phones.firstOrNull().orEmpty().trim().take(LinkRules.FIELD_MAX),
        email = c.emails.firstOrNull().orEmpty().trim().take(LinkRules.FIELD_MAX), organization = c.organization.trim().take(LinkRules.FIELD_MAX),
        createdMillis = nowMillis, updatedMillis = nowMillis, sourceId = c.sourceId,
    )

    /** [p] with the contact's phone, email and organization where [p] has none, and its source remembered. Nothing the user wrote is overwritten. */
    fun fill(p: Person, c: ContactCard, nowMillis: Long): Person {
        val next = p.copy(
            phone = p.phone.ifBlank { c.phones.firstOrNull().orEmpty().trim().take(LinkRules.FIELD_MAX) },
            email = p.email.ifBlank { c.emails.firstOrNull().orEmpty().trim().take(LinkRules.FIELD_MAX) },
            organization = p.organization.ifBlank { c.organization.trim().take(LinkRules.FIELD_MAX) },
            sourceId = p.sourceId.ifBlank { c.sourceId },
        )
        return if (next == p) p else next.copy(updatedMillis = nowMillis)
    }
}
