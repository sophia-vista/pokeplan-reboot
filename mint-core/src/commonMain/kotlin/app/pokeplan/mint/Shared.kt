package app.pokeplan.mint

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
enum class SharedKind(val label: String) {
    I_PAID("I paid"),

    THEY_PAID("They paid"),

    THEY_SETTLED("They paid me back"),

    I_SETTLED("I paid them back"),
}

@Serializable
data class SharedEntry(
    val id: String,
    val person: String,
    val kind: SharedKind,
    val amountMinor: Long,
    val currency: String,
    val day: LocalDate,
    val note: String = "",
)

data class SharedBalance(val person: String, val currency: String, val minor: Long)

object SharedRules {
    const val PERSON_MAX = 40
    const val NOTE_MAX = 120

    fun cleanPerson(raw: String): String = raw.trim().split(' ', '\n', '\t').filter { it.isNotEmpty() }.joinToString(" ").take(PERSON_MAX)

    fun signed(e: SharedEntry): Long = when (e.kind) {
        SharedKind.I_PAID, SharedKind.I_SETTLED -> e.amountMinor
        SharedKind.THEY_PAID, SharedKind.THEY_SETTLED -> -e.amountMinor
    }

    fun refusal(person: String, amountMinor: Long, currency: String): String? {
        if (cleanPerson(person).isEmpty()) return "Who is it with?"
        if (amountMinor <= 0) return "The amount must be above zero."
        (MintRules.checkCurrency(currency) as? Check.Refused)?.let { return it.reason }
        (MintRules.checkAmount(amountMinor) as? Check.Refused)?.let { return it.reason }
        return null
    }

    private fun key(person: String) = cleanPerson(person).lowercase()

    fun balances(entries: List<SharedEntry>): List<SharedBalance> =
        entries.groupBy { key(it.person) to it.currency }
            .map { (_, rows) -> SharedBalance(rows.maxByOrNull { it.day }!!.person.let(::cleanPerson), rows.first().currency, rows.sumOf(::signed)) }
            .filter { it.minor != 0L }
            .sortedWith(compareByDescending<SharedBalance> { kotlin.math.abs(it.minor) }.thenBy { it.person.lowercase() })

    fun owedToMe(balances: List<SharedBalance>, currency: String): Long = balances.filter { it.currency == currency && it.minor > 0 }.sumOf { it.minor }

    fun iOwe(balances: List<SharedBalance>, currency: String): Long = -balances.filter { it.currency == currency && it.minor < 0 }.sumOf { it.minor }

    fun settlement(balance: SharedBalance, day: LocalDate, id: String): SharedEntry? = when {
        balance.minor > 0 -> SharedEntry(id, balance.person, SharedKind.THEY_SETTLED, balance.minor, balance.currency, day)
        balance.minor < 0 -> SharedEntry(id, balance.person, SharedKind.I_SETTLED, -balance.minor, balance.currency, day)
        else -> null
    }
}
