package app.pokeplan.app

import app.pokeplan.care.CareRules
import app.pokeplan.core.reward.CareKind
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The world's backstop must be the same numbers Care's own limits use, or one of them drifts and either blocks a payment the
 * module allows or stops backstopping (POKEMON_ECOSYSTEM.md, each module's share is capped).
 */
class CareDailyCapTest {
    @Test
    fun theWorldCapIsEveryCareRulesLimit() {
        assertEquals(CareRules.ROUTINES_PAID_PER_DAY, GameStore.CARE_DAILY_CAP[CareKind.ROUTINE])
        assertEquals(CareRules.BREAKS_PAID_PER_DAY, GameStore.CARE_DAILY_CAP[CareKind.CALM_BREAK])
        assertEquals(CareRules.SKILLS_PAID_PER_DAY, GameStore.CARE_DAILY_CAP[CareKind.SKILL])
        assertEquals(CareRules.RHYTHMS_PAID_PER_WEEK, GameStore.CARE_DAILY_CAP[CareKind.WEEKLY_RHYTHM])
        assertEquals(CareRules.PLAYS_PAID_PER_DAY, GameStore.CARE_DAILY_CAP[CareKind.PLAY])
    }

    @Test
    fun everyCareKindHasACap() {
        assertEquals(CareKind.entries.toSet(), GameStore.CARE_DAILY_CAP.keys)
    }
}
