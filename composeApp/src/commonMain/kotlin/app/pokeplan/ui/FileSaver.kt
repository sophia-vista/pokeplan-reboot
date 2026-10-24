package app.pokeplan.ui

/**
 * Hands a file to the player, for the data export: Android opens the share sheet, the desktop app writes it to Downloads
 * and the web version downloads it.
 */
fun interface FileSaver {
    /** Saves or shares [text] as [name]. Returns a short note on what happened, or an error message. */
    fun save(name: String, text: String): String
}
