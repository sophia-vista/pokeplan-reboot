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

/** Downloads the data export through the browser. */
class BrowserFileSaver : FileSaver {
    override fun save(name: String, text: String): String {
        download(name, text)
        return "Downloaded $name"
    }
}

private fun download(name: String, text: String): Unit = js(
    """{ const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([text], { type: 'application/json' })); a.download = name; a.click(); setTimeout(() => URL.revokeObjectURL(a.href), 1000); }"""
)

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport { App(BrowserStorage(), focusTab = false, cries = WebCryPlayer(), files = BrowserFileSaver()) }
}
