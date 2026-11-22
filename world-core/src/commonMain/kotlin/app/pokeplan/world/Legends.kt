package app.pokeplan.world

import app.pokeplan.core.dex.PokemonType

data class LegendQuest(
    val id: String,
    val speciesId: Int,
    val name: String,
    val layer: WorldLayer,
    val dungeonId: String,
    val clears: Int,
    val afterGuardian: Boolean,
    val blurb: String,
    val types: Set<PokemonType> = emptySet(),
    val findPermille: Int = 0,
    val treasurePercent: Int = 0,
)

fun LegendQuest.bonusText(): String {
    val parts = buildList {
        if (types.isNotEmpty()) add("${types.joinToString(" and ") { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }}-favouring dungeons climb ${Legends.FASTER_PERCENT}% faster")
        if (findPermille > 0) add("${findPermille / 10.0}% more chance of a wild Pokémon on a floor")
        if (treasurePercent > 0) add("${treasurePercent}% more chance of a treasure box")
    }
    return if (parts.isEmpty()) "No bonus." else parts.joinToString("; ") + " in ${layer.label}."
}

object Legends {
    const val FASTER_PERCENT = 10

    val all: List<LegendQuest> = listOf(
        LegendQuest("kanto-zapdos", 145, "Zapdos", WorldLayer.KANTO, "power-plant", 3, false, "Storms gather where the Power Plant hums. Clear it three times.", setOf(PokemonType.ELECTRIC)),
        LegendQuest("kanto-articuno", 144, "Articuno", WorldLayer.KANTO, "seafoam-islands", 3, false, "Something cold watches from the deepest Seafoam cave. Clear it three times.", setOf(PokemonType.ICE)),
        LegendQuest("kanto-moltres", 146, "Moltres", WorldLayer.KANTO, "mt-ember", 3, false, "A fire bird nests at the top of Mt. Ember. Clear it three times.", setOf(PokemonType.FIRE)),
        LegendQuest("kanto-mewtwo", 150, "Mewtwo", WorldLayer.KANTO, "cerulean-cave", 1, true, "Kanto's Guardian waits on the last floor of Cerulean Cave.", findPermille = 10),
        LegendQuest("kanto-mew", 151, "Mew", WorldLayer.KANTO, "cerulean-cave", 3, true, "Where Mewtwo came from. Clear Cerulean Cave three times once its Guardian has been faced.", treasurePercent = 3),
        LegendQuest("johto-lugia", 249, "Lugia", WorldLayer.JOHTO, "whirl-islands", 3, false, "The guardian of the sea sleeps in the deepest Whirl Islands cave. Clear it three times.", setOf(PokemonType.WATER, PokemonType.FLYING)),
        LegendQuest("johto-ho-oh", 250, "Ho-Oh", WorldLayer.JOHTO, "tin-tower", 1, true, "Johto's Guardian waits on the top floor of the Tin Tower.", treasurePercent = 3, findPermille = 5),
        LegendQuest("hoenn-groudon", 383, "Groudon", WorldLayer.HOENN, "mt-chimney", 3, false, "The land's ancient Pokémon stirs under Mt. Chimney. Clear it three times.", setOf(PokemonType.GROUND, PokemonType.FIRE)),
        LegendQuest("hoenn-kyogre", 382, "Kyogre", WorldLayer.HOENN, "seafloor-cavern", 3, false, "The sea's ancient Pokémon sleeps beneath the Seafloor Cavern. Clear it three times.", setOf(PokemonType.WATER)),
        LegendQuest("sinnoh-dialga", 483, "Dialga", WorldLayer.SINNOH, "mt-coronet", 3, false, "Time itself is thin at the summit of Mt. Coronet. Clear it three times.", setOf(PokemonType.STEEL)),
        LegendQuest("sinnoh-palkia", 484, "Palkia", WorldLayer.SINNOH, "spear-pillar", 3, false, "Space folds around the Spear Pillar. Clear it three times.", setOf(PokemonType.WATER)),
        LegendQuest("sinnoh-giratina", 487, "Giratina", WorldLayer.SINNOH, "distortion-world", 1, true, "Sinnoh's Guardian waits in the Distortion World.", setOf(PokemonType.GHOST), treasurePercent = 3),
        LegendQuest("hoenn-rayquaza", 384, "Rayquaza", WorldLayer.HOENN, "sky-pillar", 1, true, "Hoenn's Guardian waits at the top of the Sky Pillar.", setOf(PokemonType.FLYING, PokemonType.DRAGON), findPermille = 5),
        LegendQuest("unova-reshiram", 643, "Reshiram", WorldLayer.UNOVA, "dragonspiral-tower", 3, false, "The tower's top burns white. Clear the Dragonspiral Tower three times.", setOf(PokemonType.FIRE)),
        LegendQuest("unova-zekrom", 644, "Zekrom", WorldLayer.UNOVA, "n-castle", 3, false, "A storm gathers in N's Castle. Clear it three times.", setOf(PokemonType.ELECTRIC)),
        LegendQuest("unova-kyurem", 646, "Kyurem", WorldLayer.UNOVA, "giant-chasm", 1, true, "Unova's Guardian waits in the Giant Chasm.", setOf(PokemonType.ICE), treasurePercent = 3),
        LegendQuest("kalos-xerneas", 716, "Xerneas", WorldLayer.KALOS, "victory-road-kalos", 3, false, "The tree of life glows past Victory Road. Clear it three times.", setOf(PokemonType.FAIRY)),
        LegendQuest("kalos-yveltal", 717, "Yveltal", WorldLayer.KALOS, "lysandre-labs", 3, false, "Life drains away beneath Lysandre Labs. Clear it three times.", setOf(PokemonType.DARK)),
        LegendQuest("kalos-zygarde", 718, "Zygarde", WorldLayer.KALOS, "terminus-cave", 1, true, "Kalos's Guardian keeps order in the Terminus Cave.", setOf(PokemonType.GROUND), treasurePercent = 3),
        LegendQuest("alola-solgaleo", 791, "Solgaleo", WorldLayer.ALOLA, "mount-lanakila", 3, false, "The sun rests on the summit of Mount Lanakila. Clear it three times.", setOf(PokemonType.STEEL)),
        LegendQuest("alola-lunala", 792, "Lunala", WorldLayer.ALOLA, "aether-paradise", 3, false, "The moon sings in the Aether Paradise. Clear it three times.", setOf(PokemonType.GHOST)),
        LegendQuest("alola-necrozma", 800, "Necrozma", WorldLayer.ALOLA, "ultra-megalopolis", 1, true, "Alola's Guardian waits in the Ultra Megalopolis.", setOf(PokemonType.PSYCHIC), treasurePercent = 3),
        LegendQuest("galar-zacian", 888, "Zacian", WorldLayer.GALAR, "route-10", 3, false, "The sword's old hero waits where the snow is deepest. Clear Route 10 three times.", setOf(PokemonType.FAIRY)),
        LegendQuest("galar-zamazenta", 889, "Zamazenta", WorldLayer.GALAR, "giants-seat", 3, false, "The shield's old hero waits at Giant's Seat. Clear it three times.", setOf(PokemonType.FIGHTING)),
        LegendQuest("galar-eternatus", 890, "Eternatus", WorldLayer.GALAR, "energy-plant", 1, true, "Galar's Guardian waits in the Energy Plant.", setOf(PokemonType.POISON), treasurePercent = 3),
        LegendQuest("paldea-koraidon", 1007, "Koraidon", WorldLayer.PALDEA, "socarrat-trail", 3, false, "An old partner runs the Socarrat Trail. Clear it three times.", setOf(PokemonType.DRAGON)),
        LegendQuest("paldea-miraidon", 1008, "Miraidon", WorldLayer.PALDEA, "zero-lab", 3, false, "A future partner hums inside the Zero Lab. Clear it three times.", setOf(PokemonType.ELECTRIC)),
        LegendQuest("paldea-terapagos", 1024, "Terapagos", WorldLayer.PALDEA, "area-zero", 1, true, "Paldea's Guardian waits at the bottom of Area Zero.", setOf(PokemonType.NORMAL), treasurePercent = 3),
        LegendQuest("hisui-arceus", 493, "Arceus", WorldLayer.HISUI, "temple-of-sinnoh", 1, true, "Hisui's Guardian, the Original One, waits in the Temple of Sinnoh.", setOf(PokemonType.NORMAL), treasurePercent = 3),
    )

    val coveredSpecies: Set<Int> by lazy { all.map { it.speciesId }.toSet() }

    fun of(layer: WorldLayer): List<LegendQuest> = all.filter { it.layer == layer }

    fun byId(id: String): LegendQuest? = all.firstOrNull { it.id == id }

    fun progress(data: WorldData, quest: LegendQuest): Int = minOf(data.clears[quest.dungeonId] ?: 0, quest.clears)

    fun isMet(data: WorldData, quest: LegendQuest): Boolean =
        progress(data, quest) >= quest.clears && (!quest.afterGuardian || data.layerDone(quest.layer))

    fun ready(data: WorldData): List<LegendQuest> = all.filter { it.id !in data.legends && isMet(data, it) }
}
