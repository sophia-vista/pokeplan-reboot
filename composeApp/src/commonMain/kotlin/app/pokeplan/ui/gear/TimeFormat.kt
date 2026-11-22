package app.pokeplan.ui.gear

fun formatTimer(ms: Long): String {
    val total = ((ms.coerceAtLeast(0) + 999) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
}

fun formatStopwatch(ms: Long): String {
    val t = ms.coerceAtLeast(0)
    val cs = (t % 1000) / 10
    val total = t / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val tail = s.toString().padStart(2, '0') + "." + cs.toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$tail" else "$m:$tail"
}

val QUICK_TIMER_MINUTES = listOf(1, 3, 5, 10, 15, 25, 30, 45, 60)
