package app.pokeplan.ui.launcher

import app.pokeplan.core.dex.PokemonType
import app.pokeplan.ui.StatusKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TownQuirksTest {
    @Test
    fun theSpeciesTheOwnerNamedHaveTheirQuirk() {
        listOf("snorlax", "slakoth", "slaking", "abra", "komala", "munna", "musharna", "morelull").forEach { assertEquals(Quirk.ASLEEP, TownQuirks.of(it), it) }
        listOf("metapod", "kakuna", "cascoon", "silcoon", "sudowoodo", "ferroseed", "ferrothorn", "sandygast", "palossand").forEach { assertEquals(Quirk.ROOTED, TownQuirks.of(it), it) }
        listOf("mew", "ditto", "zorua", "zoroark").forEach { assertEquals(Quirk.DISGUISED, TownQuirks.of(it), it) }
        listOf("shuckle", "slowpoke", "slowbro", "slowking", "torkoal", "carbink").forEach { assertEquals(Quirk.SLOW, TownQuirks.of(it), it) }
        listOf("vigoroth", "ninjask", "varoom", "revavroom", "zebstrika", "doduo", "dodrio", "cyclizar", "cinderace", "arcanine", "talonflame", "pikachu", "pachirisu", "emolga", "dedenne", "togedemaru", "morpeko").forEach { assertEquals(Quirk.FAST, TownQuirks.of(it), it) }
        assertEquals(Quirk.ERRATIC, TownQuirks.of("spinda"))
        listOf("ralts", "wimpod").forEach { assertEquals(Quirk.TIMID, TownQuirks.of(it), it) }
        assertNull(TownQuirks.of("eevee"))
        assertNull(TownQuirks.of(null))
    }

    @Test
    fun slowAndFastWalkAtTheirOwnPaceAndTheOthersAtTheUsualOne() {
        assertEquals(0.5f, TownQuirks.pace(Quirk.SLOW))
        assertEquals(1.9f, TownQuirks.pace(Quirk.FAST))
        assertEquals(1f, TownQuirks.pace(null))
        assertEquals(1f, TownQuirks.pace(Quirk.TIMID))
    }

    @Test
    fun onlyTheStationaryQuirksAreSetDownInARoom() {
        assertEquals(true, TownQuirks.stays(Quirk.ROOTED) && TownQuirks.stays(Quirk.ASLEEP))
        assertEquals(false, TownQuirks.stays(Quirk.FAST) || TownQuirks.stays(null))
    }

    @Test
    fun anErraticOneStumblesMoreThanAConfusedOne() {
        assertEquals(30, TownQuirks.stumble(Quirk.ERRATIC, confused = false))
        assertEquals(8, TownQuirks.stumble(null, confused = true))
        assertEquals(0, TownQuirks.stumble(null, confused = false))
    }

    @Test
    fun aGuardianShowsTheIconOfItsFirstType() {
        assertEquals(StatusKind.FLAME, TownQuirks.aura(listOf(PokemonType.FIRE, PokemonType.FLYING)))
        assertEquals(StatusKind.ICE, TownQuirks.aura(listOf(PokemonType.ICE)))
        assertEquals(StatusKind.TWINKLE, TownQuirks.aura(listOf(PokemonType.PSYCHIC)))
        assertEquals(StatusKind.TWINKLE, TownQuirks.aura(emptyList()))
    }
}
