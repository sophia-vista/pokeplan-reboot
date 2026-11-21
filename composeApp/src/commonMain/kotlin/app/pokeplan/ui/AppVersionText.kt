package app.pokeplan.ui

val AppVersion.display: String
    get() = if (BUILD > 0) "$NAME (build $BUILD)" else "$NAME (dev)"
