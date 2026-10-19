package app.pokeplan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.pokeplan.ui.App
import app.pokeplan.ui.Tab

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storage = saveStorage(this)
        val motion = AccelerometerMotion(this)
        // A widget can open the app on a tab ("FOCUS" for the Start focus widget).
        val tab = intent?.getStringExtra(EXTRA_TAB)?.let { name -> Tab.entries.firstOrNull { it.name == name } } ?: Tab.HOME
        setContent { App(storage, focusTab = true, motion = motion, startTab = tab, cries = AndroidCryPlayer(this)) }
    }

    companion object {
        const val EXTRA_TAB = "app.pokeplan.TAB"
    }
}
