package app.pokeplan.mint

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SharedRulesTest {
    private val day = LocalDate(2026, 11, 20)
    private fun e(person: String, kind: SharedKind, minor: Long, currency: String = "PHP", id: String = "$person$kind$minor") = SharedEntry(id, person, kind, minor, currency, day)

    @Test fun whatIPaidIsOwedToMeAndWhatTheyPaidIsOwedToThem() {
        val b = SharedRules.balances(listOf(e("Ana", SharedKind.I_PAID, 50_000), e("Ben", SharedKind.THEY_PAID, 30_000)))
        assertEquals(listOf(SharedBalance("Ana", "PHP", 50_000), SharedBalance("Ben", "PHP", -30_000)), b)
        assertEquals(50_000, SharedRules.owedToMe(b, "PHP"))
        assertEquals(30_000, SharedRules.iOwe(b, "PHP"))
    }

    @Test fun aNameIsMatchedWithoutCaseOrEdgeSpaces() {
        val b = SharedRules.balances(listOf(e("Ana", SharedKind.I_PAID, 100), e("  ana ", SharedKind.I_PAID, 50, id = "x")))
        assertEquals(1, b.size)
        assertEquals(150, b.single().minor)
    }

    @Test fun aSettlementBringsTheBalanceToZeroAndIsLeftOut() {
        val owed = listOf(e("Ana", SharedKind.I_PAID, 50_000))
        val settle = SharedRules.settlement(SharedRules.balances(owed).single(), day, "s")
        assertEquals(SharedKind.THEY_SETTLED, settle?.kind)
        assertEquals(50_000, settle?.amountMinor)
        assertEquals(emptyList(), SharedRules.balances(owed + settle!!))
        val owing = listOf(e("Ben", SharedKind.THEY_PAID, 12_345))
        assertEquals(SharedKind.I_SETTLED, SharedRules.settlement(SharedRules.balances(owing).single(), day, "t")?.kind)
        assertNull(SharedRules.settlement(SharedBalance("Ana", "PHP", 0), day, "u"))
    }

    @Test fun overpayingTurnsTheBalanceRound() {
        val b = SharedRules.balances(listOf(e("Ana", SharedKind.I_PAID, 100), e("Ana", SharedKind.THEY_SETTLED, 150, id = "y")))
        assertEquals(-50, b.single().minor)
    }

    @Test fun currenciesNeverMixAndPeopleAreNeverNetted() {
        val b = SharedRules.balances(listOf(e("Ana", SharedKind.I_PAID, 100), e("Ben", SharedKind.THEY_PAID, 40), e("Ana", SharedKind.THEY_PAID, 7, "USD")))
        assertEquals(100, SharedRules.owedToMe(b, "PHP"))
        assertEquals(40, SharedRules.iOwe(b, "PHP"))
        assertEquals(7, SharedRules.iOwe(b, "USD"))
    }

    @Test fun anEntryWithoutAPersonOrAnAmountIsRefused() {
        assertNotNull(SharedRules.refusal("  ", 100, "PHP"))
        assertNotNull(SharedRules.refusal("Ana", 0, "PHP"))
        assertNull(SharedRules.refusal("Ana", 100, "PHP"))
    }
}
