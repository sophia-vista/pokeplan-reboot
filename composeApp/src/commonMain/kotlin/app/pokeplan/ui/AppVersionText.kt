package app.pokeplan.ui

/** "0.1.0 (build 12)" for a release build, "0.1.0 (dev)" for anything built locally or by a pull request. */
val AppVersion.display: String
    get() = if (BUILD > 0) "$NAME (build $BUILD)" else "$NAME (dev)"
