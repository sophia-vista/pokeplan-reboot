package app.pokeplan.app.link

import app.pokeplan.app.testing.FakeClock
import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.core.reward.Reward
import app.pokeplan.core.reward.SafariRules
import app.pokeplan.link.LinkRules
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkSafariTest {

    private val utc = TimeZone.UTC
    private val start = LocalDateTime(2026, 11, 7, 18, 0).toInstant(utc).toEpochMilliseconds()

    private class Setup(val game: GameStore, val link: LinkStore, val clock: FakeClock)

    private fun setup(): Setup {
        val clock = FakeClock(start)
        val game = GameStore(MemoryStorage(), clock, utc, Random(7))
        game.start("Sophia", 25)
        return Setup(game, LinkStore(MemoryStorage(), clock, game, utc, rng = Random(3)), clock)
    }

    private fun LinkStore.people(n: Int) = (1..n).map { addPerson("Person $it").record!! }

    @Test
    fun aSafariIsSeededByTheOpaqueIdAlone() {
        val s = setup()
        val ana = s.link.addPerson("Ana").record!!
        val again = s.link.safariPool(ana.id)
        assertEquals(again, s.link.safariPool(ana.id))
        assertEquals(SafariRules.BASE_SLOTS, again.size)
        s.link.updatePerson(ana.copy(name = "Someone Else", organization = "Elsewhere"))
        assertEquals(again, s.link.safariPool(ana.id))
        assertFalse(LinkRules.safariRef(ana.id).contains(ana.id))
    }

    @Test
    fun morePeopleUnlockMoreSlotsWithoutReshufflingAndTheCountIsCapped() {
        val s = setup()
        val ana = s.link.addPerson("Ana").record!!
        val before = s.link.safariPool(ana.id)
        s.link.people(SafariRules.PEOPLE_PER_SLOT)
        val wider = s.link.safariPool(ana.id)
        assertEquals(SafariRules.BASE_SLOTS + 1, wider.size)
        assertEquals(before, wider.take(before.size))
        s.link.people(60)
        assertEquals(SafariRules.slots(SafariRules.PEOPLE_CAP), s.link.safariPool(ana.id).size)
        assertTrue(s.link.safariPool(ana.id).size <= SafariRules.MAX_SLOTS)
        assertEquals(SafariRules.PEOPLE_CAP, LinkRules.SAFARI_PEOPLE_CAP)
    }

    @Test
    fun theMeCardDoesNotWidenSafaris() {
        val s = setup()
        s.link.addPerson("Ana")
        val spread = LinkRules.safariSpread(s.link.data)
        s.link.createMe("Sophia")
        assertEquals(spread, LinkRules.safariSpread(s.link.data))
    }

    @Test
    fun aVisitCatchesOnePokemonOncePerPersonPerDayAndPaysNothingElse() {
        val s = setup()
        val ana = s.link.addPerson("Ana").record!!
        val coins = s.game.data.player.coins
        val owned = s.game.data.player.pokemon.size
        val out = assertNotNull(s.link.visitSafari(ana.id))
        assertEquals(1, out.filterIsInstance<Reward.Hatched>().size)
        assertEquals(owned + 1, s.game.data.player.pokemon.size)
        assertEquals(coins, s.game.data.player.coins)
        assertEquals(app.pokeplan.app.Origin.How.SAFARI, s.game.data.origins[out.filterIsInstance<Reward.Hatched>().single().pokemon.id]?.how)
        assertTrue(s.link.safariToday(ana.id).visited)
        assertNull(s.link.visitSafari(ana.id))
        s.clock.advanceDays(1)
        assertFalse(s.link.safariToday(ana.id).visited)
        assertNotNull(s.link.visitSafari(ana.id))
    }

    @Test
    fun aDayHasAtMostSafariPerDayCatchesAcrossEverySafari() {
        val s = setup()
        val people = s.link.people(GameStore.SAFARI_PER_DAY + 3)
        val caught = people.count { s.link.visitSafari(it.id) != null }
        assertEquals(GameStore.SAFARI_PER_DAY, caught)
        assertEquals(0, s.link.safariToday(people.last().id).left)
        s.clock.advanceDays(1)
        assertTrue(s.link.safariToday(people.last().id).left > 0)
    }

    @Test
    fun addingPeopleOrTurningTheSwitchOffPaysNothing() {
        val s = setup()
        val before = s.game.data.player.pokemon.size
        val ana = s.link.addPerson("Ana").record!!
        s.link.people(5)
        assertEquals(before, s.game.data.player.pokemon.size)
        s.link.setSafari(false)
        assertNull(s.link.visitSafari(ana.id))
        s.link.setSafari(true); s.link.setPostcardTrail(false)
        assertNull(s.link.visitSafari(ana.id))
        assertEquals(before, s.game.data.player.pokemon.size)
        assertEquals("Ana", s.link.data.people.first { it.id == ana.id }.name)
    }

    @Test
    fun theLogSaysOnlyFriendSafari() {
        val s = setup()
        val ana = s.link.addPerson("Ana Reyes").record!!
        s.link.visitSafari(ana.id)
        val entry = s.game.data.rewardLog.last()
        assertEquals(GameStore.SAFARI_SOURCE, entry.source)
        assertFalse(entry.toString().contains("Ana"))
        assertFalse(s.game.data.ledgerNotes.toString().contains("Ana"))
    }

    @Test
    fun aVisitNeverReplaysAcrossAReload() {
        val s = setup()
        val ana = s.link.addPerson("Ana").record!!
        assertNotNull(s.link.visitSafari(ana.id))
        s.game.reload()
        assertNull(s.link.visitSafari(ana.id))
    }
}
