package app.pokeplan.huawei

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.pokeplan.app.heal.HuaweiHealth
import app.pokeplan.app.heal.HuaweiRecords
import app.pokeplan.app.heal.HuaweiWalk
import app.pokeplan.app.heal.ScreenCapture
import app.pokeplan.app.heal.ScreenNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Reads Huawei Health's screens (INTEGRATIONS_AND_PRIVACY.md 5.2.2). Declared for `com.huawei.health` only (res/xml/huawei_reader.xml), so Android sends it
 * no other app's events; the package is checked again on every event and every window. It captures what is visible on a page once the page settles (Huawei
 * Health keeps its hidden tabs in the window, and those are left out), keeps it in [HuaweiCapture] (memory only), and on the player's tap walks Huawei Health:
 * one pass down the Health page that opens each card of [HuaweiWalk.CARDS] (Exercise records, Heart, Weight, Sleep) as it comes into view and goes Back, with a scroll budget per page (and, where a page has Day / Week tabs, its Week tab). The only things it taps are those cards and that tab,
 * found by their title; it never types or changes anything, and stops as soon as Back does not bring the Health page back. It never logs. After every walk it
 * turns itself off, because banking apps refuse to open while a sideloaded accessibility service is on.
 */
class HuaweiScreenService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var page = ""
    private var walkJob: Job? = null
    private val settle = Runnable { capture() }

    val walking: Boolean get() = walkJob?.isActive == true

    /** What HEAL already has from Huawei Health for this read, so cards and lists with nothing new are skipped (HuaweiWalk.Known). */
    private var known = HuaweiWalk.Known()

    override fun onServiceConnected() { HuaweiCapture.screen = this }

    override fun onUnbind(intent: Intent?): Boolean {
        stop()
        return super.onUnbind(intent)
    }

    override fun onDestroy() { stop(); scope.cancel(); super.onDestroy() }

    private fun stop() {
        if (HuaweiCapture.screen === this) HuaweiCapture.screen = null
        main.removeCallbacksAndMessages(null)
        walkJob?.cancel()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName?.toString() != HuaweiHealth.PACKAGE) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) event.className?.toString()?.substringAfterLast('.')?.takeIf { it.isNotBlank() }?.let { page = it }
        // Content changes come in bursts while a page loads or animates: capture once it has been still for a moment. A walk captures on its own steps.
        if (!walking) { main.removeCallbacks(settle); main.postDelayed(settle, SETTLE_MS) }
    }

    // --- reading a page ---------------------------------------------------------------------------------------------------

    /** Huawei Health's window now, or null when another app is in front. */
    private fun huaweiRoot(): AccessibilityNodeInfo? = rootInActiveWindow?.takeIf { it.packageName?.toString() == HuaweiHealth.PACKAGE }

    private fun capture(root: AccessibilityNodeInfo? = huaweiRoot()): ScreenCapture? {
        root ?: return null
        val started = System.nanoTime()
        val nodes = ArrayList<ScreenNode>()
        collect(root, 0, nodes)
        captureNanos += System.nanoTime() - started
        captures++
        return ScreenCapture(System.currentTimeMillis(), page, nodes).also { HuaweiCapture.addScreen(it) }
    }

    /** Time spent reading Huawei Health's screens during a walk (each element is a call to another process), for the walk log: it runs on the app's main thread. */
    private var captureNanos = 0L
    private var captures = 0

    private fun collect(node: AccessibilityNodeInfo, depth: Int, out: MutableList<ScreenNode>, via: String = "", viaDistance: Int = 0) {
        if (out.size >= MAX_NODES || depth > MAX_DEPTH || node.isPassword || !node.isVisibleToUser) return
        val text = node.text?.toString()?.trim()?.take(MAX_CHARS).orEmpty()
        val description = node.contentDescription?.toString()?.trim()?.take(MAX_CHARS).orEmpty()
        val id = node.viewIdResourceName.orEmpty()
        val kind = node.className?.toString()?.substringAfterLast('.').orEmpty()
        // The element that takes a tap on this one: itself, or its nearest clickable ancestor within 8 levels (what clickableAncestor does in the walk).
        val here = if (node.isClickable) id.substringAfter(":id/").ifBlank { kind } else via
        val hereDistance = if (node.isClickable) 0 else viaDistance
        val tapVia = if (here.isNotEmpty() && hereDistance <= TAP_LEVELS - 1) here else ""
        if (text.isNotEmpty() || description.isNotEmpty() || node.isScrollable) {
            out += ScreenNode(text, description, id, kind, depth, node.isClickable, node.isScrollable, actionNames(node), tapVia)
        }
        for (i in 0 until node.childCount) node.getChild(i)?.let { collect(it, depth + 1, out, here, hereDistance + 1) }
    }

    /** The actions Android offers on [node] that matter to the walk (a debug aid in the capture: which controls can be manipulated). */
    private fun actionNames(node: AccessibilityNodeInfo): List<String> = node.actionList.mapNotNull {
        when (it.id) {
            AccessibilityNodeInfo.ACTION_CLICK -> "click"
            AccessibilityNodeInfo.ACTION_LONG_CLICK -> "long-click"
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> "scroll-fwd"
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> "scroll-back"
            AccessibilityNodeInfo.ACTION_SET_TEXT -> "set-text"
            AccessibilityNodeInfo.ACTION_EXPAND -> "expand"
            AccessibilityNodeInfo.ACTION_COLLAPSE -> "collapse"
            else -> null
        }
    }

    /** The visible elements of [root], breadth first. */
    private fun visible(root: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> = sequence {
        val queue = ArrayDeque(listOf(root))
        var visited = 0
        while (queue.isNotEmpty() && visited++ < MAX_NODES * 4) {
            val n = queue.removeFirst()
            if (!n.isVisibleToUser) continue
            yield(n)
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.addLast(it) }
        }
    }

    private fun AccessibilityNodeInfo.id() = viewIdResourceName.orEmpty().substringAfter(":id/")

    /** The biggest visible scrolling element of [root] (the page's own list rather than a carousel), or null. */
    private fun mainScroller(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val r = Rect()
        return visible(root).filter { it.isScrollable }.maxByOrNull { it.getBoundsInScreen(r); r.width().toLong() * r.height() }
    }

    /** Whether [root] is Huawei Health's Health page (its card list and its Edit button), not a card's own page. */
    private fun isHealthPage(root: AccessibilityNodeInfo) = visible(root).any { it.id() == "id_recyclerview" } && visible(root).any { it.id() == "function_set_items_title" }

    /** The visible title element of [wanted] on the Health page, or null. */
    private fun card(root: AccessibilityNodeInfo, wanted: HuaweiWalk.Card) = visible(root).firstOrNull { it.id() == "function_set_items_title" && wanted.matches(it.text?.toString().orEmpty()) }

    /** Every visible card title element on the Health page, top to bottom. */
    private fun titles(root: AccessibilityNodeInfo) = visible(root).filter { it.id() == "function_set_items_title" }.toList()

    /** Clicks [node] or, if that is refused, each clickable ancestor above it in turn (within 8 levels); true once one takes the click. Refreshed first: a list re-binds its rows. */
    private fun tap(node: AccessibilityNodeInfo): Boolean {
        node.refresh()
        var n: AccessibilityNodeInfo? = node
        repeat(TAP_LEVELS) {
            val c = n ?: return@repeat
            if (c.isClickable && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            n = c.parent
        }
        return touch(node)
    }

    /**
     * A real touch at the middle of [node], for a control that declares a click but refuses it (Huawei Health's Steps summary: it handles touches itself, so the
     * accessibility click is refused). Only used after every click was refused; it needs the service's gesture capability (huawei_reader.xml).
     */
    private fun touch(node: AccessibilityNodeInfo): Boolean {
        val r = Rect()
        node.getBoundsInScreen(r)
        if (r.width() <= 0 || r.height() <= 0) { lastTouch = "no bounds"; return false }
        if (serviceInfo?.capabilities?.and(android.accessibilityservice.AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES) == 0) { lastTouch = "gestures not granted"; return false }
        val path = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, TOUCH_MS)).build()
        val sent = runCatching { dispatchGesture(gesture, null, null) }.onFailure { lastTouch = "error ${it.javaClass.simpleName}" }.getOrDefault(false)
        if (sent) lastTouch = "sent" else if (!lastTouch.startsWith("error")) lastTouch = "refused"
        return sent
    }

    /** What the last [touch] did, for the walk log. */
    private var lastTouch = "not tried"

    /** What a refused tap tried, for the walk log: the ids from [node] up, with "(tap)" on the clickable ones (ids only, never text). */
    private fun triedChain(node: AccessibilityNodeInfo): String {
        val out = ArrayList<String>()
        var n: AccessibilityNodeInfo? = node
        repeat(TAP_LEVELS) { val c = n ?: return@repeat; out += c.id().ifBlank { c.className?.toString()?.substringAfterLast('.').orEmpty() } + if (c.isClickable) "(tap)" else ""; n = c.parent }
        return out.joinToString(" > ")
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = node
        repeat(TAP_LEVELS) { if (n == null) return null; if (n!!.isClickable) return n; n = n!!.parent }
        return null
    }

    // --- the walk ---------------------------------------------------------------------------------------------------------

    /** Opens Huawei Health and walks it (see the class comment), then brings PokéLife back. [done] gets null or what stopped it, on the main thread. */
    fun walk(known: HuaweiWalk.Known = HuaweiWalk.Known(), done: (String?) -> Unit) {
        this.known = known
        if (walking) { done("Already reading Huawei Health"); return } // copy-ok: result
        val launch = packageManager.getLaunchIntentForPackage(HuaweiHealth.PACKAGE) ?: run { done("Huawei Health is not installed"); return } // copy-ok: result
        main.removeCallbacks(settle)
        captureNanos = 0L; captures = 0
        note("Walk started")
        var answered = false
        fun answer(problem: String?) { if (!answered) { answered = true; done(problem) } }
        walkJob = scope.launch {
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val startedAt = System.currentTimeMillis()
            val problem = withTimeoutOrNull(TIMEOUT_MS) { runWalk() ?: OK } ?: "Huawei Health took too long. What was seen is kept." // copy-ok: result
            note("Walk took ${(System.currentTimeMillis() - startedAt) / 1000} s; reading screens took ${captureNanos / 1_000_000_000} s in $captures reads")
            comeBack()
            answer(problem.takeIf { it != OK })
        }.also { job ->
            job.invokeOnCompletion { e ->
                main.post {
                    if (e != null) answer("Screen reading was turned off. What was seen is kept.") // copy-ok: result
                    // Off again at once, however the walk ended: banking apps (GCash, MariBank) refuse to open while a sideloaded accessibility service is on (5.2.2, rule 2).
                    if (HuaweiCapture.screen === this) turnOff()
                }
            }
        }
    }

    /**
     * Null when the walk got through; otherwise what stopped it, in words. One pass down the Health page: each wanted card ([HuaweiWalk.CARDS]) is opened as it
     * comes into view and Back returns to the same place, so the page is not scrolled to its end and back up first. The pass ends when every card has been opened;
     * a card that never came into view is looked for once, scrolling back up.
     */
    private suspend fun runWalk(): String? {
        delay(FIRST_WAIT_MS)
        if (awaitHealthPage() == null) return "Huawei Health did not show its Health page" // copy-ok: result
        val opened = HashSet<String>()
        note("Health page found")
        capture()
        // The Steps summary at the top comes first (the page starts at its top); it is not a card, so it is found by its own element.
        val stepsEl = huaweiRoot()?.let { root -> visible(root).firstOrNull { it.id() == HuaweiWalk.STEPS_ID } }
        if (stepsEl == null) note("Steps: summary not found") else {
            note("Steps: in view")
            if (HuaweiWalk.skipCard(HuaweiWalk.STEPS, known)) note("Steps: skipped, nothing new")
            else visit(HuaweiWalk.STEPS, stepsEl) { huaweiRoot()?.let { r -> visible(r).firstOrNull { it.id() == HuaweiWalk.STEPS_ID } } }?.let { return it }
        }
        var scrolls = 0
        while (true) {
            val root = huaweiRoot() ?: return "Huawei Health closed" // copy-ok: result
            val next = titles(root).firstNotNullOfOrNull { el -> HuaweiWalk.cardFor(el.text?.toString().orEmpty())?.takeIf { it.title !in opened }?.let { it to el } }
            if (next != null && HuaweiWalk.skipCard(next.first, known)) {
                opened += next.first.title
                note("${next.first.title}: skipped, nothing new")
                continue
            }
            if (next != null) {
                opened += next.first.title
                note("${next.first.title}: in view")
                visit(next.first, next.second) { huaweiRoot()?.let { r -> titles(r).firstOrNull { it.text?.toString()?.trim() == next.second.text?.toString()?.trim() } } }?.let { return it }
                continue
            }
            if (scrolls++ >= HuaweiWalk.HOME_SCROLLS) { note("Health page: scroll limit reached"); break }
            val before = capture(root)?.signature
            if (mainScroller(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) != true) { note("Health page: scroll refused"); break }
            delay(SCROLL_WAIT_MS)
            if (capture()?.signature == before) { note("Health page: end of list"); break }
            note("Health page: scrolled")
        }
        for (wanted in HuaweiWalk.remaining(opened)) {
            val root = huaweiRoot() ?: return "Huawei Health closed" // copy-ok: result
            val target = findCard(root, wanted) ?: run { note("${wanted.title}: not found"); null } ?: continue
            visit(wanted, target)?.let { return it }
        }
        return null
    }

    /** Opens [target] (the title element of [wanted]), captures its page within the card's scroll budget, and goes Back; null, or what stopped the walk. */
    private suspend fun visit(wanted: HuaweiWalk.Card, target: AccessibilityNodeInfo, refind: () -> AccessibilityNodeInfo? = { null }): String? {
        if (!tap(target)) {
            // A list that is still settling refuses a click on a row it has just re-bound: look again after a moment and try once more.
            delay(RETAP_WAIT_MS)
            val again = refind()
            if (again == null || !tap(again)) { note("${wanted.title}: tap refused (${triedChain(again ?: target)}; touch ${lastTouch})"); return null }
        }
        delay(PAGE_WAIT_MS)
        val detail = huaweiRoot() ?: return "Huawei Health closed" // copy-ok: result
        if (isHealthPage(detail)) { note("${wanted.title}: tap opened nothing"); return null }
        // A page that shows no words yet (a web page, a slow card) gets one more wait; the log says how much of it there was.
        var shown = capture(detail)
        if (shown == null || shown.nodes.none { it.text.isNotBlank() || it.description.isNotBlank() }) { delay(PAGE_WAIT_MS); shown = capture() }
        // Still no words: the page may be a window of its own (a dialog or sheet), so read every Huawei Health window.
        if (shown == null || shown.nodes.none { it.text.isNotBlank() || it.description.isNotBlank() }) {
            val others = windows.mapNotNull { it.root }.filter { it.packageName?.toString() == HuaweiHealth.PACKAGE }
            others.forEach { capture(it) }
            note("${wanted.title}: ${others.size} windows read")
        }
        note("${wanted.title}: opened ${page.ifBlank { "a page" }} (${shown?.nodes?.size ?: 0} elements)")
        val exercise = wanted.title == "Exercise records"
        scrollThrough(wanted.scrolls) { exercise && (HuaweiWalk.exerciseIsOldEnough(it, System.currentTimeMillis(), TimeZone.currentSystemDefault()) || HuaweiWalk.exerciseIsKnown(it, known, System.currentTimeMillis(), TimeZone.currentSystemDefault())) }
        // A page with Day / Week / Month / Year tabs (the Sleep page): also capture its Week view, which is a tab of the same page, so Back below still leaves it.
        if (wanted.week) {
            val tapped = openWeek()
            note("${wanted.title}: Week tab ${if (tapped) "tapped" else "not tapped"}")
            if (tapped) { delay(PAGE_WAIT_MS); scrollThrough(wanted.scrolls) }
        }
        if (wanted.allData) openAllData(wanted)
        // Back until the Health page shows, never pressing Back on the Health page itself (that would leave Huawei Health). A page whose All data was opened needs more presses (the
        // All data page, then the card page); a look-only card may open a layer of its own (an intro, a pop-up).
        if (!backToHealth(if (wanted.allData) 4 else if (HuaweiWalk.isWanted(wanted)) 1 else 3)) return "Stopped after ${wanted.title}: Back did not return to the Health page" // copy-ok: result
        note("${wanted.title}: Back to the Health page")
        return null
    }

    /** Presses Back up to [presses] times, only while the Health page is not showing; true once it is. */
    private suspend fun backToHealth(presses: Int): Boolean {
        repeat(presses) {
            if (huaweiRoot()?.let { isHealthPage(it) } == true) return true
            performGlobalAction(GLOBAL_ACTION_BACK)
            delay(BACK_WAIT_MS)
        }
        return awaitHealthPage() != null
    }

    /** The "All data" item of an open menu (its text or description; a web page's menu exposes either), or null. */
    private fun allDataItem(): AccessibilityNodeInfo? {
        // The menu may be a window of its own (a pop-up), so every Huawei Health window is searched, not only the active one.
        val roots = (listOfNotNull(huaweiRoot()) + windows.mapNotNull { it.root }.filter { it.packageName?.toString() == HuaweiHealth.PACKAGE })
        return roots.firstNotNullOfOrNull { r ->
            visible(r).firstOrNull { n -> listOf(n.text, n.contentDescription).any { it?.toString()?.trim().equals(HuaweiWalk.ALL_DATA, ignoreCase = true) } }
        }
    }

    private fun onAllDataPage(root: AccessibilityNodeInfo) = visible(root).any { it.id() == "titleSpinner" || it.id() == "privacy_title" }

    /** On the open card page: the four-dots menu, then "All data", kept as history (scrolled until a row is old enough, [HuaweiWalk.allDataIsFarEnough]); a page without the menu is noted and left. */
    private suspend fun openAllData(wanted: HuaweiWalk.Card) {
        val root = huaweiRoot() ?: return
        val r = Rect()
        val menu = visible(root).filter { n -> HuaweiWalk.isMenuButton(n.contentDescription?.toString().orEmpty()) && clickableAncestor(n) != null }
            .minByOrNull { it.getBoundsInScreen(r); r.top * 10_000 - r.right }
        if (menu == null) { note("${wanted.title}: no menu button found"); return }
        if (!tap(menu)) { note("${wanted.title}: menu tap refused"); return }
        delay(MENU_WAIT_MS)
        capture() // the open menu, so its items show in the sample
        windows.mapNotNull { it.root }.filter { it.packageName?.toString() == HuaweiHealth.PACKAGE }.forEach { capture(it) }
        // A web page (Weight, Cycle Calendar) draws its menu a moment later than a native one.
        var item = allDataItem() ?: run { delay(MENU_WAIT_MS * 2); capture(); allDataItem() }
        if (item == null && touch(menu)) {
            // A web page (Weight, Cycle Calendar) may take a touch where an accessibility click did nothing.
            delay(MENU_WAIT_MS * 2)
            capture()
            item = allDataItem()
            note("${wanted.title}: menu touched (${if (item != null) "menu showed" else "still no menu"})")
        }
        if (item == null) { note("${wanted.title}: '${HuaweiWalk.ALL_DATA}' is not in the menu"); return }
        if (!tap(item)) { note("${wanted.title}: '${HuaweiWalk.ALL_DATA}' tap refused"); return }
        delay(PAGE_WAIT_MS)
        note("${wanted.title}: ${HuaweiWalk.ALL_DATA} opened ${page.ifBlank { "a page" }}")
        capture()
        if (wanted.entries > 0) openEntries(wanted)
        val now = System.currentTimeMillis()
        val zone = TimeZone.currentSystemDefault()
        val knownDay = HuaweiWalk.knownDay(wanted, known)
        scrollThrough(wanted.allDataScrolls) { HuaweiWalk.allDataIsFarEnough(it, now, zone) || HuaweiWalk.allDataIsKnown(it, knownDay, now, zone) }
    }

    /** The date text of an All data row ("11/12"), from the sibling of its value element. */
    private fun rowDateText(title: AccessibilityNodeInfo): String? {
        val p = title.parent ?: return null
        return (0 until p.childCount).mapNotNull { p.getChild(it) }.firstOrNull { it.id() == "privacy_time" }?.text?.toString()
    }

    /** What identifies an All data row: the words of its container (value and date). */
    private fun rowKey(title: AccessibilityNodeInfo): String {
        val p = title.parent ?: return title.text?.toString().orEmpty()
        return (0 until p.childCount).mapNotNull { p.getChild(it)?.text?.toString() }.joinToString("|")
    }

    /**
     * Opens the newest [HuaweiWalk.Card.entries] rows of the All data list one by one (a Sleep row opens to its bed and wake times), capturing each and coming back to the
     * list. It only taps rows; the page's Delete icon is never touched. Back is pressed only while the list is not showing and the Health page is not either.
     */
    private suspend fun openEntries(wanted: HuaweiWalk.Card) {
        val done = HashSet<String>()
        var opened = 0
        var rounds = 0
        val today = Instant.fromEpochMilliseconds(System.currentTimeMillis()).toLocalDateTime(TimeZone.currentSystemDefault()).date
        while (opened < wanted.entries && rounds++ < wanted.entries * 3) {
            val root = huaweiRoot() ?: return
            if (!onAllDataPage(root)) return
            val row = visible(root).filter { it.id() == "privacy_content_title" }.firstOrNull { rowKey(it) !in done }
            if (row == null) {
                val before = capture(root)?.signature
                if (mainScroller(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) != true) return
                delay(SCROLL_WAIT_MS)
                if (capture()?.signature == before) return
                continue
            }
            done += rowKey(row)
            // The list is newest first: a day already saved as a night means every day below it is saved too.
            val day = rowDateText(row)?.let { HuaweiRecords.rowDate(it, today) }
            if (day != null && day in known.nightDays) { note("${wanted.title}: reached a saved day, nothing older to open"); break }
            if (!tap(row)) { note("${wanted.title}: entry tap refused"); continue }
            delay(PAGE_WAIT_MS)
            val detail = capture()
            opened++
            note("${wanted.title}: entry $opened opened ${page.ifBlank { "a page" }} (${detail?.nodes?.size ?: 0} elements)")
            repeat(2) {
                val now = huaweiRoot() ?: return
                if (onAllDataPage(now) || isHealthPage(now)) return@repeat
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(BACK_WAIT_MS)
            }
        }
        note("${wanted.title}: $opened entries opened")
    }

    /** Taps the "Week" tab of the open card page, when it has one and it is not the tab already showing; returns whether it tapped. */
    private fun openWeek(): Boolean {
        val root = huaweiRoot() ?: return false
        if (isHealthPage(root)) return false
        val tab = visible(root).firstOrNull { it.text?.toString()?.trim() == "Week" && it.className?.toString()?.endsWith("TextView") == true } ?: return false
        if (tab.isSelected) return false
        return clickableAncestor(tab)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    /** The Health page, waiting through a splash or an ad for a few tries; null when it does not come. */
    private suspend fun awaitHealthPage(): AccessibilityNodeInfo? {
        repeat(MAX_TRIES) {
            huaweiRoot()?.takeIf { isHealthPage(it) }?.let { return it }
            delay(RETRY_MS)
        }
        return null
    }

    /** Captures the page, then scrolls its main list and captures again until it stops moving, [enough] says the captured page is far enough, or [max] scrolls. */
    private suspend fun scrollThrough(max: Int, enough: (ScreenCapture) -> Boolean = { false }) {
        var now = capture()
        var last = now?.signature
        repeat(max) {
            if (now != null && enough(now!!)) return
            val root = huaweiRoot() ?: return
            val scroller = mainScroller(root) ?: return
            if (!scroller.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return
            delay(SCROLL_WAIT_MS)
            now = capture()
            if (now?.signature == last) return
            last = now?.signature
        }
    }

    /** The title element of [wanted], scrolling the Health page back up and then down to find it (a list only holds the cards near the screen). */
    private suspend fun findCard(start: AccessibilityNodeInfo, wanted: HuaweiWalk.Card): AccessibilityNodeInfo? {
        card(start, wanted)?.let { return it }
        for (direction in listOf(false, true)) repeat(HuaweiWalk.HOME_SCROLLS) {
            val root = huaweiRoot() ?: return null
            card(root, wanted)?.let { return it }
            val scroller = mainScroller(root) ?: return null
            if (!scroller.performAction(if (direction) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return@repeat
            delay(SCROLL_WAIT_MS)
        }
        return huaweiRoot()?.let { card(it, wanted) }
    }

    /** One line in the walk log (card titles and outcomes only; never Huawei's own text). */
    private fun note(line: String) = HuaweiCapture.log(line)

    /** Turns this service off in Android's settings, as the player would; it stays off until turned on again. */
    fun turnOff() {
        stop()
        disableSelf()
    }

    /** Back to PokéLife the way its icon would: its own task comes to the front as it was. */
    private fun comeBack() {
        packageManager.getLaunchIntentForPackage(packageName)?.let { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    private companion object {
        const val OK = ""
        const val SETTLE_MS = 800L
        const val FIRST_WAIT_MS = 2_500L
        const val RETRY_MS = 1_500L
        const val SCROLL_WAIT_MS = 1_000L
        const val PAGE_WAIT_MS = 2_500L
        const val BACK_WAIT_MS = 1_500L
        const val MENU_WAIT_MS = 1_000L
        const val RETAP_WAIT_MS = 800L
        const val TOUCH_MS = 60L
        const val TIMEOUT_MS = 420_000L
        const val MAX_TRIES = 4
        const val MAX_NODES = 400
        const val MAX_DEPTH = 40
        const val TAP_LEVELS = 8
        const val MAX_CHARS = 200
    }
}
