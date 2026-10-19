package app.pokeplan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import app.pokeplan.ui.App
import app.pokeplan.ui.Tab
import app.pokeplan.widgets.updateWidgets
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Fires when the app comes back to the front: a widget may have changed the save meanwhile. */
    private val resumes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var created = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storage = saveStorage(this)
        val motion = AccelerometerMotion(this)
        // A widget can open the app on a tab ("FOCUS" for the Start focus widget).
        val tab = intent?.getStringExtra(EXTRA_TAB)?.let { name -> Tab.entries.firstOrNull { it.name == name } } ?: Tab.HOME
        setContent { App(storage, focusTab = true, motion = motion, startTab = tab, externalChanges = resumes, cries = AndroidCryPlayer(this)) }
    }

    override fun onResume() {
        super.onResume()
        // The first resume follows onCreate, which has just read the save.
        if (created) resumes.tryEmit(Unit)
        created = true
    }

    /** Leaving the app: bring the widgets up to date with what just happened. */
    override fun onStop() {
        super.onStop()
        lifecycleScope.launch { updateWidgets(applicationContext) }
    }

    companion object {
        const val EXTRA_TAB = "app.pokeplan.TAB"
    }
}
