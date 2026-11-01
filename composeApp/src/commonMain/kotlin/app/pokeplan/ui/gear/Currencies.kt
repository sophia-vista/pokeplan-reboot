package app.pokeplan.ui.gear

import app.pokeplan.app.GearRates
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

/** A currency the converter knows by name: its ISO [code], [name] and other things people call it (`rmb`, `yuan`, `peso`). */
class CurrencyInfo(val code: String, val name: String, val aliases: List<String> = emptyList(), val minorUnits: Int = 2)

/**
 * The currencies of the rates service (the European Central Bank's set), by name, so the converter is searched by "yen" or "rmb" and not only by
 * code, and each is shown with the right number of decimals (yen, won and kroner of Iceland have none). Names are bundled, so searching needs no
 * network.
 */
object Currencies {
    val all: List<CurrencyInfo> = listOf(
        CurrencyInfo("USD", "US dollar", listOf("dollar", "dollars", "buck")), CurrencyInfo("EUR", "Euro", listOf("euros")), CurrencyInfo("JPY", "Japanese yen", listOf("yen"), 0),
        CurrencyInfo("PHP", "Philippine peso", listOf("peso", "pesos", "piso")), CurrencyInfo("CNY", "Chinese yuan", listOf("yuan", "rmb", "renminbi")), CurrencyInfo("KRW", "South Korean won", listOf("won"), 0),
        CurrencyInfo("GBP", "Pound sterling", listOf("pound", "pounds", "british pound", "quid")), CurrencyInfo("AUD", "Australian dollar"), CurrencyInfo("CAD", "Canadian dollar"), CurrencyInfo("NZD", "New Zealand dollar", listOf("kiwi")),
        CurrencyInfo("SGD", "Singapore dollar"), CurrencyInfo("HKD", "Hong Kong dollar"), CurrencyInfo("CHF", "Swiss franc", listOf("franc")), CurrencyInfo("THB", "Thai baht", listOf("baht")),
        CurrencyInfo("IDR", "Indonesian rupiah", listOf("rupiah")), CurrencyInfo("MYR", "Malaysian ringgit", listOf("ringgit")), CurrencyInfo("INR", "Indian rupee", listOf("rupee", "rupees")),
        CurrencyInfo("BRL", "Brazilian real", listOf("real")), CurrencyInfo("MXN", "Mexican peso"), CurrencyInfo("ZAR", "South African rand", listOf("rand")), CurrencyInfo("TRY", "Turkish lira", listOf("lira")),
        CurrencyInfo("SEK", "Swedish krona", listOf("krona", "kronor")), CurrencyInfo("NOK", "Norwegian krone", listOf("krone", "kroner")), CurrencyInfo("DKK", "Danish krone"), CurrencyInfo("ISK", "Icelandic króna", listOf("krona"), 0),
        CurrencyInfo("PLN", "Polish złoty", listOf("zloty")), CurrencyInfo("CZK", "Czech koruna", listOf("koruna")), CurrencyInfo("HUF", "Hungarian forint", listOf("forint")), CurrencyInfo("RON", "Romanian leu", listOf("leu")),
        CurrencyInfo("BGN", "Bulgarian lev", listOf("lev")), CurrencyInfo("ILS", "Israeli shekel", listOf("shekel", "nis")),
    )

    private val byCode = all.associateBy { it.code }

    fun info(code: String): CurrencyInfo? = byCode[code]

    /** "PHP, Philippine peso"; a code the list does not know (the service added one) is just the code. */
    fun label(code: String): String = byCode[code]?.let { "$code, ${it.name}" } ?: code

    /** The currencies matching [query] by code, name or alias (one wrong letter in a longer word is forgiven), in the order of [among] when given, best first. */
    fun search(query: String, among: Collection<String>? = null): List<CurrencyInfo> {
        val q = query.trim().lowercase()
        val pool = (among ?: all.map { it.code }).mapNotNull { code -> byCode[code] ?: CurrencyInfo(code, code) }
        if (q.isEmpty()) return pool
        return pool.map { c ->
            val names = listOf(c.code.lowercase(), c.name.lowercase()) + c.aliases
            val score = names.maxOf { n ->
                when {
                    n == q -> 100
                    n.startsWith(q) -> 60
                    q.length >= 2 && n.contains(q) -> 40
                    q.length >= 4 && n.split(' ').any { w -> w.length >= 4 && closeTo(q, w) } -> 20
                    else -> 0
                }
            }
            score to c
        }.filter { it.first > 0 }.sortedByDescending { it.first }.map { it.second }
    }

    private fun closeTo(a: String, b: String): Boolean {
        if (abs(a.length - b.length) > 1) return false
        var i = 0; var j = 0; var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++edits > 1) return false
            when { a.length > b.length -> i++; a.length < b.length -> j++; else -> { i++; j++ } }
        }
        return edits + (a.length - i) + (b.length - j) <= 1
    }

    /** [amount] of [code] as money: rounded to the currency's minor units (none for yen) and grouped by thousands, with the code after it. */
    fun format(amount: Double, code: String): String {
        val places = byCode[code]?.minorUnits ?: 2
        val scale = 10.0.pow(places)
        val rounded = round(abs(amount) * scale) / scale
        val whole = rounded.toLong()
        val fraction = if (places == 0) "" else "." + round((rounded - whole) * scale).toLong().toString().padStart(places, '0')
        val grouped = whole.toString().reversed().chunked(3).joinToString(",").reversed()
        return (if (amount < 0 && rounded != 0.0) "-" else "") + grouped + fraction + " " + code
    }

    /**
     * How many units of [to] one unit of [from] buys. A rate the player entered by hand wins when one of the two is the [home] currency and the
     * other has a manual rate (the manual rate is units of that currency per one of home); otherwise the reference rates through the dollar. Null when
     * a currency is unknown or the rate is not a positive finite number.
     */
    fun rate(from: String, to: String, rates: GearRates?, home: String, manual: Map<String, Double>): Double? {
        if (from == to) return 1.0
        manual[to]?.takeIf { from == home && it.isFinite() && it > 0 }?.let { return it }
        manual[from]?.takeIf { to == home && it.isFinite() && it > 0 }?.let { return 1.0 / it }
        val a = rates?.perUsd?.get(from) ?: return null
        val b = rates.perUsd[to] ?: return null
        val r = b / a
        return if (r.isFinite() && r > 0) r else null
    }
}

/** What a purchase abroad really costs at home: the reference estimate, the rate the player was offered, the fees, and the difference, each apart. */
data class TravelCost(val reference: Double, val atQuotedRate: Double, val fees: Double, val total: Double, val extra: Double, val extraPercent: Double)

object Travel {
    /**
     * A price of [foreign] units abroad, [referenceRate] being foreign units per one home unit (the reference rate) and [quotedRate] the one the
     * player was offered (same unit; null to use the reference). The home cost at the quoted rate gets [feePercent] on top and then [feeFixed] (in
     * home currency). The reference is only an estimate: banks, cards and changers each have their own rate, which only the player knows.
     */
    fun estimate(foreign: Double, referenceRate: Double, quotedRate: Double?, feePercent: Double, feeFixed: Double): TravelCost? {
        if (!foreign.isFinite() || foreign < 0 || !referenceRate.isFinite() || referenceRate <= 0) return null
        val quoted = quotedRate ?: referenceRate
        if (!quoted.isFinite() || quoted <= 0 || !feePercent.isFinite() || feePercent < 0 || !feeFixed.isFinite() || feeFixed < 0) return null
        val reference = foreign / referenceRate
        val atRate = foreign / quoted
        val fees = atRate * feePercent / 100.0 + feeFixed
        val total = atRate + fees
        val extra = total - reference
        return TravelCost(reference, atRate, fees, total, extra, if (reference == 0.0) 0.0 else extra / reference * 100.0)
    }
}
