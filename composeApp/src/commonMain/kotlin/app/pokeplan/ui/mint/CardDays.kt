package app.pokeplan.ui.mint

import app.pokeplan.mint.MintRules
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** 1 to 1st, 2 to 2nd, 11 to 11th, 22 to 22nd, 31 to 31st. */
internal fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}

/** "Statement on the 22nd · in 4 days", or null without a day. */
internal fun dayLine(label: String, day: Int, today: LocalDate): String? {
    val next = MintRules.nextOnDay(day, today) ?: return null
    val away = today.daysUntil(next)
    return "$label on the ${ordinal(day)} · " + when (away) { 0 -> "today"; 1 -> "tomorrow"; else -> "in $away days" }
}

/** A day-of-month field's text: digits only, at most two, 1 to 31; empty is none (0). */
internal fun dayInput(raw: String): String = raw.filter { it.isDigit() }.take(2).let { t -> if ((t.toIntOrNull() ?: 0) > 31) "31" else t }
