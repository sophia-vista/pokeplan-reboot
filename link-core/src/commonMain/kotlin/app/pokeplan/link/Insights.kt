package app.pokeplan.link

import kotlinx.datetime.LocalDate

/**
 * Everything LINK can work out about a person's birth, worked out when asked for and never stored (LINK.md 2.6). It needs the Birthday date; with the year
 * it adds the Chinese and numerology readings, and with a time and place the rising sign. Nothing here leaves LINK, pays, or feeds a Pokémon.
 */
data class Insights(
    val birthday: ImportantDate?,
    /** The birth date with its year, or null if the birthday is missing or has no year. */
    val date: LocalDate?,
    /** The newspaper sun sign from the month and day alone, when the year is unknown. */
    val sunByDate: Sign?,
    val western: NatalChart?,
    val zodiac: ChineseYear?,
    val bazi: BaZiChart?,
    val kua: BaZi.Kua?,
    val lifePath: Int?,
    /** True when the year is known but outside 1902 to 2098, so the Chinese readings are not offered. */
    val chineseOutOfRange: Boolean,
) {
    companion object {
        fun of(data: LinkData, person: Person): Insights {
            val b = data.birthdayOf(person.id)
            val date = b?.birthDate()
            val birth = person.profile.birth
            val sunByDate = b?.let { Sign.ofDate(it.month, it.day) }
            if (date == null) return Insights(b, null, sunByDate, null, null, null, null, null, false)
            val inRange = BaZi.supports(date.year)
            return Insights(
                birthday = b, date = date, sunByDate = sunByDate,
                western = Sky.chart(date, birth.minutes, birth.utcOffsetMinutes, birth.place),
                zodiac = if (inRange) BaZi.zodiacYear(date) else null,
                bazi = if (inRange) BaZi.chart(date, birth.minutes, birth.utcOffsetMinutes) else null,
                kua = if (inRange) birth.kua?.let { BaZi.kua(date, birth.minutes, birth.utcOffsetMinutes, it) } else null,
                lifePath = BaZi.lifePath(date),
                chineseOutOfRange = !inRange,
            )
        }
    }
}
