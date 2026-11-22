package app.pokeplan.world

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.FormInfo
import app.pokeplan.core.dex.FormKind
import kotlin.random.Random

/**
 * Regional forms belong to the WORLD layer of their home region (Alolan forms to Alola, Galarian to
 * Galar, Hisuian to Hisui, Paldean to Paldea), so a wild find in that layer is the regional form.
 * Hisui is its own layer even though its Pokédex is merged into Sinnoh.
 */
object RegionalForms {
    fun layer(form: FormInfo): WorldLayer? = when {
        form.kind == FormKind.REGIONAL -> when {
            form.identifier.endsWith("-alola") -> WorldLayer.ALOLA
            form.identifier.endsWith("-galar") -> WorldLayer.GALAR
            form.identifier.endsWith("-hisui") -> WorldLayer.HISUI
            form.identifier.contains("-paldea") -> WorldLayer.PALDEA
            else -> null
        }
        // White-Striped Basculin is the Hisuian form of Basculin but PokeAPI files it as a plain
        // alternate form; without it Basculegion has no way in.
        form.identifier == "basculin-white-striped" -> WorldLayer.HISUI
        else -> null
    }

    fun inLayer(speciesId: Int, layer: WorldLayer): List<FormInfo> =
        Dex.formsOf(speciesId).filter { layer(it) == layer }

    /** The form a find of [speciesId] becomes in [layer], or null when the layer's form is the default. */
    fun formFor(speciesId: Int, layer: WorldLayer, random: Random): String? =
        inLayer(speciesId, layer).let { forms -> if (forms.isEmpty()) null else forms[random.nextInt(forms.size)].identifier }

    /** The form's own name when there is one, else the species name. */
    fun displayName(speciesId: Int, formIdentifier: String?): String =
        formIdentifier?.let { Dex.form(it)?.name } ?: Dex.species(speciesId).name
}
