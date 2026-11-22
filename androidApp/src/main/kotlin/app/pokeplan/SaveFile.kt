package app.pokeplan

import android.content.Context
import app.pokeplan.app.care.CareStore
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.GameStore
import app.pokeplan.app.heal.HealStore
import app.pokeplan.app.plan.BaseLinkOwner
import app.pokeplan.app.base.BaseStore
import app.pokeplan.app.link.LinkStore
import app.pokeplan.app.mint.MintStore
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.app.storage.Storage
fun saveStorage(context: Context): Storage = LiveStore.storage(context, "save", "save.json")

fun careStorage(context: Context): Storage = LiveStore.storage(context, "care", "care.json")

fun careStore(context: Context, world: GameStore = widgetStore(context)): CareStore =
    CareStore(
        careStorage(context), Clock { System.currentTimeMillis() }, world,
        weekStart = { world.data.settings.weekStart },
    )

fun healStorage(context: Context): Storage {
    val s = LiveStore.get(context)
    return if (s != null && s.healTables.isSealed() && s.healCipher == null) MemoryStorage() else LiveStore.storage(context, "heal", "heal.json")
}

fun baseStorage(context: Context): Storage = LiveStore.storage(context, "home", "home.json")

fun linkStorage(context: Context): Storage = LiveStore.storage(context, "link", "link.json")

fun mintStorage(context: Context): Storage {
    val s = LiveStore.get(context)
    return if (s != null && s.mintCipher != null && s.mintTables.isActive()) DatabaseStorage(s.live, "mint") else MemoryStorage()
}

fun healStore(context: Context, world: GameStore = widgetStore(context)): HealStore =
    HealStore(
        healStorage(context), Clock { System.currentTimeMillis() }, world,
        weekStart = { world.data.settings.weekStart },
    )

fun baseStore(context: Context, world: GameStore = widgetStore(context)): BaseStore =
    BaseStore(baseStorage(context), Clock { System.currentTimeMillis() }, world)

fun linkStore(context: Context, world: GameStore = widgetStore(context)): LinkStore =
    LinkStore(linkStorage(context), Clock { System.currentTimeMillis() }, world)

fun mintStore(context: Context, world: GameStore = widgetStore(context)): MintStore =
    MintStore(mintStorage(context), Clock { System.currentTimeMillis() }, world, weekStart = { world.data.settings.weekStart })

fun widgetStore(context: Context): GameStore {
    val clock = Clock { System.currentTimeMillis() }
    val game = GameStore(saveStorage(context), clock)
    if (game.data.tasks.any { it.ownedElsewhere }) game.registerLinkOwner(BaseLinkOwner(BaseStore(baseStorage(context), clock, game)))
    return game
}
