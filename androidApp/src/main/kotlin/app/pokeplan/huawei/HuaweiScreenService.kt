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
        if (!walking) { main.removeCallbacks(settle); main.postDelayed(settle, SETTLE_MS) }
    }

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

    private fun mainScroller(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val r = Rect()
        return visible(root).filter { it.isScrollable }.maxByOrNull { it.getBoundsInScreen(r); r.width().toLong() * r.height() }
    }

    private fun isHealthPage(root: AccessibilityNodeInfo) = visible(root).any { it.id() == "id_recyclerview" } && visible(root).any { it.id() == "function_set_items_title" }

    private fun card(root: AccessibilityNodeInfo, title: String) = visible(root).firstOrNull { it.id() == "function_set_items_title" && it.text?.toString()?.trim() == title }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = node
        repeat(8) { if (n == null) return null; if (n!!.isClickable) return n; n = n!!.parent }
        return null
    }

    fun walk(done: (String?) -> Unit) {
        if (walking) { done("Already reading Huawei Health"); return }
        val launch = packageManager.getLaunchIntentForPackage(HuaweiHealth.PACKAGE) ?: run { done("Huawei Health is not installed"); return }
        main.removeCallbacks(settle)
        var answered = false
        fun answer(problem: String?) { if (!answered) { answered = true; done(problem) } }
        walkJob = scope.launch {
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val problem = withTimeoutOrNull(TIMEOUT_MS) { runWalk() ?: OK } ?: "Huawei Health took too long. What was seen is kept."
            comeBack()
            answer(problem.takeIf { it != OK })
        }.also { job ->
            job.invokeOnCompletion { e ->
                main.post {
                    if (e != null) answer("Screen reading was turned off. What was seen is kept.")
                    if (HuaweiCapture.screen === this) turnOff()
                }
            }
        }
    }

    private suspend fun runWalk(): String? {
        delay(FIRST_WAIT_MS)
        if (awaitHealthPage() == null) return "Huawei Health did not show its Health page"
        scrollThrough(forward = true, MAX_SCROLLS)
        for (title in DETAIL_CARDS) {
            val root = huaweiRoot() ?: return "Huawei Health closed"
            val target = findCard(root, title) ?: continue
            if (clickableAncestor(target)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) != true) continue
            delay(PAGE_WAIT_MS)
            val detail = huaweiRoot() ?: return "Huawei Health closed"
            if (isHealthPage(detail)) continue
            scrollThrough(forward = true, DETAIL_SCROLLS)
            if (openWeek()) { delay(PAGE_WAIT_MS); scrollThrough(forward = true, DETAIL_SCROLLS) }
            performGlobalAction(GLOBAL_ACTION_BACK)
            delay(BACK_WAIT_MS)
            if (awaitHealthPage() == null) return "Stopped after $title: Back did not return to the Health page"
        }
        return null
    }

    private fun openWeek(): Boolean {
        val root = huaweiRoot() ?: return false
        if (isHealthPage(root)) return false
        val tab = visible(root).firstOrNull { it.text?.toString()?.trim() == "Week" && it.className?.toString()?.endsWith("TextView") == true } ?: return false
        if (tab.isSelected) return false
        return clickableAncestor(tab)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private suspend fun awaitHealthPage(): AccessibilityNodeInfo? {
        repeat(MAX_TRIES) {
            huaweiRoot()?.takeIf { isHealthPage(it) }?.let { return it }
            delay(RETRY_MS)
        }
        return null
    }

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

    fun turnOff() {
        stop()
        disableSelf()
    }

    private fun comeBack() {
        packageManager.getLaunchIntentForPackage(packageName)?.let { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    private companion object {
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
