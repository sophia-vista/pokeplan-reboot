package app.pokeplan

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

/**
 * What Health Connect shows when the player asks why Heal wants access (it requires an activity that answers `ACTION_SHOW_PERMISSIONS_RATIONALE`, and on Android 14 the
 * "permission usage" screen). Plain words, no network, nothing collected.
 */
class HealthRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val text = TextView(this).apply {
            setPadding(pad, pad * 2, pad, pad)
            textSize = 16f
            text = "PokéLife's Heal module can read exercise sessions, sleep sessions, weight, body fat and drinks from Health Connect, only the kinds you choose, and only when you tap " +
                "\"Read now\" in Heal → Connections. It never writes to Health Connect and never reads in the background.\n\n" +
                "What it reads is kept on this phone, inside Heal, marked with the app that wrote it. It is never sent anywhere: PokéLife has no account and no server. " +
                "It never earns a Pokémon, a candy or a reward.\n\n" +
                "You can stop at any time in Heal → Connections (Disconnect) or in Health Connect itself, and delete what was imported from one app or from all of them."
        }
        setContentView(ScrollView(this).apply { addView(text) })
    }
}
