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

/**
 * A text box that holds a typed value (a date, a time, a length) and tells the owner what it understood. Every valid
 * spelling is applied at once ([onChange]), so Save always reads what is on the screen; while the text is not yet a value
 * (`9:` on the way to `9:30`) the last good value stays and the field only says what it takes. When the value changes from
 * outside (a key, a stepper) the text is rewritten to match; typing is never rewritten under the cursor.
 */
@Composable
internal fun <T : Any> TypedValueField(
    value: T?, onChange: (T?) -> Unit, parse: (String) -> T?, format: (T) -> String, preview: (T) -> String?,
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
        modifier, label = label, placeholder = placeholder, singleLine = true,
        helper = if (parsed != null && refusal == null) preview(parsed) else null,
        error = when {
            refusal != null -> refusal
            text.isNotBlank() && parsed == null -> takes
            else -> null
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    )
}

/**
 * A time of day you can type (`9`, `930`, `21:05`, `9pm`), with the common times as keys and small steps either way. With a
 * [noneLabel] the time may be missing ("Any time"). Steps are in minutes and stop at 00:00 and 23:59 rather than wrapping a
 * day, unless [wrap] says the time is a clock on its own (an alarm).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PixelTimeField(
    value: LocalTime?, onChange: (LocalTime?) -> Unit, accent: Color, modifier: Modifier = Modifier, label: String? = null,
    presets: List<LocalTime> = emptyList(), noneLabel: String? = null, steps: List<Int> = listOf(1, 15, 60), wrap: Boolean = false,
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
                value, onChange, { ExactInput.parseTime(it) }, { ExactInput.formatTime(it) }, { clockWords(it) },
                placeholder = "HH:MM", takes = "Type a time such as 9:30, 21:05 or 9pm",
                allowEmpty = noneLabel != null, reject = reject,
            )
            if (value != null && steps.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    steps.reversed().forEach { s -> StepKey("-" + stepName(s)) { onChange(stepTime(value, -s, wrap)) } }
                    steps.forEach { s -> StepKey("+" + stepName(s)) { onChange(stepTime(value, s, wrap)) } }
                }
            }
        }
    }
}

private fun stepName(minutes: Int) = if (minutes % 60 == 0) "${minutes / 60}h" else "$minutes"

@Composable
private fun StepKey(text: String, onClick: () -> Unit) = PixelButton(text, onClick, color = Pixel.InkSoft, compact = true)

internal fun stepTime(t: LocalTime, minutes: Int, wrap: Boolean): LocalTime {
    val raw = t.hour * 60 + t.minute + minutes
    val total = if (wrap) ((raw % 1440) + 1440) % 1440 else raw.coerceIn(0, 24 * 60 - 1)
    return LocalTime(total / 60, total % 60)
}

/** "9:30 am" / "9:30 pm" under a 24-hour box, so the typed time is read back the way the clock on the wall says it. */
internal fun clockWords(t: LocalTime): String {
    val h = if (t.hour % 12 == 0) 12 else t.hour % 12
    return "$h:${t.minute.toString().padStart(2, '0')} ${if (t.hour < 12) "am" else "pm"}"
}

/**
 * A length you can type (`45`, `1h30`, `1:30`, `90m`) with the common lengths as keys and a step either way. [minutes] is
 * null for none when [noneLabel] is given.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PixelDurationField(
    minutes: Int?, onChange: (Int?) -> Unit, accent: Color, modifier: Modifier = Modifier, label: String? = null,
    presets: List<Int> = listOf(15, 30, 45, 60), noneLabel: String? = null, step: Int = 5, max: Int = 24 * 60, min: Int = 1,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        if (label != null) FieldLabel(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            if (noneLabel != null) TabButton(noneLabel, minutes == null, { onChange(null) }, key = accent)
            presets.forEach { p -> TabButton(ExactInput.formatMinutes(p), minutes == p, { onChange(p) }, key = accent) }
        }
        TypedValueField(
            minutes, onChange, { ExactInput.parseDurationMinutes(it) }, { it.toString() }, { ExactInput.formatMinutes(it) },
            placeholder = "Minutes, or 1h30", takes = "Whole minutes, such as 45, 1h30 or 1:30", allowEmpty = noneLabel != null,
            reject = { if (it < min || it > max) "Between $min min and ${ExactInput.formatMinutes(max)}" else null },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            val now = minutes ?: 0
            StepKey("-$step") { onChange((now - step).takeIf { it >= min }) }
            StepKey("+$step") { onChange((now + step).coerceAtMost(max)) }
        }
    }
}

/** A date and a time of day together: the date field above, the time below (a missing time is "Any time"). */
@Composable
fun PixelDateTimeField(
    day: kotlinx.datetime.LocalDate?, time: LocalTime?, today: kotlinx.datetime.LocalDate, accent: Color,
    onChange: (kotlinx.datetime.LocalDate?, LocalTime?) -> Unit, modifier: Modifier = Modifier, label: String? = null,
    noDayLabel: String? = "No date", timeNoneLabel: String? = "Any time", timePresets: List<LocalTime> = emptyList(),
    dayPresets: List<Pair<String, Int>> = listOf("Today" to 0, "Tomorrow" to 1), max: kotlinx.datetime.LocalDate? = null, min: kotlinx.datetime.LocalDate? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        if (label != null) FieldLabel(label)
        PixelDateField(
            day, today, accent, { onChange(it, if (it == null) null else time) }, presets = dayPresets, noneLabel = noDayLabel,
            max = max, min = min,
        )
        if (day != null) PixelTimeField(time, { onChange(day, it) }, accent, presets = timePresets, noneLabel = timeNoneLabel)
    }
}

/**
 * One moment on the phone's clock, typed or picked as a day and a time (an activity's end, a night's wake-up, a drink). The
 * day is never after today and, unless [future] is true, the time is never later than [nowMillis]. [nowKey] adds a "Now" key.
 */
@Composable
fun PixelMomentField(
    millis: Long, onChange: (Long) -> Unit, nowMillis: Long, accent: Color, modifier: Modifier = Modifier, label: String? = null,
    zone: kotlinx.datetime.TimeZone = kotlinx.datetime.TimeZone.currentSystemDefault(), future: Boolean = false, nowKey: Boolean = true,
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
            at.time, { t -> t?.let { onChange(moment(at.date, it)) } }, accent, steps = listOf(1, 15, 60),
            reject = { t -> if (!future && at.date == now.date && t > now.time) "That time has not happened yet" else null },
        )
        if (nowKey) PixelButton("Now", { onChange(nowMillis - nowMillis % 60_000L) }, color = Pixel.InkSoft, compact = true)
    }
}
