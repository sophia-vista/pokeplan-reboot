package app.pokeplan.mint

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Where a captured alert came from: a notification (MINT.md 10.4) or a text message. Both are read on the phone only,
 * and only from a [CaptureSource] on the list below.
 */
@Serializable
enum class CaptureChannel { NOTIFICATION, MESSAGE }

/**
 * One bank or wallet MINT may read alerts from (MINT.md 10.4, INTEGRATIONS_AND_PRIVACY.md 7.3): a narrow list of the
 * exact notification packages and SMS sender ids. Everything else is dropped before any content is read, never kept,
 * counted or logged.
 */
enum class CaptureSource(val label: String, val packages: List<String>, val senders: List<String>) {
    GCASH("GCash", listOf("com.globe.gcash.android", "com.globe.gcash"), listOf("GCASH", "GLOBE-GCASH")),
    MAYA("Maya", listOf("com.paymaya", "com.paymaya.stage"), listOf("MAYA", "PAYMAYA")),
    MARIBANK("MariBank", listOf("com.maribank.android", "com.maribank.app"), listOf("MARIBANK")),
    GOTYME("GoTyme", listOf("com.gotyme.ph", "com.gotyme"), listOf("GOTYME")),
    UNIONBANK("UnionBank", listOf("com.unionbankph.online", "com.unionbank.online"), listOf("UNIONBANK", "UBP", "UNIONBANKPH")),
    BDO("BDO", listOf("com.bdo.mobilebanking", "com.bdo.authenticator", "com.bdo.digitalbanking"), listOf("BDO")),
    BPI("BPI", listOf("com.bpi.mobile", "ph.com.bpi.mobile"), listOf("BPI", "BPIEXPRESS")),
    METROBANK("Metrobank", listOf("com.metrobank.mobile"), listOf("METROBANK", "MBTC")),
    LANDBANK("LandBank", listOf("com.landbank.mobilebanking", "ph.gov.landbank"), listOf("LANDBANK", "LBP")),
    CIMB("CIMB", listOf("com.cimbbank.ph", "com.cimb.ph"), listOf("CIMB")),
    SEABANK("SeaBank", listOf("com.seabank.ph", "ph.seabank.app"), listOf("SEABANK")),
    ;

    companion object {
        /** The source whose notification package this is, or null (checked before any content is read). */
        fun ofPackage(packageName: String): CaptureSource? = entries.firstOrNull { packageName.trim() in it.packages }

        /** The source whose SMS sender id this is (case and spaces ignored), or null. */
        fun ofSender(sender: String): CaptureSource? {
            val id = sender.trim().uppercase().replace(" ", "")
            if (id.isEmpty()) return null
            return entries.firstOrNull { s -> s.senders.any { it.replace(" ", "") == id || id.startsWith(it.replace(" ", "")) } }
        }

        fun ofName(name: String): CaptureSource? = entries.firstOrNull { it.name == name }
    }
}

/**
 * One alert as the phone saw it, handed to [MintCapture.parse] in memory. The raw [title] and [text] are never saved:
 * only the fields of the [CaptureCandidate] the parse makes out of them.
 */
data class CaptureText(
    val channel: CaptureChannel,
    val source: CaptureSource,
    val atMillis: Long,
    val day: LocalDate,
    val title: String = "",
    val text: String = "",
)

/**
 * What a [CaptureText] said, reduced to the fields a [Transaction] keeps (MINT.md 10.4: "Retain only the minimum parsed
 * fields needed after confirmation"): the amount, its direction, a merchant or description, the day and the alert's
 * source. It is proposed as a PENDING record for the player to confirm, adjust or skip (5.9) and is never posted by
 * itself.
 */
@Serializable
data class CaptureCandidate(
    /** A stable fingerprint of the parsed fields, so the same alert arriving twice is one proposal. */
    val ref: String,
    /** The [CaptureSource] name, never the raw message. */
    val source: String,
    val channel: CaptureChannel,
    /** Only [TxType.EXPENSE] or [TxType.INCOME]. */
    val type: TxType,
    val amountMinor: Long,
    val currency: String,
    /** The merchant or a short plain-words description; falls back to the source's label. */
    val payee: String,
    /** A short note for the record, such as "From GCash". */
    val note: String,
    val day: LocalDate,
    val atMillis: Long,
    /** The last four digits of the card or account the alert names, when it names them (used to pick the account). */
    val lastDigits: String? = null,
) {
    val sourceLabel: String get() = CaptureSource.ofName(source)?.label ?: source
}

/**
 * Reads a bank or wallet alert on the phone and makes a [CaptureCandidate] out of it (MINT.md 10.4). Nothing here
 * touches the network, the save or the raw text once it has been parsed, and an alert it cannot read with confidence
 * (an OTP, a promo, an amount it cannot find, a direction it cannot tell) becomes null rather than a guess.
 *
 * The parse is deliberately plain Kotlin (no regex, no locale): the same code runs on the JVM and in the browser, and
 * the same alert parsed twice gives the same candidate.
 */
object MintCapture {
    /** Longest text looked at; anything longer is not a bank alert. */
    const val TEXT_MAX: Int = 600

    private const val PESO_SIGN = '\u20B1'
    private const val MASK_BULLET = '\u2022'
    private const val NAME_LIMIT = MintRules.NAME_MAX * 2

    /**
     * The candidate [message] proposes, or null when it is not a transaction MINT can read with confidence.
     * [currency] is the account currency the alert is read in; [MintRules.NAME_MAX] caps the payee.
     */
    fun parse(message: CaptureText, currency: String = "PHP"): CaptureCandidate? {
        val title = message.title.trim()
        val body = message.text.trim()
        if (title.isEmpty() && body.isEmpty()) return null
        val raw = when {
            title.isEmpty() -> body
            body.isEmpty() -> title
            else -> title + "\n" + body
        }
        if (raw.length > TEXT_MAX) return null
        val lower = lowercaseChars(raw)
        if (isNotATransaction(lower)) return null
        val type = direction(lower) ?: return null
        val amountMinor = readAmount(lower, currency) ?: return null
        if (deliversACode(lower)) return null
        val lastDigits = readLastDigits(raw, lower)
        val named = readPayee(raw, lower) ?: message.source.label
        val payee = if (named.length > NAME_LIMIT) named.take(NAME_LIMIT).trim() else named
        return CaptureCandidate(
            ref = reference(message, type, amountMinor, payee, lastDigits),
            source = message.source.name,
            channel = message.channel,
            type = type,
            amountMinor = amountMinor,
            currency = currency,
            payee = payee,
            note = ("From " + message.source.label).take(MintRules.NOTE_MAX),
            day = message.day,
            atMillis = message.atMillis,
            lastDigits = lastDigits,
        )
    }

    // --- what is never a proposed record ------------------------------------------------------------------------------

    /**
     * Text that is never a completed transaction: promos and offers, failed or reversed payments, statement reminders
     * and scheduled future debits. It has to be checked even when a verb and an amount are there ("your payment of
     * PHP 500 was declined").
     */
    private val REFUSED = listOf(
        "voucher", "promo", "promos", "promocode", "promo code", "coupon", "offer", "offers",
        "pre-approved", "pre approved", "preapproved", "loan offer", "expiring",
        "failed", "declined", "unsuccessful", "not successful", "cancelled", "canceled",
        "reversed", "reversal", "voided",
        "reminder", "scheduled", "will be debited", "will be charged", "will be deducted",
        "will be auto-debited", "will be auto debited", "upcoming", "due on", "payment is due",
        "bill is due", "auto debit", "auto-debit", "autodebit", "standing instruction",
        "statement is ready", "e-statement", "pay before", "pay on or before",
    )

    private fun isNotATransaction(lower: String): Boolean = REFUSED.any { findWord(lower, it) >= 0 }

    /**
     * The security wording a real alert carries ("never share your OTP") is not a reason to drop it; only an alert that
     * is actually delivering a code is refused, when the code marker is followed by the digits themselves.
     */
    private val CODE_MARKERS = listOf(
        "otp", "one-time password", "one time password", "one-time pin", "one time pin",
        "verification code", "verify code", "verification", "code is", "password is", "pin is",
        "password", "pin", "do not share", "never share", "do not disclose", "do not give",
        "keep it private",
    )

    private fun deliversACode(lower: String): Boolean = CODE_MARKERS.any { marker ->
        var at = findWord(lower, marker)
        while (at >= 0) {
            if (digitsSoonAfter(lower, at + marker.length)) return true
            at = findWord(lower, marker, at + 1)
        }
        false
    }

    /** Whether four to eight digits follow within a short reach, the way a code is delivered ("OTP is 123456"). */
    private fun digitsSoonAfter(text: String, from: Int): Boolean {
        var digits = 0
        var i = from
        var reach = 0
        while (i < text.length && reach < 40) {
            val c = text[i]
            when {
                c.isDigit() -> { digits++; if (digits >= 4) return true }
                c == ' ' || c == '-' || c == ':' || c == '.' || c == '#' || c == ',' -> {}
                else -> if (digits > 0) return false
            }
            i++
            reach++
        }
        return false
    }

    // --- direction -----------------------------------------------------------------------------------------------------

    private val INCOME_WORDS = listOf(
        "credited", "received", "deposit", "deposited", "cash in", "cash-in", "cashin",
        "you received", "added to your", "refund", "refunded", "cashback", "inward",
        "credit transaction", "money in", "proceeds",
    )

    private val EXPENSE_WORDS = listOf(
        "debited", "paid", "payment", "you paid", "sent", "transfer to", "withdrawn", "withdrawal",
        "cash out", "cash-out", "cashout", "purchase", "charged", "billed", "outward",
        "deducted", "deduction", "spent", "debit transaction",
    )

    /** INCOME or EXPENSE, or null when the words say both or neither (never a guess). */
    private fun direction(lower: String): TxType? {
        val income = INCOME_WORDS.any { findWord(lower, it) >= 0 }
        val expense = EXPENSE_WORDS.any { findWord(lower, it) >= 0 }
        return when {
            income && !expense -> TxType.INCOME
            expense && !income -> TxType.EXPENSE
            else -> null
        }
    }

    // --- amount --------------------------------------------------------------------------------------------------------

    /** Words whose following figure is not the amount of this transaction (a balance, a limit, a reference number). */
    private val BALANCE_WORDS = listOf("balance", "balances", "bal", "avl", "limit", "limits")

    private val BALANCE_SKIP = listOf(
        "is", "now", "of", "was", "at", "as", "today", "your", "new", "the", "current",
        "available", "php", "p", "peso", "pesos", "in", "on", "remaining",
    )

    private val REF_WORDS = listOf("ref", "reference", "trace", "auth", "approval", "txn")

    private val REF_SKIP = listOf("no", "number", "num", "code", "id")

    /**
     * The one amount the alert names, in minor units, or null when it names none/two that cannot be told apart.
     * A figure next to a currency mark (PHP, P, ₱, the account's code) wins over a bare one; balances, limits, card
     * endings and reference numbers are masked out first so they can never be read as the amount.
     */
    private fun readAmount(lower: String, currency: String): Long? {
        val chars = lower.toCharArray()
        maskWords(chars, lower, BALANCE_WORDS, BALANCE_SKIP, requireDigit = true, minRun = 1)
        maskWords(chars, lower, REF_WORDS, REF_SKIP, requireDigit = false, minRun = 3)
        for (hit in lastFourHits(lower)) blank(chars, hit.maskFrom, hit.runEnd)
        val text = chars.concatToString()

        val marked = ArrayList<Long>()
        val bare = ArrayList<Long>()
        var i = 0
        while (i < text.length) {
            if (!text[i].isDigit()) {
                i++
                continue
            }
            val start = i
            var j = i
            var digits = 0
            var commas = 0
            var decimal = false
            while (j < text.length) {
                val c = text[j]
                if (c.isDigit()) {
                    digits++
                    j++
                } else if (c == ',') {
                    commas++
                    j++
                } else if (c == '.' && !decimal) {
                    var k = j + 1
                    var fraction = 0
                    while (k < text.length && text[k].isDigit()) {
                        fraction++
                        k++
                    }
                    if (fraction in 1..2) {
                        decimal = true
                        digits += fraction
                        j = k
                    } else {
                        break
                    }
                } else {
                    break
                }
            }
            val end = j
            i = if (end > start) end else start + 1
            if (digits == 0 || digits > 12) continue
            if (start > 0 && (isWordChar(text[start - 1]) || text[start - 1] == '.')) {
                // "PHP250.00": the currency code sits right against the figure and is not part of a word.
                if (!currencyTouching(text, currency, start)) continue
            }
            // A figure in brackets is a phone number, a reference or a date, never the money.
            if (start > 0 && text[start - 1] == '(') {
                var k = end
                while (k < text.length && (text[k] == ' ' || text[k] == '-')) k++
                if (k < text.length && text[k] == ')') continue
            }
            if (end < text.length) {
                val after = text[end]
                if (isWordChar(after)) continue
                if (after == '.' && end + 1 < text.length && isWordChar(text[end + 1])) continue
                if (after == '%') continue
                if (after == '/' || after == ':' || after == '-') continue
            }
            if (start > 0 && (text[start - 1] == '/' || text[start - 1] == ':' || text[start - 1] == '-')) continue
            if (readSuffixWord(lower, end) in setOf("points", "point", "pts", "percent")) continue
            val numeral = text.substring(start, end).trimEnd(',', '.')
            if (numeral.isEmpty()) continue
            val minor = Money.parse(numeral, currency) ?: continue
            if (minor <= 0) continue
            val isMarked = currencyBefore(text, currency, start) || currencyAfter(text, currency, end)
            if (!isMarked) {
                // A bare two-letter figure or a bare year is never the amount of a peso alert.
                if (!decimal && commas == 0 && digits <= 2) continue
                if (!decimal && commas == 0 && digits == 4) {
                    val n = numeral.toLongOrNull() ?: 0L
                    if (n in 1900L..2099L) continue
                }
            }
            if (isMarked) marked += minor else bare += minor
        }
        return marked.firstOrNull() ?: bare.firstOrNull()
    }

    /**
     * Blanks the phrase starting at each [words] hit through the figure it names, so a balance, a limit or a reference
     * number can never be read as the transaction amount. Searching uses [lower]; blanking uses [chars] (same length).
     */
    private fun maskWords(chars: CharArray, lower: String, words: List<String>, skips: List<String>, requireDigit: Boolean, minRun: Int) {
        for (word in words) {
            var at = findWord(lower, word)
            while (at >= 0) {
                var i = at + word.length
                var guard = 0
                var stopped = false
                while (i < lower.length && guard < 40) {
                    val c = lower[i]
                    if (c == ' ' || c == '\t' || c == ':' || c == '.' || c == ',' || c == '#' || c == '=' || c == '-' || c == PESO_SIGN) {
                        i++
                        guard++
                        continue
                    }
                    var moved = false
                    for (s in skips) {
                        if (lower.startsWith(s, i) && isBoundaryAfter(lower, i, s.length)) {
                            i += s.length
                            moved = true
                            guard++
                            break
                        }
                    }
                    if (moved) continue
                    if (if (requireDigit) c.isDigit() else isAlnum(c)) break
                    stopped = true
                    break
                }
                if (!stopped && i < lower.length && (if (requireDigit) lower[i].isDigit() else isAlnum(lower[i]))) {
                    val end = if (requireDigit) tokenEnd(lower, i) else alnumRunEnd(lower, i)
                    if (end - i >= minRun) blank(chars, at, end)
                }
                at = findWord(lower, word, at + word.length)
            }
        }
    }

    /** The end of a money-like token starting at [start] (digits, thousands commas and at most one decimal point). */
    private fun tokenEnd(text: String, start: Int): Int {
        var j = start
        var decimal = false
        while (j < text.length) {
            val c = text[j]
            if (c.isDigit() || c == ',') {
                j++
                continue
            }
            if (c == '.' && !decimal) {
                var k = j + 1
                var fraction = 0
                while (k < text.length && text[k].isDigit()) {
                    fraction++
                    k++
                }
                if (fraction in 1..2) {
                    decimal = true
                    j = k
                    continue
                }
            }
            break
        }
        return j
    }

    /** The lower-case word that follows [from] after its spaces, or "" (used to skip "50 points" and "5 percent"). */
    private fun readSuffixWord(lower: String, from: Int): String {
        var i = from
        while (i < lower.length && (lower[i] == ' ' || lower[i] == '\t')) i++
        val start = i
        while (i < lower.length && isWordChar(lower[i])) i++
        return if (i > start) lower.substring(start, i) else ""
    }

    private fun currencyBefore(text: String, currency: String, start: Int): Boolean {
        var i = start
        while (i > 0 && (text[i - 1] == ' ' || text[i - 1] == '\t')) i--
        if (i == 0) return false
        if (text[i - 1] == PESO_SIGN) return true
        for (code in currencyCodes(currency)) {
            val from = i - code.length
            if (from >= 0 && text.startsWith(code, from) && (from == 0 || !isWordChar(text[from - 1]))) return true
        }
        return false
    }

    private fun currencyAfter(text: String, currency: String, end: Int): Boolean {
        var i = end
        while (i < text.length && (text[i] == ' ' || text[i] == '\t')) i++
        if (i >= text.length) return false
        if (text[i] == PESO_SIGN) return true
        val codes = currencyCodes(currency) + listOf("peso", "pesos")
        for (code in codes) {
            if (text.startsWith(code, i)) {
                val after = i + code.length
                if (after >= text.length || !isWordChar(text[after])) return true
            }
        }
        return false
    }

    /** Whether the letters right before [start] are this alert's currency, as in "PHP250.00". */
    private fun currencyTouching(text: String, currency: String, start: Int): Boolean {
        var from = start
        while (from > 0 && text[from - 1].isLetter()) from--
        if (from == start) return false
        return lowercaseChars(text.substring(from, start)) in (currencyCodes(currency) + listOf("peso", "pesos"))
    }

    private fun currencyCodes(currency: String): List<String> {
        val code = lowercaseChars(currency.trim())
        return if (code.isEmpty() || code == "php") listOf("php", "p") else listOf("php", "p", code)
    }

    // --- last four digits ----------------------------------------------------------------------------------------------

    /** One "ending 1234" / "****1234" / "card no. 1234" phrase: where its mask starts and where its alphanumeric run ends. */
    private class LastFourHit(val maskFrom: Int, val runAt: Int, val runEnd: Int)

    private fun lastFourHits(lower: String): List<LastFourHit> {
        val out = ArrayList<LastFourHit>()
        var i = 0
        while (i < lower.length) {
            val c = lower[i]
            if (c == 'e' && lower.startsWith("ending", i) && isBoundaryBefore(lower, i) && isBoundaryAfter(lower, i, 6)) {
                val at = lastFourStart(lower, i + 6)
                val end = alnumRunEnd(lower, at)
                if (end > at) out += LastFourHit(i, at, end)
                i += 6
                continue
            }
            if (c == '*' || c == MASK_BULLET || c == 'x') {
                var j = i
                while (j < lower.length && (lower[j] == '*' || lower[j] == MASK_BULLET || lower[j] == 'x')) j++
                if (j - i >= 3) {
                    val at = skipMarks(lower, j)
                    val end = alnumRunEnd(lower, at)
                    if (end > at) out += LastFourHit(i, j, end)
                }
                i = j
                continue
            }
            i++
        }
        for (word in listOf("card", "acct", "account")) {
            var at = findWord(lower, word)
            while (at >= 0) {
                var j = skipMarks(lower, at + word.length)
                var matched = false
                if (lower.startsWith("no", j) && isBoundaryAfter(lower, j, 2)) {
                    j = skipMarks(lower, j + 2)
                    matched = true
                } else if (lower.startsWith("num", j) && isBoundaryAfter(lower, j, 3)) {
                    j = skipMarks(lower, j + 3)
                    matched = true
                } else if (lower.startsWith("number", j) && isBoundaryAfter(lower, j, 6)) {
                    j = skipMarks(lower, j + 6)
                    matched = true
                }
                if (matched) {
                    val end = alnumRunEnd(lower, j)
                    if (end > j) out += LastFourHit(at, j, end)
                }
                at = findWord(lower, word, at + word.length)
            }
        }
        return out
    }

    /** Skips "in", "with", "no", "number" and masking marks ("****", "••••", "xxxx") after "ending". */
    private fun lastFourStart(lower: String, from: Int): Int {
        var i = from
        var guard = 0
        while (i < lower.length && guard < 20) {
            val c = lower[i]
            if (c == ' ' || c == '\t' || c == '.' || c == ':' || c == '#' || c == '-' || c == '*' || c == MASK_BULLET) {
                i++
                guard++
                continue
            }
            if (c == 'x') {
                var j = i
                while (j < lower.length && lower[j] == 'x') j++
                if (j - i >= 3) {
                    i = j
                    guard++
                    continue
                }
            }
            var moved = false
            for (w in listOf("in", "with", "no", "number", "digits", "last")) {
                if (lower.startsWith(w, i) && isBoundaryAfter(lower, i, w.length)) {
                    i += w.length
                    moved = true
                    guard++
                    break
                }
            }
            if (!moved) break
        }
        return i
    }

    private fun skipMarks(text: String, from: Int): Int {
        var i = from
        while (i < text.length) {
            val c = text[i]
            if (c == ' ' || c == '\t' || c == '.' || c == ':' || c == '#' || c == '-' || c == '*' || c == MASK_BULLET) {
                i++
            } else {
                break
            }
        }
        return i
    }

    /** The last four characters the alert names, digits preferred, or null when it names no card or account ending. */
    private fun readLastDigits(raw: String, lower: String): String? {
        val values = ArrayList<String>()
        for (hit in lastFourHits(lower)) {
            lastFourValue(raw.substring(hit.runAt, hit.runEnd))?.let { values += it }
        }
        return values.firstOrNull { value -> value.all { it.isDigit() } } ?: values.firstOrNull()
    }

    private fun lastFourValue(run: String): String? {
        if (run.isEmpty()) return null
        if (run.all { it.isDigit() }) return if (run.length >= 4) run.substring(run.length - 4) else null
        val hasDigit = run.any { it.isDigit() }
        val hasLetter = run.any { it.isLetter() }
        if (!hasDigit || !hasLetter) return null
        if (run.length >= 4) return run.substring(0, 4)
        return if (run.length >= 2) run else null
    }

    // --- payee ---------------------------------------------------------------------------------------------------------

    private val PAYEE_KEYS = listOf("merchant", "payee", "to", "at", "from", "via", "for")

    private val PAYEE_STOPS = listOf(
        " on", " at", " via", " using", " with", " from", " for", " of", " ref", " reference",
        " dated", " date", " ending", " acct", " account", " card", " balance", " limit",
        " today", " yesterday", " amount", " peso", " pesos", " php", " wallet", " transaction",
    )

    private val PAYEE_BAD_PARTS = listOf(
        "amount", "balance", "account", "acct", "reference", "ref no", "otp", "password",
        "credit card", "debit card", " your ", " our ", " my ", "transaction", "ending", "php",
        "peso", PESO_SIGN.toString(),
    )

    private val PAYEE_STOP_NAMES = setOf(
        "card", "cards", "account", "acct", "balance", "wallet", "bank", "creditcard", "debitcard",
        "accountnumber", "cardnumber", "cash", "payment", "transaction", "amount", "transfer",
    )

    /** A short payee from "at NAME", "to NAME", "from NAME", "via NAME", "merchant: NAME" or "for NAME". */
    private fun readPayee(raw: String, lower: String): String? {
        for (key in PAYEE_KEYS) {
            var at = findWord(lower, key)
            while (at >= 0) {
                var j = at + key.length
                while (j < lower.length && (lower[j] == ' ' || lower[j] == '\t')) j++
                if (j < lower.length && (lower[j] == ':' || lower[j] == '-' || lower[j] == '=')) {
                    j++
                    while (j < lower.length && (lower[j] == ' ' || lower[j] == '\t')) j++
                }
                val name = captureName(raw, lower, j)
                if (name != null) return name
                at = findWord(lower, key, at + key.length)
            }
        }
        return null
    }

    private fun captureName(raw: String, lower: String, start: Int): String? {
        val limit = minOf(lower.length, start + 140)
        var i = start
        while (i < limit) {
            val c = lower[i]
            if (c == '\n' || c == '\r') break
            val allowed = c.isLetterOrDigit() || c == ' ' || c == '\t' || c == '&' || c == '\'' ||
                c == '.' || c == '-' || c == '/' || c == '*' || c == '+' || c == '#' || c == '@'
            if (!allowed) break
            if (c == '.' && (i + 1 >= limit || lower[i + 1] == ' ' || lower[i + 1] == '\t' || lower[i + 1] == '\n')) break
            i++
        }
        var name = raw.substring(start, i).trim()
        var cut = name.length
        val nameLower = lowercaseChars(name)
        for (stop in PAYEE_STOPS) {
            val at = findWord(nameLower, stop)
            if (at in 0 until cut) cut = at
        }
        name = trimEdges(name.substring(0, cut))
        val lowName = lowercaseChars(name)
        if (lowName.startsWith("your ") || lowName == "your" || lowName.startsWith("my ") || lowName == "my" ||
            lowName.startsWith("our ") || lowName == "our"
        ) return null
        name = stripLeadingArticles(name)
        name = collapseSpaces(trimEdges(name))
        if (!validPayee(name)) return null
        return name
    }

    private fun validPayee(name: String): Boolean {
        if (name.length < 2) return false
        if (name.count { it.isLetter() } < 2) return false
        val normalized = normalizeName(name)
        if (normalized.isEmpty() || normalized in PAYEE_STOP_NAMES) return false
        val low = lowercaseChars(name)
        return PAYEE_BAD_PARTS.none { low.contains(it) }
    }

    private fun stripLeadingArticles(name: String): String {
        for (prefix in listOf("the ", "a ", "an ")) {
            if (name.length > prefix.length && lowercaseChars(name).startsWith(prefix)) return name.substring(prefix.length)
        }
        return name
    }

    private fun trimEdges(text: String): String {
        var a = 0
        var b = text.length
        while (a < b && (text[a].isWhitespace() || text[a] in EDGE_CHARS)) a++
        while (b > a && (text[b - 1].isWhitespace() || text[b - 1] in EDGE_CHARS)) b--
        return text.substring(a, b)
    }

    private const val EDGE_CHARS = ",.;:-*/#'&()\"@"

    private fun collapseSpaces(text: String): String {
        val builder = StringBuilder(text.length)
        var space = false
        for (ch in text) {
            if (ch == ' ' || ch == '\t') {
                if (!space && builder.isNotEmpty()) builder.append(' ')
                space = true
            } else {
                builder.append(ch)
                space = false
            }
        }
        return builder.toString().trim()
    }

    // --- fingerprint ---------------------------------------------------------------------------------------------------

    /**
     * A stable fingerprint of the fields that make one purchase: the source, the direction, the amount, the day, the
     * payee (lower case, letters and digits only) and the card ending. The channel and the raw text are left out, so the
     * same purchase seen once by text message and once by notification is one proposal.
     */
    private fun reference(message: CaptureText, type: TxType, amountMinor: Long, payee: String, lastDigits: String?): String {
        val key = message.source.name + "|" + type.name + "|" + amountMinor + "|" + message.day + "|" +
            normalizeName(payee) + "|" + (lastDigits ?: "")
        var hash = -3750763034362895579L
        val prime = 1099511628211L
        for (ch in key) {
            hash = hash xor ch.code.toLong()
            hash *= prime
        }
        return "cap-" + (if (hash < 0) "n" else "p") + hash.toString(16).removePrefix("-")
    }

    // --- small text helpers (plain Kotlin: no regex, no locale) --------------------------------------------------------

    /** [text] with every character lower cased one for one, so indices still line up with the original. */
    private fun lowercaseChars(text: String): String {
        val builder = StringBuilder(text.length)
        for (ch in text) builder.append(ch.lowercaseChar())
        return builder.toString()
    }

    private fun normalizeName(text: String): String {
        val lower = lowercaseChars(text)
        val builder = StringBuilder(lower.length)
        for (ch in lower) if (ch.isLetterOrDigit()) builder.append(ch)
        return builder.toString()
    }

    private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'

    private fun isAlnum(c: Char): Boolean = c.isLetterOrDigit()

    private fun isBoundaryBefore(text: String, start: Int): Boolean = start <= 0 || !isWordChar(text[start - 1])

    private fun isBoundaryAfter(text: String, start: Int, length: Int): Boolean =
        start + length >= text.length || !isWordChar(text[start + length])

    /** First whole-word (or whole-phrase) occurrence of [phrase] in [text], or -1. Case-sensitive: pass lower-case. */
    private fun findWord(text: String, phrase: String, from: Int = 0): Int {
        if (phrase.isEmpty()) return -1
        val checkBefore = isWordChar(phrase[0])
        val checkAfter = isWordChar(phrase[phrase.length - 1])
        var i = maxOf(0, from)
        while (i + phrase.length <= text.length) {
            if (text.startsWith(phrase, i)) {
                val beforeOk = !checkBefore || i == 0 || !isWordChar(text[i - 1])
                val afterOk = !checkAfter || i + phrase.length == text.length || !isWordChar(text[i + phrase.length])
                if (beforeOk && afterOk) return i
            }
            i++
        }
        return -1
    }

    private fun alnumRunEnd(text: String, from: Int): Int {
        var i = from
        while (i < text.length && isAlnum(text[i])) i++
        return i
    }

    private fun blank(chars: CharArray, from: Int, to: Int) {
        var i = maxOf(0, from)
        val end = minOf(chars.size, to)
        while (i < end) {
            chars[i] = ' '
            i++
        }
    }
}
