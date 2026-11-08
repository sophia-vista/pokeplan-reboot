package app.pokeplan.world

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.FormKind
import app.pokeplan.core.dex.MoveCategory
import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.HeldItems
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.OwnedPokemon

/**
 * What a Pokémon's moves, ability and held item add to a team's power in a dungeon (WORLD.md 3.2, `POKEMON_ECOSYSTEM.md` 8.3): the simplified,
 * deterministic battle identity of an idle expedition. Each part is a plain percent of the Pokémon's level, shown to the player by name
 * ([Breakdown.parts]), and **nothing is counted that is not listed here**: a move, ability or item that is not in these rules adds nothing and the
 * screens do not say it does.
 *
 * - **Moves** ([OwnedPokemon.activeMoves], up to four): each damaging move of a type the dungeon favours adds [FAVORED_MOVE_PERCENT] (at most [MAX_FAVORED_MOVES]
 *   of them); one damaging move of power [STRONG_POWER] or more adds [STRONG_MOVE_PERCENT]; any status move adds [UTILITY_PERCENT] once. So a varied set that
 *   fits the place beats four copies of one type, and no species, move or type wins everywhere.
 * - **Ability**: one tied to a type the dungeon favours adds [ABILITY_TYPE_PERCENT]; a general fighting ability adds [ABILITY_FLAT_PERCENT] anywhere.
 *   Abilities that help World's skills (berries, finds, Poké) are another list and never count here.
 * - **Held item** ([HeldItems.battle]): [HeldItems.battle] percent, or [HeldItems.MEGA_STONE_BATTLE_PERCENT] for a Mega Stone held by the Pokémon it is for.
 *
 * Together they add at most [MAX_PERCENT] of the level, next to the 20% for a favoured type, so level stays the main thing. The numbers are *(tune)*;
 * [VERSION] changes with them.
 */
object BattleIdentity {
    /** The version of this rule; bump it with any number below. */
    const val VERSION = 1

    const val FAVORED_MOVE_PERCENT = 6
    const val MAX_FAVORED_MOVES = 2
    const val STRONG_MOVE_PERCENT = 4
    const val STRONG_POWER = 80
    const val UTILITY_PERCENT = 2
    const val ABILITY_TYPE_PERCENT = 6
    const val ABILITY_FLAT_PERCENT = 3
    const val MAX_PERCENT = 25

    /** The extra power for a favoured type: 20% of the level. */
    const val FAVORED_TYPE_FACTOR = 0.2

    /** The abilities World's skills already use (berries, finds, Poké): they have that one job and never count here. */
    private val skillAbilities: Set<String> = AbilityPerks.bySkill.values.flatten().toSet()

    /** Abilities that fight for one type: they count in a dungeon that favours it. *(tune)* */
    val typeAbilities: Map<String, PokemonType> = mapOf(
        "Blaze" to PokemonType.FIRE, "Flash Fire" to PokemonType.FIRE, "Magma Armor" to PokemonType.FIRE, "Drought" to PokemonType.FIRE, "White Smoke" to PokemonType.FIRE,
        "Torrent" to PokemonType.WATER, "Water Absorb" to PokemonType.WATER, "Storm Drain" to PokemonType.WATER, "Drizzle" to PokemonType.WATER, "Rain Dish" to PokemonType.WATER,
        "Overgrow" to PokemonType.GRASS, "Sap Sipper" to PokemonType.GRASS, "Leaf Guard" to PokemonType.GRASS, "Grassy Surge" to PokemonType.GRASS,
        "Swarm" to PokemonType.BUG, "Shield Dust" to PokemonType.BUG, "Tinted Lens" to PokemonType.BUG,
        "Static" to PokemonType.ELECTRIC, "Volt Absorb" to PokemonType.ELECTRIC, "Lightning Rod" to PokemonType.ELECTRIC, "Motor Drive" to PokemonType.ELECTRIC, "Electric Surge" to PokemonType.ELECTRIC,
        "Levitate" to PokemonType.GROUND, "Arena Trap" to PokemonType.GROUND, "Sand Veil" to PokemonType.GROUND, "Sand Stream" to PokemonType.ROCK, "Sand Force" to PokemonType.ROCK, "Rock Head" to PokemonType.ROCK, "Solid Rock" to PokemonType.ROCK,
        "Snow Cloak" to PokemonType.ICE, "Snow Warning" to PokemonType.ICE, "Ice Body" to PokemonType.ICE, "Thick Fat" to PokemonType.ICE, "Refrigerate" to PokemonType.ICE,
        "Iron Fist" to PokemonType.FIGHTING, "Inner Focus" to PokemonType.FIGHTING, "Justified" to PokemonType.FIGHTING, "Steadfast" to PokemonType.FIGHTING,
        "Poison Point" to PokemonType.POISON, "Poison Touch" to PokemonType.POISON, "Corrosion" to PokemonType.POISON, "Stench" to PokemonType.POISON,
        "Shadow Tag" to PokemonType.GHOST, "Cursed Body" to PokemonType.GHOST, "Aftermath" to PokemonType.GHOST,
        "Magnet Pull" to PokemonType.STEEL, "Steelworker" to PokemonType.STEEL, "Clear Body" to PokemonType.STEEL, "Heavy Metal" to PokemonType.STEEL,
        "Psychic Surge" to PokemonType.PSYCHIC, "Synchronize" to PokemonType.PSYCHIC, "Magic Bounce" to PokemonType.PSYCHIC, "Telepathy" to PokemonType.PSYCHIC,
        "Multiscale" to PokemonType.DRAGON, "Rough Skin" to PokemonType.DRAGON, "Dragon's Maw" to PokemonType.DRAGON,
        "Prankster" to PokemonType.DARK, "Dark Aura" to PokemonType.DARK, "Rattled" to PokemonType.DARK,
        "Fairy Aura" to PokemonType.FAIRY, "Pixilate" to PokemonType.FAIRY, "Cute Charm" to PokemonType.FAIRY, "Misty Surge" to PokemonType.FAIRY,
        "Keen Eye" to PokemonType.FLYING, "Big Pecks" to PokemonType.FLYING, "Gale Wings" to PokemonType.FLYING, "Aerilate" to PokemonType.FLYING,
        "Scrappy" to PokemonType.NORMAL, "Normalize" to PokemonType.NORMAL, "Own Tempo" to PokemonType.NORMAL,
    ).filterKeys { it !in skillAbilities }

    /** General fighting abilities: they count in every dungeon. *(tune)* */
    val flatAbilities: Set<String> = setOf(
        "Huge Power", "Pure Power", "Sturdy", "Technician", "Adaptability", "Sniper", "Battle Armor", "Shell Armor", "Marvel Scale", "Filter",
        "Tough Claws", "Strong Jaw", "Sheer Force", "Moxie", "Beast Boost", "Defiant", "Competitive", "Download", "Regenerator", "Mold Breaker", "Unaware",
        "Intimidate", "Pressure", "Guts",
    ).filterTo(linkedSetOf()) { it !in skillAbilities }

    /** One named thing that adds [percent] of the level. */
    data class Part(val label: String, val percent: Int)

    /** What [member] adds in a dungeon: its [level], whether its type is favoured, and the named parts of its battle identity. */
    data class Breakdown(val member: OwnedPokemon, val typeFavored: Boolean, val parts: List<Part>) {
        val level: Int get() = member.level

        /** The share of the level the parts add, at most [MAX_PERCENT]. */
        val percent: Int get() = parts.sumOf { it.percent }.coerceAtMost(MAX_PERCENT)

        /** What the level alone is worth, with the 20% for a favoured type. */
        val levelPower: Double get() = level * (if (typeFavored) 1.0 + FAVORED_TYPE_FACTOR else 1.0)

        /** What moves, ability and item add to the member's power. */
        val identityPower: Double get() = level * percent / 100.0

        /** The member's whole contribution to the team's power. */
        val power: Double get() = levelPower + identityPower
    }

    fun breakdown(p: OwnedPokemon, dungeon: DungeonDef): Breakdown =
        Breakdown(p, p.form.types.any { it in dungeon.favored }, parts(p, dungeon))

    /** The parts of [p]'s battle identity in [dungeon], by name, each with its percent of the level. Moves first, then the ability and the item. */
    fun parts(p: OwnedPokemon, dungeon: DungeonDef): List<Part> = buildList {
        val moves = p.activeMoves
        val damaging = moves.filter { it.category != MoveCategory.STATUS && (it.power ?: 0) > 0 }
        damaging.filter { it.type in dungeon.favored }.take(MAX_FAVORED_MOVES).forEach { add(Part("${it.name} (${it.type.name.lowercase()}, favoured here)", FAVORED_MOVE_PERCENT)) }
        damaging.filter { (it.power ?: 0) >= STRONG_POWER }.maxByOrNull { it.power ?: 0 }?.let { add(Part("${it.name} (power ${it.power})", STRONG_MOVE_PERCENT)) }
        moves.firstOrNull { it.category == MoveCategory.STATUS }?.let { add(Part("${it.name} (support)", UTILITY_PERCENT)) }
        val type = typeAbilities[p.ability]
        when {
            type != null && type in dungeon.favored -> add(Part("${p.ability} (${type.name.lowercase()}, favoured here)", ABILITY_TYPE_PERCENT))
            p.ability in flatAbilities -> add(Part(p.ability, ABILITY_FLAT_PERCENT))
        }
        val item = p.heldItem
        if (item != null) {
            val percent = HeldItems.battle[item] ?: if (megaStoneFits(p, item)) HeldItems.MEGA_STONE_BATTLE_PERCENT else null
            if (percent != null) add(Part(Items.name(item), percent))
        }
    }

    private fun megaStoneFits(p: OwnedPokemon, item: String): Boolean =
        item.startsWith("mega-stone-") && Dex.forms.any { it.kind == FormKind.MEGA && it.speciesId == p.speciesId && Items.megaStone(it.identifier) == item }
}
