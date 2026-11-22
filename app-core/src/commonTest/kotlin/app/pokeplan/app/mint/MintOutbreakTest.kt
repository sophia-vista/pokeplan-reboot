package app.pokeplan.app.mint

import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.reward.Reward
import app.pokeplan.core.reward.RewardEngine
import app.pokeplan.mint.AccountType
import app.pokeplan.mint.TxType
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MintOutbreakTest {
    private class FakeClock(var millis: Long) : Clock {
        override fun nowMillis() = millis
        fun advanceDays(n: Int) { millis += n * 86_400_000L }
    }

    private val utc = TimeZone.UTC
    private val start = LocalDateTime(2026, 11, 7, 18, 0).toInstant(utc).toEpochMilliseconds()

    private class Setup(val game: GameStore, val mint: MintStore, val clock: FakeClock)

    private fun setup(): Setup {
        val clock = FakeClock(start)
        val game = GameStore(MemoryStorage(), clock, utc, Random(7)).also { it.start("Sophia", 25) }
        return Setup(game, MintStore(MemoryStorage(), clock, game, utc, rng = Random(3)), clock)
    }

    private fun Setup.account(): String {
        val a = assertNotNull(mint.addAccount("Savings", AccountType.BANK, "PHP", 100_000).record).id
        assertNotNull(mint.record(TxType.EXPENSE, 2_500, a, atMillis = clock.millis - 86_400_000L).record)
        return a
    }

    @Test
    fun aReconciledAccountOpensAnOutbreakOfThreeDifferentPokemonAndLogsOpenNone() {
        val s = setup()
        val a = s.account()
        assertTrue(s.game.data.outbreaks.isEmpty(), "logging a record opens no outbreak")
        assertNotNull(s.mint.reconcile(a, 97_500).record)
        val outbreak = s.game.data.outbreaks.single()
        assertEquals(RewardEngine.OUTBREAK_OPTIONS, outbreak.options.map { it.speciesId }.distinct().size)
        assertEquals(RewardEngine.MINT_OUTBREAK_ROLLS.getValue(app.pokeplan.core.reward.MintKind.RECONCILED), outbreak.shinyRolls)
    }

    @Test
    fun pickingOneCatchesItDirectlyIntoMintsPartyAndUsesTheOutbreakUp() {
        val s = setup()
        val a = s.account()
        s.mint.reconcile(a, 97_500)
        val outbreak = s.game.data.outbreaks.single()
        val pick = outbreak.options[1].speciesId
        val before = s.game.data.player.partyOf(PartyIds.MINT).size
        val rewards = assertNotNull(s.game.claimOutbreak(outbreak.id, pick))
        val caught = rewards.filterIsInstance<Reward.Hatched>().single().pokemon
        assertEquals(pick, caught.speciesId)
        assertEquals(PartyIds.MINT, caught.partyId)
        assertTrue(s.game.data.player.partyOf(PartyIds.MINT).size >= before)
        assertTrue(s.game.data.outbreaks.isEmpty())
        assertNull(s.game.claimOutbreak(outbreak.id, pick), "an outbreak is used once")
    }

    @Test
    fun anAskForASpeciesTheOutbreakDoesNotOfferCatchesNothing() {
        val s = setup()
        val a = s.account()
        s.mint.reconcile(a, 97_500)
        val outbreak = s.game.data.outbreaks.single()
        val other = (1..200).first { id -> outbreak.options.none { it.speciesId == id } }
        assertNull(s.game.claimOutbreak(outbreak.id, other))
        assertEquals(1, s.game.data.outbreaks.size)
    }

    @Test
    fun eachWeekInARowWithAReviewAddsAShinyRoll() {
        val s = setup()
        s.account()
        val rolls = mutableListOf<Int>()
        repeat(3) {
            assertTrue(assertNotNull(s.mint.finishReview().record).paid)
            val outbreak = s.game.data.outbreaks.last()
            rolls += outbreak.shinyRolls
            s.game.claimOutbreak(outbreak.id, outbreak.options.first().speciesId)
            s.clock.advanceDays(7)
        }
        assertEquals(listOf(3, 4, 5), rolls)
    }
}
