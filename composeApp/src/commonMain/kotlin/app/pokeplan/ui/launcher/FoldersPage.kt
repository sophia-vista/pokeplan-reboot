package app.pokeplan.ui.launcher

import app.pokeplan.ui.theme.PixelShape

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PmdText
import app.pokeplan.ui.theme.Spacing

private val fieldShape = PixelShape

@Composable
internal fun PmdField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(Spacing.Target).background(Color.White.copy(alpha = 0.14f), fieldShape).padding(horizontal = Spacing.L), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.7f))
        BasicTextField(
            value, onChange, singleLine = true, modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White), cursorBrush = SolidColor(Color.White),
        )
    }
}

@Composable
internal fun FoldersPage(
    apps: List<LauncherApp>, prefs: LauncherPrefs, onPrefs: (LauncherPrefs) -> Unit, host: LauncherHost, openFolder: String?, onOpenFolder: (String?) -> Unit,
    onClose: () -> Unit, pixel: Int,
) {
    val density = LocalDensity.current.density
    val folder = prefs.folders.firstOrNull { it.name == openFolder }
    var text by remember(openFolder) { mutableStateOf(openFolder.orEmpty()) }
    var confirmDelete by remember(openFolder) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Pixel.Night).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = Spacing.Page, end = Spacing.Page, top = Spacing.XL, bottom = Spacing.XXL), verticalArrangement = Arrangement.spacedBy(Spacing.XL)) {
            PmdBox(pixel, Modifier.fillMaxWidth()) {
                Column {
                    PmdMenuLine("Back", pixel) { if (folder != null) onOpenFolder(null) else onClose() }
                    PmdRule(pixel)
                    PmdMenuLine(folder?.name ?: "Folders", pixel, dim = true) {}
                }
            }
            if (folder == null) {
                PmdBox(pixel, Modifier.fillMaxWidth()) {
                    Column {
                        if (prefs.folders.isEmpty()) PmdText("No folders yet", pixel, Modifier.padding(horizontal = pmdRowPad(pixel, density), vertical = Spacing.M), alpha = 0.7f)
                        prefs.folders.forEach { f -> PmdMenuLine("${f.name}  ${f.apps.count { key -> apps.any { it.key == key } }}", pixel) { onOpenFolder(f.name) } }
                    }
                }
                PmdBox(pixel, Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        PmdField(text, { text = it.take(Folder.MAX_NAME) }, "New folder name")
                        PmdMenuLine("Make the folder", pixel, dim = text.isBlank()) { if (text.isNotBlank()) { val made = prefs.addFolderWidget(text); onPrefs(made); if (made != prefs) onOpenFolder(text.trim().take(Folder.MAX_NAME)); text = "" } }
                    }
                }
            } else {
                PmdBox(pixel, Modifier.fillMaxWidth()) {
                    Column {
                        val inside = folder.apps.mapNotNull { key -> apps.firstOrNull { it.key == key } }
                        if (inside.isEmpty()) PmdText("Empty: press and hold an app in All apps and choose Add to folder", pixel, Modifier.padding(horizontal = pmdRowPad(pixel, density), vertical = Spacing.M), alpha = 0.7f)
                        inside.forEach { app ->
                            Row(Modifier.fillMaxWidth().height(Spacing.Target + Spacing.M), verticalAlignment = Alignment.CenterVertically) {
                                Row(
                                    Modifier.weight(1f).height(Spacing.Target + Spacing.M).clickable(role = Role.Button, onClickLabel = "Open ${app.label}") { host.open(app.key) }.padding(horizontal = pmdRowPad(pixel, density)),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.L),
                                ) {
                                    AppIcon(host, app.key, 40.dp)
                                    PmdText(app.label, pixel, Modifier.weight(1f))
                                }
                                Box(Modifier.height(Spacing.Target).clickable(role = Role.Button, onClickLabel = "Take ${app.label} out of the folder") { onPrefs(prefs.removeFromFolder(folder.name, app.key)) }.padding(horizontal = Spacing.M), contentAlignment = Alignment.Center) {
                                    PmdText("Remove", pixel, alpha = 0.75f)
                                }
                            }
                        }
                    }
                }
                PmdBox(pixel, Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        PmdField(text, { text = it.take(Folder.MAX_NAME) }, "Folder name")
                        PmdMenuLine("Rename", pixel, dim = text.isBlank() || text.trim() == folder.name) { if (text.isNotBlank()) { onPrefs(prefs.renameFolder(folder.name, text)); onOpenFolder(text.trim().take(Folder.MAX_NAME)) } }
                        PmdRule(pixel)
                        PmdMenuLine(if (confirmDelete) "Tap again to delete the folder" else "Delete the folder", pixel, dim = !confirmDelete) {
                            if (confirmDelete) { onPrefs(prefs.deleteFolder(folder.name)); onOpenFolder(null) } else confirmDelete = true
                        }
                    }
                }
            }
        }
    }
}
