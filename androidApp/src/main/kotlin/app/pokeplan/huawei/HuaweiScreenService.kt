package app.pokeplan.huawei

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.pokeplan.app.heal.HuaweiHealth
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

/**
 * Reads Huawei Health's screens (INTEGRATIONS_AND_PRIVACY.md 5.2.2). Declared for `com.huawei.health` only (res/xml/huawei_reader.xml), so Android sends it
 * no other app's events; the package is checked again on every event and every window. It captures what is visible on a page once the page settles (Huawei
 * Health keeps its hidden tabs in the window, and those are left out), keeps it in [HuaweiCapture] (memory only), and on the player's tap walks Huawei Health:
 * the Health page scrolled to the end, then the Sleep, Weight and Exercise records cards' own pages (and, where a page has Day / Week tabs, its Week tab), and back. The only things it taps are those three cards and that tab,
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

    private fun collect(node: AccessibilityNodeInfo, depth: Int, out: MutableList<ScreenNode>) {
        if (out.size >= MAX_NODES || depth > MAX_DEPTH || node.isPassword || !node.isVisibleToUser) return
        val text = node.text?.toString()?.trim()?.take(MAX_CHARS).orEmpty()
        val description = node.contentDescription?.toString()?.trim()?.take(MAX_CHARS).orEmpty()
        if (text.isNotEmpty() || description.isNotEmpty() || node.isScrollable) {
            out += ScreenNode(text, description, node.viewIdResourceName.orEmpty(), node.className?.toString()?.substringAfterLast('.').orEmpty(), depth, node.isClickable, node.isScrollable)
        }
        for (i in 0 until node.childCount) node.getChild(i)?.let { collect(it, depth + 1, out) }
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

    /** The visible card titled [title] on the Health page, by its title element. */
    private fun card(root: AccessibilityNodeInfo, title: String) = visible(root).firstOrNull { it.id() == "function_set_items_title" && it.text?.toString()?.trim() == title }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = node
        repeat(8) { if (n == null) return null; if (n!!.isClickable) return n; n = n!!.parent }
        return null
    }

    // --- the walk ---------------------------------------------------------------------------------------------------------

    /** Opens Huawei Health and walks it (see the class comment), then brings PokéLife back. [done] gets null or what stopped it, on the main thread. */
    fun walk(done: (String?) -> Unit) {
        if (walking) { done("Already reading Huawei Health"); return } // copy-ok: result
        val launch = packageManager.getLaunchIntentForPackage(HuaweiHealth.PACKAGE) ?: run { done("Huawei Health is not installed"); return } // copy-ok: result
        main.removeCallbacks(settle)
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

    /** Null when the walk got through; otherwise what stopped it, in words. */
    private suspend fun runWalk(): String? {
        delay(FIRST_WAIT_MS)
        if (awaitHealthPage() == null) return "Huawei Health did not show its Health page" // copy-ok: result
        scrollThrough(forward = true, MAX_SCROLLS)
        for (title in DETAIL_CARDS) {
            val root = huaweiRoot() ?: return "Huawei Health closed" // copy-ok: result
            val target = findCard(root, title) ?: continue
            if (clickableAncestor(target)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) != true) continue
            delay(PAGE_WAIT_MS)
            val detail = huaweiRoot() ?: return "Huawei Health closed" // copy-ok: result
            if (isHealthPage(detail)) continue // the tap opened nothing
            scrollThrough(forward = true, DETAIL_SCROLLS)
            // A page with Day / Week / Month / Year tabs (the Sleep page): also capture its Week view, which is a tab of the same page, so Back below still leaves it.
            if (openWeek()) { delay(PAGE_WAIT_MS); scrollThrough(forward = true, DETAIL_SCROLLS) }
            performGlobalAction(GLOBAL_ACTION_BACK)
            delay(BACK_WAIT_MS)
            if (awaitHealthPage() == null) return "Stopped after $title: Back did not return to the Health page" // copy-ok: result
        }
        return null
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

    /** Captures the page, then scrolls its main list and captures again until it stops moving or [max] scrolls. */
    private suspend fun scrollThrough(forward: Boolean, max: Int) {
        var last = capture()?.signature
        repeat(max) {
            val root = huaweiRoot() ?: return
            val scroller = mainScroller(root) ?: return
            if (!scroller.performAction(if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return
            delay(SCROLL_WAIT_MS)
            val now = capture()?.signature
            if (now == last) return
            last = now
        }
    }

    /** The card titled [title], scrolling the Health page back up to find it (a list only holds the cards near the screen). */
    private suspend fun findCard(start: AccessibilityNodeInfo, title: String): AccessibilityNodeInfo? {
        card(start, title)?.let { return it }
        for (direction in listOf(false, true)) repeat(MAX_SCROLLS) {
            val root = huaweiRoot() ?: return null
            card(root, title)?.let { return it }
            val scroller = mainScroller(root) ?: return null
            if (!scroller.performAction(if (direction) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return@repeat
            delay(SCROLL_WAIT_MS)
        }
        return huaweiRoot()?.let { card(it, title) }
    }

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
        /** The only cards the walk opens: the ones HEAL has records for. */
        val DETAIL_CARDS = listOf("Sleep", "Weight", "Exercise records")
        const val OK = ""
        const val SETTLE_MS = 800L
        const val FIRST_WAIT_MS = 2_500L
        const val RETRY_MS = 1_500L
        const val SCROLL_WAIT_MS = 1_000L
        const val PAGE_WAIT_MS = 2_500L
        const val BACK_WAIT_MS = 1_500L
        const val TIMEOUT_MS = 90_000L
        const val MAX_TRIES = 4
        const val MAX_SCROLLS = 12
        const val DETAIL_SCROLLS = 6
        const val MAX_NODES = 400
        const val MAX_DEPTH = 40
        const val MAX_CHARS = 200
    }
}
