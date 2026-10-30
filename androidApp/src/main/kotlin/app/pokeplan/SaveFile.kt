package app.pokeplan

import android.content.Context
import app.pokeplan.app.CareStore
import app.pokeplan.app.Clock
import app.pokeplan.app.FileStorage
import app.pokeplan.app.GameStore
import app.pokeplan.app.Storage
import java.io.File

/** The save is one JSON file in the app's private storage; the app and the widgets share it. */
fun saveStorage(context: Context): Storage = FileStorage(File(context.filesDir, "save.json"))

/** The Care module's own save, apart from Plan's (docs/ecosystem.md, section 6). */
fun careStorage(context: Context): Storage = FileStorage(File(context.filesDir, "care.json"))

/** Care's store outside the app (widgets, reminders): its own save, paying into [world] (the shared save) like the app does. */
fun careStore(context: Context, world: GameStore = widgetStore(context)): CareStore =
    CareStore(
        careStorage(context), Clock { System.currentTimeMillis() }, world,
        weekStart = { world.data.settings.weekStart },
    )

/** A store for the widgets: reads the same save as the app and writes back what a tap changes. */
fun widgetStore(context: Context): GameStore =
    GameStore(saveStorage(context), Clock { System.currentTimeMillis() })
