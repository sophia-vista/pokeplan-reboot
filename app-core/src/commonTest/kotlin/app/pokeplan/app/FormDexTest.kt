package app.pokeplan.app

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.FormKind
import app.pokeplan.core.dex.Gender
import app.pokeplan.core.dex.HatchOption
import app.pokeplan.core.pokemon.Hatching
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.reward.PlayerState
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FormDexTest {
    private fun pokemon(identifier: String): OwnedPokemon {
        val form = Dex.form(identifier)!!
        return Hatching.create("p-$identifier", HatchOption(form.speciesId, identifier), 0, 0L, 25, Random(1)).copy(isShiny = false)
    }

    private fun data(vararg owned: OwnedPokemon, caught: Set<Int> = owned.map { it.speciesId }.toSet()) =
        AppData(player = PlayerState(pokemon = owned.toList(), caught = caught))

    private fun form(identifier: String) = Dex.form(identifier)!!

    @Test
    fun a_pokemon_counts_for_its_own_form_and_only_that_one() {
        val d = FormDex.record(data(pokemon("vulpix-alola")))
        assertTrue(FormDex.caught(d, form("vulpix-alola")))
        assertFalse(FormDex.caught(d, form("vulpix")))
    }

    @Test
    fun a_female_counts_for_the_female_form_and_a_mega_stone_for_the_mega() {
        val female = pokemon("meowstic-male").copy(gender = Gender.FEMALE)
        val mega = pokemon("venusaur").copy(heldItem = Items.megaStone("venusaur-mega"))
        val d = FormDex.record(data(female, mega))
        assertTrue(FormDex.caught(d, form("meowstic-female")))
        assertTrue(FormDex.caught(d, form("venusaur-mega")))
        assertTrue(FormDex.caught(d, form("venusaur")))
    }

    @Test
    fun forms_stay_after_the_pokemon_is_gone_and_shinies_are_kept_apart() {
        val shiny = pokemon("vulpix-alola").copy(isShiny = true)
        val recorded = FormDex.record(data(shiny))
        val after = recorded.copy(player = recorded.player.copy(pokemon = emptyList()))
        assertTrue(FormDex.caught(after, form("vulpix-alola")))
        assertTrue(FormDex.shiny(after, form("vulpix-alola")))
        assertFalse(FormDex.shiny(after, form("vulpix")))
    }

    @Test
    fun a_species_caught_before_the_form_dex_counts_its_default_form() {
        val vulpix = form("vulpix").speciesId
        val old = AppData(player = PlayerState(caught = setOf(vulpix)), shinySpecies = setOf(vulpix))
        assertTrue(FormDex.caught(old, form("vulpix")))
        assertFalse(FormDex.caught(old, form("vulpix-alola")))
        assertTrue(FormDex.shiny(old, form("vulpix")))
    }

    @Test
    fun gigantamax_and_alternate_forms_are_shown_but_not_counted() {
        assertFalse(FormDex.counts(Dex.formsOf(6).first { it.kind == FormKind.GMAX }))
        assertFalse(FormDex.counts(form("rotom-heat")))
        assertTrue(FormDex.counts(form("vulpix-alola")))
    }
}
