package app.pokeplan.app

import app.pokeplan.core.reward.CareKind
import app.pokeplan.core.reward.HealKind
import app.pokeplan.core.reward.BaseKind
import app.pokeplan.core.reward.LinkMoment
import app.pokeplan.core.reward.MintKind
import kotlinx.datetime.LocalDate

/**
 * A tally in [AppData.counters] that holds for one period, named by its first day: today's petting, this week's Magic
 * Guard. Its keys are the family, the day and an optional detail ("mq-2026-10-30-walk"). Setting one drops the family's
 * keys for other periods, so the map never grows. The keys are part of the save: keep their format.
 */
internal class PeriodCounter(private val family: String) {
    private fun key(period: LocalDate, detail: String?): String = "$family$period" + detail?.let { "-$it" }.orEmpty()

    fun get(counters: Map<String, Int>, period: LocalDate, detail: String? = null): Int = counters[key(period, detail)] ?: 0

    fun isSet(counters: Map<String, Int>, period: LocalDate, detail: String? = null): Boolean = key(period, detail) in counters

    fun set(counters: Map<String, Int>, period: LocalDate, value: Int, detail: String? = null): Map<String, Int> =
        counters.filterKeys { !isOtherPeriod(it, period) } + (key(period, detail) to value)

    // Another family can share the start of the name ("mq-reroll-" and "mq-"): a key is ours only if a date follows it.
    private fun isOtherPeriod(key: String, period: LocalDate): Boolean =
        key.startsWith(family) && DATE.matches(key.substring(family.length).take(10)) && !key.startsWith("$family$period")

    private companion object {
        val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    }
}

/** Every tally the store keeps, and the day each one counts by. */
internal object Counters {
    /** Friendship-warming work done on the streak day. */
    val WARM = PeriodCounter("warm-")

    /** Candies' worth of exp PLAN event companions earned for events of the day (the period is the day an occurrence started). */
    val EVENT_CANDIES = PeriodCounter("event-candies-")

    /** Friendship petting gave today. */
    val PET = PeriodCounter("pet-")

    /** Care visitors befriended today. */
    val BEFRIEND = PeriodCounter("befriend-")

    /** Goal milestones paid today. */
    val MILESTONES = PeriodCounter("milestones:")

    /** The welcome-back gift, once per absence: the period is the last active day before it. */
    val WELCOME = PeriodCounter("welcome-")

    /** Magic Guard, once a week: the period is the week's first day. */
    val MAGIC_GUARD = PeriodCounter("magic-guard-")

    /** Mini-quests done on the streak day; the detail is the quest. */
    val MINI_QUEST = PeriodCounter("mq-")

    /** Which of today's finds on the launcher town's floor are collected: a bit per find (`TownFinds`). */
    val TOWN_FINDS = PeriodCounter("town-finds-")

    /** The streak day's mini-quest reroll. */
    val MINI_QUEST_REROLL = PeriodCounter("mq-reroll-")

    private val care = CareKind.entries.associateWith { PeriodCounter("care-${it.name}-") }

    /** Care moments of [kind] paid today. */
    fun care(kind: CareKind): PeriodCounter = care.getValue(kind)

    private val heal = HealKind.entries.associateWith { PeriodCounter("heal-${it.name}-") }

    /** HEAL moments of [kind] paid today. */
    fun heal(kind: HealKind): PeriodCounter = heal.getValue(kind)

    private val mint = MintKind.entries.associateWith { PeriodCounter("mint-${it.name}-") }

    /** MINT moments of [kind] paid in the period: the day for a reconciled account, the week's first day for a review. */
    fun mint(kind: MintKind): PeriodCounter = mint.getValue(kind)

    private val home = BaseKind.entries.associateWith { PeriodCounter("home-${it.name}-") }

    /** HOME moments of [kind] paid today. */
    fun base(kind: BaseKind): PeriodCounter = home.getValue(kind)

    /** Points towards the next Habitat Egg. It carries over from day to day. */
    const val HOME_EGG_METER = "home-egg-meter"

    /** Points towards the next Secret Base decoration (BASE.md 9.5.4). It carries over from day to day. */
    const val HOME_DECOR_METER = "home-decor-meter"

    private val link = LinkMoment.entries.associateWith { PeriodCounter("link-${it.name}-") }

    /** LINK moments of [kind] paid today. */
    fun link(kind: LinkMoment): PeriodCounter = link.getValue(kind)

    /** Points towards the next Postcard Egg. It carries over from day to day. */
    const val LINK_EGG_METER = "link-egg-meter"

    /** Points towards the next Ledger Egg. It carries over from day to day. */
    const val MINT_EGG_METER = "mint-egg-meter"

    /** Points towards the next Field Egg. It carries over from day to day. */
    const val HEAL_EGG_METER = "heal-egg-meter"

    /** Points towards the next Care Egg. It carries over from day to day. */
    const val CARE_EGG_METER = "care-egg-meter"
}
