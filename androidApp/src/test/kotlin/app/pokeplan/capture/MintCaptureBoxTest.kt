package app.pokeplan.capture

import app.pokeplan.AndroidTestBase
import app.pokeplan.FakeAndroidKeyStore
import app.pokeplan.mint.CaptureCandidate
import app.pokeplan.mint.CaptureChannel
import app.pokeplan.mint.TxType
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** MINT's box of alerts the phone read but the player has not looked at yet (MINT.md 10.4, 22I). */
@RunWith(RobolectricTestRunner::class)
class MintCaptureBoxTest : AndroidTestBase() {
    private var seq = 0
    private fun alert(ref: String = "a${seq++}", at: Long = System.currentTimeMillis()) = CaptureCandidate(
        ref = ref, source = "GCASH", channel = CaptureChannel.MESSAGE, type = TxType.EXPENSE, amountMinor = 25_000,
        currency = "PHP", payee = "SM CITY", note = "From GCash", day = LocalDate(2026, 11, 25), atMillis = at,
    )

    private fun box() = File(context.filesDir, "mint-capture.sealed")

    @Test
    fun theBoxIsSealedOnDiskAndTakesEachAlertOnce() {
        MintCaptureBox.add(context, listOf(alert("one")))
        assertTrue(box().isFile)
        val onDisk = box().readText()
        assertFalse("no merchant is ever on disk in the clear", onDisk.contains("SM CITY"))
        assertFalse("no amount is ever on disk in the clear", onDisk.contains("25000"))
        assertEquals(listOf("one"), MintCaptureBox.take(context).map { it.ref })
        assertTrue(MintCaptureBox.take(context).isEmpty())
        assertFalse(box().exists())
    }

    @Test
    fun theSameAlertIsHeldOnce() {
        val a = alert("same")
        MintCaptureBox.add(context, listOf(a))
        MintCaptureBox.add(context, listOf(a))
        assertEquals(listOf("same"), MintCaptureBox.take(context).map { it.ref })
    }

    @Test
    fun theBoxKeepsTheNewestForty() {
        repeat(45) { i -> MintCaptureBox.add(context, listOf(alert("a$i"))) }
        val held = MintCaptureBox.take(context)
        assertEquals(40, held.size)
        assertEquals("a44", held.last().ref)
        assertFalse("the oldest ones go first", held.any { it.ref == "a0" })
    }

    @Test
    fun anAlertOlderThanAWeekIsNotHeld() {
        MintCaptureBox.add(context, listOf(alert("old", at = System.currentTimeMillis() - 8L * 86_400_000L)))
        assertTrue(MintCaptureBox.take(context).isEmpty())
    }

    @Test
    fun withoutTheKeyNothingIsWrittenInTheClear() {
        FakeAndroidKeyStore.available = false
        MintCaptureBox.add(context, listOf(alert("nothing")))
        assertFalse("with no key the box fails closed", box().exists())
        FakeAndroidKeyStore.available = true
    }
}
