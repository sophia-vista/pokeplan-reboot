package app.pokeplan.plan

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Timestamps are epoch milliseconds; these turn them into wall-clock time in a zone. */
fun Long.toLocalDateTime(zone: TimeZone): LocalDateTime = Instant.fromEpochMilliseconds(this).toLocalDateTime(zone)

fun LocalDateTime.toEpochMillis(zone: TimeZone): Long = with(zone) { this@toEpochMillis.toInstant().toEpochMilliseconds() }
