package app.pokeplan.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.Spacing
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month

/** The launcher's second page: the widgets placed on Today (the calendar and the day's agenda and tasks to start with), scrolling when they are many. */
@Composable
internal fun TodayPage(env: WidgetEnv) {
    Box(Modifier.fillMaxSize().background(Pixel.Night.copy(alpha = 0.5f)).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Spacing.XXL, bottom = Spacing.XL), verticalArrangement = Arrangement.spacedBy(Spacing.XL)) {
            if (env.editing) EditToolbar(env, Modifier.padding(horizontal = Spacing.Page))
            env.prefs.widgetsIn(WidgetArea.TODAY).forEach { slot -> androidx.compose.runtime.key(slot.kind, slot.arg) { LauncherWidget(slot, env) } }
        }
    }
}

internal fun two(n: Int) = n.toString().padStart(2, '0')

internal fun dayName(d: DayOfWeek) = d.name.lowercase().replaceFirstChar { it.uppercase() }

internal fun monthName(m: Month) = m.name.lowercase().replaceFirstChar { it.uppercase() }
