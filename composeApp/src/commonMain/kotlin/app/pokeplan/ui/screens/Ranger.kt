package app.pokeplan.ui.screens

import app.pokeplan.ui.theme.drawPixelDisc
import app.pokeplan.ui.theme.drawPixelRing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.dex.Rarity
import app.pokeplan.core.pokemon.GearTool
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** How many loops a Ranger request needs for [speciesId]: 2 for a common line up to 4 for a rare one or a legend. */
internal fun loopsNeeded(speciesId: Int): Int = when (HatchRules.rarity(speciesId)) {
    Rarity.COMMON -> 2
    Rarity.UNCOMMON -> 3
    else -> 4
}

/** How fast the loaner moves, in dp a second: its base Speed decides (a slow line drifts, a fast one darts). */
internal fun loanerSpeed(speciesId: Int): Float = 40f + Dex.defaultForm(speciesId).stats.speed * 0.9f

/** How far around [center] a stroke from [from] to [to] swings, in radians (positive clockwise on a screen). */
internal fun swing(center: Offset, from: Offset, to: Offset): Float {
    val a = atan2(from.y - center.y, from.x - center.x)
    val b = atan2(to.y - center.y, to.x - center.x)
    var d = b - a
    while (d > PI) d -= (2 * PI).toFloat()
    while (d < -PI) d += (2 * PI).toFloat()
    return d
}

/**
 * The Ranger request (docs/ecosystem.md): a Capture Styler. The tool's loaner roams the arena; draw loops around it with a
 * finger or the mouse (the line is UI chrome, the Pokémon its real sprite). Enough loops and it lends a hand to this one
 * tool: a loan, not a catch, so it never joins the party, the PC or the Dex. Gentle by design: nothing is lost by failing,
 * and a stroke that goes wide just doesn't count. With Reduce motion the loaner moves slowly.
 */
@Composable
internal fun RangerStyler(model: AppModel, tool: GearTool, onClose: () -> Unit) {
    val species = tool.loaner
    val need = loopsNeeded(species)
    val reduce = model.data.settings.reduceMotion
    val density = LocalDensity.current.density
    val spriteDp = 96
    var arena by remember { mutableStateOf(Offset(600f, 400f)) }
    var pos by remember { mutableStateOf(Offset(300f, 200f)) }
    var heading by remember { mutableFloatStateOf(0.7f) }
    var loops by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }
    val line = remember { mutableStateListOf<Offset>() }
    // How far the line has swung around the Pokémon since it began; a full turn is a loop.
    var turned by remember { mutableFloatStateOf(0f) }
    val speed = loanerSpeed(species) * density * (if (reduce) 0.35f else 1f)

    // The loaner wanders, bouncing softly off the edges and changing its mind now and then.
    LaunchedEffect(arena, done) {
        var t = 0f
        while (!done) {
            delay(16)
            t += 0.016f
            heading += sin(t * 1.7f) * 0.02f
            var next = pos + Offset(kotlin.math.cos(heading), sin(heading)) * (speed * 0.016f)
            val margin = spriteDp * density / 2
            if (next.x < margin || next.x > arena.x - margin) { heading = (PI - heading).toFloat(); next = pos }
            if (next.y < margin || next.y > arena.y - margin) { heading = -heading; next = pos }
            pos = next
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Text(
            if (done) "${Dex.species(species).name} will lend a hand to ${tool.label}!" else "Draw a loop around ${Dex.species(species).name}: $loops of $need",
            style = MaterialTheme.typography.titleSmall,
        )
        Box(
            Modifier.fillMaxWidth().height(260.dp).onSizeChanged { arena = Offset(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(done) {
                    if (done) return@pointerInput
                    detectDragGestures(
                        onDragStart = { line.clear(); line.add(it); turned = 0f },
                        onDragEnd = { line.clear(); turned = 0f },
                        onDragCancel = { line.clear(); turned = 0f },
                    ) { change, _ ->
                        change.consume()
                        val last = line.lastOrNull() ?: change.position
                        // Only strokes that stay near the Pokémon count; a stroke far away is just a line.
                        if (hypot(change.position.x - pos.x, change.position.y - pos.y) < spriteDp * density * 1.8f) turned += swing(pos, last, change.position)
                        line.add(change.position)
                        if (abs(turned) >= 2f * PI.toFloat() * 0.92f) {
                            loops++
                            line.clear(); turned = 0f
                            if (loops >= need) { done = true; model.store.lendGearHelper(tool) }
                        }
                    }
                },
        ) {
            Box(Modifier.fillMaxWidth().height(260.dp)) {
                Canvas(Modifier.fillMaxWidth().height(260.dp)) {
                    drawRect(Pixel.Border.copy(alpha = 0.25f))
                    if (line.size > 1) for (i in 1 until line.size) drawLine(Pixel.Red, line[i - 1], line[i], strokeWidth = 5f * density, cap = StrokeCap.Round)
                    drawPixelRing(Pixel.Red.copy(alpha = 0.18f), pos, spriteDp * density * 0.7f, 4f * density)
                }
                Box(Modifier.offset { IntOffset((pos.x - spriteDp * density / 2).roundToInt(), (pos.y - spriteDp * density / 2).roundToInt()) }.size(spriteDp.dp), contentAlignment = Alignment.Center) {
                    AnimatedSprite(Dex.defaultForm(species).id, false, Dex.species(species).name, size = spriteDp.dp, animate = !reduce)
                }
            }
        }
        PixelButton(if (done) "Done" else "Not now", onClose, Modifier.fillMaxWidth(), if (done) Pixel.Green else Pixel.InkSoft, compact = true)
    }
}
