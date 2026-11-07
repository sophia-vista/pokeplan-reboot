package app.pokeplan

import android.content.Context
import app.pokeplan.app.CareStore
import app.pokeplan.app.Clock
import app.pokeplan.app.GameStore
import app.pokeplan.app.HealStore
import app.pokeplan.app.MintStore
import app.pokeplan.app.MemoryStorage
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

/**
 * The Heal module's own save (private health records), apart from Plan's and Care's: its own tables in the database, created there from the start
 * (`heal.json` exists only as a fallback while the database has never opened). It is never read by a widget.
 */
fun healStorage(context: Context): Storage = LiveStore.storage(context, "heal", "heal.json")

/**
 * The Mint module's own save (private financial records), apart from every other module's: its own tables in the database, created there from the start
 * with every row sealed by a Keystore key (`KeystoreMintKey`); there is no file fallback. No widget, notification or reminder reads it.
 */
fun mintStorage(context: Context): Storage {
    val s = LiveStore.get(context)
    // Sealed rows in the database, or memory only: Mint is never written in clear, not even as a fallback file.
    return if (s != null && s.mintCipher != null && s.mintTables.isActive()) DatabaseStorage(s.live, "mint") else MemoryStorage()
}

/** Heal's store outside the app (reminders): its own records, paying Field Research into [world] like the app does. */
fun healStore(context: Context, world: GameStore = widgetStore(context)): HealStore =
    HealStore(
        healStorage(context), Clock { System.currentTimeMillis() }, world,
        weekStart = { world.data.settings.weekStart },
    )

/** Mint's store outside the app (bill reminders): its own sealed records, read only; the reminder never pays or changes a record. */
fun mintStore(context: Context, world: GameStore = widgetStore(context)): MintStore =
    MintStore(mintStorage(context), Clock { System.currentTimeMillis() }, world, weekStart = { world.data.settings.weekStart })

/** A store for the widgets: reads the same save as the app and writes back what a tap changes. */
fun widgetStore(context: Context): GameStore =
    GameStore(saveStorage(context), Clock { System.currentTimeMillis() })
