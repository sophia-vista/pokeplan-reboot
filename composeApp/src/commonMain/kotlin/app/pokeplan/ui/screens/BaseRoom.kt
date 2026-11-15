package app.pokeplan.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.pokeplan.base.Cell
import app.pokeplan.base.Decorations
import app.pokeplan.base.Placement
import app.pokeplan.base.Room
import app.pokeplan.base.RoomGrid
import app.pokeplan.base.RoomLook
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.orNull
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelKey
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.theme.dialogueBox
import org.jetbrains.compose.resources.decodeToImageBitmap
import pokeplan.composeapp.generated.resources.Res

/*
 * The Secret Base room (docs/pokelife `MODULES/HOME.md` 9.5): Base's party standing in a Hoenn room among the decorations the player placed. A view of the
 * party, never a place a Pokémon is assigned to. The room, the decorations and the Pokémon are drawn at one pixel size (a whole-number scale, 5x on a phone
 * and 4x when narrower), flush with the dialogue frame (8 dp at the sides, 6 dp above and below), as the owner chose on 15 November 2026.
 */

/** The room's own pixels: 11 x 9 cells of 16 px. */
private const val ROOM_W = RoomGrid.WIDTH * RoomGrid.CELL_PX
private const val ROOM_H = RoomGrid.HEIGHT * RoomGrid.CELL_PX
private const val DECORATION_PX = 24
private const val SPRITE_BOX_PX = 48 // the box a Pokémon is drawn in (a frame of up to 48 art pixels); it stands on the bottom edge

private val bitmaps = mutableMapOf<String, ImageBitmap?>()

@Composable
private fun rememberHoenn(path: String): ImageBitmap? {
    val image by produceState(bitmaps[path], path) {
        value = bitmaps[path]
        if (value == null) {
            value = orNull { Res.readBytes(path).decodeToImageBitmap() }
            bitmaps[path] = value
        }
    }
    return image
}

/** The room's look for [lead] (HOME.md 9.5.3): the primary type of Base's lead picks a tree, a shrub or a cave; with no lead, a shrub. */
internal fun roomLook(lead: OwnedPokemon?): RoomLook = RoomLook.forType(lead?.form?.types?.firstOrNull()?.name)

/** The whole-number scale that fits [availablePx] pixels across: 5 at most (a phone), fewer when narrower. */
internal fun roomScale(availablePx: Float): Int = (availablePx / ROOM_W).toInt().coerceIn(1, 5)

/**
 * The room as a picture: the floor of [look], the flat decorations, then the standing ones and the Pokémon of [party] in order from the back of the room to
 * the front. With [arrange] a dot marks every empty cell, the cell of [held] is outlined, and a tap on a cell calls [onCell]. [scale] fixes the scale (a test);
 * by default it is the largest that fits.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun RoomPicture(
    look: RoomLook, placed: List<Placement>, party: List<OwnedPokemon>, modifier: Modifier = Modifier, animate: Boolean = true,
    arrange: Boolean = false, held: String? = null, scale: Int? = null, onCell: (Cell) -> Unit = {},
) {
    val density = LocalDensity.current
    val frameX = 8.dp
    val floor = rememberHoenn("files/hoenn/${look.tileset}.png")
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val s = scale ?: roomScale(with(density) { (maxWidth - frameX * 2).toPx() })
        fun px(artPixels: Int) = with(density) { (artPixels * s).toDp() }
        val things = buildList<Pair<Int, @Composable () -> Unit>> {
            placed.filter { Decorations.get(it.id)?.flat == false }.forEach { p ->
                val bottom = p.y * RoomGrid.CELL_PX + RoomGrid.CELL_PX
                add(bottom to { DecorationImage(p.id, p.x * RoomGrid.CELL_PX + RoomGrid.CELL_PX / 2, bottom, s) })
            }
            Room.standingCells(placed, party.size).forEachIndexed { i, cell ->
                val bottom = cell.y * RoomGrid.CELL_PX + RoomGrid.CELL_PX - 2
                val mon = party[i]
                add(bottom to {
                    AnimatedSprite(
                        mon.form.id, mon.isShiny, mon.nickname,
                        Modifier.offset { IntOffset((cell.x * RoomGrid.CELL_PX + RoomGrid.CELL_PX / 2 - SPRITE_BOX_PX / 2) * s, (bottom - SPRITE_BOX_PX) * s) },
                        size = px(SPRITE_BOX_PX), animate = animate, ground = 0.dp, fixedScale = s,
                    )
                })
            }
        }.sortedBy { it.first }
        val mark = Pixel.Warning
        Box(
            Modifier.dialogueBox().padding(horizontal = frameX, vertical = 6.dp).semantics {
                contentDescription = "Secret Base room, ${placed.size} decorations" + if (party.isNotEmpty()) ", ${party.joinToString { it.nickname }}" else ""
            },
        ) {
            Box(Modifier.size(px(ROOM_W), px(ROOM_H)).background(Color(0xFF1E1E1E))) {
                floor?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None) }
                placed.filter { Decorations.get(it.id)?.flat == true }.forEach { p ->
                    DecorationImage(p.id, p.x * RoomGrid.CELL_PX + RoomGrid.CELL_PX / 2, p.y * RoomGrid.CELL_PX + RoomGrid.CELL_PX, s)
                }
                things.forEach { (_, draw) -> draw() }
                if (arrange) Canvas(
                    Modifier.fillMaxSize().pointerInput(s, placed) {
                        detectTapGestures { at ->
                            val x = (at.x / (RoomGrid.CELL_PX * s)).toInt()
                            val y = (at.y / (RoomGrid.CELL_PX * s)).toInt()
                            if (RoomGrid.contains(x, y)) onCell(Cell(x, y))
                        }
                    },
                ) {
                    val cell = (RoomGrid.CELL_PX * s).toFloat()
                    RoomGrid.CELLS.forEach { c ->
                        val here = placed.firstOrNull { it.x == c.x && it.y == c.y }
                        if (here == null) {
                            val dot = 2f * s
                            drawRect(Color.White.copy(alpha = 0.6f), Offset(c.x * cell + (cell - dot) / 2, c.y * cell + (cell - dot) / 2), Size(dot, dot))
                        } else if (here.id == held) {
                            drawRect(mark, Offset(c.x * cell + s, c.y * cell + s), Size(cell - 2f * s, cell - 2f * s), style = Stroke(width = s.toFloat()))
                        }
                    }
                }
            }
        }
    }
}

/** One decoration, its sprite centred on [centre] with its feet on row [bottom] (the room's own pixels), at [scale] screen pixels each. */
@Composable
private fun DecorationImage(id: String, centre: Int, bottom: Int, scale: Int) {
    val image = rememberHoenn("files/hoenn/decorations/$id.png") ?: return
    val density = LocalDensity.current
    Image(
        image, Decorations.get(id)?.name,
        Modifier.offset { IntOffset((centre - DECORATION_PX / 2) * scale, (bottom - DECORATION_PX) * scale) }.size(with(density) { (DECORATION_PX * scale).toDp() }),
        contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None,
    )
}

/** The Room card's screen: the room, and under it a box to arrange from. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RoomView(model: AppModel, startArranging: Boolean = false, startHeld: String? = null) {
    val owned = model.data.decorations
    val data = model.baseData
    val placed = data.room.filter { it.id in owned }
    val player = model.data.player
    val party = player.partyOf(PartyIds.BASE)
    val inBox = Room.inBox(owned, data.room)
    var arrange by remember { mutableStateOf(startArranging) }
    var held by remember { mutableStateOf(startHeld) }
    val calm = LocalReduceMotion.current

    fun change(label: String, act: () -> Unit) {
        val before = model.baseData
        act()
        model.haptic()
        model.offerUndo(label) { model.base.restore(before) }
    }

    RoomPicture(roomLook(player.baseLead), placed, party, animate = !calm, arrange = arrange, held = held) { cell ->
        val here = placed.firstOrNull { it.x == cell.x && it.y == cell.y }
        val current = held
        val name = current?.let { Decorations.get(it)?.name }.orEmpty()
        when {
            here != null -> held = if (held == here.id) null else here.id
            current == null -> Unit
            placed.any { it.id == current } -> { change("Moved $name") { model.base.moveDecoration(current, cell.x, cell.y) }; held = null }
            else -> { change("Placed $name") { model.base.placeDecoration(current, cell.x, cell.y) }; held = null }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
        PixelButton(if (arrange) "Done" else "Arrange", { arrange = !arrange; held = null })
        val h = held
        if (arrange && h != null) {
            Text(Decorations.get(h)?.name.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            if (placed.any { it.id == h }) PixelButton("Put away", {
                val name = Decorations.get(h)?.name.orEmpty()
                change("Put away $name") { model.base.removeDecoration(h) }
                held = null
            }, color = Pixel.InkSoft)
        }
    }
    if (arrange && inBox.isNotEmpty()) TextBox(Modifier.fillMaxWidth()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            inBox.forEach { d ->
                PixelKey({ held = if (held == d.id) null else d.id }, Modifier.size(Spacing.Target), key = Pixel.Blue, selected = held == d.id, quiet = held != d.id, horizontal = 0.dp) {
                    val image = rememberHoenn("files/hoenn/decorations/${d.id}.png")
                    if (image != null) Image(image, d.name, Modifier.align(Alignment.Center).size(32.dp), filterQuality = FilterQuality.None)
                }
            }
        }
    }
}
