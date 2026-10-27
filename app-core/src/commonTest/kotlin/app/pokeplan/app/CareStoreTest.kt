package app.pokeplan.app

import app.pokeplan.care.CareRules
import app.pokeplan.care.Emotion
import app.pokeplan.core.reward.Reward
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CareStoreTest {
    private class FakeClock(var millis: Long) : Clock {
        override fun nowMillis() = millis
        fun advanceDays(n: Int) { millis += n * 86_400_000L }
    }

    private val utc = TimeZone.UTC
    private val monday = LocalDateTime(2026, 10, 19, 9, 0).toInstant(utc).toEpochMilliseconds()

    private class Setup(val game: GameStore, val care: CareStore, val clock: FakeClock, val storage: MemoryStorage)

    private fun setup(started: Boolean = true): Setup {
        val clock = FakeClock(monday)
        val game = GameStore(MemoryStorage(), clock, utc, Random(7))
        if (started) game.start("Sophia", 25)
        val storage = MemoryStorage()
        return Setup(game, CareStore(storage, clock, game, utc, rng = Random(3)), clock, storage)
    }

    private val Setup.points get() = game.data.player.trainerPoints

    @Test
    fun aRoutinePaysOnceADayAndUndoNeverPaysTwice() {
        val s = setup()
        val water = s.care.addRoutine("Drink water")
        val before = s.points
        assertTrue(s.care.completeRoutine(water.id).any { it is Reward.Friendship })
        assertEquals(before + 1, s.points)
        s.care.undoRoutine(water.id)
        assertTrue(s.care.completeRoutine(water.id).isEmpty())
        assertEquals(before + 1, s.points)
        assertEquals("Care: a routine", s.game.data.rewardLog.last().source, "the log never names the routine")
    }

    @Test
    fun onlyFiveRoutinesPayADay() {
        val s = setup()
        val before = s.points
        repeat(CareRules.ROUTINES_PAID_PER_DAY + 2) { s.care.completeRoutine(s.care.addRoutine("R$it").id) }
        assertEquals(before + CareRules.ROUTINES_PAID_PER_DAY, s.points)
        assertEquals(CareRules.ROUTINES_PAID_PER_DAY + 2, s.care.data.routines.count { it.doneOn(s.care.today) }, "the rest are still ticked")
    }

    @Test
    fun calmBreaksPayFromAMinuteAndThreeADay() {
        val s = setup()
        assertTrue(s.care.finishCalmBreak(30).isEmpty(), "under a minute is kept but pays nothing")
        repeat(4) { s.care.finishCalmBreak(90) }
        assertEquals(CareRules.BREAKS_PAID_PER_DAY, s.care.data.breaks.count { it.paid })
        assertEquals(5, s.care.data.breaks.size)
        assertEquals(CareRules.BREAK_MAX_SECONDS, s.care.finishCalmBreak(600).let { s.care.data.breaks.last().seconds })
    }

    @Test
    fun fourDaysOfCareInAWeekPayTheRhythmOnce() {
        val s = setup()
        val stretch = s.care.addRoutine("Stretch")
        repeat(3) { s.care.completeRoutine(stretch.id); s.clock.advanceDays(1) }
        val rewards = s.care.completeRoutine(stretch.id)
        assertEquals(1 + 3, rewards.count { it is Reward.ItemDrop } + rewards.count { it is Reward.Friendship }, "a routine and three rhythm berries")
        assertEquals(1, s.care.data.rhythmWeeks.size)
        s.clock.advanceDays(1)
        assertTrue(s.care.completeRoutine(stretch.id).none { it is Reward.ItemDrop }, "the rhythm pays once a week")
    }

    @Test
    fun theDiaryCardAndJournalNeverPay() {
        val s = setup()
        val before = s.game.data
        s.care.saveDiary(mapOf(Emotion.SADNESS to 4, Emotion.JOY to 9), urges = mapOf("Skip lunch" to 2, " " to 3), skills = setOf("stop", "nope"), note = "  rough day ")
        s.care.write("  Tea with a friend ", tags = setOf("people", " "))
        val prompt = s.care.prompt()!!
        s.care.write("The walk home", promptId = prompt.id)
        assertEquals(before, s.game.data, "nothing reached the world")
        val card = s.care.diaryToday()!!
        assertEquals(mapOf(Emotion.SADNESS to 4, Emotion.JOY to 5), card.feelings, "ratings are kept from 0 to 5")
        assertEquals(mapOf("Skip lunch" to 2), card.urges)
        assertEquals(setOf("stop"), card.skills, "only skills from the library")
        assertEquals("rough day", card.note)
        assertEquals(setOf("people"), s.care.data.journal.first().tags)
        assertEquals(null, s.care.prompt(), "one prompt a day once answered")
    }

    @Test
    fun oneDiaryCardADay() {
        val s = setup()
        s.care.saveDiary(mapOf(Emotion.CALM to 2))
        s.care.saveDiary(mapOf(Emotion.CALM to 3))
        assertEquals(3, s.care.data.diary.single().feelings[Emotion.CALM])
        s.clock.advanceDays(1)
        assertEquals(null, s.care.diaryToday())
        s.care.saveDiary(mapOf(Emotion.CALM to 1))
        assertEquals(2, s.care.data.diary.size)
    }

    @Test
    fun aSkillPaysOnceADayAndThreeSkillsADay() {
        val s = setup()
        val before = s.points
        assertTrue(s.care.practiseSkill("stop").any { it is Reward.ItemDrop })
        assertTrue(s.care.practiseSkill("stop").isEmpty(), "the same skill again the same day pays nothing")
        s.care.practiseSkill("observe")
        s.care.practiseSkill("tipp")
        assertTrue(s.care.practiseSkill("dear-man").isEmpty(), "three skills a day pay")
        assertEquals(before + 3, s.points)
        assertEquals(5, s.care.data.practices.size, "every practice is kept")
        assertEquals("Care: a skill practised", s.game.data.rewardLog.last().source, "the log never names the skill")
        assertTrue(s.care.practiseSkill("paced-breathing").isEmpty(), "paced breathing is practised as a calm break")
        assertTrue(s.care.practiseSkill("made-up").isEmpty())
    }

    @Test
    fun theJournalSearchesTextAndTags() {
        val s = setup()
        s.care.write("A walk by the river", tags = setOf("outside"))
        s.clock.millis += 1_000
        s.care.write("Coffee with Sam")
        assertEquals(listOf("Coffee with Sam", "A walk by the river"), s.care.searchJournal("").map { it.text })
        assertEquals(listOf("A walk by the river"), s.care.searchJournal("OUTSIDE").map { it.text })
        s.care.editEntry(s.care.data.journal.first().id, "A long walk")
        assertEquals(listOf("A long walk"), s.care.searchJournal("long").map { it.text })
    }

    @Test
    fun skippingShowsTheNextPrompt() {
        val s = setup()
        val first = s.care.prompt()!!
        s.care.skipPrompt()
        assertFalse(first == s.care.prompt())
    }

    @Test
    fun beforeStartingNothingPaysButCareStillWorks() {
        val s = setup(started = false)
        val r = s.care.addRoutine("Walk")
        assertTrue(s.care.completeRoutine(r.id).isEmpty())
        assertTrue(s.care.data.routines.single().doneOn(s.care.today))
        assertEquals(null, s.care.data.routines.single().paidOn, "it can still pay once the trainer starts")
    }

    @Test
    fun careHasItsOwnSaveApartFromPlan() {
        val s = setup()
        s.care.addRoutine("Wind down")
        assertTrue(s.storage.text!!.contains("Wind down"))
        assertFalse(s.game.exportJson().contains("Wind down"))
        val again = CareStore(s.storage, s.clock, s.game, utc)
        assertEquals(s.care.data, again.data)
        again.erase()
        assertTrue(again.data.routines.isEmpty())
    }

    private fun befriendOnce(s: Setup, day: Int): app.pokeplan.care.BefriendResult? {
        s.care.completeRoutine(s.care.addRoutine("R$day").id)
        return s.care.befriend(s.care.visitors().first().speciesId)
    }

    @Test
    fun aVisitorBecomesAFriendAfterFourDaysAndOneBefriendADay() {
        val s = setup()
        assertTrue(s.care.visitors().size in 2..3)
        assertTrue(s.care.visitors().all { it.points == 0 })
        // Nothing to spend the befriend on until some care has paid.
        assertEquals(null, s.care.befriend(s.care.visitors().first().speciesId))
        val species = s.care.visitors().first().speciesId
        val owned = s.game.data.player.pokemon.size
        repeat(3) { day ->
            val r = assertNotNull(befriendOnce(s, day))
            assertFalse(r.friend)
            assertEquals(day + 1, r.visitor.points)
            assertEquals(null, s.care.befriend(species), "one befriend a day")
            assertEquals(owned, s.game.data.player.pokemon.size)
            s.clock.advanceDays(1)
            assertTrue(s.care.visitors().any { it.speciesId == species && it.points == day + 1 }, "a warming visitor stays")
        }
        val r = assertNotNull(befriendOnce(s, 3))
        assertTrue(r.friend)
        val friend = s.game.data.player.pokemon.last()
        assertEquals(species, friend.speciesId)
        assertEquals(app.pokeplan.core.pokemon.PartyIds.CARE, friend.partyId)
        assertEquals(friend.id, s.game.data.player.careLeadId, "the first in Care's party leads")
        assertTrue(s.care.visitors().none { it.speciesId == species })
        assertTrue(s.game.data.rewardLog.last().rewards.any { it is Reward.Hatched })
        assertEquals("Care: made a friend", s.game.data.rewardLog.last().source)
    }

    @Test
    fun aWarmingVisitorWandersOffAfterAFewDaysWithoutAVisit() {
        val s = setup()
        val species = s.care.visitors().first().speciesId
        assertNotNull(befriendOnce(s, 0))
        s.clock.advanceDays(app.pokeplan.care.Visitors.WANDER_DAYS + 1)
        assertTrue(s.care.visitors().none { it.speciesId == species && it.points > 0 })
        assertEquals(3, s.care.visitors().size, "a fuller choice after a break")
    }

    @Test
    fun theSameDeviceAndDayOfferTheSameVisitors() {
        val s = setup()
        val first = s.care.visitors()
        val again = CareStore(s.storage, s.clock, s.game, utc, rng = Random(99))
        assertEquals(first, again.visitors())
    }
}
