package app.pokeplan.ui.screens

import app.pokeplan.ui.theme.Sizes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pokeplan.app.GameStore
import app.pokeplan.app.GearData
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.gear.Randomizer
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val NOT_SECURE = "Everyday randomness for choosing a dinner or who goes first. It is not secure and not for gambling or anything that has to be provably fair."

/**
 * The Picker: a list you keep on this phone, each option with a weight (1 to 10), edited in place and moved up or down; pick one or several without
 * repeats, skip the last pick, shuffle the order, and (only if you turn it on) keep the last picks. The result is announced to a screen reader.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PickerTool(model: AppModel, onUse: () -> Unit) {
    val store = model.store
    val gear = model.data.gear
    val options = model.data.pickerOptions
    var text by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf<List<String>>(emptyList()) }
    var picking by remember { mutableStateOf(false) }
    var skipLast by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var editText by remember { mutableStateOf("") }
    var count by remember { mutableStateOf(1) }
    val reduce = model.data.settings.reduceMotion
    val scope = rememberCoroutineScope()
    fun moveWeights(old: String, new: String) = store.changeGear { g -> g.copy(pickerWeights = (g.pickerWeights - old).let { w -> g.pickerWeights[old]?.let { w + (new to it) } ?: w }) }

    fun pick() {
        if (options.size < 2 || picking) return
        picking = true
        scope.launch {
            // A quick roll through the list before it settles, unless motion is off.
            if (!reduce) repeat(8) { shown = listOf(options.random()); delay(70L + it * 18L) }
            val result = if (count == 1) listOfNotNull(Randomizer.pick(options, gear.pickerWeights, Random, avoid = if (skipLast) gear.pickerHistory.firstOrNull() ?: shown.firstOrNull() else null))
            else Randomizer.pickMany(options, gear.pickerWeights, count, Random)
            shown = result
            if (gear.pickerHistoryOn) store.changeGear { g -> g.copy(pickerHistory = (result + g.pickerHistory).take(GearData.MAX_HISTORY)) }
            picking = false
            onUse()
        }
    }

    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelField(text, { text = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, label = "Add something to pick from")
            PixelIconButton("plus", "Add", { store.setPickerOptions(options + text); text = "" }, Pixel.InkSoft, enabled = text.isNotBlank() && options.size < GameStore.MAX_PICKER_OPTIONS)
            options.forEachIndexed { i, option ->
                if (editing == i) {
                    PixelField(editText, { editText = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, label = "Change it")
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        PixelIconButton("check", "Save", {
                            val new = editText.trim()
                            if (new.isNotEmpty() && (new == option || new !in options)) { store.setPickerOptions(options.toMutableList().also { it[i] = new }); if (new != option) moveWeights(option, new) }
                            editing = null
                        }, Pixel.Blue)
                        PixelIconButton("close", "Cancel", { editing = null }, Pixel.InkSoft)
                    }
                } else {
                    val weight = Randomizer.weightOf(option, gear.pickerWeights)
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                        Text(option + if (weight > 1) "  (x$weight)" else "", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            PixelIconButton("pencil", "Edit", { editing = i; editText = option }, Pixel.InkSoft)
                            PixelIconButton("arrow-up", "Up", { if (i > 0) store.setPickerOptions(options.toMutableList().also { it.add(i - 1, it.removeAt(i)) }) }, Pixel.InkSoft, enabled = i > 0)
                            PixelIconButton("arrow-down", "Down", { if (i < options.lastIndex) store.setPickerOptions(options.toMutableList().also { it.add(i + 1, it.removeAt(i)) }) }, Pixel.InkSoft, enabled = i < options.lastIndex)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            PixelIconButton("minus", "Less likely", { store.changeGear { g -> g.copy(pickerWeights = g.pickerWeights + (option to (weight - 1).coerceAtLeast(1))) } }, Pixel.InkSoft, enabled = weight > 1)
                            PixelIconButton("plus", "More", { store.changeGear { g -> g.copy(pickerWeights = g.pickerWeights + (option to (weight + 1).coerceAtMost(Randomizer.MAX_WEIGHT))) } }, Pixel.InkSoft, enabled = weight < Randomizer.MAX_WEIGHT)
                            PixelIconButton("trash", "Remove", { store.setPickerOptions(options - option); store.changeGear { g -> g.copy(pickerWeights = g.pickerWeights - option) } }, Pixel.InkSoft)
                        }
                    }
                }
            }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            FieldLabel("How many to pick")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                (1..3).forEach { n -> PixelButton(n.toString(), { count = n }, Modifier.weight(1f), if (count == n) Pixel.Blue else Pixel.InkSoft, compact = true, enabled = n <= options.size.coerceAtLeast(1)) }
                PixelButton(if (skipLast) "Skip last: on" else "Skip last: off", { skipLast = !skipLast }, Modifier.weight(2f), if (skipLast) Pixel.Green else Pixel.InkSoft, compact = true, enabled = count == 1)
            }
            val answer = if (shown.isEmpty()) "" else shown.joinToString("\n")
            if (answer.isNotEmpty()) Text(answer, Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "Picked: " + shown.joinToString(", ") }, style = MaterialTheme.typography.headlineSmall)
            PixelButton(if (picking) "Picking" else if (count == 1) "Pick one" else "Pick $count", { pick() }, Modifier.fillMaxWidth(), Pixel.Blue, enabled = options.size >= 2 && !picking)
            PixelButton("Shuffle the order", { store.setPickerOptions(Randomizer.shuffle(options, Random)) }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true, enabled = options.size >= 2)
            PixelButton(if (gear.pickerHistoryOn) "Keeping the last picks" else "Not keeping the last picks", { store.changeGear { it.copy(pickerHistoryOn = !it.pickerHistoryOn, pickerHistory = if (it.pickerHistoryOn) emptyList() else it.pickerHistory) } }, Modifier.fillMaxWidth(), if (gear.pickerHistoryOn) Pixel.Green else Pixel.InkSoft, compact = true)
            if (gear.pickerHistoryOn && gear.pickerHistory.isNotEmpty()) Text("Lately: " + gear.pickerHistory.take(8).joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            Text(NOT_SECURE, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }
}

/** A coin that spins and lands, and dice (`2d6+3`, `d20`) with quick buttons and, if you turn it on, a short history. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CoinTool(model: AppModel, onUse: () -> Unit) {
    val store = model.store
    val gear = model.data.gear
    val spin = remember { Animatable(0f) }
    var side by remember { mutableStateOf<String?>(null) }
    var flipping by remember { mutableStateOf(false) }
    var dice by remember { mutableStateOf("2d6") }
    var rolled by remember { mutableStateOf<String?>(null) }
    var count by remember { mutableStateOf(1) }
    var historyOn by remember { mutableStateOf(gear.diceHistory.isNotEmpty()) }
    val reduce = model.data.settings.reduceMotion
    val scope = rememberCoroutineScope()
    fun roll(spec: String) {
        val r = Randomizer.roll(spec, Random)
        rolled = r?.text ?: "That is not a roll. Try 2d6, d20 or 3d8+2 (up to ${Randomizer.MAX_DICE} dice of 2 to ${Randomizer.MAX_SIDES} sides)."
        if (r != null && historyOn) store.changeGear { g -> g.copy(diceHistory = (listOf(r.text) + g.diceHistory).take(GearData.MAX_HISTORY)) }
        onUse()
    }
    TextBox {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Box(Modifier.height(120.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                ItemIcon("bottle-cap", Modifier.size(Sizes.SpriteL).graphicsLayer { rotationX = spin.value }, size = Sizes.SpriteL, description = "Coin")
            }
            Text(side ?: "Flip it", Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.headlineSmall)
            PixelButton(if (flipping) "Flipping" else "Toss the coin", {
                if (!flipping) scope.launch {
                    flipping = true
                    val heads = Random.nextBoolean()
                    if (!reduce) {
                        // Four whole turns from the last whole turn, plus a half turn for tails, so the coin always lands showing its face.
                        val to = (spin.value / 360f).toInt() * 360f + 1440f + if (heads) 0f else 180f
                        spin.animateTo(to, tween(900))
                    }
                    side = if (heads) "Heads" else "Tails"
                    flipping = false
                    onUse()
                }
            }, Modifier.fillMaxWidth(), Pixel.Blue, enabled = !flipping)
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            FieldLabel("Dice")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                listOf(4, 6, 8, 10, 12, 20, 100).forEach { sides -> PixelButton("d$sides", { roll("${if (count == 1) "" else count}d$sides") }, color = Pixel.Blue, compact = true) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                listOf(1, 2, 3, 4).forEach { n -> PixelButton("x$n", { count = n }, Modifier.weight(1f), if (count == n) Pixel.Blue else Pixel.InkSoft, compact = true) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                PixelField(dice, { dice = it.take(14) }, Modifier.weight(1f), singleLine = true, placeholder = "Your own (2d6+3)")
                PixelButton("Roll", { roll(dice) }, Modifier.weight(0.5f), Pixel.Blue, compact = true)
            }
            rolled?.let { Text(it, Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.titleMedium) }
            PixelButton(if (historyOn) "Keeping the last rolls" else "Not keeping the last rolls", { historyOn = !historyOn; if (!historyOn) store.changeGear { it.copy(diceHistory = emptyList()) } }, Modifier.fillMaxWidth(), if (historyOn) Pixel.Green else Pixel.InkSoft, compact = true)
            if (historyOn) gear.diceHistory.take(8).forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
            Text(NOT_SECURE, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }
}
