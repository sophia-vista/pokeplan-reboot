package app.pokeplan.ui.theme

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import app.pokeplan.ui.screens.LocalReduceMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val HOLD_TO_CONFIRM_MILLIS = 1200

private const val ARMED_MILLIS = 4000L

const val TAP_AGAIN = "Tap again to confirm"

@Composable
fun HoldToConfirm(
    text: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Pixel.Danger,
    enabled: Boolean = true,
    compact: Boolean = true,
    holdMillis: Int = HOLD_TO_CONFIRM_MILLIS,
) {
    val calm = LocalReduceMotion.current
    var progress by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(armed) { if (armed) { delay(ARMED_MILLIS); armed = false } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        val explicit = Modifier.semantics { onClick(label = text) { if (enabled) { onConfirm(); true } else false } }
        if (calm) {
            PixelKey(
                { if (armed) { armed = false; onConfirm() } else armed = true }, Modifier.fillMaxWidth().then(explicit),
                key = color, enabled = enabled, compact = compact,
            ) { look -> KeyLabel(if (armed) TAP_AGAIN else text, look) }
        } else {
            PixelKey(
                null, Modifier.fillMaxWidth().then(explicit).pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(onPress = {
                        var job: Job? = null
                        job = scope.launch {
                            val start = androidx.compose.runtime.withFrameNanos { it }
                            while (progress < 1f) {
                                val now = androidx.compose.runtime.withFrameNanos { it }
                                progress = ((now - start) / 1_000_000f / holdMillis).coerceAtMost(1f)
                            }
                            progress = 0f
                            onConfirm()
                        }
                        tryAwaitRelease()
                        job.cancel()
                        progress = 0f
                    })
                },
                key = color, enabled = enabled, compact = compact,
            ) { look -> KeyLabel(text, look) }
            PixelBar(progress, color = color)
        }
    }
}
