package app.pokeplan.app

import app.pokeplan.core.pokemon.Items
import app.pokeplan.plan.MiniQuests
import app.pokeplan.plan.Rank
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NudgeStoreTest {
    private class FakeClock(var millis: Long) : Clock {
        override fun nowMillis() = millis
    }

    private val utc = TimeZone.UTC
    private fun at(day: Int, hour: Int) = LocalDateTime(2026, 10, day, hour, 0).toInstant(utc).toEpochMilliseconds()
    private val storage = MemoryStorage()
    private val clock = FakeClock(at(19, 9))
    private val s = GameStore(storage, clock, utc, Random(3)).also { it.start("Ash", 25) }

    private fun berries() = s.data.player.inventory.counts.filterKeys { it.startsWith("berry-") }.values.sum()

    @Test
    fun pickForMeRemembersThePickAndPaysItOnce() {
        val a = s.addTask("Write", Rank.B, planned = s.today)
        s.addTask("Read", Rank.B, planned = s.today)
        val pick = assertNotNull(s.pickForMe())
        assertEquals(pick.id, s.data.pickedTaskId)
        clock.millis += 60 * 60_000L
        s.completeTask(pick.id)
        clock.millis += 20_000L
        s.tick()
        assertEquals(null, s.data.pickedTaskId, "used up")
        assertTrue(a.id.isNotEmpty())
    }

    @Test
    fun miniQuestsPayABerryEachAndOneMoreForAllThree() {
        val before = berries()
        val quests = s.miniQuests()
        assertEquals(MiniQuests.PER_DAY, quests.size)
        assertTrue(s.canRerollMiniQuests())
        assertTrue(s.completeMiniQuest(quests[0].id))
        assertFalse(s.completeMiniQuest(quests[0].id), "once a day")
        assertFalse(s.canRerollMiniQuests(), "no reroll once one is done")
        s.completeMiniQuest(quests[1].id)
        s.completeMiniQuest(quests[2].id)
        assertEquals(before + 3 + MiniQuests.ALL_THREE_BONUS_BERRIES, berries())
        assertFalse(s.streakDay in s.data.activeDays, "mini-quests are not work: no active day")
        clock.millis = at(20, 9)
        assertFalse(s.miniQuestDone(s.miniQuests()[0].id), "a new day, new quests")
    }

    @Test
    fun aRerollChangesTheDaysQuests() {
        val first = s.miniQuests()
        s.rerollMiniQuests()
        assertFalse(s.canRerollMiniQuests())
        assertTrue(first != s.miniQuests())
    }

    @Test
    fun workAfterMidnightCountsForTheDayBefore() {
        clock.millis = at(20, 2)
        assertEquals(LocalDate(2026, 10, 19), s.streakDay)
        clock.millis = at(20, 4)
        assertEquals(LocalDate(2026, 10, 20), s.streakDay)
    }

    @Test
    fun theWelcomeBackGiftComesOncePerAbsence() {
        storage.save(GameStore.json.encodeToString(AppData.serializer(), s.data.copy(activeDays = setOf(LocalDate(2026, 10, 1)))))
        s.reload()
        assertTrue(s.welcomeBackDue())
        val candies = s.data.player.inventory[Items.RARE_CANDY]
        assertEquals(2, s.claimWelcomeBack().size)
        assertEquals(candies + GameStore.WELCOME_BACK_RARE_CANDIES, s.data.player.inventory[Items.RARE_CANDY])
        assertFalse(s.welcomeBackDue())
        assertTrue(s.claimWelcomeBack().isEmpty())
    }
}
