package app.pokeplan.shared.dex

import app.pokeplan.shared.dex.generated.DEX_ENTRIES
import app.pokeplan.shared.dex.generated.DEX_EVOLUTIONS
import app.pokeplan.shared.dex.generated.DEX_FORMS
import app.pokeplan.shared.dex.generated.DEX_REGIONS
import app.pokeplan.shared.dex.generated.DEX_SPECIES
import app.pokeplan.shared.model.PokemonType

/** Parses the generated tables (see tools/species/generate.py). Fields are `|` separated, `-` means empty. */
internal object DexParser {
    private fun rows(chunks: List<String>): List<List<String>> =
        chunks.joinToString("").lineSequence().filter { it.isNotBlank() }.map { it.split('|') }.toList()

    private fun String.orNull(): String? = if (this == "-") null else this

    private fun list(value: String, separator: Char = ','): List<String> =
        if (value == "-") emptyList() else value.split(separator)

    fun species(): List<SpeciesInfo> = rows(DEX_SPECIES).map { f ->
        val gender = f[5].toInt()
        SpeciesInfo(
            id = f[0].toInt(),
            identifier = f[1],
            name = f[2],
            generation = f[3].toInt(),
            evolvesFrom = f[4].orNull()?.toInt(),
            femaleEighths = if (gender < 0) null else gender,
            isBaby = f[6] == "1",
            isLegendary = f[7] == "1",
            isMythical = f[8] == "1",
            cosmeticForms = list(f[9]),
        )
    }

    fun forms(): List<FormInfo> = rows(DEX_FORMS).map { f ->
        val s = f[6].split(',').map { it.toInt() }
        FormInfo(
            id = f[0].toInt(),
            identifier = f[1],
            speciesId = f[2].toInt(),
            kind = FormKind.valueOf(f[3]),
            name = f[4],
            types = list(f[5]).map { PokemonType.valueOf(it.uppercase()) },
            stats = BaseStats(s[0], s[1], s[2], s[3], s[4], s[5]),
            abilities = list(f[7]),
            hiddenAbility = f[8].orNull(),
        )
    }

    fun evolutions(): List<Evolution> = rows(DEX_EVOLUTIONS).map { f ->
        Evolution(
            from = f[0].toInt(),
            to = f[1].toInt(),
            kind = EvolutionKind.valueOf(f[2]),
            level = f[3].orNull()?.toInt(),
            item = f[4].orNull(),
            heldItem = f[5].orNull(),
            timeOfDay = f[6].orNull()?.let { TimeOfDay.valueOf(it.uppercase()) },
            gender = when (f[7]) {
                "F" -> Gender.FEMALE
                "M" -> Gender.MALE
                else -> null
            },
            friendship = f[8].orNull()?.toInt(),
            requiredForm = f[9].orNull(),
            evolvedForm = f[10].orNull(),
            chancePercent = f[11].orNull()?.toInt(),
            conditions = list(f[12], ';'),
        )
    }

    /** Pokédex text per species id. */
    fun entries(): Map<Int, String> = DEX_ENTRIES.joinToString("").lineSequence().filter { it.isNotBlank() }
        .associate { line -> line.substringBefore('|').toInt() to line.substringAfter('|') }

    /** Region name to ordered species ids. Includes the extra "HISUI" list used to pick Hisuian variants. */
    fun regions(): Map<String, List<Int>> = rows(DEX_REGIONS).associate { f -> f[0] to f[1].split(',').map { it.toInt() } }
}
