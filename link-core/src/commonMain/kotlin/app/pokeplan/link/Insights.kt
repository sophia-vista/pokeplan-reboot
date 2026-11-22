package app.pokeplan.link

import kotlinx.datetime.LocalDate

data class Insights(
    val birthday: ImportantDate?,
    val date: LocalDate?,
    val sunByDate: Sign?,
    val western: NatalChart?,
    val zodiac: ChineseYear?,
    val bazi: BaZiChart?,
    val kua: BaZi.Kua?,
    val lifePath: Int?,
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
