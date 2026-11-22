package app.pokeplan.ui

fun interface FileSaver {
    fun save(name: String, text: String): String
}
