package app.pokeplan.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import app.pokeplan.app.storage.Storage
import kotlinx.browser.localStorage

class BrowserStorage(private val key: String = "pokeplan.save") : Storage {
    override fun load(): String? = localStorage.getItem(key)
    override fun save(text: String) = localStorage.setItem(key, text)
    override fun keepAside(text: String) = localStorage.setItem("$key.unreadable", text)
}

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
    ComposeViewport { App(BrowserStorage(), careStorage = BrowserStorage("pokeplan.care"), focusTab = false, cries = WebCryPlayer(), files = BrowserFileSaver()) }
}
