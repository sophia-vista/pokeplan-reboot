package app.pokeplan.ui.gear

import app.pokeplan.app.GearRates
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.isSuccess
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Where the rates come from, shown beside them. */
const val RATES_SOURCE = "Frankfurter (European Central Bank daily reference rates)"

/**
 * The Currency tool's exchange rates from Frankfurter, a free service of the European Central Bank's daily rates (no account
 * or key). Only the rates are fetched; nothing about the user is sent. One client lives as long as the tool is open and is closed with
 * [close]; every request has a timeout, and a failed one leaves the cached rates alone (the caller keeps them).
 */
class RatesClient(private val http: HttpClient = newHttpClient()) {
    /** Why the last request failed, in words for the player, or null when it did not. */
    var lastError: String? = null
        private set

    /** Today's rates (stamped with [nowMillis]), or null with no connection, a bad answer or a timeout; [lastError] says which. */
    suspend fun latest(nowMillis: Long): GearRates? = try {
        val response = http.get("https://api.frankfurter.dev/v1/latest?base=USD")
        if (!response.status.isSuccess()) {
            lastError = "The rates service answered ${response.status.value}."
            null
        } else parseRates(response.bodyAsText(), nowMillis).also { lastError = if (it == null) "The rates service sent something that is not rates." else null }
    } catch (e: Exception) {
        lastError = "Could not reach the rates service."
        null
    }

    fun close() = http.close()

    companion object {
        fun newHttpClient(): HttpClient = HttpClient { install(HttpTimeout) { connectTimeoutMillis = 5_000; requestTimeoutMillis = 10_000; socketTimeoutMillis = 10_000 } }
    }
}

private val CODE = Regex("[A-Z]{3}")
private val DATE = Regex("(\\d{4})-(\\d{2})-(\\d{2})")

/**
 * Reads the service's answer (`{"base":"USD","date":"2026-10-30","rates":{"EUR":0.86,...}}`) into rates per US dollar, whatever the base is
 * (the base itself counts as 1, and the rest are divided by the dollar's rate). Null when it is not that: a bad date or code, a rate that is not a
 * finite number above zero, a base that is not a currency code, or a base other than the dollar when the dollar is not among the rates.
 */
fun parseRates(text: String, fetchedAtMillis: Long = 0): GearRates? = try {
    val root = Json.parseToJsonElement(text).jsonObject
    val date = root["date"]?.jsonPrimitive?.content ?: return null
    val parts = DATE.matchEntire(date)?.destructured ?: return null
    val (year, month, day) = parts.toList().map { it.toInt() }
    if (year !in 2000..2100 || month !in 1..12 || day !in 1..31) return null
    val base = root["base"]?.jsonPrimitive?.content ?: "USD"
    if (!CODE.matches(base)) return null
    val raw = (root["rates"] as? JsonObject)?.mapNotNull { (code, value) ->
        val rate = value.jsonPrimitive.doubleOrNull
        if (CODE.matches(code) && rate != null && rate.isFinite() && rate > 0.0) code to rate else null
    }?.toMap() ?: return null
    if (raw.isEmpty()) return null
    val perBase = raw + (base to 1.0)
    val dollar = perBase["USD"] ?: return null
    val perUsd = perBase.mapValues { it.value / dollar }
    GearRates(date, perUsd + ("USD" to 1.0), fetchedAtMillis, RATES_SOURCE)
} catch (e: Exception) {
    null
}

/** How old the rates are and where they are from, in words: "Rates of 2026-10-30, fetched 3 h ago, from ...". [nowMillis] is the clock; a cache from before the fetch time was kept says only its date. */
fun ratesFreshness(rates: GearRates, nowMillis: Long): String {
    val source = rates.source.ifEmpty { RATES_SOURCE }
    val age = if (rates.fetchedAtMillis > 0 && nowMillis >= rates.fetchedAtMillis) {
        val minutes = (nowMillis - rates.fetchedAtMillis) / 60_000
        ", fetched " + when { minutes < 1 -> "just now"; minutes < 60 -> "$minutes min ago"; minutes < 24 * 60 -> "${minutes / 60} h ago"; else -> "${minutes / (24 * 60)} days ago" }
    } else ""
    return "Rates of ${rates.date}$age, from $source. Reference rates, not what a bank or money changer gives."
}

/** [amount] of [from] in [to] at [rates] (units of each currency for a dollar); null when either currency is unknown. */
fun convertCurrency(amount: Double, from: String, to: String, rates: GearRates): Double? {
    val a = rates.perUsd[from] ?: return null
    val b = rates.perUsd[to] ?: return null
    return amount / a * b
}
