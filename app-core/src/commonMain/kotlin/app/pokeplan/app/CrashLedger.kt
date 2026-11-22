package app.pokeplan.app

object CrashLedger {
    const val WINDOW_MILLIS = 60_000L

    const val LIMIT = 2

    private const val KEEP = 10

    fun record(times: List<Long>, nowMillis: Long): List<Long> = (live(times, nowMillis) + nowMillis).takeLast(KEEP)

    fun inCrashLoop(times: List<Long>, nowMillis: Long): Boolean = live(times, nowMillis).size > LIMIT

    private fun live(times: List<Long>, nowMillis: Long) = times.filter { nowMillis - it in 0..WINDOW_MILLIS }

    fun encode(times: List<Long>): String = times.joinToString(",")

    fun decode(text: String?): List<Long> = text.orEmpty().split(",").mapNotNull { it.trim().toLongOrNull() }
}

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
