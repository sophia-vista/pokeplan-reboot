package app.pokeplan

import android.content.Context
import app.pokeplan.app.CareStore
import app.pokeplan.app.Clock
import app.pokeplan.app.GameStore
import app.pokeplan.app.Storage

/**
 * The save is one document in the app's private database; the app and the widgets share it (see [LiveStore]). Before the
 * first start of a build with the database, and only if it can never be opened, it is the file `save.json`.
 */
fun saveStorage(context: Context): Storage = LiveStore.storage(context, "save", "save.json")

/** The Care module's own save, apart from Plan's (docs/ecosystem.md, section 6): its own document, `care.json` before the database. */
fun careStorage(context: Context): Storage = LiveStore.storage(context, "care", "care.json")

/** Care's store outside the app (widgets, reminders): its own save, paying into [world] (the shared save) like the app does. */
fun careStore(context: Context, world: GameStore = widgetStore(context)): CareStore =
    CareStore(
        careStorage(context), Clock { System.currentTimeMillis() }, world,
        weekStart = { world.data.settings.weekStart },
    )

/** A store for the widgets: reads the same save as the app and writes back what a tap changes. */
fun widgetStore(context: Context): GameStore =
    GameStore(saveStorage(context), Clock { System.currentTimeMillis() })
