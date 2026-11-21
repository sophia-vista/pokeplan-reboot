package app.pokeplan.huawei

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.pokeplan.app.heal.HuaweiHealth
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
import kotlinx.datetime.TimeZone

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
        val nodes = ArrayList<ScreenNode>()
        collect(root, 0, nodes)
        return ScreenCapture(System.currentTimeMillis(), page, nodes).also { HuaweiCapture.addScreen(it) }
    }

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

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = node
        repeat(TAP_LEVELS) { if (n == null) return null; if (n!!.isClickable) return n; n = n!!.parent }
        return null
    }

    // --- the walk ---------------------------------------------------------------------------------------------------------

    /** Opens Huawei Health and walks it (see the class comment), then brings PokéLife back. [done] gets null or what stopped it, on the main thread. */
    fun walk(done: (String?) -> Unit) {
        if (walking) { done("Already reading Huawei Health"); return } // copy-ok: result
        val launch = packageManager.getLaunchIntentForPackage(HuaweiHealth.PACKAGE) ?: run { done("Huawei Health is not installed"); return } // copy-ok: result
        main.removeCallbacks(settle)
        note("Walk started")
        var answered = false
        fun answer(problem: String?) { if (!answered) { answered = true; done(problem) } }
        walkJob = scope.launch {
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val problem = withTimeoutOrNull(TIMEOUT_MS) { runWalk() ?: OK } ?: "Huawei Health took too long. What was seen is kept." // copy-ok: result
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
        huaweiRoot()?.let { root -> visible(root).firstOrNull { it.id() == HuaweiWalk.STEPS_ID }?.let { el ->
            note("Steps: in view")
            visit(HuaweiWalk.STEPS, el)?.let { return it }
        } ?: note("Steps: summary not found") }
        var scrolls = 0
        while (true) {
            val root = huaweiRoot() ?: return "Huawei Health closed" // copy-ok: result
            val next = titles(root).firstNotNullOfOrNull { el -> HuaweiWalk.cardFor(el.text?.toString().orEmpty())?.takeIf { it.title !in opened }?.let { it to el } }
            if (next != null) {
                opened += next.first.title
                note("${next.first.title}: in view")
                visit(next.first, next.second)?.let { return it }
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
    private suspend fun visit(wanted: HuaweiWalk.Card, target: AccessibilityNodeInfo): String? {
        if (clickableAncestor(target)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) != true) { note("${wanted.title}: tap refused or no clickable ancestor"); return null }
        delay(PAGE_WAIT_MS)
        val detail = huaweiRoot() ?: return "Huawei Health closed" // copy-ok: result
        if (isHealthPage(detail)) { note("${wanted.title}: tap opened nothing"); return null }
        note("${wanted.title}: opened ${page.ifBlank { "a page" }}")
        val exercise = wanted.title == "Exercise records"
        scrollThrough(wanted.scrolls) { exercise && HuaweiWalk.exerciseIsOldEnough(it, System.currentTimeMillis(), TimeZone.currentSystemDefault()) }
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

    /** On the open card page: the four-dots menu, then "All data", scrolled within the card's own budget; a page without the menu is noted and left; what happens goes in the walk log. */
    private suspend fun openAllData(wanted: HuaweiWalk.Card) {
        val root = huaweiRoot() ?: return
        val r = Rect()
        val menu = visible(root).filter { n -> HuaweiWalk.isMenuButton(n.contentDescription?.toString().orEmpty()) || n.id().let { it.contains("more", true) || it.contains("menu", true) || it.contains("overflow", true) } }
            .filter { clickableAncestor(it) != null }
            .minByOrNull { it.getBoundsInScreen(r); r.top * 10_000 - r.right }
        if (menu == null) { note("${wanted.title}: no menu button found"); return }
        if (clickableAncestor(menu)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) != true) { note("${wanted.title}: menu tap refused"); return }
        delay(MENU_WAIT_MS)
        capture() // the open menu, so its items show in the sample
        val item = huaweiRoot()?.let { r2 -> visible(r2).firstOrNull { it.text?.toString()?.trim().equals(HuaweiWalk.ALL_DATA, ignoreCase = true) } }
        if (item == null) { note("${wanted.title}: '${HuaweiWalk.ALL_DATA}' is not in the menu"); return }
        if (clickableAncestor(item)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) != true) { note("${wanted.title}: '${HuaweiWalk.ALL_DATA}' tap refused"); return }
        delay(PAGE_WAIT_MS)
        note("${wanted.title}: ${HuaweiWalk.ALL_DATA} opened ${page.ifBlank { "a page" }}")
        scrollThrough(wanted.allDataScrolls)
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
        const val TIMEOUT_MS = 240_000L
        const val MAX_TRIES = 4
        const val MAX_NODES = 400
        const val MAX_DEPTH = 40
        const val TAP_LEVELS = 8
        const val MAX_CHARS = 200
    }
}
