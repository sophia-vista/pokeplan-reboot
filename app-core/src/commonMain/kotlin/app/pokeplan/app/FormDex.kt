package app.pokeplan.app

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.FormInfo
import app.pokeplan.core.dex.FormKind
import app.pokeplan.core.dex.Gender
import app.pokeplan.core.pokemon.FormRoutes
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.OwnedPokemon

object FormDex {
    fun counts(form: FormInfo): Boolean = form.kind in COUNTED || FormRoutes.counts(form)

    fun formsOf(p: OwnedPokemon): List<FormInfo> {
        val all = Dex.formsOf(p.speciesId)
        return listOf(p.form) +
            all.filter { it.kind == FormKind.GENDER && p.gender == Gender.FEMALE } +
            all.filter { it.kind == FormKind.MEGA && p.heldItem == Items.megaStone(it.identifier) } +
            listOfNotNull(FormRoutes.heldForm(p))
    }

    fun record(data: AppData): AppData {
        val owned = data.player.pokemon
        val forms = owned.flatMap { p -> formsOf(p).map { it.id } }.toSet()
        val shiny = owned.filter { it.isShiny }.flatMap { p -> formsOf(p).map { it.id } }.toSet()
        if (data.caughtForms.containsAll(forms) && data.shinyForms.containsAll(shiny)) return data
        return data.copy(caughtForms = data.caughtForms + forms, shinyForms = data.shinyForms + shiny)
    }

    fun caught(data: AppData, form: FormInfo): Boolean =
        form.id in data.caughtForms || data.player.pokemon.any { p -> formsOf(p).any { it.id == form.id } } ||
            (form.kind == FormKind.DEFAULT && form.speciesId in data.player.caught && unrecorded(data, form.speciesId, data.caughtForms, shiny = false))

    fun shiny(data: AppData, form: FormInfo): Boolean =
        form.id in data.shinyForms || data.player.pokemon.any { p -> p.isShiny && formsOf(p).any { it.id == form.id } } ||
            (form.kind == FormKind.DEFAULT && form.speciesId in data.shinySpecies && unrecorded(data, form.speciesId, data.shinyForms, shiny = true))

    private fun unrecorded(data: AppData, speciesId: Int, record: Set<Int>, shiny: Boolean): Boolean =
        Dex.formsOf(speciesId).none { it.id in record } && data.player.pokemon.none { it.speciesId == speciesId && (!shiny || it.isShiny) }

    private val COUNTED = setOf(FormKind.DEFAULT, FormKind.REGIONAL, FormKind.GENDER, FormKind.MEGA)
}
