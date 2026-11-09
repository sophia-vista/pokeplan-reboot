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

/** What a [Feedback] says about: something saved, something earned, or something that did not work. */
enum class FeedbackKind(val shownMillis: Long) { SAVED(2_500L), REWARD(4_000L), ERROR(6_000L) }

/**
 * The one place the app tells the player how something went (saved, earned, did not work), as one line in one bar pinned above the tabs ([FeedbackHost]),
 * the same on every screen. A screen says it once with [AppModel.say] when it happens, or keeps it up while it holds ([Report]); it never draws its own
 * message box. Rich reward moments (an egg hatching, a level up) keep their own scenes; this is for the sentence that goes with an ordinary action.
 */
data class Feedback(val text: String, val kind: FeedbackKind, val sticky: Boolean, val serial: Long)

/**
 * Says [text] while this is on the screen, for a problem that stays until the player fixes it (a field the app cannot read): the bar shows it as long as the
 * screen holds it and takes it down when the screen clears it or is left. A `null` [text] says nothing.
 */
@Composable
fun Report(model: AppModel, text: String?, tone: NoticeTone = NoticeTone.INFO) {
    if (text == null) return
    val kind = if (tone == NoticeTone.INFO) FeedbackKind.SAVED else FeedbackKind.ERROR
    DisposableEffect(text, kind) {
        model.showFeedback(text, kind, sticky = true)
        onDispose { model.dropFeedback(text) }
    }
}

/** The bar. At most one line at a time: the newest, and an error is not replaced by a plain "Saved." arriving at the same moment. */
@Composable
fun FeedbackHost(model: AppModel, modifier: Modifier = Modifier) {
    val feedback = model.feedback ?: return
    // A sticky line is taken down by whoever put it up; a timed one takes itself down (the serial says it is still the same one).
    // A saved line stays up a moment, a reward longer, an error longest.
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
