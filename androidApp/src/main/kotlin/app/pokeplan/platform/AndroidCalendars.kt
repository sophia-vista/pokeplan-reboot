package app.pokeplan.platform

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import app.pokeplan.plan.CalendarEvent
import app.pokeplan.plan.DeviceCalendar
import app.pokeplan.ui.CalendarSource
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class AndroidCalendars(private val activity: ComponentActivity) : CalendarSource {
    private var pending: ((Boolean) -> Unit)? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.invoke(granted)
        pending = null
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
}
