package app.pokeplan.data

import kotlin.test.Test
import kotlin.test.assertEquals

class ReminderRepositoryTest {
    private fun held(id: String, at: Long, reason: String = "snooze") = HeldReminder(id, "PLAN", "t1", "task", "x", at, reason)

    @Test
    fun aHoldIsOneRowPerReminderSoSnoozingTwiceReplacesTheFirst() {
        val s = JvmDatabase.open()
        s.reminders.hold(held("PLAN|t1|task|x", 100), 1)
        s.reminders.hold(held("PLAN|t1|task|x", 500), 2)
        s.reminders.hold(held("CARE|r|routine|d", 300).copy(module = "CARE"), 2)
        assertEquals(listOf(300L, 500L), s.reminders.all().map { it.atMillis })
        s.reminders.release("PLAN|t1|task|x")
        assertEquals(listOf("CARE|r|routine|d"), s.reminders.all().map { it.id })
    }

    @Test
    fun oldHoldsAreDroppedAndAHoldSurvivesAReopen() {
        val f = java.io.File.createTempFile("rem", ".db").also { it.delete(); it.deleteOnExit() }
        JvmDatabase.open(f.path).let { s ->
            s.reminders.hold(held("a", 10), 1); s.reminders.hold(held("b", 1000, "repeat"), 1)
            s.reminders.dropOlderThan(500)
            s.close()
        }
        assertEquals(listOf("b" to "repeat"), JvmDatabase.open(f.path).reminders.all().map { it.id to it.reason })
    }

    @Test
    fun aVersionTwoDatabaseMigratesToTheReminderTable() {
        val f = java.io.File.createTempFile("v2db", ".db").also { it.delete(); it.deleteOnExit() }
        java.io.File("src/commonMain/sqldelight/databases/2.db").copyTo(f, overwrite = true)
        java.sql.DriverManager.getConnection("jdbc:sqlite:${f.path}").use { it.createStatement().execute("PRAGMA user_version = 2") }
        val s = JvmDatabase.open(f.path)
        s.reminders.hold(held("a", 1), 1)
        assertEquals(1, s.reminders.all().size)
    }
}
