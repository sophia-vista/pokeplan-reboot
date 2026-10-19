package app.pokeplan.shared.model

/** Length of a focus session, as set on the timer. */
data class FocusDuration(val hours: Int, val mins: Int, val secs: Int) {
    init {
        require(hours in 0..MAX_HOURS) { "hours must be 0..$MAX_HOURS" }
        require(mins in 0..59) { "mins must be 0..59" }
        require(secs in 0..59) { "secs must be 0..59" }
    }

    val totalSeconds: Long get() = hours * 3600L + mins * 60L + secs

    val millis: Long get() = totalSeconds * 1000

    /** Whole minutes, rounding up when there are leftover seconds. */
    val minutesRoundedUp: Int get() = hours * 60 + mins + if (secs > 0) 1 else 0

    val isLongEnough: Boolean get() = totalSeconds >= MIN_SECONDS

    companion object {
        const val MAX_HOURS = 99
        const val MIN_MINUTES = 5
        private const val MIN_SECONDS = MIN_MINUTES * 60L
    }
}
