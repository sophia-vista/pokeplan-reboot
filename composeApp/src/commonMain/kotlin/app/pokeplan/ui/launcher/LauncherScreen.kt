package app.pokeplan.ui.launcher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pokeplan.app.ThemeMode
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.PixelBackground
import app.pokeplan.ui.screens.PlanBuddyStage
import app.pokeplan.ui.screens.TaskRow
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.produceState
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.SectionLabel
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.Place
import app.pokeplan.ui.screens.LocalReduceMotion
import app.pokeplan.ui.screens.PlanBuddyStage
import app.pokeplan.ui.screens.TaskRow
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelTheme
import app.pokeplan.ui.theme.SectionLabel
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the launcher screens ask of the phone; Android fills it from `LauncherApps` and the system settings. */
interface LauncherHost {
    /** An app's own icon, drawn off the main thread and cached by the host; null when it has none. */
    suspend fun icon(key: String): ImageBitmap?
    fun open(key: String)
    fun appInfo(key: String)
    fun uninstall(key: String)
    /** Opens Android's own settings page for choosing the home app. */
    fun openHomeSettings()
    /** Opens PokéLife itself on [place] (Focus from the Focus button, Home from the buddy). */
    fun openApp(place: Place)
}

private val rowShape = RoundedCornerShape(14.dp)

/** The wallpaper behind the launcher: a real game box wallpaper, the same on the home page and under the app list. */
private const val WALLPAPER = "bw12"

/**
 * PokéLife as the phone's home screen: the home page (the buddy, today's tasks, a Focus button, pinned apps) on a Pokémon
 * pixel wallpaper and, one swipe or tap up, every app A to Z over that wallpaper blurred and darkened. It shows and starts
 * what already pays and pays nothing itself.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LauncherScreen(model: AppModel, apps: List<LauncherApp>, prefs: LauncherPrefs, onPrefs: (LauncherPrefs) -> Unit, isDefault: Boolean, host: LauncherHost) {
    val data = model.data
    val dark = when (data.settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    LaunchedEffect(Unit) {
        while (true) {
            model.tick()
            delay(1000)
        }
    }
    var listOpen by remember { mutableStateOf(false) }
    var sheetKey by remember { mutableStateOf<String?>(null) }
    val sheetApp = sheetKey?.let { key -> apps.firstOrNull { it.key == key } }
    PixelTheme(dark) {
        CompositionLocalProvider(LocalReduceMotion provides data.settings.reduceMotion) {
            PixelBackground(WALLPAPER, Modifier.fillMaxSize().background(Pixel.Paper)) {
                // Home is the one screen that must never close: back only steps out of the sheet or the list.
                BackHandler(enabled = true) { if (sheetApp != null) sheetKey = null else listOpen = false }
                HomePage(model, apps, prefs, isDefault, host, onAllApps = { listOpen = true }, onHold = { sheetKey = it.key })
                AnimatedVisibility(listOpen, enter = slideInVertically { it / 4 } + fadeIn(), exit = slideOutVertically { it / 4 } + fadeOut()) {
                    AppListPage(apps, prefs, host, onClose = { listOpen = false }, onHold = { sheetKey = it.key })
                }
                if (sheetApp != null) AppSheet(sheetApp, prefs, onPrefs, onClose = { sheetKey = null }, host = host)
            }
        }
    }
}

@Composable
private fun HomePage(model: AppModel, apps: List<LauncherApp>, prefs: LauncherPrefs, isDefault: Boolean, host: LauncherHost, onAllApps: () -> Unit, onHold: (LauncherApp) -> Unit) {
    val data = model.data
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.L)) {
            if (!isDefault) TextBox {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Text("Make PokéLife your home screen", style = MaterialTheme.typography.titleSmall)
                    Text("Pick it as the home app in Android's settings. Nothing changes until you do.", style = MaterialTheme.typography.bodyMedium)
                    PixelButton("Open home settings", host::openHomeSettings, Modifier.fillMaxWidth(), Pixel.Blue)
                }
            }
            if (!data.onboarded) {
                TextBox {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Text("Welcome", style = MaterialTheme.typography.titleSmall)
                        Text("Open PokéLife once to meet your first Pokémon.", style = MaterialTheme.typography.bodyMedium)
                        PixelButton("Open PokéLife", { host.openApp(Place.HOME) }, Modifier.fillMaxWidth(), Pixel.Red)
                    }
                }
            } else {
                PlanBuddyStage(model, onOpen = { host.openApp(Place.HOME) })
                val open = model.store.openTodayTasks()
                TextBox {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        SectionLabel("Today")
                        if (open.isEmpty()) Text("Nothing left for today.", style = MaterialTheme.typography.bodyMedium)
                        open.take(4).forEach { task -> TaskRow(task) { model.completeTask(task.id) } }
                        if (open.size > 4) Text("and ${open.size - 4} more in the Planner", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    }
                }
                val running = data.focus != null
                PixelButton(if (running) "Back to focus" else "Start focus", { host.openApp(Place.FOCUS) }, Modifier.fillMaxWidth(), if (running) Pixel.Green else Pixel.Red)
            }
        }
        // Pinned apps stay in reach under the scrolling page, above the handle.
        val pinned = LauncherList.pinned(apps, prefs)
        if (pinned.isNotEmpty()) Box(Modifier.padding(horizontal = Spacing.Page)) { PinnedGrid(pinned, host, onHold) }
        // The handle for the app list: a tap or a swipe up opens it.
        Box(
            Modifier.fillMaxWidth().height(Spacing.Target + Spacing.S)
                .pointerInput(Unit) {
                    var drag = 0f
                    detectVerticalDragGestures(onDragStart = { drag = 0f }, onDragEnd = { if (drag < -40f) onAllApps() }) { _, dy -> drag += dy }
                }
                .clickable(role = Role.Button, onClickLabel = "Open all apps", onClick = onAllApps),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(40.dp).height(4.dp).background(Color.White, CircleShape))
                Text("All apps", style = MaterialTheme.typography.labelMedium, color = Color.White, modifier = Modifier.padding(top = Spacing.XS))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedGrid(pinned: List<LauncherApp>, host: LauncherHost, onHold: (LauncherApp) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(20.dp)).padding(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        pinned.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { app ->
                    Column(
                        Modifier.weight(1f).clip(rowShape).combinedClickable(role = Role.Button, onLongClick = { onHold(app) }, onClick = { host.open(app.key) }).padding(vertical = Spacing.XS),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppIcon(host, app.key, 52.dp)
                        Text(app.label, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** An app's own icon; a quiet disc stands in while it loads, so scrolling never waits on it. */
@Composable
private fun AppIcon(host: LauncherHost, key: String, size: androidx.compose.ui.unit.Dp) {
    val icon by produceState<ImageBitmap?>(null, key) { value = host.icon(key) }
    val shown = icon
    if (shown != null) Image(shown, contentDescription = null, modifier = Modifier.size(size))
    else Box(Modifier.size(size).background(Color.White.copy(alpha = 0.2f), CircleShape))
}

private sealed interface ListRow {
    data class Header(val letter: Char) : ListRow
    data class Entry(val app: LauncherApp) : ListRow
}

/** Every app A to Z over the wallpaper, blurred and darkened (the blur shows from Android 12; the dark veil always). */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun AppListPage(apps: List<LauncherApp>, prefs: LauncherPrefs, host: LauncherHost, onClose: () -> Unit, onHold: (LauncherApp) -> Unit) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<AppCategory?>(null) }
    var showHidden by remember { mutableStateOf(false) }
    val categories = LauncherList.categories(apps, prefs)
    // A category that emptied (its last app moved or hidden) falls back to all apps.
    if (category != null && categories.none { it.first == category }) category = null
    val anyHidden = prefs.hidden.any { key -> apps.any { it.key == key } }
    if (showHidden && !anyHidden) showHidden = false
    val shown = LauncherList.visible(apps, prefs, query, category, hidden = showHidden)
    val sections = query.isBlank()
    val rows = if (sections) buildList<ListRow> { shown.forEach { app -> if ((lastOrNull() as? ListRow.Entry)?.app?.letter() != app.letter()) add(ListRow.Header(app.letter())); add(ListRow.Entry(app)) } } else shown.map<LauncherApp, ListRow> { ListRow.Entry(it) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    BackHandler(enabled = true) { if (query.isNotEmpty()) query = "" else onClose() }
    val soft = Color.White.copy(alpha = 0.7f)
    Box(Modifier.fillMaxSize()) {
        PixelBackground(WALLPAPER, Modifier.fillMaxSize().blur(24.dp).background(Pixel.Paper)) {}
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)))
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.padding(horizontal = Spacing.Page, vertical = Spacing.S), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Box(
                    Modifier.weight(1f).height(Spacing.Target).background(Color.White.copy(alpha = 0.14f), rowShape).padding(horizontal = Spacing.M),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) Text("Search apps", style = MaterialTheme.typography.bodyLarge, color = soft)
                    BasicTextField(
                        query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search apps" },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White), cursorBrush = SolidColor(Color.White),
                    )
                }
                Box(Modifier.height(Spacing.Target).clip(rowShape).clickable(role = Role.Button, onClick = onClose).padding(horizontal = Spacing.S), contentAlignment = Alignment.Center) {
                    Text("Close", style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
            }
            LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = Spacing.Page), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                item { Chip("All", "", category == null && !showHidden) { category = null; showHidden = false } }
                items(categories) { (c, n) -> Chip(c.label, "$n", category == c && !showHidden, icon = c.icon) { category = c; showHidden = false } }
                if (anyHidden) item { Chip("Hidden", "", showHidden) { showHidden = true; category = null } }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (rows.isEmpty()) Text(
                    if (query.isBlank()) "No apps here." else "No app matches \"${query.trim()}\".",
                    style = MaterialTheme.typography.bodyMedium, color = soft, modifier = Modifier.align(Alignment.Center),
                )
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = Spacing.Page, end = 40.dp, top = Spacing.S, bottom = Spacing.L)) {
                    items(rows, key = { row -> when (row) { is ListRow.Header -> "#h${row.letter}"; is ListRow.Entry -> row.app.key } }) { row ->
                        when (row) {
                            is ListRow.Header -> Text(row.letter.toString(), style = MaterialTheme.typography.titleSmall, color = soft, modifier = Modifier.padding(top = Spacing.M, bottom = Spacing.XS, start = Spacing.XS))
                            is ListRow.Entry -> Row(
                                Modifier.fillMaxWidth().clip(rowShape).combinedClickable(role = Role.Button, onLongClick = { onHold(row.app) }, onClick = { host.open(row.app.key) })
                                    .padding(vertical = 6.dp, horizontal = Spacing.XS),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                            ) {
                                AppIcon(host, row.app.key, 40.dp)
                                Text(row.app.label, style = MaterialTheme.typography.bodyLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (sections && rows.size > 12) LetterScroller(LauncherList.letters(shown).map { it.first }, Modifier.align(Alignment.CenterEnd)) { letter ->
                    val at = rows.indexOfFirst { it is ListRow.Header && it.letter == letter }
                    if (at >= 0) scope.launch { listState.scrollToItem(at) }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, count: String, selected: Boolean, icon: String? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.height(36.dp).clip(shape).background(if (selected) Color.White else Color.White.copy(alpha = 0.14f), shape)
            .clickable(role = Role.Tab, onClick = onClick).padding(horizontal = Spacing.M),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
    ) {
        if (icon != null) ItemIcon(icon, size = 20.dp, description = null)
        Text(if (count.isEmpty()) label else "$label $count", style = MaterialTheme.typography.labelMedium, color = if (selected) Color(0xFF252637) else Color.White, maxLines = 1)
    }
}

/** The strip of letters on the right edge: touch or drag along it to jump to a letter. */
@Composable
private fun LetterScroller(letters: List<Char>, modifier: Modifier, onLetter: (Char) -> Unit) {
    if (letters.isEmpty()) return
    var height by remember { mutableStateOf(1f) }
    var last by remember { mutableStateOf<Char?>(null) }
    fun pick(y: Float) {
        val letter = letters[(y / height * letters.size).toInt().coerceIn(0, letters.lastIndex)]
        if (letter != last) { last = letter; onLetter(letter) }
    }
    Column(
        modifier.width(32.dp).fillMaxHeight().padding(vertical = Spacing.S)
            .onSizeChanged { height = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(letters) { detectDragGestures(onDragStart = { pick(it.y) }, onDragEnd = { last = null }, onDragCancel = { last = null }) { change, _ -> change.consume(); pick(change.position.y) } }
            .pointerInput(letters) { detectTapGestures { pick(it.y); last = null } },
        verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { Text(it.toString(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f)) }
    }
}

/** The long-press sheet: pin, hide, move to a category, app info, uninstall. */
@Composable
internal fun AppSheet(app: LauncherApp, prefs: LauncherPrefs, onPrefs: (LauncherPrefs) -> Unit, onClose: () -> Unit, host: LauncherHost) {
    var moving by remember { mutableStateOf(false) }
    val pinned = app.key in prefs.pinned
    val hidden = app.key in prefs.hidden
    val none = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(indication = null, interactionSource = none, onClick = onClose)) {
        Box(Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(Spacing.L).clickable(indication = null, interactionSource = none) {}) {
            TextBox {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                        AppIcon(host, app.key, 40.dp)
                        Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!moving) {
                        if (!hidden) PixelButton(if (pinned) "Unpin from home" else "Pin to home", { onPrefs(if (pinned) prefs.unpin(app.key) else prefs.pin(app.key)); onClose() }, Modifier.fillMaxWidth(), Pixel.Blue)
                        PixelButton(if (hidden) "Show again" else "Hide", { onPrefs(if (hidden) prefs.unhide(app.key) else prefs.hide(app.key)); onClose() }, Modifier.fillMaxWidth(), Pixel.InkSoft)
                        PixelButton("Category: ${prefs.categoryOf(app).label}", { moving = true }, Modifier.fillMaxWidth(), Pixel.InkSoft)
                        PixelButton("App info", { host.appInfo(app.key); onClose() }, Modifier.fillMaxWidth(), Pixel.InkSoft)
                        if (app.removable) PixelButton("Uninstall", { host.uninstall(app.key); onClose() }, Modifier.fillMaxWidth(), Pixel.Danger)
                    } else {
                        Text("Move to", style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft)
                        AppCategory.entries.forEach { c ->
                            PixelButton(c.label, { onPrefs(prefs.move(app, c)); onClose() }, Modifier.fillMaxWidth(), if (c == prefs.categoryOf(app)) Pixel.Blue else Pixel.InkSoft, compact = true)
                        }
                    }
                }
            }
        }
    }
}
