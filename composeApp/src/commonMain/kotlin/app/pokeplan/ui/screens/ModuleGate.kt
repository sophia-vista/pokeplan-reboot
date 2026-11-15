package app.pokeplan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Report
import app.pokeplan.ui.DeviceGate
import app.pokeplan.ui.theme.Notice
import app.pokeplan.ui.theme.NoticeTone
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.pokemon.label

/** The modules whose records are private and that can ask for the phone's screen lock from Settings (Mint has its own switch, in its settings, and the same door). */
internal val gateableModules = listOf(Module.CARE, Module.HEAL, Module.BASE, Module.LINK)

private fun locked(model: AppModel, gate: DeviceGate, key: String, now: Long): Boolean {
    val unlocked = model.moduleUnlockedAt[key] ?: 0L
    val away = maxOf(gate.leftAt(), model.moduleLeftAt[key] ?: 0L)
    return unlocked == 0L || (away > unlocked && now - away > DeviceGate.GRACE_MILLIS)
}

/**
 * The optional screen-lock door in front of a private module (Care's journal, Heal, Home, Link), switched on in Settings → Privacy (`Settings.lockedModules`).
 * Mint's door is the same one, switched on in Mint's own settings (MINT.md section 13.2).
 */
@Composable
internal fun ModuleGate(model: AppModel, module: Module, content: @Composable () -> Unit) =
    ScreenLockGate(model, module, module.name in model.data.settings.lockedModules, content)

/**
 * The one screen-lock door every private module uses: with [on] and a screen lock on the phone, the module shows a locked card until the person proves
 * themselves with the phone's own lock (PIN, pattern, password, fingerprint or face, whichever the phone offers), and closes again after the app or the
 * module has been left for longer than [DeviceGate.GRACE_MILLIS]. A door only: nothing is encrypted with it and no password is made, so it can never make
 * a record unreadable. Without a gate or a lock on the phone it shows the module at once.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ScreenLockGate(model: AppModel, module: Module, on: Boolean, content: @Composable () -> Unit) {
    val gate = model.gate
    val key = module.name
    if (gate == null || !on || !gate.available()) { content(); return }
    var isLocked by remember { mutableStateOf(locked(model, gate, key, model.store.nowMillis)) }
    var failed by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { model.moduleLeftAt[key] = model.store.nowMillis } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(isLocked) {
        while (!isLocked) {
            val now = model.store.nowMillis
            // Coming back inside the grace keeps it open. Only while the app is in front: a tick after the app was left must not count as being back, or the door would never close again.
            if (locked(model, gate, key, now)) isLocked = true
            else if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) model.moduleUnlockedAt[key] = now
            kotlinx.coroutines.delay(1_000)
        }
    }
    if (!isLocked) { content(); return }
    fun ask() { gate.authenticate { ok -> if (ok) { model.moduleUnlockedAt[key] = model.store.nowMillis; failed = false; isLocked = false } else failed = true } }
    LaunchedEffect(Unit) { ask() }
    val toModules = { model.module = null }
    BackHandler(enabled = true, onBack = toModules)
    // The card sits at the top of the page with the gutter and fits its content; a parent that fills the screen must not stretch it.
    Column(Modifier.fillMaxWidth().padding(Spacing.L)) { TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("${module.label} is locked")
            if (failed) Report(model, "That did not open it.", tone = NoticeTone.WARNING)
            PixelButton("Unlock", ::ask, Modifier.fillMaxWidth(), module.look.accent)
            PixelButton("Back", toModules, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        }
    } }
}
