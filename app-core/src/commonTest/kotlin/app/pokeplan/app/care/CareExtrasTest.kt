package app.pokeplan.app.care

import app.pokeplan.care.CareAction
import app.pokeplan.care.CarePlay
import app.pokeplan.care.Friend
import app.pokeplan.care.PlayActivity
import app.pokeplan.care.PlayReaction
import app.pokeplan.care.Reunions
import app.pokeplan.core.pokemon.Stat
import app.pokeplan.core.reward.RewardEngine
import app.pokeplan.core.reward.VisitorInheritance
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage

class CareExtrasTest {
    private class FakeClock(var millis: Long) : Clock {
        override fun nowMillis() = millis
        fun advanceDays(n: Int) { millis += n * 86_400_000L }
    }

    private val utc = TimeZone.UTC
    private val monday = LocalDateTime(2026, 10, 19, 9, 0).toInstant(utc).toEpochMilliseconds()

    private class Setup(val game: GameStore, val care: CareStore, val clock: FakeClock)

    private fun setup(): Setup {
        val clock = FakeClock(monday)
        val game = GameStore(MemoryStorage(), clock, utc, Random(7)).also { it.start("Sophia", 25) }
        return Setup(game, CareStore(MemoryStorage(), clock, game, utc, rng = Random(3)), clock)
    }

    private fun befriendOne(s: Setup): app.pokeplan.care.BefriendResult {
        val visitor = s.care.visitors().first()
        repeat(8) {
            s.care.startVisit(visitor.speciesId)
            for (kind in listOf(visitor.favorite) + CareAction.entries.filter { it != visitor.favorite }) {
                val step = s.care.visitAct(kind) ?: break
                step.result?.let { if (it.friend) return it }
            }
            s.care.leaveVisit()?.let { if (it.friend) return it }
            s.clock.millis += app.pokeplan.care.Visitors.REST_MILLIS + 1
        }
        error("the visitor did not become a friend")
    }

    @Test
    fun aVisitorsTemperamentIsStableAndWhatAnEggTakesIsLightAndRepeatable() {
        val id = "v123-9-0"
        assertEquals(VisitorInheritance.natureOf(id), VisitorInheritance.natureOf(id))
        assertEquals(VisitorInheritance.plan(id, "egg1"), VisitorInheritance.plan(id, "egg1"), "the same egg, the same gift")
        assertEquals(VisitorInheritance.Plan(null, null, null), VisitorInheritance.plan("", "egg1"), "a friend of an old save passes nothing on")
        val plans = (1..400).map { VisitorInheritance.plan(id, "egg$it") }
        val natures = plans.count { it.nature != null }
        assertTrue(natures in 100..240, "about ${VisitorInheritance.NATURE_PERCENT}% take the nature: $natures of 400")
        assertTrue(plans.count { !it.any } > 40, "many take nothing")
        assertTrue(plans.count { it.rareStat != null } in 1..60, "the rare trait is rare")
        assertTrue(plans.all { it.stat == null || it.stat == VisitorInheritance.strongStatOf(id) })
    }

    @Test
    fun inheritingRaisesAnIvToItsFloorAndNeverTouchesShinyOrSpecies() {
        val s = setup()
        val base = s.game.data.player.pokemon.first().copy(isShiny = false)
        val weak = base.copy(ivs = base.ivs.with(Stat.ATTACK, 3))
        val out = VisitorInheritance.apply(weak, VisitorInheritance.Plan(null, Stat.ATTACK, null))
        assertEquals(VisitorInheritance.STAT_FLOOR, out.ivs[Stat.ATTACK])
        assertEquals(weak.speciesId, out.speciesId); assertEquals(weak.isShiny, out.isShiny); assertEquals(weak.ability, out.ability)
        val strong = weak.copy(ivs = weak.ivs.with(Stat.ATTACK, 31))
        assertEquals(31, VisitorInheritance.apply(strong, VisitorInheritance.Plan(null, Stat.ATTACK, null)).ivs[Stat.ATTACK], "a floor, never lowers")
    }

    @Test
    fun aFriendKeepsAnOriginRecordOfTheVisitorAndWhatItPassedOn() {
        val s = setup()
        val result = befriendOne(s)
        assertTrue(result.friend)
        val hatched = result.rewards.filterIsInstance<app.pokeplan.core.reward.Reward.Hatched>().first().pokemon
        val origin = assertNotNull(s.game.data.origins[hatched.id])
        val record = assertNotNull(origin.visitor, "the egg and hatchling keep a small origin record")
        assertEquals(result.visitor.speciesId, record.speciesId)
        assertEquals(result.visitor.id, record.visitorId)
        assertTrue(record.note.isNotBlank())
        assertEquals(VisitorInheritance.plan(result.visitor.id, hatched.id).labels().takeIf { !result.joined } ?: emptyList(), record.inherited)
        assertEquals(listOf(result.visitor.id), s.care.data.friends.map { it.visitorId })
    }

    @Test
    fun aReunionComesBackToSayHelloTakesNoMeadowPlaceAndPaysNothing() {
        val s = setup()
        assertNull(s.care.reunion(), "no friends yet, no reunion")
        val result = befriendOne(s)
        assertTrue(result.friend)
        val coinsBefore = s.game.data.player.coins
        val inventoryBefore = s.game.data.player.inventory
        val pokemonBefore = s.game.data.player.pokemon.size
        var reunion: app.pokeplan.care.Reunion? = null
        var days = 0
        while (reunion == null && days < 60) { s.clock.advanceDays(1); days++; reunion = s.care.reunion() }
        val back = assertNotNull(reunion, "a friend comes back in time")
        assertEquals(result.visitor.id, back.friend.visitorId)
        assertTrue(s.care.meadow().none { it.speciesId == result.visitor.speciesId }, "a reunion is not a regular visitor")
        assertTrue(s.care.visitors().size <= app.pokeplan.care.Visitors.MAX_VISITORS + app.pokeplan.care.Visitors.MAX_QUEUE)
        s.clock.advanceDays(10)
        assertEquals(back, s.care.reunion())
        val line = assertNotNull(s.care.greetReunion())
        assertTrue(line.contains("remember", ignoreCase = true))
        assertNull(s.care.data.reunion)
        assertEquals(pokemonBefore, s.game.data.player.pokemon.size)
        assertEquals(coinsBefore, s.game.data.player.coins)
        assertEquals(inventoryBefore, s.game.data.player.inventory)
    }

    @Test
    fun aReunionRollIsTheSameForTheSameDeviceAndDayAndNeverStacks() {
        val friends = listOf(Friend(25, "pikachu", "v1", LocalDate(2026, 10, 1)), Friend(1, "bulbasaur", "v2", LocalDate(2026, 10, 2)))
        val day = LocalDate(2026, 11, 1)
        val a = Reunions.roll(5, day, friends, null, emptyMap())
        assertEquals(a, Reunions.roll(5, day, friends, null, emptyMap()))
        val waiting = (0..200).firstNotNullOf { Reunions.roll(it, day, friends, null, emptyMap()) }
        assertNull(Reunions.roll(1, day, friends, waiting, emptyMap()), "one at a time")
        assertNull(Reunions.roll(1, day, emptyList(), null, emptyMap()))
        val seen = friends.associate { it.visitorId to day }
        assertTrue((0..200).all { Reunions.roll(it, day, friends, null, seen) == null }, "a friend that just came is not back at once")
    }

    @Test
    fun everyPokemonLikesTwoWaysAndTheyAreDifferentAndStable() {
        val (p, s) = CarePlay.preferences("abc")
        assertNotEquals(p, s)
        assertEquals(p to s, CarePlay.preferences("abc"))
        assertTrue((1..50).map { CarePlay.preferences("id$it").first }.toSet().size > 1)
        val primaryActivity = PlayActivity.entries.first { it.action == p }
        assertEquals(PlayReaction.DELIGHTED, CarePlay.reaction("abc", primaryActivity))
    }

    @Test
    fun playingIsFreeToRepeatPaysOncePerPokemonPerDayAndDiscoversOnlyWhatItShows() {
        val s = setup()
        val id = s.game.data.player.buddyId!!
        assertTrue(s.care.discoveredLikes(id).isEmpty(), "preferences begin undiscovered")
        val (primary, secondary) = CarePlay.preferences(id)
        val favourite = PlayActivity.entries.first { it.action == primary }
        val friendshipBefore = s.game.data.player.buddy!!.friendship
        val eggMeterBefore = s.game.careEggMeter()
        val (reaction, paid) = s.care.playWith(id, favourite)
        assertEquals(PlayReaction.DELIGHTED, reaction)
        assertNotNull(paid, "the first time with a Pokémon today pays")
        assertTrue(s.game.data.player.buddy!!.friendship > friendshipBefore, "the Pokémon played with takes the friendship")
        assertEquals(setOf(primary), s.care.discoveredLikes(id))
        assertEquals(eggMeterBefore, s.game.careEggMeter(), "but fills no point of Care's Egg meter (rules version 29)")
        val coins = s.game.data.player.coins
        repeat(20) { assertNull(s.care.playWith(id, favourite).second) }
        assertEquals(coins, s.game.data.player.coins)
        val plain = PlayActivity.entries.first { it.action != primary && it.action != secondary }
        assertEquals(PlayReaction.PLEASED, s.care.playWith(id, plain).first)
        assertFalse(plain.action in s.care.discoveredLikes(id))
        s.clock.advanceDays(1)
        assertNotNull(s.care.playWith(id, favourite).second)
        assertEquals(0, RewardEngine.CARE_EGG_POINTS.getValue(app.pokeplan.core.reward.CareKind.PLAY))
    }

    @Test
    fun suggestionsNeverExpireAndSurpriseAlwaysGivesAnActivity() {
        val s = setup()
        val id = s.game.data.player.buddyId!!
        val a = s.care.suggestion(id)
        assertEquals(a, s.care.suggestion(id))
        assertTrue(s.care.surprise(id) in PlayActivity.entries)
        val (primary, _) = CarePlay.preferences(id)
        s.care.playWith(id, PlayActivity.entries.first { it.action == primary })
        assertNotEquals(primary, s.care.suggestion(id).action.takeIf { it == primary })
    }
}
