package app.pokeplan.platform

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import app.pokeplan.plan.CalendarEvent
import app.pokeplan.plan.DeviceCalendar
import app.pokeplan.app.integrations.CalendarWriter
import app.pokeplan.app.integrations.ProviderEvent
import app.pokeplan.app.integrations.ProviderRow
import app.pokeplan.app.integrations.TargetCalendar
import app.pokeplan.ui.CalendarSource
import app.pokeplan.ui.CalendarWriteAccess
import android.content.ContentValues
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class AndroidCalendars(private val activity: ComponentActivity) : CalendarSource, CalendarWriter, CalendarWriteAccess {
    private var pending: ((Boolean) -> Unit)? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.invoke(granted)
        pending = null
    }

    private var pendingWrite: ((Boolean) -> Unit)? = null
    private val writeLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        pendingWrite?.invoke(granted[Manifest.permission.READ_CALENDAR] == true && granted[Manifest.permission.WRITE_CALENDAR] == true)
        pendingWrite = null
    }

    override fun writePermitted(): Boolean =
        permitted() && activity.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    override fun requestWritePermission(done: (Boolean) -> Unit) {
        if (writePermitted()) return done(true)
        pendingWrite = done
        writeLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
    }

    override fun permitted(): Boolean =
        activity.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    override fun requestPermission(done: (Boolean) -> Unit) {
        if (permitted()) return done(true)
        pending = done
        launcher.launch(Manifest.permission.READ_CALENDAR)
    }

    override fun calendars(): List<DeviceCalendar> {
        if (!permitted()) return emptyList()
        val columns = arrayOf(
            CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.CALENDAR_COLOR,
        )
        val out = mutableListOf<DeviceCalendar>()
        try {
            activity.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, columns, "${CalendarContract.Calendars.VISIBLE} = 1", null,
                "${CalendarContract.Calendars.ACCOUNT_NAME}, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME}",
            )?.use { c ->
                while (c.moveToNext()) {
                    out += DeviceCalendar(c.getLong(0), c.getString(1) ?: "Calendar", c.getString(2) ?: "", c.getInt(3).toLong() and 0xFFFFFFFFL)
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        return out
    }

    override fun events(calendarIds: Set<Long>, from: LocalDate, to: LocalDate): List<CalendarEvent> {
        if (calendarIds.isEmpty() || !permitted()) return emptyList()
        val zone = TimeZone.currentSystemDefault()
        val begin = from.minus(DatePeriod(days = 1)).atStartOfDayIn(zone).toEpochMilliseconds()
        val end = to.plus(DatePeriod(days = 2)).atStartOfDayIn(zone).toEpochMilliseconds()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val columns = arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.ACCESS_LEVEL, CalendarContract.Instances.DISPLAY_COLOR,
        )
        val status = CalendarContract.Instances.STATUS
        val mine = CalendarContract.Instances.SELF_ATTENDEE_STATUS
        val selection = "${CalendarContract.Instances.CALENDAR_ID} IN (${calendarIds.joinToString(",")})" +
            " AND ($status IS NULL OR $status != ${CalendarContract.Instances.STATUS_CANCELED})" +
            " AND ($mine IS NULL OR $mine != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED})"
        val out = mutableListOf<CalendarEvent>()
        try {
            activity.contentResolver.query(uri, columns, selection, null, "${CalendarContract.Instances.BEGIN}")?.use { c ->
                while (c.moveToNext()) {
                    val allDay = c.getInt(4) == 1
                    val eventZone = if (allDay) TimeZone.UTC else zone
                    val start = Instant.fromEpochMilliseconds(c.getLong(1)).toLocalDateTime(eventZone)
                    val finish = Instant.fromEpochMilliseconds(c.getLong(2)).toLocalDateTime(eventZone)
                    if (finish.date < from || start.date > to) continue
                    out += CalendarEvent(
                        id = "${c.getLong(0)}@${c.getLong(1)}", calendarId = c.getLong(5), title = c.getString(3)?.ifBlank { null } ?: "(No title)",
                        start = start, end = finish, allDay = allDay,
                        private = c.getInt(6) == CalendarContract.Events.ACCESS_PRIVATE,
                        color = c.getInt(7).toLong() and 0xFFFFFFFFL,
                    )
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        return out
    }

    // The two-way sync of PLAN's own events (PLAN 8.1). Only calendars the player may write to are offered, only the rows
    // PokéLife made are ever touched, and every call here returns quietly (null or false) if the permission is gone.

    override fun writableCalendars(): List<TargetCalendar> {
        if (!permitted()) return emptyList()
        val columns = arrayOf(
            CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME,
        )
        val out = mutableListOf<TargetCalendar>()
        try {
            activity.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, columns,
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR} AND ${CalendarContract.Calendars.VISIBLE} = 1",
                null, "${CalendarContract.Calendars.ACCOUNT_NAME}, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME}",
            )?.use { c -> while (c.moveToNext()) out += TargetCalendar(c.getLong(0), c.getString(1) ?: "Calendar", c.getString(2) ?: "") }
        } catch (e: SecurityException) {
            return emptyList()
        }
        return out
    }

    override fun read(calendarId: Long, ids: Set<Long>): Map<Long, ProviderRow>? {
        if (!permitted()) return null
        if (ids.isEmpty()) return emptyMap()
        val columns = arrayOf(
            CalendarContract.Events._ID, CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION, CalendarContract.Events.EVENT_TIMEZONE, CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.RRULE, CalendarContract.Events.EXDATE, CalendarContract.Events.DELETED, CalendarContract.Events.EVENT_COLOR,
        )
        val out = mutableMapOf<Long, ProviderRow>()
        try {
            activity.contentResolver.query(
                CalendarContract.Events.CONTENT_URI, columns,
                "${CalendarContract.Events.CALENDAR_ID} = $calendarId AND ${CalendarContract.Events._ID} IN (${ids.joinToString(",")})", null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    out[id] = ProviderRow(
                        id, c.getInt(9) == 1,
                        ProviderEvent(
                            title = c.getString(1) ?: "", allDay = c.getInt(6) == 1, startMillis = c.getLong(2),
                            endMillis = if (c.isNull(3)) null else c.getLong(3), duration = c.getString(4)?.ifBlank { null },
                            timezone = c.getString(5) ?: "UTC", rrule = c.getString(7)?.ifBlank { null }, exdate = c.getString(8)?.ifBlank { null },
                            color = if (c.isNull(10)) null else c.getInt(10),
                        ),
                    )
                }
            }
        } catch (e: SecurityException) {
            return null
        }
        return out
    }

    /**
     * The events the player made in this calendar: they are its organizer (an invite is someone else's), whole (not one edited
     * occurrence of a series), and not a series that has edited occurrences (PLAN would lose them), not deleted or cancelled.
     */
    override fun importable(calendarId: Long): List<ProviderRow> {
        if (!permitted()) return emptyList()
        val columns = arrayOf(
            CalendarContract.Events._ID, CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION, CalendarContract.Events.EVENT_TIMEZONE, CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.RRULE, CalendarContract.Events.EXDATE, CalendarContract.Events.DELETED, CalendarContract.Events.EVENT_COLOR,
        )
        val out = mutableListOf<ProviderRow>()
        val withExceptions = mutableSetOf<Long>()
        try {
            activity.contentResolver.query(
                CalendarContract.Events.CONTENT_URI, arrayOf(CalendarContract.Events.ORIGINAL_ID),
                "${CalendarContract.Events.CALENDAR_ID} = $calendarId AND ${CalendarContract.Events.ORIGINAL_ID} IS NOT NULL AND ${CalendarContract.Events.DELETED} = 0", null, null,
            )?.use { c -> while (c.moveToNext()) if (!c.isNull(0)) withExceptions += c.getLong(0) }
            activity.contentResolver.query(
                CalendarContract.Events.CONTENT_URI, columns,
                "${CalendarContract.Events.CALENDAR_ID} = $calendarId AND ${CalendarContract.Events.DELETED} = 0 AND ${CalendarContract.Events.ORIGINAL_ID} IS NULL" +
                    " AND ${CalendarContract.Events.IS_ORGANIZER} = 1 AND (${CalendarContract.Events.STATUS} IS NULL OR ${CalendarContract.Events.STATUS} != ${CalendarContract.Events.STATUS_CANCELED})",
                null, "${CalendarContract.Events.DTSTART} DESC",
            )?.use { c ->
                while (c.moveToNext() && out.size < IMPORT_SCAN_LIMIT) {
                    val id = c.getLong(0)
                    if (id in withExceptions) continue
                    out += ProviderRow(
                        id, false,
                        ProviderEvent(
                            title = c.getString(1) ?: "", allDay = c.getInt(6) == 1, startMillis = c.getLong(2),
                            endMillis = if (c.isNull(3)) null else c.getLong(3), duration = c.getString(4)?.ifBlank { null },
                            timezone = c.getString(5) ?: "UTC", rrule = c.getString(7)?.ifBlank { null }, exdate = c.getString(8)?.ifBlank { null },
                            color = if (c.isNull(10)) null else c.getInt(10),
                        ),
                    )
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        return out
    }

    override fun insert(calendarId: Long, event: ProviderEvent): Long? {
        if (!writePermitted()) return null
        val values = values(event).apply { put(CalendarContract.Events.CALENDAR_ID, calendarId) }
        return try {
            activity.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)?.let { ContentUris.parseId(it) }?.takeIf { it > 0 }
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    override fun update(id: Long, event: ProviderEvent): Boolean {
        if (!writePermitted()) return false
        return try {
            activity.contentResolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), values(event), null, null) > 0
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    override fun delete(id: Long): Boolean {
        if (!writePermitted()) return false
        return try {
            activity.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null) > 0
        } catch (e: SecurityException) {
            false
        }
    }

    /** A repeating row carries DURATION and no DTEND, a single one the opposite; the provider rejects a row with both. */
    private fun values(e: ProviderEvent) = ContentValues().apply {
        put(CalendarContract.Events.TITLE, e.title)
        put(CalendarContract.Events.DTSTART, e.startMillis)
        put(CalendarContract.Events.EVENT_TIMEZONE, e.timezone)
        put(CalendarContract.Events.ALL_DAY, if (e.allDay) 1 else 0)
        if (e.endMillis != null) put(CalendarContract.Events.DTEND, e.endMillis) else putNull(CalendarContract.Events.DTEND)
        if (e.duration != null) put(CalendarContract.Events.DURATION, e.duration) else putNull(CalendarContract.Events.DURATION)
        if (e.rrule != null) put(CalendarContract.Events.RRULE, e.rrule) else putNull(CalendarContract.Events.RRULE)
        if (e.exdate != null) put(CalendarContract.Events.EXDATE, e.exdate) else putNull(CalendarContract.Events.EXDATE)
        if (e.color != null) put(CalendarContract.Events.EVENT_COLOR, e.color)
    }

    private companion object {
        const val IMPORT_SCAN_LIMIT = 1000
    }
}
