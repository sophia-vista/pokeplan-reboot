package app.pokeplan.mint

import kotlinx.datetime.LocalDate

/**
 * Statement import from a CSV file the player chose (MINT.md sections 10.2 and 5.3; INTEGRATIONS_AND_PRIVACY.md sections 7.2 and 8.2). This is a **user-assisted
 * import**: nothing is connected to a bank or an e-wallet, nothing is fetched, and the file is read once, on the phone. It is all pure:
 *
 * 1. [parse] reads the text (quotes, doubled quotes, line breaks inside a field, comma, semicolon or tab, a byte-order mark) and refuses a file that is too big.
 * 2. [guess] proposes which column is the date, the description and the amount (or a debit and a credit column); the player can change any of them.
 * 3. [preview] turns the rows into proposed transactions for **one account**, each marked [RowState.NEW], [RowState.DUPLICATE] (this exact row was imported
 *    before: nothing to add), [RowState.POSSIBLE] (a record you wrote yourself looks the same: kept apart until you say) or [RowState.PROBLEM] (with the reason).
 * 4. `MintStore.importStatement` saves the rows the player kept, in one write, tagged with an import batch and a fingerprint, so importing the same file again adds
 *    nothing, and the whole batch can be removed on its own without touching a record the player wrote.
 *
 * Money is read exactly (never a floating-point number), a transaction keeps the account's currency (a file is not converted), and an import never pays: only
 * reconciling and the weekly review do (MINT.md section 16).
 */
object StatementImport {
    /** The biggest file read, in characters, and the most data rows kept: a bigger one is refused, not cut, so nothing is half-imported. */
    const val MAX_CHARS = 2_000_000
    const val MAX_ROWS = 5_000

    sealed interface Parsed {
        data class Table(val rows: List<List<String>>, val delimiter: Char) : Parsed
        data class Refused(val reason: String) : Parsed
    }

    /** Reads [text] as delimited text. Never throws; a file that is not usable comes back as [Parsed.Refused] with the reason in words. */
    fun parse(text: String): Parsed {
        if (text.length > MAX_CHARS) return Parsed.Refused("That file is too big to import (over ${MAX_CHARS / 1_000_000} million characters).")
        val body = text.removePrefix("﻿")
        if (body.isBlank()) return Parsed.Refused("That file is empty.")
        if (body.take(2000).any { it == '\u0000' }) return Parsed.Refused("That does not look like a text (CSV) file.")
        val delimiter = detectDelimiter(body)
        val rows = ArrayList<List<String>>()
        val field = StringBuilder()
        var row = ArrayList<String>()
        var quoted = false
        var i = 0
        fun endField() { row.add(field.toString().trim()); field.clear() }
        fun endRow() {
            endField()
            if (row.any { it.isNotEmpty() }) rows.add(row)
            row = ArrayList()
        }
        while (i < body.length) {
            val c = body[i]
            when {
                quoted -> if (c == '"') {
                    if (i + 1 < body.length && body[i + 1] == '"') { field.append('"'); i++ } else quoted = false
                } else field.append(c)
                c == '"' && field.isEmpty() -> quoted = true
                c == delimiter -> endField()
                c == '\r' -> { if (i + 1 < body.length && body[i + 1] == '\n') i++; endRow() }
                c == '\n' -> endRow()
                else -> field.append(c)
            }
            i++
            if (rows.size > MAX_ROWS + 50) return Parsed.Refused("That file has more than $MAX_ROWS rows. Split it by month and import them one at a time.")
        }
        if (quoted) return Parsed.Refused("A quoted field in that file never ends, so it is not a well-formed CSV.")
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        if (rows.size < 2) return Parsed.Refused("That file needs a header row and at least one transaction.")
        return Parsed.Table(rows, delimiter)
    }

    /** The delimiter that splits the first lines into the same number of fields most often (outside quotes): comma, semicolon or tab. */
    private fun detectDelimiter(text: String): Char {
        val lines = text.lineSequence().take(8).filter { it.isNotBlank() }.toList()
        fun fields(line: String, d: Char): Int {
            var q = false; var n = 1
            for (c in line) if (c == '"') q = !q else if (c == d && !q) n++
            return n
        }
        return listOf(',', ';', '\t').maxByOrNull { d -> if (lines.isEmpty()) 0 else lines.map { fields(it, d) }.let { f -> if (f.all { it == f.first() } && f.first() > 1) f.first() * 10 else f.max() } } ?: ','
    }

    // --- columns ----------------------------------------------------------------------------------------------------------

    /**
     * Which column of the file is which. [amount] is one signed column (a minus is money out, unless [flip]); [debit] and [credit] are the two-column form (a bank
     * that prints "Withdrawal" and "Deposit"). A column is its index in the row, or null for none.
     */
    data class Columns(
        val date: Int? = null, val description: Int? = null, val amount: Int? = null, val debit: Int? = null, val credit: Int? = null, val reference: Int? = null,
        /** The file prints charges as positive amounts (a credit-card statement): a plus is money out. */
        val flip: Boolean = false,
    ) {
        val usable: Boolean get() = date != null && (amount != null || debit != null || credit != null)
    }

    enum class DateOrder(val label: String, val example: String) {
        YEAR_FIRST("Year first", "2026-11-05"), DAY_FIRST("Day first", "05/11/2026"), MONTH_FIRST("Month first", "11/05/2026"),
    }

    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    /** Looks at the header names and proposes the columns. A guess, shown to the player to confirm or change. */
    fun guess(header: List<String>): Columns {
        fun find(vararg words: String, not: List<String> = emptyList()): Int? =
            header.indexOfFirst { h -> val l = h.lowercase(); words.any { it in l } && not.none { it in l } }.takeIf { it >= 0 }
        val debit = find("debit", "withdraw", "money out", "paid out", "outflow")
        val credit = find("credit", "deposit", "money in", "paid in", "inflow", not = listOf("card"))
        val amount = find("amount", "value", not = listOf("balance"))
        return Columns(
            date = find("date", "posted", "time"),
            description = find("description", "details", "payee", "merchant", "narrative", "particulars", "memo", "transaction"),
            amount = if (debit != null && credit != null) null else amount,
            debit = if (debit != null && credit != null) debit else null,
            credit = if (debit != null && credit != null) credit else null,
            reference = find("reference", "ref", "id", "trace", "confirmation"),
        )
    }

    /** The order the dates in [samples] are written in, or null when the samples do not settle it (every day and month is 12 or less): then the player chooses. */
    fun detectOrder(samples: List<String>): DateOrder? {
        var first = false; var second = false
        for (s in samples) {
            val parts = s.trim().split('/', '-', '.', ' ').filter { it.isNotEmpty() }
            if (parts.size < 3) continue
            val a = parts[0].toIntOrNull() ?: continue
            val b = parts[1].toIntOrNull() ?: continue
            if (a > 31) return DateOrder.YEAR_FIRST
            if (a > 12) first = true
            if (b > 12) second = true
        }
        return when { first && !second -> DateOrder.DAY_FIRST; second && !first -> DateOrder.MONTH_FIRST; else -> null }
    }

    /** One date in the file; a time of day after it is ignored, a month may be a name (`05 Nov 2026`, `Nov 5, 2026`). Null when it is not a real date. */
    fun parseDate(text: String, order: DateOrder): LocalDate? {
        val t = text.trim().substringBefore('T').let { if (it.length > 10 && it.contains(' ') && it.first().isDigit() && it.indexOf(' ') in 8..10) it.substringBefore(' ') else it }
        val named = t.split('/', '-', '.', ' ', ',').filter { it.isNotEmpty() }
        if (named.size == 3 && named.any { w -> w.length >= 3 && w.all { it.isLetter() } }) {
            val monthIndex = named.indexOfFirst { w -> w.length >= 3 && w.all { it.isLetter() } }
            val month = months.indexOf(named[monthIndex].lowercase().take(3)) + 1
            if (month == 0) return null
            val numbers = named.filterIndexed { i, _ -> i != monthIndex }.map { it.toIntOrNull() ?: return null }
            val (day, year) = if (numbers[0] > 31) numbers[1] to numbers[0] else numbers[0] to numbers[1]
            return date(year, month, day)
        }
        val p = t.split('/', '-', '.').map { it.trim() }
        if (p.size != 3 || p.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
        val n = p.map { it.toInt() }
        return when {
            p[0].length == 4 -> date(n[0], n[1], n[2])
            order == DateOrder.YEAR_FIRST -> null
            order == DateOrder.DAY_FIRST -> date(year(n[2], p[2].length), n[1], n[0])
            else -> date(year(n[2], p[2].length), n[0], n[1])
        }
    }

    private fun year(y: Int, digits: Int) = if (digits <= 2) 2000 + y else y

    private fun date(y: Int, m: Int, d: Int): LocalDate? =
        if (y !in 1990..2100 || m !in 1..12 || d !in 1..31) null else runCatching { LocalDate(y, m, d) }.getOrNull()

    /**
     * One amount cell as a signed number of minor units: `-1,250.50`, `(1,250.50)`, `PHP 1,250.50`, `₱1,250.50 DR`, `1250.50-`. A trailing or leading minus, brackets or
     * "DR" mean negative; "CR" means positive. Null when it is empty or not a number. More decimals than the currency has is not rounded: the cell is refused.
     */
    fun parseSigned(cell: String, currency: String): Long? {
        var s = cell.trim()
        if (s.isEmpty()) return null
        var negative = false
        if (s.startsWith("(") && s.endsWith(")")) { negative = true; s = s.substring(1, s.length - 1) }
        val upper = s.uppercase().trim()
        if (upper.endsWith("DR")) { negative = true; s = s.dropLast(2) } else if (upper.endsWith("CR")) s = s.dropLast(2)
        s = s.trim()
        if (s.startsWith("-") || s.startsWith("−")) { negative = true; s = s.drop(1) } else if (s.endsWith("-")) { negative = true; s = s.dropLast(1) } else if (s.startsWith("+")) s = s.drop(1)
        val digits = s.filter { it.isDigit() || it == '.' || it == ',' }
        if (digits.isEmpty()) return null
        val rest = s.filterNot { it.isDigit() || it == '.' || it == ',' || it.isWhitespace() }
        if (rest.isNotEmpty() && rest.uppercase() != currency && rest.uppercase() != "PHP" && rest != "₱" && rest != "$" && rest != "€" && rest != "£" && rest != "¥" && rest.uppercase() != "P") return null
        val minor = Money.parse(digits, currency) ?: return null
        return if (negative) -minor else minor
    }

    // --- the preview ------------------------------------------------------------------------------------------------------

    enum class RowState { NEW, DUPLICATE, POSSIBLE, PROBLEM }

    /**
     * One proposed transaction. [line] is its place in the file (the header is line 1). [signedMinor] is negative for money out. [fingerprint] is what makes a re-import
     * recognise it: account, day, signed amount, description, the file's own reference and, for identical rows in one file, a count (two coffees on a day are two records).
     */
    data class Row(
        val line: Int, val state: RowState, val day: LocalDate? = null, val signedMinor: Long = 0, val description: String = "", val reference: String = "",
        val fingerprint: String = "", val problem: String = "",
    ) {
        val type: TxType get() = if (signedMinor < 0) TxType.EXPENSE else TxType.INCOME
        val amountMinor: Long get() = kotlin.math.abs(signedMinor)
        /** Whether the player starts with this row kept: new rows are, duplicates and look-alikes are not. */
        val keptByDefault: Boolean get() = state == RowState.NEW
    }

    data class Preview(val account: FinancialAccount, val columns: Columns, val order: DateOrder, val rows: List<Row>) {
        val new: Int get() = rows.count { it.state == RowState.NEW }
        val duplicates: Int get() = rows.count { it.state == RowState.DUPLICATE }
        val possible: Int get() = rows.count { it.state == RowState.POSSIBLE }
        val problems: Int get() = rows.count { it.state == RowState.PROBLEM }
        val firstDay: LocalDate? get() = rows.mapNotNull { it.day }.minOrNull()
        val lastDay: LocalDate? get() = rows.mapNotNull { it.day }.maxOrNull()
    }

    /** What a description is compared as: lower case, one space between words. */
    fun normalised(text: String): String = text.lowercase().split(' ', '\t', '\n').filter { it.isNotEmpty() }.joinToString(" ")

    /**
     * Builds the preview of [table] (header included) for [account] under [columns] and [order]. Nothing is saved. A row is a problem when its date or amount can not be
     * read, when it is zero, or when it is too large; the rest of the file still previews. [data] is only read, to recognise rows imported before and look-alikes.
     */
    fun preview(table: List<List<String>>, account: FinancialAccount, columns: Columns, order: DateOrder, data: MintData): Preview {
        val body = table.drop(1)
        val known = data.transactions.mapNotNull { it.fingerprint }.toHashSet()
        val own = data.transactions.filter { it.accountId == account.id && it.status != TxStatus.VOIDED }
        val seen = HashMap<String, Int>()
        val rows = body.take(MAX_ROWS).mapIndexed { index, cells ->
            val line = index + 2
            fun cell(i: Int?) = i?.let { cells.getOrNull(it) }?.trim().orEmpty()
            val day = parseDate(cell(columns.date), order)
                ?: return@mapIndexed Row(line, RowState.PROBLEM, problem = if (cell(columns.date).isEmpty()) "No date." else "The date \"${cell(columns.date).take(20)}\" could not be read.")
            val signed: Long? = if (columns.amount != null) {
                parseSigned(cell(columns.amount), account.currency)?.let { if (columns.flip) -it else it }
            } else {
                val out = parseSigned(cell(columns.debit), account.currency)?.let { -kotlin.math.abs(it) }
                val inn = parseSigned(cell(columns.credit), account.currency)?.let { kotlin.math.abs(it) }
                when { out != null && inn != null && out != 0L && inn != 0L -> null; out != null && out != 0L -> out; inn != null && inn != 0L -> inn; else -> null }
            }
            if (signed == null) return@mapIndexed Row(line, RowState.PROBLEM, day, problem = "The amount could not be read.")
            if (signed == 0L) return@mapIndexed Row(line, RowState.PROBLEM, day, problem = "The amount is zero.")
            if (kotlin.math.abs(signed) > Money.MAX_MINOR) return@mapIndexed Row(line, RowState.PROBLEM, day, problem = "That amount looks wrong.")
            val description = cell(columns.description).take(MintRules.NAME_MAX * 2)
            val ref = cell(columns.reference).take(40)
            val base = "${account.id}|$day|$signed|${normalised(description)}|${ref}"
            val count = (seen[base] ?: 0) + 1
            seen[base] = count
            val fingerprint = "$base|#$count"
            val state = when {
                fingerprint in known -> RowState.DUPLICATE
                own.any { it.day == day && it.amountMinor == kotlin.math.abs(signed) && (it.type == TxType.EXPENSE || it.type == TxType.FEE) == (signed < 0) && it.type != TxType.TRANSFER && it.type != TxType.ADJUSTMENT } -> RowState.POSSIBLE
                else -> RowState.NEW
            }
            Row(line, state, day, signed, description, ref, fingerprint)
        }
        return Preview(account, columns, order, rows)
    }
}
