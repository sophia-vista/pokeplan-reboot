package app.pokeplan.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/**
 * The app's one back handler. Compose's own `BackHandler` is deprecated in favour of
 * `NavigationBackHandler`, which needs a state object; this keeps the old call shape
 * (`BackHandler(enabled) { ... }`) so every screen stays a one-line call.
 */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val current by rememberUpdatedState(onBack)
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = enabled,
        onBackCompleted = { current() },
    )
}
