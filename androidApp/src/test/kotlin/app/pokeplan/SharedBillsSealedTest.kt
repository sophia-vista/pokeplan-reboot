package app.pokeplan

import app.pokeplan.mint.SharedKind
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class SharedBillsSealedTest : AndroidTestBase() {
    @Test fun aSharedEntryIsSealedOnTheDisk() {
        val game = widgetStore(context)
        assertNull(mintStore(context, game).addShared("Quokkafriend", SharedKind.I_PAID, 12_345, LocalDate(2026, 11, 20), "Zebradinnernote", "PHP"))
        processDeath()

        val bytes = databaseBytes()
        assertFalse("Quokkafriend" in bytes, "a person's name never reaches the disk in clear")
        assertFalse("Zebradinnernote" in bytes, "a note never reaches the disk in clear")
        val back = mintStore(context, widgetStore(context)).data.shared.single()
        assertEquals("Quokkafriend", back.person)
        assertEquals(12_345, back.amountMinor)
    }
}
