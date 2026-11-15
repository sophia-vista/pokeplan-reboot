package app.pokeplan.ui

import app.pokeplan.app.storage.Clock
import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.MemoryStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The one feedback line: the newest wins, an error is not hidden by a plain "Saved.", and a screen takes down only what it put up. */
class FeedbackTest {
    private val clock = object : Clock { override fun nowMillis() = 1_700_000_000_000L }
    private fun model() = AppModel(GameStore(MemoryStorage(), clock).also { it.start("Sophia", 25) })

    @Test
    fun theNewestLineReplacesTheOldOneAndRepeatsAreSeenAgain() {
        val m = model()
        m.say("Saved.")
        val first = m.feedback!!.serial
        m.say("Saved.")
        assertEquals(FeedbackKind.SAVED, m.feedback!!.kind)
        assertEquals(true, m.feedback!!.serial > first, "the same words again are a new line, so the bar shows them again")
    }

    @Test
    fun anErrorStaysOverASavedLineThatArrivesWithIt() {
        val m = model()
        m.say("That could not be saved.", FeedbackKind.ERROR)
        m.say("Saved.")
        assertEquals("That could not be saved.", m.feedback!!.text)
        m.say("Another problem.", FeedbackKind.ERROR)
        assertEquals("Another problem.", m.feedback!!.text)
    }

    @Test
    fun aLineIsTakenDownOnlyByItsOwnWordsOrSerial() {
        val m = model()
        m.say("One")
        m.dropFeedback("Two")
        assertEquals("One", m.feedback!!.text)
        m.dropFeedback(m.feedback!!.serial + 1)
        assertEquals("One", m.feedback!!.text)
        m.dropFeedback("One")
        assertNull(m.feedback)
    }
}
