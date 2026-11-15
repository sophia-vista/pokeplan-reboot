package app.pokeplan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelTheme
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The first screen after the phone's database was found damaged with no backup to put back (REP-026 c). It shows before any store opens. The old
 * save files are only as new as the cutover to the database, so using them silently would undo everything since: the player chooses.
 * The damaged file is kept aside whichever is chosen, and the old files are never deleted by this screen.
 */
@Composable
fun RecoveryChoice(olderFromMillis: Long?, onOlder: () -> Unit, onFresh: () -> Unit, darkTheme: Boolean = isSystemInDarkTheme()) {
    PixelTheme(darkTheme) {
        Box(Modifier.fillMaxSize().background(Pixel.Paper).safeDrawingPadding(), contentAlignment = Alignment.Center) {
            TextBox(Modifier.padding(Spacing.L)) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                    PixelTitle("Your data could not be opened")
                    Text("The file that holds it was damaged and there is no backup to put back. The damaged file is kept either way.", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (olderFromMillis != null) "Older files from ${date(olderFromMillis)} are still on this phone. They do not have anything done since."
                        else "Older files are still on this phone. They do not have anything done since.",
                        style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
                    )
                    PixelButton("Use the older data", onOlder, Modifier.fillMaxWidth(), Pixel.Warning)
                    PixelButton("Start fresh", onFresh, Modifier.fillMaxWidth(), Pixel.Danger)
                }
            }
        }
    }
}

private fun date(millis: Long): String = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
