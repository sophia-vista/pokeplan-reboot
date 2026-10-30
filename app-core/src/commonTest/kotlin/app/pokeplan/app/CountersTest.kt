package app.pokeplan.app

import app.pokeplan.core.reward.CareKind
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CountersTest {
    private val monday = LocalDate(2026, 10, 26)
    private val tuesday = LocalDate(2026, 10, 27)

    @Test
    fun settingATallyDropsTheFamilysOtherDaysOnly() {
        val before = mapOf(
            "mq-$monday-walk" to 1, "mq-reroll-$monday" to 1, "milestones:$monday" to 2,
            "care-ROUTINE-$monday" to 3, Counters.CARE_EGG_METER to 7,
        )
        val after = Counters.MINI_QUEST.set(before, tuesday, 1, detail = "stretch")
        assertEquals(
            mapOf(
                "mq-reroll-$monday" to 1, "milestones:$monday" to 2, "care-ROUTINE-$monday" to 3,
                Counters.CARE_EGG_METER to 7, "mq-$tuesday-stretch" to 1,
            ),
            after,
        )
        assertTrue(Counters.MINI_QUEST.isSet(after, tuesday, "stretch"))
    }

    @Test
    fun theKeysKeepTheFormatOldSavesUse() {
        val counters = Counters.MILESTONES.set(mapOf("milestones:$monday" to 3), tuesday, 1)
        assertEquals(mapOf("milestones:$tuesday" to 1), counters, "yesterday's milestones are dropped, not kept forever")
        assertEquals(2, Counters.care(CareKind.CALM_BREAK).get(mapOf("care-CALM_BREAK-$monday" to 2), monday))
        assertEquals(mapOf(Counters.CARE_EGG_METER to 5, "care-SKILL-$tuesday" to 1), Counters.care(CareKind.SKILL).set(mapOf(Counters.CARE_EGG_METER to 5), tuesday, 1))
    }
}
