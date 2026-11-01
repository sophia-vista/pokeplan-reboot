package app.pokeplan.ui.gear

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EverydayTest {
    @Test fun tipAndSplit() {
        val s = Everyday.tipSplit(86.40, 15.0, 3)!!
        assertEquals(12.96, s.tip); assertEquals(99.36, s.total); assertEquals(33.12, s.each)
        assertNull(Everyday.tipSplit(-1.0, 10.0, 2)); assertNull(Everyday.tipSplit(10.0, 10.0, 0)); assertNull(Everyday.tipSplit(Double.NaN, 10.0, 2))
    }

    @Test fun percents() {
        assertEquals(25.0, Everyday.percentOf(12.5, 200.0)); assertEquals(50.0, Everyday.whatPercent(1.0, 2.0)); assertNull(Everyday.whatPercent(1.0, 0.0))
        assertEquals(-20.0, Everyday.percentChange(50.0, 40.0)); assertNull(Everyday.percentChange(0.0, 5.0))
    }

    @Test fun saleWithTaxOnTheReducedPrice() {
        val s = Everyday.sale(100.0, 30.0, 12.0)!!
        assertEquals(30.0, s.saved); assertEquals(70.0, s.afterDiscount); assertEquals(8.4, s.tax); assertEquals(78.4, s.total)
        assertNull(Everyday.sale(100.0, 120.0, 0.0))
    }

    @Test fun loanPayment() {
        val l = Everyday.loan(200_000.0, 6.0, 30.0)!!
        assertEquals(1199.10, l.monthly)
        assertTrue(l.interest > 230_000)
        assertEquals(100.0, Everyday.loan(1200.0, 0.0, 1.0)!!.monthly, "no interest is the principal over the months")
        assertNull(Everyday.loan(0.0, 5.0, 1.0)); assertNull(Everyday.loan(100.0, 5.0, 0.0))
    }

    @Test fun savingsGrowth() {
        assertEquals(1104.71, Everyday.savings(1000.0, 10.0, 1.0, 12)!!)
        assertEquals(1200.0, Everyday.savings(0.0, 0.0, 1.0, 12, 100.0)!!)
        assertNotNull(Everyday.savings(1000.0, 5.0, 10.0)); assertNull(Everyday.savings(-1.0, 5.0, 1.0))
    }

    @Test fun tripAndMoneyText() {
        assertEquals(12.5, Everyday.tripCost(100.0, 16.0, 2.0)); assertNull(Everyday.tripCost(100.0, 0.0, 2.0))
        assertEquals("12.50", Everyday.money(12.5)); assertEquals("0.05", Everyday.money(0.05)); assertEquals("-3.00", Everyday.money(-3.0))
    }
}
