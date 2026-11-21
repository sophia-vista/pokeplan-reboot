package app.pokeplan.app

import app.pokeplan.core.reward.CareKind
import app.pokeplan.core.reward.HealKind
import app.pokeplan.core.reward.BaseKind
import app.pokeplan.core.reward.LinkMoment
import app.pokeplan.core.reward.MintKind
import kotlinx.datetime.LocalDate

internal class PeriodCounter(private val family: String) {
    private fun key(period: LocalDate, detail: String?): String = "$family$period" + detail?.let { "-$it" }.orEmpty()

    fun get(counters: Map<String, Int>, period: LocalDate, detail: String? = null): Int = counters[key(period, detail)] ?: 0

    fun isSet(counters: Map<String, Int>, period: LocalDate, detail: String? = null): Boolean = key(period, detail) in counters

    fun set(counters: Map<String, Int>, period: LocalDate, value: Int, detail: String? = null): Map<String, Int> =
        counters.filterKeys { !isOtherPeriod(it, period) } + (key(period, detail) to value)

    private fun isOtherPeriod(key: String, period: LocalDate): Boolean =
        key.startsWith(family) && DATE.matches(key.substring(family.length).take(10)) && !key.startsWith("$family$period")

    private companion object {
        val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    }
}

internal object Counters {
    val WARM = PeriodCounter("warm-")

    val EVENT_CANDIES = PeriodCounter("event-candies-")

    val PET = PeriodCounter("pet-")

    val BEFRIEND = PeriodCounter("befriend-")

    val MILESTONES = PeriodCounter("milestones:")

    val WELCOME = PeriodCounter("welcome-")

    val MAGIC_GUARD = PeriodCounter("magic-guard-")

    val MINI_QUEST = PeriodCounter("mq-")

    val TOWN_FINDS = PeriodCounter("town-finds-")

    val MINI_QUEST_REROLL = PeriodCounter("mq-reroll-")

    private val care = CareKind.entries.associateWith { PeriodCounter("care-${it.name}-") }

    fun care(kind: CareKind): PeriodCounter = care.getValue(kind)

    private val heal = HealKind.entries.associateWith { PeriodCounter("heal-${it.name}-") }

    fun heal(kind: HealKind): PeriodCounter = heal.getValue(kind)

    private val mint = MintKind.entries.associateWith { PeriodCounter("mint-${it.name}-") }

    fun mint(kind: MintKind): PeriodCounter = mint.getValue(kind)

    private val home = BaseKind.entries.associateWith { PeriodCounter("home-${it.name}-") }

    fun base(kind: BaseKind): PeriodCounter = home.getValue(kind)

    val SAFARI = PeriodCounter("safari-")

    const val HOME_EGG_METER = "home-egg-meter"

    const val HOME_DECOR_METER = "home-decor-meter"

    private val link = LinkMoment.entries.associateWith { PeriodCounter("link-${it.name}-") }

    fun link(kind: LinkMoment): PeriodCounter = link.getValue(kind)

    const val LINK_EGG_METER = "link-egg-meter"

    const val MINT_EGG_METER = "mint-egg-meter"

    const val HEAL_EGG_METER = "heal-egg-meter"

    const val CARE_EGG_METER = "care-egg-meter"
}
