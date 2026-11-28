package app.pokeplan.kit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchQueryTest {
    @Test fun trimsAndIgnoresCase() {
        val q = SearchQuery.of("  Boiler ")
        assertEquals("boiler", q.text)
        assertTrue(q.matchesAny("Annual BOILER service"))
    }

    @Test fun anyOfTheFieldsCanMatchAndNullsAreSkipped() {
        val q = SearchQuery.of("rent")
        assertTrue(q.matchesAny(null, "groceries", "Rent and housing"))
        assertFalse(q.matchesAny(null, "groceries"))
        assertTrue(q.matchesAny(listOf(null, "Rent")))
    }

    @Test fun anEmptyQueryMatchesNothingAndSaysSo() {
        val q = SearchQuery.of("   ")
        assertTrue(q.isEmpty)
        assertFalse(q.matchesAny("anything"))
    }
}
