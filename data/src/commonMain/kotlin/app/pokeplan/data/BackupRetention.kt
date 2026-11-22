package app.pokeplan.data

object BackupRetention {
    const val DAILY = 7
    const val WEEKLY = 4
    private const val DAY = 24 * 60 * 60 * 1000L

    fun keep(taken: Collection<Long>, offsetMillis: Long = 0): Set<Long> {
        val newestFirst = taken.sortedDescending()
        val daily = newestFirst.distinctBy { day(it, offsetMillis) }.take(DAILY).toSet()
        val firstDailyDay = daily.minOfOrNull { day(it, offsetMillis) } ?: return daily
        val weekly = newestFirst.filter { day(it, offsetMillis) < firstDailyDay }.distinctBy { week(it, offsetMillis) }.take(WEEKLY)
        return daily + weekly
    }

    private fun day(millis: Long, offset: Long): Long = (millis + offset).floorDiv(DAY)
    private fun week(millis: Long, offset: Long): Long = (day(millis, offset) + 3).floorDiv(7L)
}
