package app.pokeplan.app.plan

import app.pokeplan.app.testing.FakeClock
import app.pokeplan.app.AppData
import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.core.pokemon.CandyRules
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.reward.Reward
import app.pokeplan.core.reward.RewardFocus
import app.pokeplan.plan.EventRepeat
import app.pokeplan.plan.Rank
import app.pokeplan.plan.Repeat
import app.pokeplan.plan.PlanEvent
import app.pokeplan.plan.Task
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventCompanionTest {

    private val utc = TimeZone.UTC
    private val start = LocalDateTime(2026, 10, 19, 9, 0).toInstant(utc).toEpochMilliseconds()

    private fun started(): Pair<GameStore, FakeClock> {
        val clock = FakeClock(start)
        return GameStore(MemoryStorage(), clock, utc, Random(7)).also {
            it.start("Sophia", 25)
            val first = it.grantPokemon(133, "Quest: Eevee")
            assertTrue(it.setInParty(first.id, true))
            assertTrue(it.setPlanLead(first.id))
        } to clock
    }

    private fun exp(s: GameStore) = s.data.rewardLog.flatMap { it.rewards }.filterIsInstance<Reward.Exp>().sumOf { it.points }
    private fun expCount(s: GameStore) = s.data.rewardLog.flatMap { it.rewards }.count { it is Reward.Exp }
    private fun lead(s: GameStore) = s.data.player.planLead!!
    private fun at(h: Int, m: Int = 0) = LocalTime(h, m)

    @Test
    fun anEventPaysItsCompanionOnceAfterItEnds() {
        val (s, clock) = started()
        val e = s.addEvent("Gym", s.today, at(10), 60)
        s.setEventCompanion(e.id, lead(s).id)
        clock.advanceMinutes(90); s.tick()
        assertEquals(0, exp(s))
        clock.advanceMinutes(40); s.tick()
        assertEquals(2 * CandyRules.XP_PER_CANDY, exp(s), "an hour is two candies' worth")
        clock.advanceMinutes(60); s.tick()
        assertEquals(1, expCount(s), "never twice")
    }

    @Test
    fun aCompanionNamedAfterTheEventEndedPaysNothing() {
        val (s, clock) = started()
        val e = s.addEvent("Earlier", s.today, at(7), 60)
        clock.advanceMinutes(30)
        s.setEventCompanion(e.id, lead(s).id)
        clock.advanceMinutes(60); s.tick()
        assertEquals(0, expCount(s))
    }

    @Test
    fun onlyAPlanPartyPokemonCanBeACompanion() {
        val (s, _) = started()
        val outsider = s.grantPokemon(16, "Quest: Pidgey")
        s.setInParty(outsider.id, false)
        val e = s.addEvent("Gym", s.today, at(10), 60)
        s.setEventCompanion(e.id, outsider.id)
        assertNull(s.data.events.single().companionId)
        s.setEventCompanion(e.id, lead(s).id)
        assertEquals(lead(s).id, s.data.events.single().companionId)
        s.setEventCompanion(e.id, null)
        assertNull(s.data.events.single().companionId)
    }

    @Test
    fun aCompanionThatLeftThePartyIsNotOwedLater() {
        val (s, clock) = started()
        val buddy = s.grantPokemon(25, "Quest: Pikachu")
        assertTrue(s.setInParty(buddy.id, true))
        val e = s.addEvent("Gym", s.today, at(10), 60)
        s.setEventCompanion(e.id, buddy.id)
        s.setInParty(buddy.id, false)
        clock.advanceMinutes(130); s.tick()
        assertEquals(0, expCount(s))
        s.setInParty(buddy.id, true)
        clock.advanceMinutes(10); s.tick()
        assertEquals(0, expCount(s), "the occurrence was settled when it ended")
    }

    @Test
    fun anAllDayEventNeverPays() {
        val (s, clock) = started()
        val day = s.addAllDayEvent("Home", s.today)
        s.setEventCompanion(day.id, lead(s).id)
        clock.advanceMinutes(24 * 60); s.tick()
        assertEquals(0, expCount(s))
    }

    @Test
    fun aQuarterHourIsTheShortestEventAndPaysOneCandy() {
        val (s, clock) = started()
        val e = s.addEvent("Call", s.today, at(10), 10)
        assertEquals(15, s.data.events.single().minutes)
        s.setEventCompanion(e.id, lead(s).id)
        clock.advanceMinutes(90); s.tick()
        assertEquals(CandyRules.XP_PER_CANDY, exp(s))
    }

    @Test
    fun aRepeatingEventPaysEachOccurrenceOnceAndNotASkippedOne() {
        val (s, clock) = started()
        val e = s.addEvent("Gym", s.today, at(10), 30, repeat = EventRepeat.DAILY)
        s.setEventCompanion(e.id, lead(s).id)
        clock.advanceMinutes(120); s.tick()
        assertEquals(1, expCount(s), "today's")
        s.deleteOccurrence(PlanEvent.occurrenceId(e.id, s.today.plus(DatePeriod(days = 1))))
        clock.advanceMinutes(24 * 60); s.tick()
        assertEquals(1, expCount(s))
        clock.advanceMinutes(24 * 60); s.tick()
        assertEquals(2, expCount(s))
    }

    @Test
    fun theDailyCapStopsAtSixCandiesOfExpAcrossEvents() {
        val (s, clock) = started()
        listOf(10, 13, 16).forEach { h -> s.setEventCompanion(s.addEvent("Long $h", s.today, at(h), 180).id, lead(s).id) }
        clock.advanceMinutes(12 * 60); s.tick()
        assertEquals(EventCap, exp(s))
    }

    @Test
    fun draggingAPaidEventToALaterDayDoesNotPayItAgain() {
        val (s, clock) = started()
        val e = s.addEvent("Gym", s.today, at(10), 60)
        s.setEventCompanion(e.id, lead(s).id)
        clock.advanceMinutes(130); s.tick()
        assertEquals(1, expCount(s))
        s.moveEvent(e.id, s.today.plus(DatePeriod(days = 1)), at(10))
        clock.advanceMinutes(24 * 60); s.tick()
        assertEquals(1, expCount(s))
    }

    @Test
    fun shiftingADailyEventBackAndForthNeverPaysMoreThanTheCap() {
        val (s, clock) = started()
        val e = s.addEvent("Long", s.today, at(10), 180, repeat = EventRepeat.DAILY)
        s.setEventCompanion(e.id, lead(s).id)
        clock.advanceMinutes(5 * 60); s.tick()
        assertEquals(4 * CandyRules.XP_PER_CANDY, exp(s))
        s.updateEvent(e.id, day = s.today.plus(DatePeriod(days = -1)))
        s.tick()
        s.updateEvent(e.id, day = s.today)
        s.tick()
        assertEquals(4 * CandyRules.XP_PER_CANDY, exp(s))
        assertEquals(1, expCount(s))
    }

    @Test
    fun anEventDraggedIntoTimeThatHasPassedPaysNothing() {
        val (s, clock) = started()
        val e = s.addEvent("Later", s.today.plus(DatePeriod(days = 1)), at(10), 60)
        s.setEventCompanion(e.id, lead(s).id)
        s.moveEvent(e.id, s.today, at(7))
        clock.advanceMinutes(30); s.tick()
        assertEquals(0, expCount(s))
    }

    @Test
    fun aRepeatingTasksCopiesKeepItsRewardFocus() {
        val (s, clock) = started()
        val task = s.addTask("Stretch", Rank.C, planned = s.today, reward = RewardFocus.EGG)
        assertTrue(s.makeRepeating(task.id, Repeat.Weekly()))
        clock.advanceMinutes(8 * 24 * 60); s.refresh()
        val copies = s.data.tasks.filter { it.seriesId != null }
        assertTrue(copies.size >= 2, "the series has made copies: ${copies.size}")
        assertTrue(copies.all { it.reward == RewardFocus.EGG }, "every copy keeps Egg: ${copies.map { it.reward }}")
    }

    @Test
    fun aSaveFromBeforeTheseFieldsLoadsWithTheirDefaults() {
        val task = Json { ignoreUnknownKeys = true }.decodeFromString(Task.serializer(), """{"id":"t","title":"Old","createdAtMillis":1}""")
        assertEquals(RewardFocus.EXP, task.reward)
        val event = Json.decodeFromString(PlanEvent.serializer(), """{"id":"e","title":"Old","day":"2026-10-19","start":"10:00"}""")
        assertNull(event.companionId)
        assertEquals(0L, event.companionSince)
        assertTrue(event.paid.isEmpty())
        assertEquals(RewardFocus.EXP, AppData().settings.defaultReward)
    }

    @Test
    fun aNewTaskTakesTheSettingsDefaultUnlessGivenOne() {
        val (s, _) = started()
        assertEquals(RewardFocus.EXP, s.addTask("a").reward)
        s.changeSettings { it.copy(defaultReward = RewardFocus.EGG) }
        assertEquals(RewardFocus.EGG, s.addTask("b").reward)
        assertEquals(RewardFocus.ITEMS, s.addTask("c", reward = RewardFocus.ITEMS).reward)
    }

    private companion object {
        val EventCap = 6 * CandyRules.XP_PER_CANDY
    }
}
