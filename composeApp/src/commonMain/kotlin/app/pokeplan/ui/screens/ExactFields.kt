package app.pokeplan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import app.pokeplan.app.ExactInput
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.plan.toLocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import app.pokeplan.ui.plan.clock
import app.pokeplan.ui.pokemon.label

/**
 * A text box that holds a typed value (a date, a time, a length); when it loses focus it rewrites what was typed in the one
 * spelling the app uses, so there is no second line saying what it understood. Every valid
 * spelling is applied at once ([onChange]), so Save always reads what is on the screen; while the text is not yet a value
 * (`9:` on the way to `9:30`) the last good value stays and the field only says what it takes. When the value changes from
 * outside (a key, a stepper) the text is rewritten to match; typing is never rewritten under the cursor.
 */
@Composable
internal fun <T : Any> TypedValueField(
    value: T?, onChange: (T?) -> Unit, parse: (String) -> T?, format: (T) -> String,
    placeholder: String, takes: String, modifier: Modifier = Modifier, label: String? = null, allowEmpty: Boolean = false,
    reject: (T) -> String? = { null },
) {
    var text by remember { mutableStateOf(value?.let(format).orEmpty()) }
    LaunchedEffect(value) { if ((text.isBlank() && value == null).not() && parse(text) != value) text = value?.let(format).orEmpty() }
    val parsed = parse(text)
    val refusal = parsed?.let(reject)
    PixelField(
        text,
        {
            text = it
            val v = parse(it)
            when {
                v != null && reject(v) == null -> onChange(v)
                it.isBlank() && allowEmpty -> onChange(null)
            }
        },
        modifier.onFocusChanged { if (!it.isFocused && parsed != null && refusal == null) text = format(parsed) },
        label = label, placeholder = placeholder, singleLine = true,
        error = when {
            refusal != null -> refusal
            text.isNotBlank() && parsed == null -> takes
            else -> null
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    )
}

/**
 * A time of day you can type (`9`, `930`, `21:05`, `9pm`), with the common times as keys. With a [noneLabel] the time may be
 * missing ("Any time").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PixelTimeField(
    value: LocalTime?, onChange: (LocalTime?) -> Unit, accent: Color, modifier: Modifier = Modifier, label: String? = null,
    presets: List<LocalTime> = emptyList(), noneLabel: String? = null,
    defaultTime: LocalTime = LocalTime(9, 0), reject: (LocalTime) -> String? = { null },
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        if (label != null) FieldLabel(label)
        if (noneLabel != null || presets.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                if (noneLabel != null) TabButton(noneLabel, value == null, { onChange(null) }, key = accent)
                presets.forEach { p -> TabButton(ExactInput.formatTime(p), value == p, { onChange(p) }, key = accent) }
            }
        }
        if (value == null && noneLabel != null) {
            PixelButton("Set a time", { onChange(defaultTime) }, color = Pixel.InkSoft, compact = true)
        } else {
            TypedValueField(
                value, onChange, { ExactInput.parseTime(it) }, { ExactInput.formatTime(it) },
                placeholder = "HH:MM", takes = "Type a time such as 9:30, 21:05 or 9pm",
                allowEmpty = noneLabel != null, reject = reject,
            )
        }
    }
}

/**
 * A length you can type (`45`, `1h30`, `1:30`, `90m`) with the common lengths as keys. [minutes] is
 * null for none when [noneLabel] is given.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PixelDurationField(
    minutes: Int?, onChange: (Int?) -> Unit, accent: Color, modifier: Modifier = Modifier, label: String? = null,
    presets: List<Int> = listOf(15, 30, 45, 60), noneLabel: String? = null, max: Int = 24 * 60, min: Int = 1,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        if (label != null) FieldLabel(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            if (noneLabel != null) TabButton(noneLabel, minutes == null, { onChange(null) }, key = accent)
            presets.forEach { p -> TabButton(ExactInput.formatMinutes(p), minutes == p, { onChange(p) }, key = accent) }
        }
        TypedValueField(
            minutes, onChange, { ExactInput.parseDurationMinutes(it) }, { ExactInput.formatMinutes(it) },
            placeholder = "Minutes, or 1h30", takes = "Whole minutes, such as 45, 1h30 or 1:30", allowEmpty = noneLabel != null,
            reject = { if (it < min || it > max) "Between $min min and ${ExactInput.formatMinutes(max)}" else null },
        )
    }
}

/** A date and a time of day together: the date field above, the time below (a missing time is "Any time"). */
@Composable
fun PixelDateTimeField(
    day: kotlinx.datetime.LocalDate?, time: LocalTime?, today: kotlinx.datetime.LocalDate, accent: Color,
    onChange: (kotlinx.datetime.LocalDate?, LocalTime?) -> Unit, modifier: Modifier = Modifier, label: String? = null,
    noDayLabel: String? = "No date", timeNoneLabel: String? = "Any time", timePresets: List<LocalTime> = emptyList(),
    dayPresets: List<Pair<String, Int>> = listOf("Today" to 0, "Tomorrow" to 1), max: kotlinx.datetime.LocalDate? = null, min: kotlinx.datetime.LocalDate? = null,
    pickLabel: String = "Pick a date",
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        if (label != null) FieldLabel(label)
        PixelDateField(
            day, today, accent, { onChange(it, if (it == null) null else time) }, presets = dayPresets, noneLabel = noDayLabel,
            max = max, min = min, pickLabel = pickLabel,
        )
        if (day != null) PixelTimeField(time, { onChange(day, it) }, accent, presets = timePresets, noneLabel = timeNoneLabel)
    }
}

/**
 * One moment on the phone's clock, typed or picked as a day and a time (an activity's end, a night's wake-up, a drink). The
 * day is never after today and, unless [future] is true, the time is never later than [nowMillis].
 */
@Composable
fun PixelMomentField(
    millis: Long, onChange: (Long) -> Unit, nowMillis: Long, accent: Color, modifier: Modifier = Modifier, label: String? = null,
    zone: kotlinx.datetime.TimeZone = kotlinx.datetime.TimeZone.currentSystemDefault(), future: Boolean = false,
    dayPresets: List<Pair<String, Int>> = listOf("Today" to 0, "Yesterday" to -1),
) {
    val at = millis.toLocalDateTime(zone)
    val now = nowMillis.toLocalDateTime(zone)
    fun moment(day: kotlinx.datetime.LocalDate, time: LocalTime) = kotlinx.datetime.LocalDateTime(day, time).toInstant(zone).toEpochMilliseconds()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        if (label != null) FieldLabel(label)
        PixelDateField(
            at.date, now.date, accent, { d -> d?.let { onChange(minOf(moment(it, at.time), if (future) Long.MAX_VALUE else nowMillis)) } },
            presets = dayPresets, max = if (future) null else now.date,
        )
        PixelTimeField(
            at.time, { t -> t?.let { onChange(moment(at.date, it)) } }, accent,
            reject = { t -> if (!future && at.date == now.date && t > now.time) "That time has not happened yet" else null },
        )
    }
}
