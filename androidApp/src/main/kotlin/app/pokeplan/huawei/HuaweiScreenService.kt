package app.pokeplan.huawei

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.pokeplan.app.HuaweiHealth
import app.pokeplan.app.ScreenCapture
import app.pokeplan.app.ScreenNode

/**
 * Reads Huawei Health's screens (INTEGRATIONS_AND_PRIVACY.md 5.2.2). Declared for `com.huawei.health` only (res/xml/huawei_reader.xml), so Android sends it
 * no other app's events; the package is checked again on every event and every window. It captures the text of a page once the page settles, keeps it in
 * [HuaweiCapture] (memory only), and on the player's tap walks Huawei Health's first page by scrolling. It never taps, types or changes anything, and never logs.
 */
class HuaweiScreenService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private var page = ""
    private var walk: Walk? = null
    private val settle = Runnable { capture() }

    val walking: Boolean get() = walk != null

    private class Walk(val done: (String?) -> Unit)

    override fun onServiceConnected() { HuaweiCapture.screen = this }

    override fun onUnbind(intent: Intent?): Boolean {
        stop()
        return super.onUnbind(intent)
    }

    override fun onDestroy() { stop(); super.onDestroy() }

    private fun stop() {
        if (HuaweiCapture.screen === this) HuaweiCapture.screen = null
        main.removeCallbacksAndMessages(null)
        walk?.let { w -> walk = null; w.done("Screen reading was turned off. What was seen is kept.") } // copy-ok: result
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName?.toString() != HuaweiHealth.PACKAGE) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) event.className?.toString()?.substringAfterLast('.')?.takeIf { it.isNotBlank() }?.let { page = it }
        // Content changes come in bursts while a page loads or animates: capture once it has been still for a moment. A walk captures on its own steps.
        if (walk == null) { main.removeCallbacks(settle); main.postDelayed(settle, SETTLE_MS) }
    }

    /** Huawei Health's window now, or null when another app is in front. */
    private fun huaweiRoot(): AccessibilityNodeInfo? = rootInActiveWindow?.takeIf { it.packageName?.toString() == HuaweiHealth.PACKAGE }

    private fun capture(root: AccessibilityNodeInfo? = huaweiRoot()): ScreenCapture? {
        root ?: return null
        val nodes = ArrayList<ScreenNode>()
        collect(root, 0, nodes)
        return ScreenCapture(System.currentTimeMillis(), page, nodes).also { HuaweiCapture.addScreen(it) }
    }

    private fun collect(node: AccessibilityNodeInfo, depth: Int, out: MutableList<ScreenNode>) {
        if (out.size >= MAX_NODES || depth > MAX_DEPTH || node.isPassword) return
        val text = node.text?.toString()?.trim()?.take(MAX_CHARS).orEmpty()
        val description = node.contentDescription?.toString()?.trim()?.take(MAX_CHARS).orEmpty()
        if (text.isNotEmpty() || description.isNotEmpty() || node.isScrollable) {
            out += ScreenNode(text, description, node.viewIdResourceName.orEmpty(), node.className?.toString()?.substringAfterLast('.').orEmpty(), depth, node.isClickable, node.isScrollable)
        }
        for (i in 0 until node.childCount) node.getChild(i)?.let { collect(it, depth + 1, out) }
    }

    /** The biggest scrolling element of [root] (the page's own list rather than a carousel), or null. */
    private fun mainScroller(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0L
        val queue = ArrayDeque(listOf(root))
        val r = Rect()
        var visited = 0
        while (queue.isNotEmpty() && visited++ < MAX_NODES * 4) {
            val n = queue.removeFirst()
            if (n.isScrollable) {
                n.getBoundsInScreen(r)
                val area = r.width().toLong() * r.height()
                if (area > bestArea) { best = n; bestArea = area }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.addLast(it) }
        }
        return best
    }

    /** Opens Huawei Health, captures its first page, scrolls it to the end capturing each step, and brings PokéLife back. [done] runs on the main thread. */
    fun walk(done: (String?) -> Unit) {
        if (walk != null) { done("Already reading Huawei Health"); return } // copy-ok: result
        val launch = packageManager.getLaunchIntentForPackage(HuaweiHealth.PACKAGE) ?: run { done("Huawei Health is not installed"); return } // copy-ok: result
        val w = Walk(done)
        walk = w
        main.removeCallbacks(settle)
        startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        main.postDelayed({ step(w, 0, 0, null) }, FIRST_WAIT_MS)
        main.postDelayed({ if (walk === w) finish(w, "Huawei Health took too long. What was seen is kept.") }, TIMEOUT_MS) // copy-ok: result
    }

    private fun step(w: Walk, scrolls: Int, tries: Int, lastSignature: String?) {
        if (walk !== w) return
        val root = huaweiRoot()
        if (root == null) {
            if (tries < MAX_TRIES) main.postDelayed({ step(w, scrolls, tries + 1, lastSignature) }, RETRY_MS)
            else finish(w, "Huawei Health did not come to the front") // copy-ok: result
            return
        }
        val c = capture(root)
        val scroller = mainScroller(root)
        // A splash or an ad has nothing to scroll: give the real page a moment before deciding the walk is over.
        if (scrolls == 0 && scroller == null && tries < MAX_TRIES) { main.postDelayed({ step(w, 0, tries + 1, null) }, RETRY_MS); return }
        if (scroller == null || c?.signature == lastSignature || scrolls >= MAX_SCROLLS || !scroller.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) { finish(w, null); return }
        main.postDelayed({ step(w, scrolls + 1, 0, c?.signature) }, SCROLL_WAIT_MS)
    }

    private fun finish(w: Walk, problem: String?) {
        if (walk !== w) return
        walk = null
        // Back to PokéLife the way its icon would: its own task comes to the front as it was.
        packageManager.getLaunchIntentForPackage(packageName)?.let { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        main.post { w.done(problem) }
    }

    private companion object {
        const val SETTLE_MS = 800L
        const val FIRST_WAIT_MS = 2_500L
        const val RETRY_MS = 1_500L
        const val SCROLL_WAIT_MS = 1_000L
        const val TIMEOUT_MS = 30_000L
        const val MAX_TRIES = 3
        const val MAX_SCROLLS = 12
        const val MAX_NODES = 400
        const val MAX_DEPTH = 40
        const val MAX_CHARS = 200
    }
}
