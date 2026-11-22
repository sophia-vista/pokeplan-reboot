package app.pokeplan.ui.gear

import kotlin.math.pow
import kotlin.math.roundToLong

object Everyday {
    private fun ok(vararg x: Double) = x.all { it.isFinite() }

    private fun cents(x: Double): Double = (x * 100).roundToLong() / 100.0

    data class Split(val tip: Double, val total: Double, val each: Double)

    fun tipSplit(bill: Double, tipPercent: Double, people: Int): Split? {
        if (!ok(bill, tipPercent) || bill < 0 || tipPercent < 0 || people < 1 || people > 100) return null
        val tip = cents(bill * tipPercent / 100)
        val total = cents(bill + tip)
        return Split(tip, total, cents(total / people))
    }

    fun percentOf(percent: Double, x: Double): Double? = if (ok(percent, x)) x * percent / 100 else null

    fun whatPercent(a: Double, b: Double): Double? = if (ok(a, b) && b != 0.0) a / b * 100 else null

    fun percentChange(from: Double, to: Double): Double? = if (ok(from, to) && from != 0.0) (to - from) / from * 100 else null

    data class Sale(val saved: Double, val afterDiscount: Double, val tax: Double, val total: Double)

    fun sale(price: Double, offPercent: Double, taxPercent: Double): Sale? {
        if (!ok(price, offPercent, taxPercent) || price < 0 || offPercent !in 0.0..100.0 || taxPercent < 0) return null
        val saved = cents(price * offPercent / 100)
        val after = cents(price - saved)
        val tax = cents(after * taxPercent / 100)
        return Sale(saved, after, tax, cents(after + tax))
    }

    data class Loan(val monthly: Double, val totalPaid: Double, val interest: Double)

    fun loan(principal: Double, annualPercent: Double, years: Double): Loan? {
        if (!ok(principal, annualPercent, years) || principal <= 0 || annualPercent < 0 || years <= 0 || years > 60) return null
        val n = (years * 12).roundToLong().toInt().coerceAtLeast(1)
        val r = annualPercent / 100 / 12
        val monthly = if (r == 0.0) principal / n else principal * r / (1 - (1 + r).pow(-n))
        val m = cents(monthly)
        val total = cents(m * n)
        return Loan(m, total, cents(total - principal))
    }

    fun savings(start: Double, annualPercent: Double, years: Double, perYear: Int = 12, addEach: Double = 0.0): Double? {
        if (!ok(start, annualPercent, years, addEach) || start < 0 || annualPercent < 0 || years < 0 || years > 100 || perYear !in 1..365 || addEach < 0) return null
        val n = (years * perYear).roundToLong().toInt()
        val r = annualPercent / 100 / perYear
        var v = start
        repeat(n) { v = v * (1 + r) + addEach }
        return cents(v)
    }

    fun tripCost(distance: Double, efficiency: Double, price: Double): Double? =
        if (ok(distance, efficiency, price) && distance >= 0 && efficiency > 0 && price >= 0) cents(distance / efficiency * price) else null

    fun money(x: Double): String {
        val c = (x * 100).roundToLong()
        val neg = c < 0
        val a = kotlin.math.abs(c)
        return (if (neg) "-" else "") + (a / 100) + "." + (a % 100).toString().padStart(2, '0')
    }
}
