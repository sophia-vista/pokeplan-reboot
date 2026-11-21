package app.pokeplan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelShape
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.focusRing
import kotlinx.datetime.LocalDate
import app.pokeplan.ui.pokemon.label

@Composable
internal fun Confirm(text: String, confirm: String, keep: String = "Keep it", onConfirm: () -> Unit, onKeep: () -> Unit) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
        PixelButton(confirm, onConfirm, Modifier.weight(1f), Pixel.Danger, compact = true)
        PixelButton(keep, onKeep, Modifier.weight(1f), Pixel.InkSoft, compact = true)
    }
}

@Composable
internal fun Rule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Pixel.Border.copy(alpha = 0.6f)))
}

@Composable
internal fun Actions(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically, content = content)
}

internal fun parseDay(text: String): LocalDate? = runCatching { LocalDate.parse(text.trim()) }.getOrNull()

@Composable
internal fun RecordRow(
    title: String, detail: String = "", warn: Boolean = false, lead: @Composable () -> Unit = {}, trail: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null, onClickLabel: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = Spacing.Target)
            .let { if (onClick != null) it.clip(PixelShape).focusRing(PixelShape).clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else it }
            .padding(vertical = Spacing.XS),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        lead()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = if (warn) Pixel.Warning else Pixel.InkSoft)
        }
        trail()
    }
}

@Composable
internal fun ModuleTabs(labels: List<String>, selected: Int, accent: Color, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { i, l -> TabButton(l, i == selected, { onSelect(i) }, Modifier.weight(1f), key = accent) }
    }
}

@Composable
internal fun DateField(label: String, value: LocalDate?, onChange: (LocalDate?) -> Unit, today: LocalDate, accent: Color, optional: Boolean = false, past: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        PixelDateField(
            value, today, accent, onChange,
            presets = if (past) listOf("Today" to 0, "Yesterday" to -1, "A week ago" to -7) else listOf("Today" to 0, "Tomorrow" to 1, "In a week" to 7),
            noneLabel = if (optional) "None" else null, max = if (past) today else null,
        )
    }
}
