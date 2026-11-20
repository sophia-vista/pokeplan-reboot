package app.pokeplan.mint

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** What a shared-bill entry says happened (MINT.md 5.8A). */
@Serializable
enum class SharedKind(val label: String) {
    /** I footed the bill: the amount is their share, now owed to me. */
    I_PAID("I paid"),

    /** Someone else footed it: the amount is my share, now owed to them. */
    THEY_PAID("They paid"),

    /** A settlement: they paid me back. */
    THEY_SETTLED("They paid me back"),

    /** A settlement: I paid them back. */
    I_SETTLED("I paid them back"),
}

/** One line of the shared-bills ledger: a person (a plain name), what happened, an amount in minor units above zero, and when. */
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

/** What one person owes or is owed in one currency: above zero they owe the player, below zero the player owes them. */
data class SharedBalance(val person: String, val currency: String, val minor: Long)

/** The shared-bills rules (MINT.md 5.8A): display only, pure, exact integers; nothing here touches an account, a transaction or a reward. */
object SharedRules {
    const val PERSON_MAX = 40
    const val NOTE_MAX = 120

    /** The name as kept: edges trimmed, inner runs of spaces made one, cut to [PERSON_MAX]. */
    fun cleanPerson(raw: String): String = raw.trim().split(' ', '\n', '\t').filter { it.isNotEmpty() }.joinToString(" ").take(PERSON_MAX)

    /** What an entry adds to its person's balance: what is owed to the player goes up with "I paid" and "I paid them back". */
    fun signed(e: SharedEntry): Long = when (e.kind) {
        SharedKind.I_PAID, SharedKind.I_SETTLED -> e.amountMinor
        SharedKind.THEY_PAID, SharedKind.THEY_SETTLED -> -e.amountMinor
    }

    /** Why an entry cannot be kept, in words, or null when it can. */
    fun refusal(person: String, amountMinor: Long, currency: String): String? {
        if (cleanPerson(person).isEmpty()) return "Who is it with?"
        if (amountMinor <= 0) return "The amount must be above zero."
        (MintRules.checkCurrency(currency) as? Check.Refused)?.let { return it.reason }
        (MintRules.checkAmount(amountMinor) as? Check.Refused)?.let { return it.reason }
        return null
    }

    private fun key(person: String) = cleanPerson(person).lowercase()

    /** Every person's balance per currency, the largest amounts first; squared-up balances (zero) are left out. */
    fun balances(entries: List<SharedEntry>): List<SharedBalance> =
        entries.groupBy { key(it.person) to it.currency }
            .map { (_, rows) -> SharedBalance(rows.maxByOrNull { it.day }!!.person.let(::cleanPerson), rows.first().currency, rows.sumOf(::signed)) }
            .filter { it.minor != 0L }
            .sortedWith(compareByDescending<SharedBalance> { kotlin.math.abs(it.minor) }.thenBy { it.person.lowercase() })

    /** Owed to the player in [currency]: the sum of the positive balances. */
    fun owedToMe(balances: List<SharedBalance>, currency: String): Long = balances.filter { it.currency == currency && it.minor > 0 }.sumOf { it.minor }

    /** What the player owes in [currency], as a positive amount: the sum of the negative balances, never netted against what is owed to them. */
    fun iOwe(balances: List<SharedBalance>, currency: String): Long = -balances.filter { it.currency == currency && it.minor < 0 }.sumOf { it.minor }

    /** The settlement that brings [balance] to zero, or null when it is square. */
    fun settlement(balance: SharedBalance, day: LocalDate, id: String): SharedEntry? = when {
        balance.minor > 0 -> SharedEntry(id, balance.person, SharedKind.THEY_SETTLED, balance.minor, balance.currency, day)
        balance.minor < 0 -> SharedEntry(id, balance.person, SharedKind.I_SETTLED, -balance.minor, balance.currency, day)
        else -> null
    }
}
