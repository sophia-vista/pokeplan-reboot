package app.pokeplan.kit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NamesTest {
    @Test fun tidyTrimsThenCaps() = assertEquals("Kitch", Names.tidy("  Kitchen ", 5))

    @Test fun takenIgnoresCase() {
        assertTrue(Names.isTaken("kitchen", listOf("Garage", "KITCHEN")))
        assertFalse(Names.isTaken("Attic", listOf("Garage")))
    }

    @Test fun addAppendsANewName() = assertEquals(listOf("kg", "ml"), Names.addTo(listOf("kg"), " ml ", 16))

    @Test fun addKeepsTheSameListForABlankOrRepeat() {
        val list = listOf("kg")
        assertSame(list, Names.addTo(list, "   ", 16))
        assertSame(list, Names.addTo(list, "KG", 16))
    }

    @Test fun distinctDropsBlanksAndRepeatsAndStopsAtTheLimit() =
        assertEquals(listOf("work", "gym"), Names.distinct(listOf(" work", "", "WORK", "gym", "chess"), 24, 2))
}
