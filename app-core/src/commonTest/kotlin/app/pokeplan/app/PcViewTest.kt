package app.pokeplan.app

import app.pokeplan.core.dex.Region
import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.Hatching
import app.pokeplan.core.pokemon.OwnedPokemon
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PcViewTest {
    private val rng = Random(7)
    private val box: List<OwnedPokemon> = (1..40).map { i ->
        Hatching.hatch("p$i", 25, false, Region.KANTO, 6, nowMillis = i * 1000L, rng = rng)
            .copy(level = 1 + (i * 7) % 30, friendship = (i * 13) % 255, isShiny = i % 9 == 0, inParty = false)
    }
    private val starred = setOf("p3", "p9", "p20")
    private fun ids(view: PcView) = view.apply(box) { it in starred }.map { it.id }

    @Test
    fun noFilterKeepsEveryPokemon() {
        for (sort in PcSort.entries) assertEquals(box.map { it.id }.toSet(), ids(PcView(sort = sort)).toSet(), sort.name)
        assertTrue(!PcView().filtering)
    }

    @Test
    fun sortsAreOrdered() {
        val byLevel = PcView(sort = PcSort.LEVEL).apply(box) { false }.map { it.level }
        assertEquals(byLevel.sortedDescending(), byLevel)
        val byDex = PcView(sort = PcSort.DEX).apply(box) { false }.map { it.speciesId }
        assertEquals(byDex.sorted(), byDex)
        val byName = PcView(sort = PcSort.NAME).apply(box) { false }.map { it.nickname.lowercase() }
        assertEquals(byName.sorted(), byName)
        val newest = PcView().apply(box) { false }.map { it.caughtAtMillis }
        assertEquals(newest.sortedDescending(), newest)
        val ivs = PcView(sort = PcSort.IVS).apply(box) { false }.map { it.ivs.total }
        assertEquals(ivs.sortedDescending(), ivs)
    }

    @Test
    fun filtersCombine() {
        assertEquals(listOf("p9", "p3", "p20").toSet(), ids(PcView(starredOnly = true)).toSet())
        assertEquals(box.filter { it.isShiny }.map { it.id }.toSet(), ids(PcView(shinyOnly = true)).toSet())
        val type = box.first().form.types.first()
        val ofType = PcView(type = type).apply(box) { false }
        assertTrue(ofType.isNotEmpty() && ofType.all { type in it.form.types })
        assertEquals(box.count { type in it.form.types }, ofType.size)
        val name = box.first().species.name
        val found = PcView(search = "  ${name.uppercase()} ").apply(box) { false }
        assertTrue(found.isNotEmpty() && found.all { name.lowercase() in it.species.name.lowercase() || name.lowercase() in it.nickname.lowercase() })
        assertEquals(emptyList(), ids(PcView(shinyOnly = true, starredOnly = true, search = "zzzz")))
        assertTrue(PcView(type = PokemonType.FIRE).filtering)
    }

    @Test
    fun boxesHoldThirtyAndStayInRange() {
        val view = PcView()
        assertEquals(2, view.boxCount(box.size))
        assertEquals(1, view.boxCount(0))
        assertEquals(1, view.boxCount(30))
        assertEquals(2, view.boxCount(31))
        val sorted = view.apply(box) { false }
        assertEquals(30, view.page(sorted).size)
        assertEquals(10, view.copy(box = 1).page(sorted).size)
        assertEquals(sorted.drop(30).map { it.id }, view.copy(box = 5).page(sorted).map { it.id }, "past the end shows the last box")
        assertEquals(sorted.take(30).map { it.id }, view.copy(box = -2).page(sorted).map { it.id })
        assertTrue(view.page(emptyList()).isEmpty())
    }

    @Test
    fun showsOnlyThePcUntilEveryoneIsAsked() {
        val places = box.associate { p ->
            val n = p.id.drop(1).toInt()
            p.id to when (n % 5) { 0 -> PcPlace.BUDDY; 1 -> PcPlace.PARTY; 2 -> PcPlace.WORLD; else -> PcPlace.PC }
        }
        val placeOf = { id: String -> places.getValue(id) }
        val pcOnly = PcView().apply(box, placeOf) { false }
        assertEquals(places.filterValues { it == PcPlace.PC }.keys, pcOnly.map { it.id }.toSet())
        assertEquals(box.size, PcView(everyone = true).apply(box, placeOf) { false }.size)
        val world = PcView(everyone = true, where = PcPlace.WORLD).apply(box, placeOf) { false }
        assertEquals(places.filterValues { it == PcPlace.WORLD }.keys, world.map { it.id }.toSet())
        assertEquals(pcOnly.map { it.id }, PcView(where = PcPlace.WORLD).apply(box, placeOf) { false }.map { it.id }, "where only counts with everyone")
        assertTrue(PcView(everyone = true, where = PcPlace.BUDDY).filtering)
        assertTrue(!PcView(everyone = true).filtering)
        val cleared = PcView(everyone = true, where = PcPlace.GEAR, shinyOnly = true, search = "x", box = 3, sort = PcSort.LEVEL).cleared()
        assertEquals(PcView(everyone = true, sort = PcSort.LEVEL), cleared)
    }

    @Test
    fun placeGroupsFollowPlaces() {
        assertEquals(PcPlace.LEAD, PcPlace.of(Place.Party("plan"), isLead = true))
        assertEquals(PcPlace.PARTY, PcPlace.of(Place.Party("plan"), isLead = false))
        assertEquals(PcPlace.GUARDIAN, PcPlace.of(Place.Guardian("heal"), false))
        assertEquals(PcPlace.WORLD, PcPlace.of(Place.WorldTeam(1), false))
        assertEquals(PcPlace.GEAR, PcPlace.of(Place.GearHelper("CLOCK"), false))
        assertEquals(PcPlace.BUDDY, PcPlace.of(Place.Buddy, false))
        assertEquals(PcPlace.PC, PcPlace.of(Place.Pc, false))
    }
}
