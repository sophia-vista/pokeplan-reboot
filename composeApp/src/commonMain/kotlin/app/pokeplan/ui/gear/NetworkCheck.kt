package app.pokeplan.ui.gear

import kotlin.math.abs

/** What a few timed requests say about the connection, in plain words. The requests themselves are made by the tool; this is the reading of the times. */
object NetworkCheck {
    /** The times in milliseconds of the requests that worked (null for one that failed). */
    data class Result(val times: List<Long?>) {
        val ok: List<Long> get() = times.filterNotNull()
        val failed: Int get() = times.count { it == null }
        val median: Long? get() = ok.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
        /** How much the times differ: the largest gap between neighbouring requests. */
        val jitter: Long? get() = if (ok.size < 2) null else ok.zipWithNext { a, b -> abs(a - b) }.max()
    }

    /** One line: how good the connection looks. */
    fun verdict(r: Result): String = when {
        r.ok.isEmpty() -> "No answer. The internet could not be reached."
        r.failed > 0 -> "Unreliable: ${r.failed} of ${r.times.size} requests failed."
        r.median!! < 100 -> "Fast: it answers in under a tenth of a second."
        r.median!! < 300 -> "Good: fine for most things."
        r.median!! < 800 -> "Slow: pages and video may stutter."
        else -> "Very slow: expect delays."
    }
}
