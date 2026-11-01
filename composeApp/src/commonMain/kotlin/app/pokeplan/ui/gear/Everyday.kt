package app.pokeplan.ui.gear

import kotlin.math.pow
import kotlin.math.roundToLong

/** The money-and-percent sums of everyday life, as pure rules (nothing here is saved). Each returns null for numbers that make no sense. */
object Everyday {
    private fun ok(vararg x: Double) = x.all { it.isFinite() }

    private fun cents(x: Double): Double = (x * 100).roundToLong() / 100.0

    /** A bill split between people: the tip, the total and what each pays (rounded to cents, the last cent going to the total). */
    data class Split(val tip: Double, val total: Double, val each: Double)

    fun tipSplit(bill: Double, tipPercent: Double, people: Int): Split? {
        if (!ok(bill, tipPercent) || bill < 0 || tipPercent < 0 || people < 1 || people > 100) return null
        val tip = cents(bill * tipPercent / 100)
        val total = cents(bill + tip)
        return Split(tip, total, cents(total / people))
    }

    /** [percent] of [x]. */
    fun percentOf(percent: Double, x: Double): Double? = if (ok(percent, x)) x * percent / 100 else null

    /** What percent [a] is of [b]; null when [b] is zero. */
    fun whatPercent(a: Double, b: Double): Double? = if (ok(a, b) && b != 0.0) a / b * 100 else null

    /** The change from [from] to [to] in percent (negative for a fall); null when [from] is zero. */
    fun percentChange(from: Double, to: Double): Double? = if (ok(from, to) && from != 0.0) (to - from) / from * 100 else null

    /** A price after [offPercent] off and [taxPercent] tax on the reduced price. */
    data class Sale(val saved: Double, val afterDiscount: Double, val tax: Double, val total: Double)

    fun sale(price: Double, offPercent: Double, taxPercent: Double): Sale? {
        if (!ok(price, offPercent, taxPercent) || price < 0 || offPercent !in 0.0..100.0 || taxPercent < 0) return null
        val saved = cents(price * offPercent / 100)
        val after = cents(price - saved)
        val tax = cents(after * taxPercent / 100)
        return Sale(saved, after, tax, cents(after + tax))
    }

    /** A loan paid back in equal monthly payments. */
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

    /** Savings grown at [annualPercent] compounded [perYear] times a year for [years], with [addEach] added every period (at its end). */
    fun savings(start: Double, annualPercent: Double, years: Double, perYear: Int = 12, addEach: Double = 0.0): Double? {
        if (!ok(start, annualPercent, years, addEach) || start < 0 || annualPercent < 0 || years < 0 || years > 100 || perYear !in 1..365 || addEach < 0) return null
        val n = (years * perYear).roundToLong().toInt()
        val r = annualPercent / 100 / perYear
        var v = start
        repeat(n) { v = v * (1 + r) + addEach }
        return cents(v)
    }

    /** What a trip costs: [distance] over [efficiency] (distance per unit of fuel) times the [price] of a unit of fuel. */
    fun tripCost(distance: Double, efficiency: Double, price: Double): Double? =
        if (ok(distance, efficiency, price) && distance >= 0 && efficiency > 0 && price >= 0) cents(distance / efficiency * price) else null

    /** Money as the screen shows it, always two decimals ("12.50") without a locale (the web has no number formatting to lean on). */
    fun money(x: Double): String {
        val c = (x * 100).roundToLong()
        val neg = c < 0
        val a = kotlin.math.abs(c)
        return (if (neg) "-" else "") + (a / 100) + "." + (a % 100).toString().padStart(2, '0')
    }
}
