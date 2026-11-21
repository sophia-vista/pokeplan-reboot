package app.pokeplan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.pokeplan.app.IntegrationInfo
import app.pokeplan.plan.toLocalDateTime
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.pokemon.label

@Composable
internal fun IntegrationCard(info: IntegrationInfo) {
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle(info.name)
            Text(if (info.cls == app.pokeplan.app.IntegrationClass.UNSUPPORTED) info.cls.label else "${info.cls.label}  ·  ${info.status.label}", style = MaterialTheme.typography.bodyLarge)
            Text(info.does, style = MaterialTheme.typography.bodyMedium)
            Text("Reads: ${info.data}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            Text(info.limits, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }
}

internal fun Long.shortDate(zone: kotlinx.datetime.TimeZone = kotlinx.datetime.TimeZone.currentSystemDefault()): String {
    val t = toLocalDateTime(zone)
    return "${t.date} ${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}
