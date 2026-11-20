package app.pokeplan.ui.link

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import app.pokeplan.link.Answer
import app.pokeplan.link.Attachment
import app.pokeplan.link.BaZi
import app.pokeplan.link.Birth
import app.pokeplan.link.Birthplaces
import app.pokeplan.link.Body
import app.pokeplan.link.Disc
import app.pokeplan.link.Element
import app.pokeplan.link.Insights
import app.pokeplan.link.Level
import app.pokeplan.link.LinkRules
import app.pokeplan.link.LoreChinese
import app.pokeplan.link.LorePersonality
import app.pokeplan.link.LoreWestern
import app.pokeplan.link.LoveLanguage
import app.pokeplan.link.Person
import app.pokeplan.link.Profile
import app.pokeplan.link.ProfileKit
import app.pokeplan.link.Social
import app.pokeplan.link.Trait
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Report
import app.pokeplan.ui.screens.Actions
import app.pokeplan.ui.screens.FormDisclosure
import app.pokeplan.ui.screens.FormGroup
import app.pokeplan.ui.screens.FormSave
import app.pokeplan.ui.screens.FormStack
import app.pokeplan.ui.screens.ModuleTabs
import app.pokeplan.ui.screens.PixelChoice
import app.pokeplan.ui.screens.PixelDropdown
import app.pokeplan.ui.screens.RecordRow
import app.pokeplan.ui.screens.Rule
import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.theme.NoticeTone
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.SectionLabel
import app.pokeplan.ui.theme.Spacing

/**
 * A person's slambook page (docs/pokelife `MODULES/LINK.md` 2.6): About (socials, likes, dislikes, slambook answers), Mind (the personality labels the
 * user notes) and Stars (birth details, the Western chart, the Chinese zodiac, the Four Pillars, Kua and Life Path). Everything is typed in by the
 * user or worked out on the phone from a birth date they gave; a reading is explained when tapped and never scored, stored or sent anywhere.
 */
private val tint: androidx.compose.ui.graphics.Color @Composable get() = ModuleAccent.LINK.accent
private val wordKeys = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
private val sentenceKeys = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
private val numberKeys = KeyboardOptions(keyboardType = KeyboardType.Number)
private val decimalKeys = KeyboardOptions(keyboardType = KeyboardType.Decimal)

@Composable
internal fun PersonProfileTab(model: AppModel, p: Person, firstTab: Int = 0, startEditing: Boolean = false) {
    var sub by remember(p.id) { mutableStateOf(firstTab) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        ModuleTabs(listOf("About", "Mind", "Stars"), sub, tint) { sub = it }
        when (sub) {
            0 -> AboutView(model, p, startEditing)
            1 -> MindView(model, p, startEditing)
            else -> StarsView(model, p, startEditing)
        }
    }
}

/** One reading: a title and a quiet line, and a tap opens what it means. */
@Composable
private fun Reading(title: String, detail: String, meaning: String) {
    var open by remember(title, detail) { mutableStateOf(false) }
    RecordRow(title, detail, onClick = { open = !open }, onClickLabel = if (open) "Hide what it means" else "Show what it means")
    if (open) Text(meaning, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft, modifier = Modifier.padding(bottom = Spacing.S))
}

private fun save(model: AppModel, p: Person, profile: Profile): Boolean = model.link.updateProfile(p.id, profile).record != null

// ----- About ------------------------------------------------------------------------------------------------------------------

@Composable
private fun AboutView(model: AppModel, p: Person, startEditing: Boolean = false) {
    var editing by remember(p.id) { mutableStateOf(startEditing) }
    val pr = p.profile
    if (editing) {
        AboutForm(model, p) { editing = false }
        return
    }
    val empty = pr.socials.isEmpty() && pr.likes.isEmpty() && pr.dislikes.isEmpty() && pr.answers.isEmpty()
    pr.socials.forEach { RecordRow(it.handle, it.platform) }
    if (pr.likes.isNotEmpty()) RecordRow(pr.likes.joinToString(", "), "Likes")
    if (pr.dislikes.isNotEmpty()) RecordRow(pr.dislikes.joinToString(", "), "Dislikes")
    pr.answers.forEach { RecordRow(it.answer, it.question) }
    if (empty) Text("Nothing written yet.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    PixelButton(if (empty) "Fill in" else "Edit", { editing = true }, Modifier.fillMaxWidth(), tint, compact = true)
}

@Composable
private fun AboutForm(model: AppModel, p: Person, onDone: () -> Unit) {
    var socials by remember(p.id) { mutableStateOf(p.profile.socials) }
    var likes by remember(p.id) { mutableStateOf(p.profile.likes.joinToString(", ")) }
    var dislikes by remember(p.id) { mutableStateOf(p.profile.dislikes.joinToString(", ")) }
    var answers by remember(p.id) { mutableStateOf(p.profile.answers) }
    var platform by remember(p.id) { mutableStateOf(ProfileKit.socialPlatforms.first()) }
    var handle by remember(p.id) { mutableStateOf("") }
    var question by remember(p.id) { mutableStateOf<String?>(null) }
    var own by remember(p.id) { mutableStateOf("") }
    var message by remember(p.id) { mutableStateOf<String?>(null) }
    FormStack {
        FormGroup(title = "Socials") {
            socials.forEachIndexed { i, s ->
                RecordRow(s.handle, s.platform, trail = { PixelButton("Remove", { socials = socials.filterIndexed { j, _ -> j != i } }, color = Pixel.InkSoft, compact = true) })
            }
            PixelDropdown(ProfileKit.socialPlatforms, platform, { it ?: "" }, { if (it != null) platform = it }, Modifier.fillMaxWidth(), key = tint, label = "Platform")
            PixelField(handle, { handle = it.take(ProfileKit.TEXT_MAX) }, Modifier.fillMaxWidth(), label = "Handle or link", singleLine = true)
            PixelButton("Add", { socials = socials + Social(platform, handle.trim()); handle = "" }, Modifier.fillMaxWidth(), tint, enabled = handle.isNotBlank(), compact = true)
        }
        FormGroup(title = "Likes and dislikes") {
            PixelField(likes, { likes = it.take(400) }, Modifier.fillMaxWidth(), label = "Likes, separated by commas", minLines = 2, keyboardOptions = sentenceKeys)
            PixelField(dislikes, { dislikes = it.take(400) }, Modifier.fillMaxWidth(), label = "Dislikes, separated by commas", minLines = 2, keyboardOptions = sentenceKeys)
        }
        FormGroup(title = "Slambook") {
            answers.forEachIndexed { i, a ->
                PixelField(a.answer, { v -> answers = answers.mapIndexed { j, x -> if (j == i) x.copy(answer = v.take(ProfileKit.ANSWER_MAX)) else x } }, Modifier.fillMaxWidth(), label = a.question, keyboardOptions = sentenceKeys)
                PixelButton("Remove", { answers = answers.filterIndexed { j, _ -> j != i } }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            }
            val unused = ProfileKit.questions.filter { q -> answers.none { it.question.equals(q, ignoreCase = true) } }
            if (unused.isNotEmpty()) PixelDropdown(unused, question, { it ?: "" }, { q -> if (q != null) { answers = answers + Answer(q, ""); question = null } }, Modifier.fillMaxWidth(), key = tint, label = "Add a question", placeholder = "Choose")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.Bottom) {
                PixelField(own, { own = it.take(ProfileKit.TEXT_MAX) }, Modifier.weight(1f), label = "Your own question", singleLine = true, keyboardOptions = sentenceKeys)
                PixelButton("Add", { answers = answers + Answer(own.trim(), ""); own = "" }, color = tint, enabled = own.isNotBlank() && answers.none { it.question.equals(own.trim(), ignoreCase = true) })
            }
        }
        FormSave("Save", {
            val next = p.profile.copy(socials = socials, likes = likes.split(',', '\n').toList(), dislikes = dislikes.split(',', '\n').toList(), answers = answers)
            if (save(model, p, next)) onDone() else message = "That could not be saved."
        }, tint)
        PixelButton("Cancel", onDone, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        message?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

// ----- Mind -------------------------------------------------------------------------------------------------------------------

@Composable
private fun MindView(model: AppModel, p: Person, startEditing: Boolean = false) {
    var editing by remember(p.id) { mutableStateOf(startEditing) }
    val pr = p.profile
    if (editing) {
        MindForm(model, p) { editing = false }
        return
    }
    val any = pr.mbti.isNotEmpty() || pr.enneagram != 0 || pr.loveLanguages.isNotEmpty() || pr.attachment != null || pr.disc.isNotEmpty() || pr.bigFive.isNotEmpty()
    if (pr.mbti.isNotEmpty()) Reading(pr.mbti, "Type", LorePersonality.mbti(pr.mbti))
    if (pr.enneagram != 0) Reading(
        if (pr.enneagramWing != 0) "${pr.enneagram}w${pr.enneagramWing}" else "${pr.enneagram}", "Enneagram",
        LorePersonality.enneagram(pr.enneagram) + if (pr.enneagramWing != 0) " Leans toward type ${pr.enneagramWing}." else "",
    )
    pr.loveLanguages.forEachIndexed { i, l -> Reading(l.label, if (i == 0) "Love language, first" else "Love language, ${i + 1}", LorePersonality.loveLanguage(l)) }
    pr.attachment?.let { Reading(it.label, "Attachment", LorePersonality.attachment(it)) }
    pr.disc.forEachIndexed { i, d -> Reading(d.label, if (i == 0) "DISC, main" else "DISC, also", LorePersonality.disc(d)) }
    pr.bigFive.entries.sortedBy { it.key.ordinal }.forEach { (t, l) -> Reading("${l.label} ${t.label.lowercase()}", "Big Five", LorePersonality.bigFive(t, l)) }
    if (!any) Text("No personality notes yet.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    else Text(LorePersonality.DISCLAIMER, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    PixelButton(if (any) "Edit" else "Add", { editing = true }, Modifier.fillMaxWidth(), tint, compact = true)
}

@Composable
private fun MindForm(model: AppModel, p: Person, onDone: () -> Unit) {
    val pr = p.profile
    var mbti by remember(p.id) { mutableStateOf(pr.mbti.takeIf { it.isNotEmpty() }) }
    var type by remember(p.id) { mutableStateOf(pr.enneagram.takeIf { it != 0 }) }
    var wing by remember(p.id) { mutableStateOf(pr.enneagramWing.takeIf { it != 0 }) }
    var love1 by remember(p.id) { mutableStateOf(pr.loveLanguages.getOrNull(0)) }
    var love2 by remember(p.id) { mutableStateOf(pr.loveLanguages.getOrNull(1)) }
    var attachment by remember(p.id) { mutableStateOf(pr.attachment) }
    var disc1 by remember(p.id) { mutableStateOf(pr.disc.getOrNull(0)) }
    var disc2 by remember(p.id) { mutableStateOf(pr.disc.getOrNull(1)) }
    var big by remember(p.id) { mutableStateOf(pr.bigFive) }
    var message by remember(p.id) { mutableStateOf<String?>(null) }
    FormStack {
        FormGroup(title = "Type tests") {
            PixelDropdown(listOf<String?>(null) + ProfileKit.mbtiTypes, mbti, { it ?: "None" }, { mbti = it }, Modifier.fillMaxWidth(), key = tint, label = "MBTI")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelDropdown(listOf<Int?>(null) + (1..9).toList(), type, { it?.toString() ?: "None" }, { type = it; wing = null }, Modifier.weight(1f), key = tint, label = "Enneagram")
                val wings = type?.let { t -> listOf<Int?>(null, if (t == 1) 9 else t - 1, if (t == 9) 1 else t + 1) } ?: listOf(null)
                PixelDropdown(wings, wing, { it?.toString() ?: "None" }, { wing = it }, Modifier.weight(1f), key = tint, label = "Wing", enabled = type != null)
            }
            PixelChoice(listOf<Attachment?>(null) + Attachment.entries, attachment, { it?.label ?: "None" }, { attachment = it }, label = "Attachment style", key = tint)
        }
        FormGroup(title = "Love language") {
            PixelDropdown(listOf<LoveLanguage?>(null) + LoveLanguage.entries, love1, { it?.label ?: "None" }, { love1 = it }, Modifier.fillMaxWidth(), key = tint, label = "First")
            PixelDropdown(listOf<LoveLanguage?>(null) + LoveLanguage.entries.filter { it != love1 }, love2, { it?.label ?: "None" }, { love2 = it }, Modifier.fillMaxWidth(), key = tint, label = "Second")
        }
        FormGroup(title = "DISC") {
            PixelDropdown(listOf<Disc?>(null) + Disc.entries, disc1, { it?.label ?: "None" }, { disc1 = it }, Modifier.fillMaxWidth(), key = tint, label = "Main")
            PixelDropdown(listOf<Disc?>(null) + Disc.entries.filter { it != disc1 }, disc2, { it?.label ?: "None" }, { disc2 = it }, Modifier.fillMaxWidth(), key = tint, label = "Also")
        }
        FormGroup(title = "Big Five") {
            Trait.entries.forEach { t ->
                PixelChoice(listOf<Level?>(null) + Level.entries, big[t], { it?.label ?: "None" }, { l -> big = if (l == null) big - t else big + (t to l) }, label = t.label, key = tint)
            }
        }
        FormSave("Save", {
            val next = pr.copy(
                mbti = mbti.orEmpty(), enneagram = type ?: 0, enneagramWing = wing ?: 0, loveLanguages = listOfNotNull(love1, love2.takeIf { it != love1 }),
                attachment = attachment, disc = listOfNotNull(disc1, disc2.takeIf { it != disc1 }), bigFive = big,
            )
            if (save(model, p, next)) onDone() else message = "That could not be saved."
        }, tint)
        PixelButton("Cancel", onDone, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        message?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

// ----- Stars ------------------------------------------------------------------------------------------------------------------

private val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private fun fmt(lon: Double): String = "${(((lon % 360.0) + 360.0) % 360.0 % 30.0).toInt()}°"

@Composable
private fun StarsView(model: AppModel, p: Person, startEditing: Boolean = false) {
    var editing by remember(p.id) { mutableStateOf(startEditing) }
    val data = model.linkData
    val ins = Insights.of(data, p)
    val birth = p.profile.birth
    if (editing) {
        BirthForm(model, p, ins.birthday?.month, ins.birthday?.day, ins.birthday?.year) { editing = false }
        return
    }
    if (ins.birthday == null) {
        Text("No birthday yet.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        PixelButton("Add birth details", { editing = true }, Modifier.fillMaxWidth(), tint, compact = true)
        return
    }
    val sunOnly = ins.sunByDate
    if (ins.date == null) {
        if (sunOnly != null) Reading(sunOnly.label, "Sun sign from the day", LoreWestern.sign(sunOnly) + " " + LoreWestern.element(sunOnly.element))
        Text("Add the birth year for the full chart.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        PixelButton("Add birth details", { editing = true }, Modifier.fillMaxWidth(), tint, compact = true)
        return
    }
    val chart = ins.western!!
    SectionLabel("Western chart")
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        listOf(Body.SUN, Body.MOON).forEach { b ->
            val pl = chart.of(b)
            val other = chart.maybe[b]
            val title = if (other != null) "${b.label} in ${pl.sign.label} or ${other.label}" else "${b.label} in ${pl.sign.label}"
            Reading(title, listOfNotNull(if (other != null) "birth time decides" else fmt(pl.longitude), chart.house(b)?.let { "house $it" }).joinToString(" · "),
                LoreWestern.body(b) + " " + LoreWestern.sign(pl.sign) + " " + LoreWestern.element(pl.sign.element) + " " + LoreWestern.modality(pl.sign.modality))
        }
        chart.rising?.let { r -> Reading("Rising in ${r.label}", fmt(chart.ascendant!!), LoreWestern.RISING + " " + LoreWestern.sign(r)) }
        if (chart.rising == null) Text("Add a birth time and place for the rising sign.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        Rule()
        chart.placements.filter { it.body != Body.SUN && it.body != Body.MOON }.forEach { pl ->
            val other = chart.maybe[pl.body]
            val title = if (other != null) "${pl.body.label} in ${pl.sign.label} or ${other.label}" else "${pl.body.label} in ${pl.sign.label}"
            val detail = listOfNotNull(fmt(pl.longitude), if (pl.retrograde) "retrograde" else null, chart.house(pl.body)?.let { "house $it" }).joinToString(" · ")
            Reading(title, detail, LoreWestern.body(pl.body) + " " + LoreWestern.sign(pl.sign) + if (pl.retrograde) " " + LoreWestern.RETROGRADE else "")
        }
        if (chart.aspects.isNotEmpty()) {
            Rule()
            chart.aspects.take(8).forEach { a -> Reading("${a.a.label} ${a.kind.label} ${a.b.label}", "${(a.orb * 10).toInt() / 10.0}° apart from exact", LoreWestern.aspect(a.kind)) }
        }
        Text(LoreWestern.DISCLAIMER + " " + LoreWestern.APPROXIMATE, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    }

    SectionLabel("Chinese zodiac")
    if (ins.chineseOutOfRange) {
        Text("Chinese readings cover ${BaZi.FIRST_YEAR} to ${BaZi.LAST_YEAR}.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    } else {
        ins.zodiac?.let { z -> Reading("Year of the ${z.animal}", z.pillar.words, LoreChinese.animal(z.animal) + " " + LoreChinese.element(z.pillar.stem.element)) }
        ins.bazi?.let { b ->
            val rows = listOf("Year" to b.year, "Month" to b.month, "Day" to b.day, "Hour" to b.hour)
            rows.forEachIndexed { i, (name, pillar) ->
                if (pillar != null) Reading("$name pillar: ${pillar.label}", pillar.words, LoreChinese.pillars[i] + if (i == 2) " " + LoreChinese.dayMaster(b.dayMaster) else "")
            }
            if (b.hour == null) Text("Add a birth time for the hour pillar.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            val counts = b.elements
            Reading(Element.entries.joinToString(" · ") { "${it.label} ${counts.getValue(it)}" }, "Five elements", LoreChinese.balance(counts))
        }
        ins.kua?.let { k ->
            Reading("Kua ${k.number}", if (k.east) "East group" else "West group", LoreChinese.kuaGroup(k.east) + " " + k.lucky.zip(LoreChinese.kuaDirections).joinToString(" ") { (d, w) -> "${d.label}: $w." })
        }
        if (ins.kua == null && birth.kuaFormula.isEmpty()) Text("Pick a Kua formula in the birth details for the Kua number.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    }
    SectionLabel("Numbers")
    ins.lifePath?.let { n -> Reading("Life Path $n", "From the birth date", LoreChinese.lifePath(n)) }
    PixelButton("Birth details", { editing = true }, Modifier.fillMaxWidth(), tint, compact = true)
}

@Composable
private fun BirthForm(model: AppModel, p: Person, m0: Int?, d0: Int?, y0: Int?, onDone: () -> Unit) {
    val b = p.profile.birth
    var month by remember(p.id) { mutableStateOf(m0?.toString().orEmpty()) }
    var day by remember(p.id) { mutableStateOf(d0?.toString().orEmpty()) }
    var year by remember(p.id) { mutableStateOf(y0?.toString().orEmpty()) }
    var time by remember(p.id) { mutableStateOf(b.minutes?.let { ProfileKit.timeText(it) }.orEmpty()) }
    var offset by remember(p.id) { mutableStateOf(ProfileKit.offsetText(b.utcOffsetMinutes)) }
    val named = Birthplaces.all.firstOrNull { it.name == b.placeName && it.latitude == b.latitude && it.longitude == b.longitude }
    var place by remember(p.id) { mutableStateOf(named?.name ?: if (b.latitude != null) OTHER else null) }
    var lat by remember(p.id) { mutableStateOf(b.latitude?.toString().orEmpty()) }
    var lon by remember(p.id) { mutableStateOf(b.longitude?.toString().orEmpty()) }
    var formula by remember(p.id) { mutableStateOf(b.kuaFormula) }
    var message by remember(p.id) { mutableStateOf<String?>(null) }
    FormStack {
        FormGroup(title = "Birth date") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelDropdown((1..12).toList(), month.toIntOrNull(), { mm -> if (mm == null) "" else monthNames[mm - 1] }, { mm -> if (mm != null) month = mm.toString() }, Modifier.weight(1.2f), key = tint, label = "Month")
                PixelField(day, { day = it.filter(Char::isDigit).take(2) }, Modifier.weight(0.8f), label = "Day", singleLine = true, keyboardOptions = numberKeys)
                PixelField(year, { year = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), label = "Year", placeholder = "unknown", singleLine = true, keyboardOptions = numberKeys)
            }
        }
        FormGroup(title = "Time and place") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelField(time, { time = it.take(5) }, Modifier.weight(1f), label = "Time, 24 hour", placeholder = "unknown", singleLine = true)
                PixelField(offset, { offset = it.take(7) }, Modifier.weight(1f), label = "UTC offset", singleLine = true)
            }
            PixelDropdown(listOf<String?>(null) + Birthplaces.all.map { it.name } + OTHER, place, { it ?: "Unknown" }, { place = it }, Modifier.fillMaxWidth(), key = tint, label = "Birthplace")
            if (place == OTHER) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelField(lat, { lat = it.take(9) }, Modifier.weight(1f), label = "Latitude", singleLine = true, keyboardOptions = decimalKeys)
                PixelField(lon, { lon = it.take(10) }, Modifier.weight(1f), label = "Longitude", singleLine = true, keyboardOptions = decimalKeys)
            }
        }
        FormGroup(title = "Kua") {
            PixelChoice(listOf("", "A", "B"), formula, { when (it) { "A" -> "Male"; "B" -> "Female"; else -> "None" } }, { formula = it ?: "" }, label = "Formula", key = tint)
        }
        FormSave("Save", {
            val mm = month.toIntOrNull(); val dd = day.toIntOrNull()
            val minutes = time.takeIf { it.isNotBlank() }?.let { ProfileKit.parseTime(it) }
            val off = ProfileKit.parseOffset(offset)
            val coords = if (place == OTHER) Birthplaces.coordinates(lat, lon) else null
            val chosen = place?.let { Birthplaces.named(it) }
            when {
                mm == null || dd == null -> message = "Write the day and the month as numbers." // copy-ok: result
                time.isNotBlank() && minutes == null -> message = "Write the time like 08:05." // copy-ok: result
                off == null -> message = "Write the UTC offset like 8 or -5:30." // copy-ok: result
                place == OTHER && coords == null -> message = "Write the latitude and longitude as numbers." // copy-ok: result
                else -> {
                    val out = model.link.setBirthday(p.id, mm, dd, year.toIntOrNull())
                    if (out.record == null) message = out.refused ?: "That could not be saved."
                    else {
                        val birth = Birth(
                            minutes = minutes, utcOffsetMinutes = off,
                            placeName = chosen?.name ?: if (coords != null) "Custom" else "", latitude = chosen?.latitude ?: coords?.first, longitude = chosen?.longitude ?: coords?.second,
                            kuaFormula = formula,
                        )
                        if (save(model, p, p.profile.copy(birth = birth))) onDone() else message = "That could not be saved."
                    }
                }
            }
        }, tint, enabled = month.isNotBlank() && day.isNotBlank())
        PixelButton("Cancel", onDone, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        message?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

private const val OTHER = "Other place"

/** One line under a person's contact details: sign, animal and type, only from what is known. Empty when nothing is. */
internal fun profileSummary(model: AppModel, p: Person): String {
    val ins = Insights.of(model.linkData, p)
    val sun = ins.western?.of(Body.SUN)?.sign ?: ins.sunByDate
    return listOfNotNull(sun?.label, ins.zodiac?.animal, p.profile.mbti.takeIf { it.isNotEmpty() }, p.profile.loveLanguages.firstOrNull()?.label).joinToString(" · ")
}
