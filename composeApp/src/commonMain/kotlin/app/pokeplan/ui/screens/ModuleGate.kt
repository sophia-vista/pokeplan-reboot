package app.pokeplan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.DeviceGate
import app.pokeplan.ui.theme.Notice
import app.pokeplan.ui.theme.NoticeTone
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox

/** The modules whose records are private and that can ask for the phone's screen lock (Mint has its own switch, in its settings). */
internal val gateableModules = listOf(Module.CARE, Module.HEAL, Module.HOME, Module.LINK)

private fun locked(model: AppModel, gate: DeviceGate, key: String, now: Long): Boolean {
    val unlocked = model.moduleUnlockedAt[key] ?: 0L
    val away = maxOf(gate.leftAt(), model.moduleLeftAt[key] ?: 0L)
    return unlocked == 0L || (away > unlocked && now - away > DeviceGate.GRACE_MILLIS)
}

/**
 * The optional screen-lock door in front of a private module (Care's journal, Heal, Home, Link): with the switch on in Settings and a screen lock on the phone,
 * the module shows a locked card until the person proves themselves with the phone's own lock (PIN, pattern, password, fingerprint or face, whichever the phone
 * offers), and closes again after the app or the module has been left for a while. A door only, like Mint's: nothing is encrypted with it and no password is made.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ModuleGate(model: AppModel, module: Module, content: @Composable () -> Unit) {
    val gate = model.gate
    val key = module.name
    if (gate == null || key !in model.data.settings.lockedModules || !gate.available()) { content(); return }
    var isLocked by remember { mutableStateOf(locked(model, gate, key, model.store.nowMillis)) }
    var failed by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { model.moduleLeftAt[key] = model.store.nowMillis } }
    LaunchedEffect(isLocked) {
        while (!isLocked) {
            val now = model.store.nowMillis
            if (locked(model, gate, key, now)) isLocked = true else model.moduleUnlockedAt[key] = now // coming back inside the grace keeps it open
            kotlinx.coroutines.delay(1_000)
        }
    }
    if (!isLocked) { content(); return }
    fun ask() { gate.authenticate { ok -> if (ok) { model.moduleUnlockedAt[key] = model.store.nowMillis; failed = false; isLocked = false } else failed = true } }
    LaunchedEffect(Unit) { ask() }
    val toModules = { model.module = null }
    BackHandler(enabled = true, onBack = toModules)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("${module.label} is locked")
            Text("Use your phone's screen lock to open it.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            if (failed) Notice("That did not open it.", tone = NoticeTone.WARNING)
            PixelButton("Unlock", ::ask, Modifier.fillMaxWidth(), module.look.accent)
            PixelButton("Back", toModules, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        }
    }
}
