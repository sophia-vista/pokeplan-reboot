package app.pokeplan.core.pokemon

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.Gender

/** Species ids used across the tests. */
object Ids {
    const val BULBASAUR = 1
    const val CHARMANDER = 4
    const val RATTATA = 19
    const val PIKACHU = 25
    const val MACHOKE = 67
    const val SLOWPOKE = 79
    const val DITTO = 132
    const val HITMONLEE = 106
    const val KANGASKHAN = 115
    const val EEVEE = 133
    const val UNOWN = 201
    const val WURMPLE = 265
    const val KIRLIA = 281
    const val SHEDINJA = 292
    const val GARCHOMP = 445
    const val PANCHAM = 674
}

/** A level 1 Pokémon with fixed, easy-to-reason-about values. */
fun pokemon(
    speciesId: Int,
    formIdentifier: String = Dex.defaultForm(speciesId).identifier,
    level: Int = 1,
    nature: Nature = Nature.HARDY,
    gender: Gender? = null,
    friendship: Int = FriendshipRules.START,
    heldItem: String? = null,
    personality: Int = 0,
    evs: StatSpread = StatSpread.ZERO,
    ivs: StatSpread = StatSpread.of(31),
): OwnedPokemon {
    val form = Dex.form(formIdentifier)!!
    return OwnedPokemon(
        id = "test-$formIdentifier",
        speciesId = speciesId,
        formIdentifier = formIdentifier,
        nickname = Dex.species(speciesId).name,
        nature = nature,
        ability = form.abilities.firstOrNull() ?: form.hiddenAbility!!,
        gender = gender,
        ivs = ivs,
        evs = evs,
        level = level,
        friendship = friendship,
        heldItem = heldItem,
        personality = personality,
        inParty = true,
        caughtAtMillis = 0,
    )
}
