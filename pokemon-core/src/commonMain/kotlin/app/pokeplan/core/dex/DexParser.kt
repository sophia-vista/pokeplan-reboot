package app.pokeplan.core.dex

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads the Dex tables, which are JSON files in the `dex` resources (see tools/species/generate.py): each an array of rows, a row an array of
 * numbers, text, nulls (nothing) and lists, in the column order read below. Read once, each when first asked for.
 */
internal object DexParser {
    private fun table(name: String): List<JsonArray> = Json.parseToJsonElement(dexJson(name)).jsonArray.map { it.jsonArray }

    private fun JsonArray.text(i: Int): String = this[i].jsonPrimitive.content
    private fun JsonArray.textOrNull(i: Int): String? = this[i].takeIf { it !is JsonNull }?.jsonPrimitive?.content
    private fun JsonArray.number(i: Int): Int = this[i].jsonPrimitive.int
    private fun JsonArray.numberOrNull(i: Int): Int? = this[i].takeIf { it !is JsonNull }?.jsonPrimitive?.int
    private fun JsonArray.texts(i: Int): List<String> = this[i].jsonArray.map { it.jsonPrimitive.content }
    private fun JsonArray.numbers(i: Int): List<Int> = this[i].jsonArray.map { it.jsonPrimitive.int }

    fun species(): List<SpeciesInfo> = table("species").map { f ->
        val gender = f.number(5)
        SpeciesInfo(
            id = f.number(0),
            identifier = f.text(1),
            name = f.text(2),
            generation = f.number(3),
            evolvesFrom = f.numberOrNull(4),
            femaleEighths = if (gender < 0) null else gender,
            isBaby = f.number(6) == 1,
            isLegendary = f.number(7) == 1,
            isMythical = f.number(8) == 1,
            cosmeticForms = f.texts(9),
            genus = f.textOrNull(10) ?: "",
            heightDm = f.number(11),
            weightHg = f.number(12),
        )
    }

    fun forms(): List<FormInfo> = table("forms").map { f ->
        val s = f.numbers(6)
        FormInfo(
            id = f.number(0),
            identifier = f.text(1),
            speciesId = f.number(2),
            kind = FormKind.valueOf(f.text(3)),
            name = f.text(4),
            types = f.texts(5).map { PokemonType.valueOf(it.uppercase()) },
            stats = BaseStats(s[0], s[1], s[2], s[3], s[4], s[5]),
            abilities = f.texts(7),
            hiddenAbility = f.textOrNull(8),
        )
    }

    fun evolutions(): List<Evolution> = table("evolutions").map { f ->
        Evolution(
            from = f.number(0),
            to = f.number(1),
            kind = EvolutionKind.valueOf(f.text(2)),
            level = f.numberOrNull(3),
            item = f.textOrNull(4),
            heldItem = f.textOrNull(5),
            timeOfDay = f.textOrNull(6)?.let { TimeOfDay.valueOf(it.uppercase()) },
            gender = when (f.textOrNull(7)) {
                "F" -> Gender.FEMALE
                "M" -> Gender.MALE
                else -> null
            },
            friendship = f.numberOrNull(8),
            requiredForm = f.textOrNull(9),
            evolvedForm = f.textOrNull(10),
            chancePercent = f.numberOrNull(11),
            conditions = f.texts(12),
        )
    }

    /** Pokédex text per species id. */
    fun entries(): Map<Int, String> = table("entries").associate { f -> f.number(0) to f.text(1) }

    /** Region name to ordered species ids. Includes the extra "HISUI" list used to pick Hisuian variants. */
    fun regions(): Map<String, List<Int>> = table("regions").associate { f -> f.text(0) to f.numbers(1) }

    fun moves(): List<MoveInfo> = table("moves").map { f ->
        MoveInfo(
            id = f.number(0),
            identifier = f.text(1),
            name = f.text(2),
            type = PokemonType.valueOf(f.text(3).uppercase()),
            category = MoveCategory.valueOf(f.text(4)),
            power = f.numberOrNull(5),
            pp = f.numberOrNull(6),
            accuracy = f.numberOrNull(7),
            priority = f.number(8),
            generation = f.number(9),
        )
    }

    /** Form (Pokémon row) id to its learnset. */
    fun learnsets(): Map<Int, Learnset> = table("learnsets").associate { f ->
        f.number(0) to Learnset(
            levelUp = f[1].jsonArray.map { it.jsonArray.let { pair -> pair[0].jsonPrimitive.int to pair[1].jsonPrimitive.int } },
            egg = f.numbers(2),
            tutor = f.numbers(3),
            machine = f.numbers(4),
        )
    }
}
