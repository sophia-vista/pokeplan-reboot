package app.pokeplan.heal

import kotlinx.datetime.LocalDate

/**
 * Heal's readings as modules (owner, 21 November 2026): one tile per [ReadingGroup], and tapping it opens that group's own list, one line per day. Heart rate, blood oxygen
 * and skin temperature are a range for the day (lowest to highest), never one entry per reading. Values are shown as read and never called good, bad, high or low.
 */
enum class ReadingGroup(val label: String, val icon: String, val types: List<VitalType>) {
    STEPS("Steps", "x-speed", emptyList()),
    HEART("Heart rate", "hp-up", listOf(VitalType.HEART_RATE, VitalType.HEART_RATE_LOW, VitalType.HEART_RATE_HIGH, VitalType.RESTING_HEART_RATE)),
    SPO2("Blood oxygen", "fresh-water", listOf(VitalType.SPO2, VitalType.SPO2_LOW, VitalType.SPO2_HIGH)),
    SKIN_TEMPERATURE("Skin temperature", "scope-lens", listOf(VitalType.SKIN_TEMPERATURE, VitalType.SKIN_TEMPERATURE_LOW, VitalType.SKIN_TEMPERATURE_HIGH)),
    SLEEP("Sleep length and score", "moon-stone", listOf(VitalType.SLEEP_LENGTH, VitalType.SLEEP_SCORE)),
    STRESS("Stress", "star-piece", listOf(VitalType.STRESS)),
}

/** One day of a [ReadingGroup]: its date and the line to show, plus how many readings it holds (to delete the day). */
data class DailyReading(val day: LocalDate, val line: String, val count: Int)

object ReadingGroups {
    /** The groups that have anything saved, in menu order. */
    fun withData(data: HealData): List<ReadingGroup> = ReadingGroup.entries.filter { g -> if (g == ReadingGroup.STEPS) data.steps.isNotEmpty() else data.vitals.any { it.type in g.types } }

    /** One line per day for [group], newest day first. */
    fun daily(data: HealData, group: ReadingGroup): List<DailyReading> {
        if (group == ReadingGroup.STEPS) return data.steps.sortedByDescending { it.day }.map { DailyReading(it.day, thousands(it.steps) + " steps", 1) }
        val byDay = data.vitals.filter { it.type in group.types }.groupBy { it.day }
        return byDay.entries.sortedByDescending { it.key }.map { (day, list) -> DailyReading(day, line(group, list), list.size) }
    }

    /** The newest day's line, for the tile; null when the group has nothing. */
    fun latest(data: HealData, group: ReadingGroup): DailyReading? = daily(data, group).firstOrNull()

    private fun line(group: ReadingGroup, list: List<VitalReading>): String {
        fun range(vararg types: VitalType, unit: String): String? {
            val v = list.filter { it.type in types }.map { it.value }
            if (v.isEmpty()) return null
            val lo = v.min(); val hi = v.max()
            return if (lo == hi) HealRules.format(lo) + unit else HealRules.format(lo) + "–" + HealRules.format(hi) + unit
        }
        fun one(type: VitalType) = list.filter { it.type == type }.maxByOrNull { it.atMillis }?.value
        return when (group) {
            ReadingGroup.STEPS -> ""
            ReadingGroup.HEART -> listOfNotNull(range(VitalType.HEART_RATE, VitalType.HEART_RATE_LOW, VitalType.HEART_RATE_HIGH, unit = " bpm"), one(VitalType.RESTING_HEART_RATE)?.let { "resting " + HealRules.format(it) + " bpm" }).joinToString(", ")
            ReadingGroup.SPO2 -> range(VitalType.SPO2, VitalType.SPO2_LOW, VitalType.SPO2_HIGH, unit = "%").orEmpty()
            ReadingGroup.SKIN_TEMPERATURE -> range(VitalType.SKIN_TEMPERATURE, VitalType.SKIN_TEMPERATURE_LOW, VitalType.SKIN_TEMPERATURE_HIGH, unit = " °C").orEmpty()
            ReadingGroup.SLEEP -> listOfNotNull(
                list.filter { it.type == VitalType.SLEEP_LENGTH }.sortedByDescending { it.value }.takeIf { it.isNotEmpty() }?.joinToString(", ") { HealRules.duration(it.value.toInt()) },
                one(VitalType.SLEEP_SCORE)?.let { "score " + HealRules.format(it) },
            ).joinToString(", ")
            ReadingGroup.STRESS -> one(VitalType.STRESS)?.let { HealRules.format(it) }.orEmpty()
        }
    }

    private fun thousands(n: Int) = n.toString().reversed().chunked(3).joinToString(",").reversed()
}
