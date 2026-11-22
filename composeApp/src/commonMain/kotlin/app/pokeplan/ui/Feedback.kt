package app.pokeplan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.pokeplan.ui.theme.NoticeTone
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelShape
import app.pokeplan.ui.theme.Spacing
import kotlinx.coroutines.delay

enum class FeedbackKind(val shownMillis: Long) { SAVED(2_500L), REWARD(4_000L), ERROR(6_000L) }

data class Feedback(val text: String, val kind: FeedbackKind, val sticky: Boolean, val serial: Long)

@Composable
fun Report(model: AppModel, text: String?, tone: NoticeTone = NoticeTone.INFO) {
    if (text == null) return
    val kind = if (tone == NoticeTone.INFO) FeedbackKind.SAVED else FeedbackKind.ERROR
    DisposableEffect(text, kind) {
        model.showFeedback(text, kind, sticky = true)
        onDispose { model.dropFeedback(text) }
    }
}

@Composable
fun FeedbackHost(model: AppModel, modifier: Modifier = Modifier) {
    val feedback = model.feedback ?: return
    if (!feedback.sticky || feedback.kind == FeedbackKind.SAVED) LaunchedEffect(feedback.serial) {
        delay(feedback.kind.shownMillis)
        model.dropFeedback(feedback.serial)
    }
    val color = when (feedback.kind) { FeedbackKind.SAVED -> Pixel.Green; FeedbackKind.REWARD -> Pixel.Blue; FeedbackKind.ERROR -> Pixel.Warning }
    Row(
        modifier.fillMaxWidth().padding(horizontal = Spacing.M, vertical = Spacing.S)
            .background(Pixel.Ink, PixelShape)
            .clickable(onClickLabel = "Dismiss") { model.dropFeedback(feedback.serial) }
            .semantics { liveRegion = if (feedback.kind == FeedbackKind.ERROR) LiveRegionMode.Assertive else LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(Spacing.XS).height(Spacing.Target).background(color))
        Text(feedback.text, Modifier.weight(1f).padding(horizontal = Spacing.L, vertical = Spacing.M), style = MaterialTheme.typography.bodyLarge, color = Pixel.OnInk, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}
