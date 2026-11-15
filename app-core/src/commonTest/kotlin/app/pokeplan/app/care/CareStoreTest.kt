package app.pokeplan.app.care

import kotlinx.datetime.minus
import kotlinx.datetime.plus
import app.pokeplan.care.CareAction
import app.pokeplan.care.CareRules
import app.pokeplan.care.Emotion
import app.pokeplan.core.reward.Reward
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import app.pokeplan.app.AppData
import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage

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

        /** Puts a second Pokémon in Care's party as its lead: Care warms only its own lead, never the Plan buddy. */
        fun giveCareLead() {
            val d = game.data
            val starter = d.player.pokemon.first()
            val lead = starter.copy(id = "care-lead", inParty = true, partyId = app.pokeplan.core.pokemon.PartyIds.CARE)
            gameStorage.save(GameStore.json.encodeToString(AppData.serializer(), d.copy(player = d.player.copy(pokemon = d.player.pokemon + lead, careLeadId = lead.id))))
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
    fun anUnreadableCareSaveIsKeptAsideAndAHalfWrittenOneIsIgnored() {
        val s = setup()
        val storage = MemoryStorage("{\"routines\": [")
        val care = CareStore(storage, s.clock, s.game, utc)
        assertEquals("{\"routines\": [", storage.keptAside, "the diary and journal are never written over")

        s.care.saveDiary(mapOf(Emotion.entries.first() to 2), note = "private")
        val saved = s.care.data
        s.storage.text = s.storage.text!!.take(20)
        s.care.reload()
        assertEquals(saved, s.care.data)
        assertTrue(care.data.routines.isEmpty())
    }

    @Test
    fun editingARoutineKeepsWhatItEarnedAndWhenItWasDone() {
        val s = setup()
        val water = s.care.addRoutine("Drink water")
        s.care.completeRoutine(water.id)
        val before = s.points
        val saved = s.care.data.routines.single()
        s.care.updateRoutine(saved.copy(title = "Drink two glasses", time = LocalTime(9, 30), days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)))
        val edited = s.care.data.routines.single()
        assertEquals("Drink two glasses", edited.title)
        assertEquals(saved.completions, edited.completions, "the days it was done stay")
        assertEquals(saved.paidOn, edited.paidOn, "and so does the day it paid")
        assertTrue(s.care.completeRoutine(water.id).isEmpty(), "editing never lets today pay again")
        assertEquals(before, s.points)
    }

    @Test
    fun aHalfWrittenJournalEntryIsKeptAcrossARestartAndClearedWhenSaved() {
        val s = setup()
        s.care.setJournalDraft("Today I felt")
        val again = CareStore(s.storage, s.clock, s.game, utc, rng = Random(3))
        assertEquals("Today I felt", again.data.journalDraft, "the draft is on disk")
        assertEquals("", s.care.write("Today I felt calm.")?.let { s.care.data.journalDraft }, "saving the entry clears the draft")
    }


    @Test
    fun aRoutinePaysOnceADayAndUndoNeverPaysTwice() {
        val s = setup()
        s.giveCareLead()
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
    fun oneTapMoodLivesOnTodaysCardAndPaysNothing() {
        val s = setup()
        val before = s.game.data.player.inventory
        s.care.setMood(4)
        assertEquals(4, s.care.diaryToday()?.mood)
        // Filling in the card afterwards keeps the mood; tapping it again takes it back and drops a card left with nothing.
        s.care.saveDiary(mapOf(Emotion.CALM to 2))
        assertEquals(4, s.care.diaryToday()?.mood)
        s.care.setMood(null)
        assertEquals(null, s.care.diaryToday()?.mood)
        assertEquals(1, s.care.data.diary.size)
        s.care.deleteDiary(s.care.data.diary.single().id)
        s.care.setMood(9)
        assertEquals(5, s.care.diaryToday()?.mood)
        s.care.setMood(null)
        assertTrue(s.care.data.diary.isEmpty())
        assertEquals(before, s.game.data.player.inventory)
        assertEquals(7, s.care.moodDays(7).size)
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
        // Tags change with the text; a blank text changes nothing.
        val id = s.care.data.journal.first().id
        s.care.editEntry(id, "A long walk", tags = setOf("Rest"))
        assertEquals(setOf("Rest"), s.care.data.journal.first { it.id == id }.tags)
        s.care.editEntry(id, "   ", tags = setOf("Work"))
        assertEquals("A long walk", s.care.data.journal.first { it.id == id }.text)
        assertEquals(setOf("Rest"), s.care.data.journal.first { it.id == id }.tags)
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

    private val rest get() = app.pokeplan.care.Visitors.REST_MILLIS + 1

    /** A visit with [species]: its favourite first, then every other kind of care, until the visit comes to its end. The last step carries what it came to. */
    private fun fullVisit(s: Setup, species: Int): app.pokeplan.care.VisitStep {
        val visitor = s.care.visitors().first { it.speciesId == species }
        assertNotNull(s.care.startVisit(species))
        val kinds = listOf(visitor.favorite) + CareAction.entries.filter { it != visitor.favorite }
        var step: app.pokeplan.care.VisitStep? = null
        for (kind in kinds) {
            step = assertNotNull(s.care.visitAct(kind))
            if (step.result != null) break // warm enough to be a friend before every kind was done
        }
        return step!!
    }

    private fun Setup.progressOf(species: Int) = care.visitors().first { it.speciesId == species }.progress

    @Test
    fun visitorsArriveOnTheirOwnAndNeedNoCareFirst() {
        val s = setup()
        // Nothing has been done in Care: visitors are here, each with its own id, and a visit can start at once.
        val here = s.care.visitors()
        assertEquals(app.pokeplan.care.Visitors.FIRST_ARRIVALS, here.size)
        assertEquals(here.size, here.map { it.id }.toSet().size)
        assertTrue(here.all { it.id.isNotEmpty() && it.progress == 0 })
        assertEquals(0, s.care.data.routines.size)
        assertNotNull(s.care.startVisit(here.first().speciesId), "no routine, break or skill has to come first")
    }

    @Test
    fun whatAVisitWarmsIsKeptAtOnceAndLeavingEarlyLosesNothing() {
        val s = setup()
        val visitor = s.care.visitors().first()
        s.care.startVisit(visitor.speciesId)
        val step = assertNotNull(s.care.visitAct(visitor.favorite))
        assertEquals(app.pokeplan.care.Reaction.LOVED, step.reaction)
        assertEquals(null, step.result, "no forced number of actions: the visit goes on")
        assertEquals(2, s.progressOf(visitor.speciesId), "its favourite warms two, as it happens")
        val left = assertNotNull(s.care.leaveVisit())
        assertFalse(left.friend)
        assertEquals(2, left.visitor.progress)
        assertEquals(null, s.care.visit)
        assertTrue(visitor.speciesId in s.care.data.met)
    }

    @Test
    fun aVisitorRestsAfterAVisitAndCanStillBeVisitedAfterwardsAnyTimeOfDay() {
        val s = setup()
        val species = s.care.visitors().first().speciesId
        fullVisit(s, species)
        val rested = s.care.visitors().first { it.speciesId == species }
        assertTrue(s.care.resting(rested))
        assertEquals(null, s.care.startVisit(species), "resting: another visit would not count yet")
        s.clock.millis += rest
        assertFalse(s.care.resting(s.care.visitors().first { it.speciesId == species }))
        assertNotNull(s.care.startVisit(species), "later the same day is fine: there is no daily limit")
    }

    @Test
    fun aVisitorBecomesAFriendInAFewVisitsAndTheVisitorLeavesWithAnEgg() {
        val s = setup()
        val species = s.care.visitors().first().speciesId
        var visits = 0
        var last: app.pokeplan.care.VisitStep
        do {
            last = fullVisit(s, species)
            visits++
            if (last.result?.friend != true) s.clock.millis += rest
        } while (last.result?.friend != true && visits < 6)
        val r = assertNotNull(last.result)
        assertTrue(r.friend)
        assertTrue(visits in 2..3, "a full visit warms 6 to 11 of ${app.pokeplan.care.Visitors.FRIEND_PROGRESS}, so it takes two or three: $visits")
        val friend = s.game.data.player.pokemon.last()
        assertEquals(if (r.joined) species else app.pokeplan.core.dex.Dex.lineRoot(species), friend.speciesId)
        assertEquals(app.pokeplan.core.pokemon.PartyIds.CARE, friend.partyId)
        assertTrue(s.care.visitors().none { it.speciesId == species }, "a befriended visitor leaves, and its place goes on")
        assertEquals("Care: made a friend", s.game.data.rewardLog.last().source)
        assertTrue(s.game.data.rewardLog.last().rewards.any { it is Reward.Hatched })
    }

    @Test
    fun aRepeatedKindOfCareWarmsNoMoreAndAnOldBidIsLetGoFree() {
        val s = setup()
        val visitor = s.care.visitors().first()
        s.care.startVisit(visitor.speciesId)
        s.care.visitAct(visitor.favorite)
        val again = assertNotNull(s.care.visitAct(visitor.favorite))
        assertEquals(app.pokeplan.care.Reaction.FLAT, again.reaction)
        assertEquals(2, again.visit.warmth)
        assertNotNull(again.visit.pendingBid)
        assertEquals(3, assertNotNull(s.care.visitNotice()).visit.warmth.also { assertEquals(3, s.progressOf(visitor.speciesId)) })
        assertEquals(null, s.care.visitNotice(), "noticing twice adds nothing")
    }

    @Test
    fun aPatOnItsLikedSpotWarmsOnceAndAMeetingIsKept() {
        val s = setup()
        val visitor = s.care.visitors().first()
        val liked = app.pokeplan.care.Visitors.likedSpot(visitor.speciesId)
        val wrong = app.pokeplan.care.PetSpot.entries.first { it != liked }
        s.care.startVisit(visitor.speciesId)
        val first = s.care.visitAct(CareAction.TOUCH, wrong)!!
        assertFalse(first.visit.spotFound, "a wrong spot costs nothing and gives nothing")
        s.care.leaveVisit()
        assertTrue(visitor.speciesId in s.care.data.met, "a visit is remembered whatever it came to")
        s.clock.millis += rest
        s.care.startVisit(visitor.speciesId)
        val warm = s.care.visitAct(CareAction.TOUCH, liked)!!
        assertTrue(warm.visit.spotFound)
        assertTrue(warm.visit.warmth >= 2)
    }

    @Test
    fun theBerryAVisitorLikesAddsABonusOnceAndAnyBerryIsSpent() {
        val s = setup()
        val visitor = s.care.visitors().first()
        val liked = app.pokeplan.care.Visitors.likedBerry(visitor.speciesId)
        val other = app.pokeplan.core.pokemon.Items.flavors.values.map { app.pokeplan.core.pokemon.Items.berry(it) }.first { it != liked }
        s.give(liked, 2)
        s.give(other, 1)
        assertEquals(mapOf(liked to 2, other to 1), s.care.berries())
        fun treat(berry: String): Int {
            s.care.startVisit(visitor.speciesId)
            val before = s.progressOf(visitor.speciesId)
            s.care.visitAct(CareAction.GIFT, berry = berry)
            val gained = s.progressOf(visitor.speciesId) - before
            s.care.leaveVisit()
            s.clock.millis += rest
            return gained
        }
        val plain = treat(other)
        assertTrue(plain <= 2, "another flavour is a treat like any other: $plain")
        assertEquals(null, s.care.berries()[other])
        assertEquals(plain + app.pokeplan.care.Visitors.BERRY_BONUS, treat(liked), "its liked berry adds a bonus")
        assertEquals(plain, treat(liked), "only once per visitor; the berry is still eaten")
        assertEquals(null, s.care.berries()[liked])
    }

    @Test
    fun aBerryNotInTheBagGivesNothing() {
        val s = setup()
        val visitor = s.care.visitors().first()
        s.care.startVisit(visitor.speciesId)
        val step = s.care.visitAct(CareAction.GIFT, berry = app.pokeplan.care.Visitors.likedBerry(visitor.speciesId))!!
        assertEquals(null, step.visit.berry)
    }

    @Test
    fun aFriendTheWorldCannotPayTodayStaysReadyAndTheVisitIsNotStranded() {
        val s = setup(started = false) // the world pays nothing before the game has started, the same refusal as its daily limit of friends
        val species = s.care.visitors().first().speciesId
        var step: app.pokeplan.care.VisitStep
        do {
            step = fullVisit(s, species)
            if (step.result == null) error("a visit ends when it is done")
            if (step.result?.waiting != true) s.clock.millis += rest
        } while (step.result?.waiting != true)
        assertEquals(null, s.care.visit, "the visit ended; it is not stuck")
        val ready = s.care.visitors().first { it.speciesId == species }
        assertTrue(ready.progress >= app.pokeplan.care.Visitors.FRIEND_PROGRESS, "the visitor stays, ready")
        s.game.start("Sophia", 25)
        s.care.startVisit(species)
        val made = assertNotNull(s.care.visitAct(CareAction.entries.first())!!.result)
        assertTrue(made.friend, "the next visit makes it a friend")
        assertTrue(s.care.visitors().none { it.speciesId == species })
    }

    @Test
    fun theMeadowHoldsFourTheRestWaitInAQueueThatNeverExpiresAndNothingIsDisplaced() {
        val s = setup()
        val V = app.pokeplan.care.Visitors
        // A year of not opening Care: arrivals pause once the meadow and the queue are full, and nobody is dropped.
        var seen = s.care.visitors().map { it.id }
        repeat(60) {
            s.clock.advanceDays(1)
            val now = s.care.visitors()
            assertTrue(now.size <= V.MAX_VISITORS + V.MAX_QUEUE)
            assertTrue(now.map { it.id }.containsAll(seen), "nobody who was here is replaced or lost")
            assertEquals(now.size, now.map { it.speciesId }.toSet().size, "a species is here once")
            seen = now.map { it.id }
        }
        assertEquals(V.MAX_VISITORS + V.MAX_QUEUE, s.care.visitors().size)
        assertEquals(V.MAX_VISITORS, s.care.meadow().size)
        assertEquals(V.MAX_QUEUE, s.care.queued().size)
        val queued = s.care.queued().first()
        assertEquals(null, s.care.startVisit(queued.speciesId), "a visitor in the queue waits for a place")
        // Befriending frees a place, and the next in the queue takes it.
        val gone = s.care.meadow().first()
        assertTrue(s.care.dismiss(gone.speciesId))
        assertEquals(queued.id, s.care.meadow().last().id)
        assertNotNull(s.care.startVisit(queued.speciesId))
    }

    @Test
    fun absenceNeverResetsOrLosesWhatAVisitorHasWarmed() {
        val s = setup()
        val visitor = s.care.visitors().first()
        s.care.startVisit(visitor.speciesId)
        s.care.visitAct(visitor.favorite)
        s.care.leaveVisit()
        val progress = s.progressOf(visitor.speciesId)
        assertTrue(progress > 0)
        s.clock.advanceDays(90)
        assertEquals(progress, s.care.visitors().first { it.id == visitor.id }.progress, "no three-day departure and no reset")
    }

    @Test
    fun dismissingKeepsWhatWasEarnedAndAVisitorCanBeAskedBack() {
        val s = setup()
        val visitor = s.care.visitors().first()
        s.care.startVisit(visitor.speciesId)
        s.care.visitAct(visitor.favorite)
        val pokemonBefore = s.game.data.player.pokemon.size
        assertTrue(s.care.dismiss(visitor.speciesId))
        assertEquals(null, s.care.visit, "the visit with it ends")
        assertTrue(s.care.visitors().none { it.id == visitor.id })
        assertEquals(pokemonBefore, s.game.data.player.pokemon.size, "nothing already earned is taken back")
        val kept = s.care.dismissed().single()
        assertEquals(2, kept.progress, "it keeps its warmth")
        assertTrue(s.care.restore(kept.id))
        assertEquals(visitor.id, s.care.visitors().last().id, "back at the end of the queue")
        assertEquals(2, s.progressOf(visitor.speciesId))
        assertFalse(s.care.restore(kept.id), "already back")
    }

    @Test
    fun anOldSaveKeepsItsVisitorsWithTheirWarmDaysAsProgress() {
        val old = """{"visitorSeed":5,"visitorsDay":"2026-10-19","befriendDay":"2026-10-18","met":[25],
            "visitors":[{"speciesId":25,"formIdentifier":"pikachu","arrivedDay":"2026-10-15","points":3,"lastVisitDay":"2026-10-18","favorite":"GIFT"},
                        {"speciesId":16,"formIdentifier":"pidgey","arrivedDay":"2026-10-19"}]}"""
        val s = setup()
        s.storage.save(old)
        val care = CareStore(s.storage, s.clock, s.game, utc, rng = Random(3))
        val here = care.visitors()
        val three = here.first { it.speciesId == 25 }
        assertEquals(3 * app.pokeplan.care.Visitors.LEGACY_DAY_PROGRESS, three.progress, "three warm days are three quarters of the way")
        assertEquals(0, three.points)
        assertEquals(app.pokeplan.care.CareAction.GIFT, three.favorite)
        assertTrue(here.all { it.id.isNotEmpty() })
        assertEquals(0, here.first { it.speciesId == 16 }.progress)
        // Loading it again changes nothing more.
        assertEquals(here, CareStore(s.storage, s.clock, s.game, utc, rng = Random(9)).visitors())
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
        assertEquals(null, s.game.careBuddy, "without a Care party, Care has no lead and does not borrow the Plan buddy")
        s.giveCareLead()
        assertEquals("care-lead", s.game.careBuddy?.id)
        assertEquals(null, s.game.buddyEvent(care = true))
        s.care.completeRoutine(s.care.addRoutine("Stretch").id)
        assertNotNull(s.game.buddyEvent(care = true))
        val buddy = s.game.careBuddy!!
        val before = buddy.friendship
        assertTrue(s.game.pet(buddy.id) > 0)
        assertTrue(s.game.data.player.pokemon.first { it.id == buddy.id }.friendship > before)
        assertEquals(0, s.game.pet("nobody"))
    }

    @Test
    fun aDiaryCardCanBeKeptForAnEarlierDayAndNeverForAFutureOne() {
        val s = setup()
        val earlier = s.care.today.minus(kotlinx.datetime.DatePeriod(days = 3))
        s.care.saveDiary(mapOf(Emotion.JOY to 3), note = "late entry", forDay = earlier)
        assertEquals("late entry", s.care.diaryOn(earlier)?.note)
        assertEquals(null, s.care.diaryToday())
        s.care.saveDiary(mapOf(Emotion.JOY to 4), forDay = earlier)
        assertEquals(1, s.care.data.diary.size, "saving the same day again replaces its card")
        s.care.saveDiary(mapOf(Emotion.JOY to 1), forDay = s.care.today.plus(kotlinx.datetime.DatePeriod(days = 2)))
        assertEquals(1, s.care.diaryToday()?.feelings?.get(Emotion.JOY), "a day still to come is today")
    }

    @Test
    fun aJournalEntryCanBeDatedAndRedatedButNotToTheFuture() {
        val s = setup()
        val earlier = LocalDateTime(2026, 10, 12, 21, 30).toInstant(utc).toEpochMilliseconds()
        val e = assertNotNull(s.care.write("Forgot to write this", atMillis = earlier))
        assertEquals(kotlinx.datetime.LocalDate(2026, 10, 12), e.day)
        val later = LocalDateTime(2026, 10, 14, 8, 5).toInstant(utc).toEpochMilliseconds()
        s.care.editEntry(e.id, "  Wrote this later ", atMillis = later, tags = setOf("people"))
        val edited = s.care.data.journal.single()
        assertEquals("Wrote this later", edited.text)
        assertEquals(kotlinx.datetime.LocalDate(2026, 10, 14), edited.day)
        assertEquals(setOf("people"), edited.tags)
        s.care.editEntry(e.id, "x", atMillis = s.clock.millis + 86_400_000L)
        assertEquals(s.clock.millis, s.care.data.journal.single().atMillis, "never dated to a moment still to come")
    }

    @Test
    fun aSkippedRoutineLeavesTodayWithoutPayingAndCanBeBroughtBack() {
        val s = setup()
        s.giveCareLead()
        val water = s.care.addRoutine("Drink water")
        val stretch = s.care.addRoutine("Stretch")
        val before = s.points
        s.care.skipRoutine(water.id)
        assertEquals(listOf(stretch.id), s.care.todayRoutines().map { it.id }, "it leaves Today's list")
        assertEquals(0 to 1, s.care.routineCount(), "and the count is of what is planned")
        assertEquals(before, s.points, "skipping pays nothing")
        assertTrue(s.care.data.routines.first { it.id == water.id }.skippedOn(s.care.today))

        val again = CareStore(s.storage, s.clock, s.game, utc, rng = Random(3))
        assertTrue(again.data.routines.first { it.id == water.id }.skippedOn(again.today), "it is saved")

        s.care.unskipRoutine(water.id)
        assertEquals(2, s.care.todayRoutines().size, "undoing the skip puts it back")
        assertTrue(s.care.completeRoutine(water.id).any { it is Reward.Friendship }, "and it can still pay")
    }

    @Test
    fun skippingNeverUndoesADoneRoutineAndTickingASkippedOneClearsTheSkip() {
        val s = setup()
        val water = s.care.addRoutine("Drink water")
        s.care.completeRoutine(water.id)
        s.care.skipRoutine(water.id)
        assertFalse(s.care.data.routines.single().skippedOn(s.care.today), "a done routine cannot be skipped")
        s.care.undoRoutine(water.id)
        s.care.skipRoutine(water.id)
        s.care.completeRoutine(water.id)
        val r = s.care.data.routines.single()
        assertTrue(r.doneOn(s.care.today) && !r.skippedOn(s.care.today))
        assertEquals(1 to 1, s.care.routineCount())
    }

    @Test
    fun aSkippedRoutineGetsNoReminder() {
        val s = setup()
        val water = s.care.addRoutine("Drink water", time = LocalTime(18, 0))
        s.care.skipRoutine(water.id)
        assertFalse(CareReminders.stillWanted(s.care.data, s.game.data, water.id, s.care.today))
    }

    @Test
    fun yesterdayCanBeTickedAndUntickedButNeverPays() {
        val s = setup()
        s.giveCareLead()
        val water = s.care.addRoutine("Drink water")
        s.clock.advanceDays(1)
        val yesterday = s.care.today.minus(kotlinx.datetime.DatePeriod(days = 1))
        val before = s.points
        assertTrue(s.care.completeRoutine(water.id, yesterday).isEmpty())
        assertTrue(s.care.data.routines.single().doneOn(yesterday))
        assertEquals(before, s.points, "recording yesterday pays nothing")
        assertEquals(null, s.care.data.routines.single().paidOn)
        assertEquals(0 to 1, s.care.routineCount(), "and today is untouched")
        assertEquals(1 to 1, s.care.routineCount(yesterday), "yesterday's own count is done of planned")

        s.care.undoRoutine(water.id, yesterday)
        assertFalse(s.care.data.routines.single().doneOn(yesterday))

        assertTrue(s.care.completeRoutine(water.id).any { it is Reward.Friendship }, "today still pays after yesterday was ticked")
        s.care.completeRoutine(water.id, s.care.today.minus(kotlinx.datetime.DatePeriod(days = 2)))
        assertFalse(s.care.data.routines.single().doneOn(s.care.today.minus(kotlinx.datetime.DatePeriod(days = 2))), "only today and yesterday can be ticked")
    }
}
