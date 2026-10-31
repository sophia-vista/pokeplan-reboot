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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.layout.widthIn
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.SceneImage
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.produceState
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.theme.focusRing
import app.pokeplan.ui.Place
import app.pokeplan.ui.Tab
import app.pokeplan.ui.screens.LocalReduceMotion
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelTheme
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.theme.focusRing
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
    /** Opens a Pokémon's page in PokéLife. */
    fun openPokemon(id: String)
    /** Opens PokéLife itself on [place] (Focus from the Focus button, Home from the buddy). */
    fun openApp(place: Place)
}

private val rowShape = RoundedCornerShape(14.dp)

/**
 * PokéLife as the phone's home screen: the home page (the buddy, today's tasks, a Focus button, pinned apps) on a Pokémon
 * pixel wallpaper and, one swipe or tap up, every app A to Z over that wallpaper blurred and darkened. It shows and starts
 * what already pays and pays nothing itself.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LauncherScreen(model: AppModel, apps: List<LauncherApp>, prefs: LauncherPrefs, onPrefs: (LauncherPrefs) -> Unit, isDefault: Boolean, host: LauncherHost, homePresses: Int = 0, active: Boolean = true) {
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
    var pickingTown by remember { mutableStateOf(false) }
    var menuMon by remember { mutableStateOf<OwnedPokemon?>(null) }
    // The Home button always comes back to the home page.
    LaunchedEffect(homePresses) {
        if (homePresses > 0) { listOpen = false; sheetKey = null; pickingTown = false; menuMon = null }
    }
    val sheetApp = sheetKey?.let { key -> apps.firstOrNull { it.key == key } }
    PixelTheme(dark) {
        CompositionLocalProvider(LocalReduceMotion provides data.settings.reduceMotion) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(Pixel.Paper)) {
                val pixel = townScale(maxWidth.value * LocalDensity.current.density)
                Town(model, prefs.scene, active, onMenu = { menuMon = it }, modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onLongPress = { pickingTown = true }) })
                // Home is the one screen that must never close: back only steps out of the sheet or the list.
                BackHandler(enabled = true) { if (menuMon != null) menuMon = null else if (pickingTown) pickingTown = false else if (sheetApp != null) sheetKey = null else listOpen = false }
                HomePage(model, apps, prefs, isDefault, host, pixel, onAllApps = { listOpen = true }, onHold = { sheetKey = it.key })
                // Drag in from the right edge to open every app (All apps does the same for anyone who does not know the gesture).
                Box(
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(24.dp).pointerInput(Unit) {
                        var total = 0f
                        detectHorizontalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total < -60f) listOpen = true }) { _, dx -> total += dx }
                    },
                )
                AnimatedVisibility(listOpen, enter = slideInVertically { it / 4 } + fadeIn(), exit = slideOutVertically { it / 4 } + fadeOut()) {
                    AppListPage(apps, prefs, host, onClose = { listOpen = false }, onHold = { sheetKey = it.key }, onTown = { listOpen = false; pickingTown = true })
                }
                menuMon?.let { mon -> PokemonMenu(mon, mon.id == model.data.player.buddy?.id, pixel, onOpen = { host.openPokemon(mon.id); menuMon = null }, onBuddy = { model.store.setBuddy(mon.id); menuMon = null }, onClose = { menuMon = null }) }
                if (pickingTown) TownPicker(prefs.scene, onPick = { onPrefs(prefs.withScene(it)); pickingTown = false }, onClose = { pickingTown = false })
                if (sheetApp != null) AppSheet(sheetApp, prefs, onPrefs, onClose = { sheetKey = null }, host = host)
            }
        }
    }
}

/**
 * The home page over the town: a small status box at the top (the companion, then the collection), the town, and by the
 * thumb two dialogue boxes: the app's three places (PokéLife, Pokémon, Me) with their icons, then your pinned apps with
 * logos (each app's own, or a game item you chose) and All apps. Dragging in from the right edge opens the app list too.
 */
@Composable
private fun HomePage(model: AppModel, apps: List<LauncherApp>, prefs: LauncherPrefs, isDefault: Boolean, host: LauncherHost, pixel: Int, onAllApps: () -> Unit, onHold: (LauncherApp) -> Unit) {
    val data = model.data
    val pinned = LauncherList.pinned(apps, prefs)
    val running = data.focus != null
    val buddy = data.player.buddy
    val density = LocalDensity.current.density
    val gap = Modifier.padding(horizontal = (6 * pixel / density).dp)
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = Spacing.M, vertical = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        if (data.onboarded && buddy != null) PmdBox(pixel, Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Open PokéLife") { host.openApp(Place.HOME) }, inset = 1) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PmdText("${buddy.nickname}  Lv. ${buddy.level}", pixel, gap)
                Box(Modifier.weight(1f))
                PmdText(if (running) "Focusing" else "${data.player.pokemon.size} Pokémon", pixel, gap, alpha = 0.75f)
            }
        }
        Box(Modifier.weight(1f))
        val note = when {
            !data.onboarded -> "Open PokéLife to meet your first Pokémon" to { host.openApp(Place.HOME) }
            !isDefault -> "Make PokéLife your home app" to host::openHomeSettings
            else -> null
        }
        if (note != null) PmdBox(pixel, Modifier.fillMaxWidth(), inset = 1) { PmdMenuLine(note.first, pixel, onClick = note.second) }
        if (data.onboarded) PmdBox(pixel, Modifier.fillMaxWidth(), inset = 1) {
            // The three places of the app, with the icons of its tabs.
            Row(Modifier.fillMaxWidth()) {
                PmdNavItem("PokéLife", Tab.HOME.icon, pixel, Modifier.weight(1f)) { host.openApp(if (running) Place.FOCUS else Place.HOME) }
                PmdNavItem("Pokémon", Tab.COLLECTION.icon, pixel, Modifier.weight(1f)) { host.openApp(Place.COLLECTION) }
                PmdNavItem("Me", Tab.ME.icon, pixel, Modifier.weight(1f)) { host.openApp(Place.ME) }
            }
        }
        PmdBox(pixel, Modifier.fillMaxWidth(), inset = 1) {
            Column {
                if (pinned.isEmpty()) PmdMenuLine("Press and hold an app in All apps to pin it", pixel, dim = true) { onAllApps() }
                else Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    pinned.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { app -> PinnedApp(app, prefs.icons[app.key], host, pixel, Modifier.weight(1f), onHold = { onHold(app) }) }
                            repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
                PmdRule(pixel)
                PmdMenuLine("All apps", pixel, center = true, onClick = onAllApps)
            }
        }
    }
}

/** A pinned app on the home page: its logo (or the game item chosen for it) over its name; tap opens it, press and hold manages it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedApp(app: LauncherApp, item: String?, host: LauncherHost, pixel: Int, modifier: Modifier, onHold: () -> Unit) {
    val density = LocalDensity.current.density
    Column(
        modifier.defaultMinSize(minHeight = 72.dp).focusRing(rowShape, Color.White).combinedClickable(role = Role.Button, onLongClick = onHold, onClick = { host.open(app.key) })
            .semantics(mergeDescendants = true) {}.padding(vertical = (2 * pixel / density).dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        if (item != null) ItemIcon(item, size = 40.dp, description = null) else AppIcon(host, app.key, 40.dp)
        Box(Modifier.padding(top = (2 * pixel / density).dp).padding(horizontal = (1 * pixel / density).dp)) { PmdText(app.label, pixel) }
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
internal fun AppListPage(apps: List<LauncherApp>, prefs: LauncherPrefs, host: LauncherHost, onClose: () -> Unit, onHold: (LauncherApp) -> Unit, onTown: () -> Unit = {}) {
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
        TownBackdrop(prefs.scene, Modifier.fillMaxSize().blur(24.dp))
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
                Box(Modifier.height(Spacing.Target).clip(rowShape).clickable(role = Role.Button, onClickLabel = "Change the town", onClick = onTown).padding(horizontal = Spacing.S), contentAlignment = Alignment.Center) {
                    Text("Town", style = MaterialTheme.typography.labelLarge, color = Color.White)
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
                                    .padding(vertical = Spacing.S, horizontal = Spacing.XS),
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
        Text(if (count.isEmpty()) label else "$label $count", style = MaterialTheme.typography.labelMedium, color = if (selected) Pixel.Outline else Color.White, maxLines = 1)
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
    var choosingIcon by remember { mutableStateOf(false) }
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
                    if (choosingIcon) {
                        Text("Icon on the home page", style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft)
                        PixelButton("The app's own logo", { onPrefs(prefs.withIcon(app.key, null)); onClose() }, Modifier.fillMaxWidth(), if (app.key !in prefs.icons) Pixel.Blue else Pixel.InkSoft, compact = true)
                        Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                            LauncherIcons.items.chunked(6).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                                    row.forEach { item ->
                                        Box(
                                            Modifier.weight(1f).height(48.dp).clip(rowShape).then(if (prefs.icons[app.key] == item) Modifier.border(2.dp, Pixel.Blue, rowShape) else Modifier)
                                                .clickable(role = Role.RadioButton, onClickLabel = "Use $item") { onPrefs(prefs.withIcon(app.key, item)); onClose() },
                                            contentAlignment = Alignment.Center,
                                        ) { ItemIcon(item, size = 32.dp, description = null) }
                                    }
                                    repeat(6 - row.size) { Box(Modifier.weight(1f)) }
                                }
                            }
                        }
                    } else if (!moving) {
                        if (!hidden) PixelButton(if (pinned) "Unpin from home" else "Pin to home", { onPrefs(if (pinned) prefs.unpin(app.key) else prefs.pin(app.key)); onClose() }, Modifier.fillMaxWidth(), Pixel.Blue)
                        PixelButton(if (hidden) "Show again" else "Hide", { onPrefs(if (hidden) prefs.unhide(app.key) else prefs.hide(app.key)); onClose() }, Modifier.fillMaxWidth(), Pixel.InkSoft)
                        PixelButton("Category: ${prefs.categoryOf(app).label}", { moving = true }, Modifier.fillMaxWidth(), Pixel.InkSoft)
                        if (pinned) PixelButton("Icon on the home page", { choosingIcon = true }, Modifier.fillMaxWidth(), Pixel.InkSoft)
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

/** Long-press the town: pick the scene it is set in, shown as small pictures. */
@Composable
internal fun TownPicker(current: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    val none = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(indication = null, interactionSource = none, onClick = onClose)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(Spacing.L).background(Pixel.Panel, RoundedCornerShape(24.dp))
                .padding(Spacing.L).clickable(indication = null, interactionSource = none) {},
            verticalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            Text("Where is your town?", style = MaterialTheme.typography.titleSmall)
            Column(Modifier.height(420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text("Scenes", style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft)
                TownChoices(TownScenes.scenes, current, onPick)
                Text("Dungeons", style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft, modifier = Modifier.padding(top = Spacing.S))
                TownChoices(TownScenes.dungeons, current, onPick)
            }
            Text("Press and hold the town any time to change it.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }
}

@Composable
private fun TownChoices(choices: List<Pair<String, String>>, current: String, onPick: (String) -> Unit) {
    choices.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            row.forEach { (name, label) ->
                Box(
                    Modifier.weight(1f).height(96.dp).clip(rowShape).then(if (name == current) Modifier.border(3.dp, Pixel.Blue, rowShape) else Modifier)
                        .clickable(role = Role.RadioButton, onClickLabel = "Set the town in $label") { onPick(name) },
                ) {
                    TownBackdrop(name, Modifier.fillMaxSize())
                    Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = Spacing.S, vertical = 2.dp))
                }
            }
            if (row.size == 1) Box(Modifier.weight(1f))
        }
    }
}

/** Long-press a Pokémon in the town: its name and level, then what can be done with it, as a dialogue box. */
@Composable
internal fun PokemonMenu(mon: OwnedPokemon, isBuddy: Boolean, pixel: Int, onOpen: () -> Unit, onBuddy: () -> Unit, onClose: () -> Unit) {
    val none = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(Modifier.fillMaxSize().background(Pixel.Scrim.copy(alpha = 0.4f)).clickable(indication = null, interactionSource = none, onClick = onClose), contentAlignment = Alignment.Center) {
        PmdBox(pixel, Modifier.padding(horizontal = Spacing.XXL).clickable(indication = null, interactionSource = none) {}) {
            Column(Modifier.widthIn(min = 200.dp)) {
                PmdMenuLine("${mon.nickname}  Lv. ${mon.level}", pixel, dim = true) {}
                PmdRule(pixel)
                PmdMenuLine("Open profile", pixel, onClick = onOpen)
                if (!isBuddy) PmdMenuLine("Make buddy", pixel, onClick = onBuddy)
                PmdMenuLine("Close", pixel, dim = true, onClick = onClose)
            }
        }
    }
}
