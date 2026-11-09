package app.pokeplan.app

/**
 * Remembers when the app crashed, to tell a crash loop from one bad moment. A launcher that crashes at start is started again by Android at
 * once, so three crashes within a minute mean the normal screen cannot be trusted: the launcher then starts in safe mode (a plain list of the
 * installed apps and the crash log to copy) instead of looping. Pure logic; the Android app keeps [encode]d times in its preferences.
 */
object CrashLedger {
    const val WINDOW_MILLIS = 60_000L

    /** More than this many crashes within the window is a loop. */
    const val LIMIT = 2

    private const val KEEP = 10

    /** [times] with a crash at [nowMillis] added; times that can no longer count (older than the window, or from the future) are dropped. */
    fun record(times: List<Long>, nowMillis: Long): List<Long> = (live(times, nowMillis) + nowMillis).takeLast(KEEP)

    /** Whether the crashes within the window before [nowMillis] are more than [LIMIT]. */
    fun inCrashLoop(times: List<Long>, nowMillis: Long): Boolean = live(times, nowMillis).size > LIMIT

    private fun live(times: List<Long>, nowMillis: Long) = times.filter { nowMillis - it in 0..WINDOW_MILLIS }

    fun encode(times: List<Long>): String = times.joinToString(",")

    fun decode(text: String?): List<Long> = text.orEmpty().split(",").mapNotNull { it.trim().toLongOrNull() }
}

/**
 * The text of a crash for the player to copy to whoever helps: when, which version, the exception types and where they were thrown. It holds
 * no exception message, because a message can carry what the player wrote or saved (the same rule as the rest of the app's logs).
 */
object CrashLog {
    private const val MAX_FRAMES = 40

    fun format(nowMillis: Long, version: String, threadName: String, error: Throwable): String = buildString {
        appendLine("PokéLife crash log")
        appendLine("version $version, time $nowMillis (millis since 1970), thread $threadName")
        var cause: Throwable? = error
        var depth = 0
        while (cause != null && depth < 5) {
            appendLine(if (depth == 0) "Crash: ${cause::class.simpleName ?: "Throwable"}" else "Caused by: ${cause::class.simpleName ?: "Throwable"}")
            cause.stackTraceToString().lineSequence().filter { it.trimStart().startsWith("at ") }.take(MAX_FRAMES).forEach { appendLine(it) }
            cause = cause.cause?.takeIf { it !== cause }
            depth++
        }
    }
}
