package app.pokeplan.link

data class ContactBirthday(val month: Int, val day: Int, val year: Int? = null)

data class ContactCard(
    val sourceId: String,
    val name: String,
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val organization: String = "",
    val birthday: ContactBirthday? = null,
)

enum class ImportKind(val label: String) { NEW("New"), KNOWN("Already in Link"), MAYBE("Maybe already in Link") }

data class ImportRow(val card: ContactCard, val kind: ImportKind, val matches: List<Person> = emptyList())

data class ImportPlan(val rows: List<ImportRow>, val unnamed: Int = 0)

data class ImportPick(val card: ContactCard, val linkTo: String? = null)

data class ImportResult(val added: Int = 0, val linked: Int = 0, val birthdays: Int = 0, val skipped: Int = 0)

object ContactImport {
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

    fun newPerson(c: ContactCard, id: String, nowMillis: Long): Person = Person(
        id = id, name = c.name.trim().take(LinkRules.NAME_MAX), phone = c.phones.firstOrNull().orEmpty().trim().take(LinkRules.FIELD_MAX),
        email = c.emails.firstOrNull().orEmpty().trim().take(LinkRules.FIELD_MAX), organization = c.organization.trim().take(LinkRules.FIELD_MAX),
        createdMillis = nowMillis, updatedMillis = nowMillis, sourceId = c.sourceId,
    )

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
