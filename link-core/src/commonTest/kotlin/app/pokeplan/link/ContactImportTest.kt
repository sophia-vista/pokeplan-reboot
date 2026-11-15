package app.pokeplan.link

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContactImportTest {
    private fun card(id: String, name: String, phones: List<String> = emptyList(), emails: List<String> = emptyList(), org: String = "", b: ContactBirthday? = null) =
        ContactCard(id, name, phones, emails, org, b)

    @Test
    fun birthdaysComeInThePhonesFormsAndJunkIsNull() {
        assertEquals(ContactBirthday(3, 14, 1990), ContactImport.parseBirthday("1990-03-14"))
        assertEquals(ContactBirthday(3, 14, null), ContactImport.parseBirthday("--03-14"))
        assertEquals(ContactBirthday(3, 14, null), ContactImport.parseBirthday("--0314"))
        assertEquals(ContactBirthday(3, 14, null), ContactImport.parseBirthday(" 1604-03-14 "), "a placeholder year is dropped, the day kept")
        assertEquals(ContactBirthday(2, 29, null), ContactImport.parseBirthday("--02-29"), "29 February without a year is valid")
        assertEquals(ContactBirthday(3, 14, 1990), ContactImport.parseBirthday("1990-03-14T00:00:00Z"))
        assertNull(ContactImport.parseBirthday("1990-02-30"))
        assertNull(ContactImport.parseBirthday("--13-01"))
        assertNull(ContactImport.parseBirthday("March 14"))
        assertNull(ContactImport.parseBirthday(""))
    }

    @Test
    fun aRowIsNewKnownOrMaybeAndAnUnnamedOneIsCounted() {
        val data = LinkData(people = listOf(
            Person("1", "Ana Cruz", phone = "+63 917 123 4567"),
            Person("2", "Ben", sourceId = "key-ben"),
        ))
        val plan = ContactImport.plan(data, listOf(
            card("key-ben", "Benjamin"),                                  // imported before, by source id
            card("key-x", "Someone", phones = listOf("0000000", "(+63) 917-123-4567")), // second phone matches Ana
            card("key-y", "ana cruz"),                                    // same name
            card("key-z", "Zed"),
            card("key-q", "  "),
            card("key-z", "Zed"),                                         // offered twice: one row
        ))
        assertEquals(listOf(ImportKind.KNOWN, ImportKind.MAYBE, ImportKind.MAYBE, ImportKind.NEW), plan.rows.map { it.kind })
        assertEquals(listOf("2"), plan.rows[0].matches.map { it.id })
        assertEquals(listOf("1"), plan.rows[1].matches.map { it.id })
        assertEquals(1, plan.unnamed, "only the blank one is unnamed; the repeat is just one row")
    }

    @Test
    fun fillingNeverOverwritesWhatTheUserWrote() {
        val p = Person("1", "Ana", phone = "111", organization = "", updatedMillis = 5)
        val c = card("k", "Ana C", phones = listOf("999"), emails = listOf("a@x.com"), org = "Acme")
        val next = ContactImport.fill(p, c, 10)
        assertEquals("111", next.phone)
        assertEquals("a@x.com", next.email)
        assertEquals("Acme", next.organization)
        assertEquals("Ana", next.name)
        assertEquals("k", next.sourceId)
        assertEquals(10, next.updatedMillis)
        assertEquals(next, ContactImport.fill(next, c, 99), "a second fill changes nothing, not even the time")
    }

    @Test
    fun aPersonSavedBeforeImportExistedStillReadsAndHasNoSource() {
        val old = Json.decodeFromString(Person.serializer(), """{"id":"1","name":"Ana","phone":"111"}""")
        assertEquals("", old.sourceId)
        assertEquals(old, Json.decodeFromString(Person.serializer(), Json.encodeToString(Person.serializer(), old)))
    }
}
