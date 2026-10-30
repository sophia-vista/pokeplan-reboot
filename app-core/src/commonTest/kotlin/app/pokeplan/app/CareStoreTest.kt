package app.pokeplan.app

import app.pokeplan.care.CareAction
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

    private class Setup(val game: GameStore, val care: CareStore, val clock: FakeClock, val storage: MemoryStorage, val gameStorage: MemoryStorage) {
        /** Puts items in the shared bag, as if earned elsewhere. */
        fun give(item: String, n: Int) {
            gameStorage.save(GameStore.json.encodeToString(AppData.serializer(), game.data.let { d -> d.copy(player = d.player.copy(inventory = d.player.inventory.add(item, n))) }))
            game.reload()
        }
    }

    private fun setup(started: Boolean = true): Setup {
        val clock = FakeClock(monday)
        val gameStorage = MemoryStorage()
        val game = GameStore(gameStorage, clock, utc, Random(7))
        if (started) game.start("Sophia", 25)
        val storage = MemoryStorage()
        return Setup(game, CareStore(storage, clock, game, utc, rng = Random(3)), clock, storage, gameStorage)
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

    /** A warm visit with the first visitor: its favourite first, then two other kinds of care. */
    private fun befriendOnce(s: Setup, day: Int): app.pokeplan.care.BefriendResult? {
        s.care.completeRoutine(s.care.addRoutine("R$day").id)
        val visitor = s.care.visitors().first()
        s.care.startVisit(visitor.speciesId) ?: return null
        val actions = listOf(visitor.favorite) + CareAction.entries.filter { it != visitor.favorite }.take(2)
        return actions.map { s.care.visitAct(it) }.last()?.result
    }

    @Test
    fun aVisitorBecomesAFriendAfterFourDaysOfOneVisitADay() {
        val s = setup()
        assertTrue(s.care.visitors().size in 2..3)
        assertTrue(s.care.visitors().all { it.points == 0 })
        // Nothing to spend the befriend on until some care has paid.
        assertEquals(null, s.care.startVisit(s.care.visitors().first().speciesId))
        val species = s.care.visitors().first().speciesId
        repeat(3) { day ->
            val r = assertNotNull(befriendOnce(s, day))
            assertFalse(r.friend)
            assertEquals(day + 1, r.visitor.points)
            assertEquals(null, s.care.startVisit(species), "one visit a day")
            assertTrue(s.game.data.rewardLog.none { it.source == "Care: made a friend" }, "no friend yet (a Care Egg may hatch)")
            s.clock.advanceDays(1)
            assertTrue(s.care.visitors().any { it.speciesId == species && it.points == day + 1 }, "a warming visitor stays")
        }
        val r = assertNotNull(befriendOnce(s, 3))
        assertTrue(r.friend)
        val friend = s.game.data.player.pokemon.last()
        // Usually an egg of its family's first stage; rarely the visitor itself.
        assertEquals(if (r.joined) species else app.pokeplan.core.dex.Dex.lineRoot(species), friend.speciesId)
        assertEquals(app.pokeplan.core.pokemon.PartyIds.CARE, friend.partyId)
        assertNotNull(s.game.data.player.careLeadId, "the first in Care's party leads (a Care Egg may have come first)")
        assertTrue(s.care.visitors().none { it.speciesId == species })
        assertTrue(s.game.data.rewardLog.last().rewards.any { it is Reward.Hatched })
        assertEquals("Care: made a friend", s.game.data.rewardLog.last().source)
    }

    @Test
    fun aVisitIsWarmWithItsFavouriteOrNoticedBidsButNotWithPlainCareAlone() {
        val s = setup()
        s.care.completeRoutine(s.care.addRoutine("Stretch").id)
        val visitor = s.care.visitors().first()
        s.care.startVisit(visitor.speciesId)
        val others = CareAction.entries.filter { it != visitor.favorite }
        val steps = others.take(3).map { s.care.visitAct(it) }
        assertTrue(steps.all { it!!.reaction == app.pokeplan.care.Reaction.LIKED })
        val result = assertNotNull(steps.last()!!.result)
        assertEquals(0, result.visitor.points, "not warm enough: no day, no penalty")
        assertTrue(s.care.visitedToday(s.care.visitors().first { it.speciesId == visitor.speciesId }))
        // Noticing both bids makes the same visit warm.
        s.clock.advanceDays(1)
        s.care.completeRoutine(s.care.addRoutine("Walk").id)
        val again = s.care.visitors().first()
        s.care.startVisit(again.speciesId)
        val kinds = CareAction.entries.filter { it != again.favorite }.take(3)
        kinds.forEachIndexed { i, a -> s.care.visitAct(a); if (i < 2) s.care.visitNotice() }
        assertEquals(1, s.care.visitors().first { it.speciesId == again.speciesId }.points)
    }

    @Test
    fun aRepeatedKindOfCareWarmsNoMoreAndAnOldBidIsLetGoFree() {
        val s = setup()
        s.care.completeRoutine(s.care.addRoutine("Stretch").id)
        val visitor = s.care.visitors().first()
        s.care.startVisit(visitor.speciesId)
        s.care.visitAct(visitor.favorite)
        val again = assertNotNull(s.care.visitAct(visitor.favorite))
        assertEquals(app.pokeplan.care.Reaction.FLAT, again.reaction)
        assertEquals(2, again.visit.warmth)
        assertNotNull(again.visit.pendingBid)
        assertEquals(1, s.care.visitNotice()!!.bidsNoticed)
        assertEquals(null, s.care.visitNotice()!!.pendingBid, "noticing twice adds nothing")
        assertEquals(3, s.care.visit!!.warmth)
    }

    @Test
    fun aPatOnItsLikedSpotWarmsOnceAndAMeetingIsKept() {
        val s = setup()
        s.care.completeRoutine(s.care.addRoutine("Stretch").id)
        val visitor = s.care.visitors().first()
        val liked = app.pokeplan.care.Visitors.likedSpot(visitor.speciesId)
        val wrong = app.pokeplan.care.PetSpot.entries.first { it != liked }
        s.care.startVisit(visitor.speciesId)
        val first = s.care.visitAct(CareAction.TOUCH, wrong)!!
        assertFalse(first.visit.spotFound, "a wrong spot costs nothing and gives nothing")
        val liked1 = CareAction.entries.first { it != CareAction.TOUCH }
        s.care.visitAct(liked1)
        val last = s.care.visitAct(CareAction.entries.first { it != CareAction.TOUCH && it != liked1 })!!
        assertTrue(visitor.speciesId in s.care.data.met, "a visit is remembered whatever it came to")
        assertEquals(null, last.visit.pendingBid)
        // Another visit, the liked spot: warms one more, once.
        s.clock.advanceDays(1)
        s.care.completeRoutine(s.care.addRoutine("Walk").id)
        val again = s.care.visitors().first { it.speciesId == visitor.speciesId }
        s.care.startVisit(again.speciesId)
        val warm = s.care.visitAct(CareAction.TOUCH, liked)!!
        assertTrue(warm.visit.spotFound)
        val before = warm.visit.warmth
        assertTrue(before >= 2)
    }

    @Test
    fun theBerryAVisitorLikesAddsABonusDayOnceAndAnyBerryIsSpent() {
        val s = setup()
        val visitor = s.care.visitors().first()
        val liked = app.pokeplan.care.Visitors.likedBerry(visitor.speciesId)
        val other = app.pokeplan.core.pokemon.Items.flavors.values.map { app.pokeplan.core.pokemon.Items.berry(it) }.first { it != liked }
        s.give(liked, 2)
        s.give(other, 1)
        fun visit(day: Int, berry: String?): app.pokeplan.care.BefriendResult {
            s.care.completeRoutine(s.care.addRoutine("R$day").id)
            s.care.startVisit(visitor.speciesId)
            val actions = listOf(visitor.favorite) + CareAction.entries.filter { it != visitor.favorite && it != CareAction.GIFT }.take(1) + CareAction.GIFT
            return actions.map { s.care.visitAct(it, berry = berry.takeIf { _ -> it == CareAction.GIFT }) }.last()!!.result!!
        }
        assertEquals(mapOf(liked to 2, other to 1), s.care.berries())
        // Another flavour is a treat like any other: spent, no bonus.
        assertEquals(1, visit(0, other).visitor.points)
        assertEquals(null, s.care.berries()[other])
        s.clock.advanceDays(1)
        // Its liked berry: a warm day and a bonus day.
        assertEquals(3, visit(1, liked).visitor.points)
        assertEquals(1, s.care.berries()[liked])
        s.clock.advanceDays(1)
        // Only once per visitor; the berry is still eaten.
        val last = visit(2, liked)
        assertTrue(last.friend, "four days: a warm one, a warm one and a bonus, and this one")
        assertEquals(null, s.care.berries()[liked])
    }

    @Test
    fun aBerryNotInTheBagGivesNothing() {
        val s = setup()
        s.care.completeRoutine(s.care.addRoutine("Stretch").id)
        val visitor = s.care.visitors().first()
        s.care.startVisit(visitor.speciesId)
        val step = s.care.visitAct(CareAction.GIFT, berry = app.pokeplan.care.Visitors.likedBerry(visitor.speciesId))!!
        assertEquals(null, step.visit.berry)
    }

    @Test
    fun everyVisitorCanBeVisitedOnceADayAndTheMeadowHoldsFour() {
        val s = setup()
        s.care.completeRoutine(s.care.addRoutine("Stretch").id)
        val (a, b) = s.care.visitors()
        assertNotNull(s.care.startVisit(a.speciesId))
        assertEquals(null, s.care.startVisit(b.speciesId), "one visit at a time")
        CareAction.entries.take(3).forEach { s.care.visitAct(it) }
        assertEquals(null, s.care.startVisit(a.speciesId), "one visit a day for each visitor")
        assertNotNull(s.care.startVisit(b.speciesId), "another visitor can still be visited today")
        CareAction.entries.take(3).forEach { s.care.visitAct(it) }
        assertTrue(s.care.befriendSpent, "every visitor has had today's visit")
        // Warming visitors stay, and new ones come only while there is room.
        repeat(3) { day ->
            s.clock.advanceDays(1)
            s.care.completeRoutine(s.care.addRoutine("R$day").id)
            for (v in s.care.visitors()) {
                s.care.startVisit(v.speciesId) ?: continue
                (listOf(v.favorite) + CareAction.entries.filter { it != v.favorite }.take(2)).forEach { s.care.visitAct(it) }
            }
            assertTrue(s.care.visitors().size <= app.pokeplan.care.Visitors.MAX_VISITORS)
        }
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

    @Test
    fun careScreensCelebrateOnlyCareMomentsAndPetCaresBuddy() {
        val s = setup()
        assertEquals(s.game.data.player.buddy?.id, s.game.careBuddy?.id, "without a Care lead, Care shows the Plan buddy")
        assertEquals(null, s.game.buddyEvent(care = true))
        s.care.completeRoutine(s.care.addRoutine("Stretch").id)
        assertNotNull(s.game.buddyEvent(care = true))
        val buddy = s.game.careBuddy!!
        val before = buddy.friendship
        assertTrue(s.game.pet(buddy.id) > 0)
        assertTrue(s.game.data.player.pokemon.first { it.id == buddy.id }.friendship > before)
        assertEquals(0, s.game.pet("nobody"))
    }
}
