package app.pokeplan.ui.gear

import app.pokeplan.app.GearRates
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The Currency tool's exchange rates from Frankfurter, a free service of the European Central Bank's daily rates (no account
 * or key). Only the rates are fetched; nothing about the user is sent.
 */
class RatesClient(private val http: HttpClient = HttpClient()) {
    /** Today's rates, or null with no connection or an answer that cannot be read. */
    suspend fun latest(): GearRates? = try {
        parseRates(http.get("https://api.frankfurter.dev/v1/latest?base=USD").bodyAsText())
    } catch (e: Exception) {
        null
    }
}

/** Reads the service's answer (`{"base":"USD","date":"2026-10-30","rates":{"EUR":0.86,...}}`), adding the dollar itself at 1. Null when it is not that. */
fun parseRates(text: String): GearRates? = try {
    val root = Json.parseToJsonElement(text).jsonObject
    val date = root["date"]?.jsonPrimitive?.content ?: return null
    val rates = (root["rates"] as? JsonObject)?.mapNotNull { (code, value) -> value.jsonPrimitive.doubleOrNull?.let { code to it } }?.toMap() ?: return null
    if (rates.isEmpty()) null else GearRates(date, rates + ("USD" to 1.0))
} catch (e: Exception) {
    null
}

/** [amount] of [from] in [to] at [rates] (units of each currency for a dollar); null when either currency is unknown. */
fun convertCurrency(amount: Double, from: String, to: String, rates: GearRates): Double? {
    val a = rates.perUsd[from] ?: return null
    val b = rates.perUsd[to] ?: return null
    return amount / a * b
}
