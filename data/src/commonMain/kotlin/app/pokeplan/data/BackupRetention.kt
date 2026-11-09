package app.pokeplan.data

/**
 * Which automatic backups to keep: the newest one of each of the last [DAILY] days that have one, and, of those taken before that, the newest one
 * of each of the last [WEEKLY] weeks that have one (weeks start on Monday). So a week of daily copies and a month of weekly ones, at most eleven in all, and
 * a phone that was off for a while still keeps what it has instead of dropping it for being old.
 * [offsetMillis] is the time zone's offset from UTC, so a day is the player's day.
 */
object BackupRetention {
    const val DAILY = 7
    const val WEEKLY = 4
    private const val DAY = 24 * 60 * 60 * 1000L

    fun keep(taken: Collection<Long>, offsetMillis: Long = 0): Set<Long> {
        val newestFirst = taken.sortedDescending()
        val daily = newestFirst.distinctBy { day(it, offsetMillis) }.take(DAILY).toSet()
        // 1 January 1970 was a Thursday, so shifting by three days makes a week begin on a Monday.
        val firstDailyDay = daily.minOfOrNull { day(it, offsetMillis) } ?: return daily
        val weekly = newestFirst.filter { day(it, offsetMillis) < firstDailyDay }.distinctBy { week(it, offsetMillis) }.take(WEEKLY)
        return daily + weekly
    }

    private fun day(millis: Long, offset: Long): Long = (millis + offset).floorDiv(DAY)
    private fun week(millis: Long, offset: Long): Long = (day(millis, offset) + 3).floorDiv(7L)
}
