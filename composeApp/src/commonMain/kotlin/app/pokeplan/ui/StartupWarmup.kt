package app.pokeplan.ui

import app.pokeplan.core.dex.Dex
import app.pokeplan.ui.theme.PmdFont

/**
 * Reads the caches the first real frame needs — the Dex tables and the dialogue font atlas — while the startup screen is
 * still up (see `App(asyncModel = true)`). They are parsed lazily on first use, and that parse on the UI thread is what
 * keeps the last drawn frame from moving; here it runs beside the loading pixels instead. Every parser caches its
 * result, so calling this again is cheap.
 */
@Suppress("UNUSED_EXPRESSION")
internal suspend fun warmStartup() {
    Dex.species
    Dex.forms
    Dex.evolutions
    PmdFont.load()
}
