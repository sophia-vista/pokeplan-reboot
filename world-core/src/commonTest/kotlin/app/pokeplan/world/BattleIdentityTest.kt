package app.pokeplan.world

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchOption
import app.pokeplan.core.dex.MoveCategory
import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.Hatching
import app.pokeplan.core.pokemon.Moveset
import app.pokeplan.core.pokemon.OwnedPokemon
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BattleIdentityTest {
    private fun mon(species: Int, level: Int) =
        Hatching.create("m$species", HatchOption(species, Dex.defaultForm(species).identifier), OwnedPokemon.PARTY_SIZE, 0L, 60, Random(1)).copy(level = level)

    private val woods = Dungeons.byId("tiny-woods")!!
    private val steel = Dungeons.byId("mt-steel")!!

    @Test
    fun theContributionIsTheLevelTheTypeAndTheNamedParts() {
        for (d in listOf(woods, steel)) for (s in listOf(1, 4, 7, 25, 74, 129)) {
            val p = mon(s, 40)
            val b = BattleIdentity.breakdown(p, d)
            assertEquals(WorldSim.contribution(p, d), b.power, 1e-9)
            assertEquals(b.levelPower + b.identityPower, b.power, 1e-9)
            assertEquals(b.parts.sumOf { it.percent }.coerceAtMost(BattleIdentity.MAX_PERCENT), b.percent)
            assertTrue(b.percent in 0..BattleIdentity.MAX_PERCENT)
        }
    }

    @Test
    fun aMoveOfAFavouredTypeAddsMoreThanAMoveOfAnotherType() {
        val p = mon(25, 50)
        val all = Moveset.learnable(p)
        val electric = all.first { it.type == PokemonType.ELECTRIC && it.category != MoveCategory.STATUS && (it.power ?: 0) in 1 until BattleIdentity.STRONG_POWER }
        val other = all.first { it.type == PokemonType.NORMAL && it.category != MoveCategory.STATUS && (it.power ?: 0) in 1 until BattleIdentity.STRONG_POWER }
        val cave = Dungeons.byId("thunderwave-cave")!!
        assertTrue(PokemonType.ELECTRIC in cave.favored)
        val withElectric = BattleIdentity.parts(p.withMoves(listOf(electric.identifier)), cave).filter { "(electric, favoured here)" in it.label && it.label.startsWith(electric.name) }
        assertEquals(listOf(BattleIdentity.FAVORED_MOVE_PERCENT), withElectric.map { it.percent })
        assertTrue(BattleIdentity.parts(p.withMoves(listOf(other.identifier)), cave).none { it.label.startsWith(other.name) }, "a move of a type the place does not favour adds nothing unless it is strong")
    }

    @Test
    fun noMoreThanTwoFavouredMovesCountAndStrongAndSupportCountOnce() {
        val p = mon(6, 80)
        val fire = Moveset.learnable(p).filter { it.type == PokemonType.FIRE && it.category != MoveCategory.STATUS && (it.power ?: 0) > 0 }.take(4)
        val parts = BattleIdentity.parts(p.withMoves(fire.map { it.identifier }), Dungeons.byId("mt-ember")!!.takeIf { PokemonType.FIRE in it.favored } ?: Dungeons.byId("burned-tower")!!)
        assertTrue(parts.count { "favoured here" in it.label && it.percent == BattleIdentity.FAVORED_MOVE_PERCENT && fire.any { m -> it.label.startsWith(m.name) } } <= BattleIdentity.MAX_FAVORED_MOVES)
    }

    @Test
    fun anAbilityCountsOnlyWhereItFitsAndSkillAbilitiesNeverCount() {
        val p = mon(7, 30).copy(ability = "Torrent")
        val waterPlace = Dungeons.all.first { PokemonType.WATER in it.favored }
        val dryPlace = Dungeons.all.first { PokemonType.WATER !in it.favored }
        assertTrue(BattleIdentity.parts(p, waterPlace).any { it.label.startsWith("Torrent") && it.percent == BattleIdentity.ABILITY_TYPE_PERCENT })
        assertTrue(BattleIdentity.parts(p, dryPlace).none { it.label.startsWith("Torrent") })
        assertTrue(BattleIdentity.parts(p.copy(ability = "Huge Power"), dryPlace).any { it.label == "Huge Power" && it.percent == BattleIdentity.ABILITY_FLAT_PERCENT })
        for (skill in AbilityPerks.bySkill.values.flatten()) assertTrue(BattleIdentity.parts(p.copy(ability = skill), waterPlace).none { it.label.startsWith(skill) }, skill)
    }

    @Test
    fun aHeldItemCountsOnlyWhenItWouldHelpInAFight() {
        val p = mon(25, 30)
        assertEquals(4, BattleIdentity.parts(p.withHeldItem("leftovers"), woods).first { it.label == "Leftovers" }.percent)
        assertTrue(BattleIdentity.parts(p.withHeldItem("amulet-coin"), woods).none { it.label.contains("Amulet") })
        val ownStone = Dex.forms.first { it.kind == app.pokeplan.core.dex.FormKind.MEGA }
        val holder = mon(ownStone.speciesId, 30).withHeldItem(app.pokeplan.core.pokemon.Items.megaStone(ownStone.identifier))
        assertTrue(BattleIdentity.parts(holder, woods).any { it.percent == app.pokeplan.core.pokemon.HeldItems.MEGA_STONE_BATTLE_PERCENT })
        val wrong = mon(25, 30).withHeldItem(app.pokeplan.core.pokemon.Items.megaStone(ownStone.identifier))
        assertTrue(BattleIdentity.parts(wrong, woods).none { it.percent == app.pokeplan.core.pokemon.HeldItems.MEGA_STONE_BATTLE_PERCENT })
    }

    @Test
    fun theWholeIdentityIsCappedAndLevelStaysTheMainThing() {
        for (d in Dungeons.all) for (s in listOf(3, 6, 9, 94, 149, 248, 373)) {
            val p = mon(s, 60)
            val b = BattleIdentity.breakdown(p, d)
            assertTrue(b.identityPower <= 60 * BattleIdentity.MAX_PERCENT / 100.0 + 1e-9, "${d.id} $s")
        }
    }

    @Test
    fun theSpeedExplanationCountsTheIdentityToo() {
        val team = listOf(mon(25, 40).withHeldItem("leftovers"), mon(7, 30))
        val e = WorldSim.explainSpeed(woods, team, WorldData())
        assertEquals(e.levels + e.favoredBonus + e.identityBonus, e.power, 1e-9)
        assertTrue(e.identityLines.any { "Leftovers +4%" in it })
    }
}
