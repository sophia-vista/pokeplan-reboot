package app.pokeplan.core.pokemon

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.Gender
import app.pokeplan.core.dex.HatchOption
import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.dex.Region
import app.pokeplan.core.dex.Egg
import app.pokeplan.core.dex.Rarity
import kotlin.random.Random

/** Turns a finished egg into a new [OwnedPokemon]. */
object Hatching {
    /** Hatches an egg earned from [effortMinutes] of effort (a focus session, a workout, ...) for the player's chosen [region]. */
    fun hatch(
        id: String,
        effortMinutes: Int,
        deepFocus: Boolean,
        region: Region,
        partySize: Int,
        nowMillis: Long,
        rng: Random = Random,
        shinyMultiplier: Double = 1.0,
    ): OwnedPokemon {
        val rarity = Egg.rates(effortMinutes, deepFocus).roll(rng)
        return hatchAtRarity(id, rarity, region, partySize, nowMillis, effortMinutes, rng, shinyMultiplier)
    }

    /** Hatches a Pokémon of a known [rarity]; [sessionMinutes] only feeds the shiny bonus. */
    fun hatchAtRarity(
        id: String,
        rarity: Rarity,
        region: Region,
        partySize: Int,
        nowMillis: Long,
        sessionMinutes: Int,
        rng: Random = Random,
        shinyMultiplier: Double = 1.0,
    ): OwnedPokemon = create(id, HatchRules.roll(region, rarity, rng), partySize, nowMillis, sessionMinutes, rng, shinyMultiplier)

    /** Builds the Pokémon for a chosen [option] (also used for quest rewards). */
    fun create(
        id: String,
        option: HatchOption,
        partySize: Int,
        nowMillis: Long,
        sessionMinutes: Int,
        rng: Random,
        shinyMultiplier: Double = 1.0,
    ): OwnedPokemon {
        val species = Dex.species(option.speciesId)
        val form = checkNotNull(Dex.form(option.formIdentifier)) { "unknown form ${option.formIdentifier}" }
        val abilities = form.abilities.ifEmpty { listOfNotNull(form.hiddenAbility) }
        val cosmetic = species.cosmeticForms.let { forms ->
            if (forms.isEmpty()) null else (listOf<String?>(null) + forms).let { it[rng.nextInt(it.size)] }
        }
        return OwnedPokemon(
            id = id,
            speciesId = species.id,
            formIdentifier = form.identifier,
            nickname = species.name,
            nature = Nature.random(rng),
            ability = abilities[rng.nextInt(abilities.size)],
            gender = species.femaleEighths?.let { if (rng.nextInt(8) < it) Gender.FEMALE else Gender.MALE },
            ivs = StatSpread.random(StatCalculator.MAX_IV, rng),
            isShiny = Shiny.roll(rng, sessionMinutes, shinyMultiplier),
            cosmeticForm = cosmetic,
            personality = rng.nextInt(PERSONALITY_RANGE),
            inParty = partySize < OwnedPokemon.PARTY_SIZE,
            caughtAtMillis = nowMillis,
        )
    }

    const val PERSONALITY_RANGE = 65536
}
