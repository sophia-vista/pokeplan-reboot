package app.pokeplan.app

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.FormInfo
import app.pokeplan.core.dex.FormKind
import app.pokeplan.core.dex.Gender
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.OwnedPokemon

/**
 * The form dex: which of a species' forms the player has had, and which as a shiny. A form counts once a Pokémon was in it:
 * its own form (default or regional), its species' female form for a female (Meowstic, Indeedee...), and a Mega form while
 * it held that form's Mega Stone. Gigantamax and the other alternate forms cannot be had in PokéPlan yet, so they are shown
 * but not counted. Like the Shiny Dex this is client-side data in the save ([AppData.caughtForms], [AppData.shinyForms]).
 */
object FormDex {
    /** Whether a form can be had in PokéPlan now, and so counts towards the form dex. */
    fun counts(form: FormInfo): Boolean = form.kind in COUNTED

    /** The forms [p] counts for. */
    fun formsOf(p: OwnedPokemon): List<FormInfo> {
        val all = Dex.formsOf(p.speciesId)
        return listOf(p.form) +
            all.filter { it.kind == FormKind.GENDER && p.gender == Gender.FEMALE } +
            all.filter { it.kind == FormKind.MEGA && p.heldItem == Items.megaStone(it.identifier) }
    }

    /** [data] with the forms of every Pokémon it owns now added to the form dex. Forms stay once had, like caught species. */
    fun record(data: AppData): AppData {
        val owned = data.player.pokemon
        val forms = owned.flatMap { p -> formsOf(p).map { it.id } }.toSet()
        val shiny = owned.filter { it.isShiny }.flatMap { p -> formsOf(p).map { it.id } }.toSet()
        if (data.caughtForms.containsAll(forms) && data.shinyForms.containsAll(shiny)) return data
        return data.copy(caughtForms = data.caughtForms + forms, shinyForms = data.shinyForms + shiny)
    }

    /**
     * Whether the player has had [form], or has it now (before the save records it). Saves from before the form dex know
     * only the species, so a species caught then, with no form on record and none owned now, counts its default form.
     */
    fun caught(data: AppData, form: FormInfo): Boolean =
        form.id in data.caughtForms || data.player.pokemon.any { p -> formsOf(p).any { it.id == form.id } } ||
            (form.kind == FormKind.DEFAULT && form.speciesId in data.player.caught && unrecorded(data, form.speciesId, data.caughtForms, shiny = false))

    /** Whether the player has had [form] as a shiny; a species in the Shiny Dex with no shiny form on record counts its default form. */
    fun shiny(data: AppData, form: FormInfo): Boolean =
        form.id in data.shinyForms || data.player.pokemon.any { p -> p.isShiny && formsOf(p).any { it.id == form.id } } ||
            (form.kind == FormKind.DEFAULT && form.speciesId in data.shinySpecies && unrecorded(data, form.speciesId, data.shinyForms, shiny = true))

    /** No form of the species on [record] and none owned now (as a shiny, for the shiny record). */
    private fun unrecorded(data: AppData, speciesId: Int, record: Set<Int>, shiny: Boolean): Boolean =
        Dex.formsOf(speciesId).none { it.id in record } && data.player.pokemon.none { it.speciesId == speciesId && (!shiny || it.isShiny) }

    private val COUNTED = setOf(FormKind.DEFAULT, FormKind.REGIONAL, FormKind.GENDER, FormKind.MEGA)
}
