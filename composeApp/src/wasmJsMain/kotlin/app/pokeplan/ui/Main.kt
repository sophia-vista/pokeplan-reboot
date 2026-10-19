package app.pokeplan.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import app.pokeplan.app.Storage
import kotlinx.browser.localStorage

/** The save lives in the browser's local storage. */
class BrowserStorage : Storage {
    override fun load(): String? = localStorage.getItem(KEY)
    override fun save(text: String) = localStorage.setItem(KEY, text)

    private companion object {
        const val KEY = "pokeplan.save"
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport { App(BrowserStorage(), focusTab = false, cries = WebCryPlayer()) }
}
