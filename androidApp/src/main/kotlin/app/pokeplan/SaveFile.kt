package app.pokeplan

import android.content.Context
import app.pokeplan.app.CareStore
import app.pokeplan.app.Clock
import app.pokeplan.app.GameStore
import app.pokeplan.app.HealStore
import app.pokeplan.app.HomeLinkOwner
import app.pokeplan.app.HomeStore
import app.pokeplan.app.LinkStore
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
 * (`heal.json` exists only as a fallback while the database has never opened). Its rows are sealed with a Keystore key (`KeystoreHealKey`) since 0.152.0. It is never read by a widget.
 */
fun healStorage(context: Context): Storage {
    val s = LiveStore.get(context)
    // Once the rows are sealed, no key means no access: memory only for this run, never a clear copy of what is sealed (the rows are untouched and open again at the next start with the key).
    return if (s != null && s.healTables.isSealed() && s.healCipher == null) MemoryStorage() else LiveStore.storage(context, "heal", "heal.json")
}

/**
 * The Home module's own save (private household records, serial numbers among them), apart from every other module's: its own tables in the database,
 * created there from the start (`home.json` exists only as a fallback while the database has never opened). No widget reads it.
 */
fun homeStorage(context: Context): Storage = LiveStore.storage(context, "home", "home.json")

/**
 * The Link module's own save (people, notes, dates and follow-ups), apart from every other module's: its own tables in the database, created there from
 * the start (`link.json` exists only as a fallback while the database has never opened). No widget reads it.
 */
fun linkStorage(context: Context): Storage = LiveStore.storage(context, "link", "link.json")

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

/** Home's store outside the app (reminders): its own records; a chore reminder's "Done" completes the chore like the app does and pays Habitat Discovery into [world]. */
fun homeStore(context: Context, world: GameStore = widgetStore(context)): HomeStore =
    HomeStore(homeStorage(context), Clock { System.currentTimeMillis() }, world)

/** Link's store outside the app (reminders): its own records; a follow-up reminder's "Done" completes it like the app does and pays Postcard Trail into [world]. */
fun linkStore(context: Context, world: GameStore = widgetStore(context)): LinkStore =
    LinkStore(linkStorage(context), Clock { System.currentTimeMillis() }, world)

/** Mint's store outside the app (bill reminders): its own sealed records, read only; the reminder never pays or changes a record. */
fun mintStore(context: Context, world: GameStore = widgetStore(context)): MintStore =
    MintStore(mintStorage(context), Clock { System.currentTimeMillis() }, world, weekStart = { world.data.settings.weekStart })

/** A store for the widgets: reads the same save as the app and writes back what a tap changes. */
fun widgetStore(context: Context): GameStore {
    val clock = Clock { System.currentTimeMillis() }
    val game = GameStore(saveStorage(context), clock)
    // A task linked to a Home chore is completed by Home (PRODUCT_BIBLE.md section 4), so a widget or a notification that ticks one needs Home's store.
    // It is opened only when such a task exists; without an owner a linked task is left exactly as it is.
    if (game.data.tasks.any { it.ownedElsewhere }) game.registerLinkOwner(HomeLinkOwner(HomeStore(homeStorage(context), clock, game)))
    return game
}
