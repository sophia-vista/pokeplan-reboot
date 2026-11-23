package app.pokeplan.ui

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.test.fail

/**
 * Real pixel comparison for [ScreenshotTest].
 *
 * Reference images are committed at `composeApp/src/jvmTest/resources/screenshots/<name>.png`; every render also
 * writes its PNG to `composeApp/build/screenshots/<name>.png` as before. This is a golden regression gate, not the
 * old "something was drawn" smoke check:
 *
 * - `UPDATE_SCREENSHOTS=1` writes (or refreshes) the baseline for every render and passes. Baselines are only ever
 *   written in that mode, so an ordinary run can never silently accept a changed screen.
 * - With no baseline on disk yet, the render passes and the name is collected for one summary line at the end of the
 *   run. While the UI is still being edited in this branch that is the expected state; once the baselines are
 *   committed, a missing baseline is a failure (the committed PNGs are the acceptance set).
 * - With a baseline, the two images are decoded once and compared pixel by pixel. A pixel counts as differing when
 *   any of its red, green, blue or alpha channels differs by more than [CHANNEL_TOLERANCE] (0-255). That tolerance
 *   absorbs anti-aliasing and font-rasterisation noise without hiding a real change. More than
 *   [MAX_DIFFERING_FRACTION] of the image differing fails the render; the failure names the differing percentage,
 *   the first differing region's bounding box and where the actual, expected and diff images were written.
 *
 * The comparison is same-environment by design: baselines are regenerated only on a reviewed visual change
 * (`UPDATE_SCREENSHOTS=1`, then inspect the PNG diffs in the commit). See `ARCHITECTURE/TEST_STRATEGY.md` section 12.
 */
object ScreenshotCompare {
    private val baselineDir = File("src/jvmTest/resources/screenshots")
    private val outputDir = File("build/screenshots")

    /** A differing pixel is one whose red, green, blue or alpha channel differs by more than this (0-255). */
    const val CHANNEL_TOLERANCE = 8

    /** Up to this fraction of the image may differ through anti-aliasing/font noise; more is a regression. */
    const val MAX_DIFFERING_FRACTION = 0.002

    /**
     * The reviewed acceptance set: the screens whose pixels are compared on every run. The remaining renders stay
     * smoke renders (they still write their PNG) so the comparison can grow screen by screen without committing 151
     * full-size baselines at once. Add a name here together with its reviewed baseline.
     */
    val GOLDEN = setOf(
        "onboarding",
        "home",
        "home-rewards",
        "home-undo",
        "home-regions",
        "large-text-home",
        "gear-menu",
        "world-menu",
        "world-map-kanto",
        "planner",
        "collection-quests",
        "care-routine-edit-xl",
    )

    private const val UPDATE_ENV = "UPDATE_SCREENSHOTS"
    private const val ALLOW_MISSING_ENV = "SCREENSHOTS_ALLOW_MISSING"

    private val update = System.getenv(UPDATE_ENV) == "1"
    private val allowMissing = System.getenv(ALLOW_MISSING_ENV) == "1"
    private val missing = mutableListOf<String>()
    private var written = 0
    private var compared = 0

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                if (missing.isNotEmpty()) {
                    val shown = missing.take(8).joinToString(", ")
                    val more = if (missing.size > 8) ", ..." else ""
                    println("[screenshots] ${missing.size} renders have no committed baseline yet, so they were not compared: $shown$more")
                }
                if (written > 0) println("[screenshots] wrote $written baselines ($UPDATE_ENV=1); review them before committing")
                if (compared > 0) println("[screenshots] compared $compared renders against their committed baselines")
            },
        )
    }

    /** Compares one rendered PNG with its committed baseline, or writes it in update mode. */
    fun compare(name: String, png: ByteArray) {
        if (name !in GOLDEN) return
        val baseline = File(baselineDir, "$name.png")
        if (update) {
            baseline.parentFile.mkdirs()
            baseline.writeBytes(png)
            synchronized(this) { written++ }
            return
        }
        if (!baseline.exists()) {
            if (baselineDir.isDirectory && !allowMissing) {
                fail(
                    "screenshot \"$name\" has no baseline (${baseline.path}) while other baselines are committed. " +
                        "Run `UPDATE_SCREENSHOTS=1 ./gradlew :composeApp:jvmTest --tests \"app.pokeplan.ui.ScreenshotTest\"` " +
                        "and review the new PNG (or set $ALLOW_MISSING_ENV=1 for a deliberately partial run).",
                )
            }
            synchronized(this) { missing += name }
            return
        }
        val baselineBytes = baseline.readBytes()
        if (png.contentEquals(baselineBytes)) {
            synchronized(this) { compared++ }
            return
        }
        val expected = decode(baselineBytes, baseline.path)
        val actual = decode(png, "build/screenshots/$name.png")
        if (expected.width != actual.width || expected.height != actual.height) {
            fail(
                "screenshot \"$name\" changed size: baseline ${expected.width}x${expected.height} px, " +
                    "rendered ${actual.width}x${actual.height} px. Actual: build/screenshots/$name.png, " +
                    "baseline: ${baseline.path}.",
            )
        }
        val diff = comparePixels(expected, actual)
        if (diff.fraction > MAX_DIFFERING_FRACTION) {
            val diffFile = File(outputDir, "$name.diff.png")
            File(outputDir, "$name.expected.png").writeBytes(baselineBytes)
            writeDiff(diffFile, actual, diff)
            fail(
                "screenshot \"$name\" differs from its baseline: ${diff.percentage()} of pixels differ " +
                    "(allowed ${percent(MAX_DIFFERING_FRACTION)}, per-channel tolerance $CHANNEL_TOLERANCE/255); " +
                    "first differing region ${diff.firstRegion.width}x${diff.firstRegion.height} px at " +
                    "(${diff.firstRegion.left}, ${diff.firstRegion.top}); all differing pixels in ${diff.allDiffingBounds}; " +
                    "actual: build/screenshots/$name.png, baseline: ${baseline.path}, diff: ${diffFile.path}, " +
                    "expected copy: build/screenshots/$name.expected.png.",
            )
        }
        if (diff.differing > 0) {
            println(
                "[screenshots] \"$name\" is within tolerance: ${diff.percentage()} differing " +
                    "(${diff.firstRegion.width}x${diff.firstRegion.height} px first at " +
                    "(${diff.firstRegion.left}, ${diff.firstRegion.top})), allowed ${percent(MAX_DIFFERING_FRACTION)}",
            )
        }
        synchronized(this) { compared++ }
    }

    private fun percent(fraction: Double) = "%.2f%%".format(Locale.ROOT, fraction * 100)

    private class Pixels(val width: Int, val height: Int, val rgba: ByteArray)

    private fun decode(png: ByteArray, source: String): Pixels {
        val bitmap = try {
            Bitmap.makeFromImage(Image.makeFromEncoded(png))
        } catch (e: Throwable) {
            fail("screenshot \"$source\" is not a readable PNG: ${e.message}")
        }
        val info = ImageInfo(bitmap.width, bitmap.height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
        val rgba = bitmap.readPixels(info, bitmap.width * 4, 0, 0)
            ?: fail("screenshot \"$source\" (${bitmap.width}x${bitmap.height}) could not be decoded to RGBA pixels")
        return Pixels(bitmap.width, bitmap.height, rgba)
    }

    private data class Region(val left: Int, val top: Int, val width: Int, val height: Int)

    private class Diff(
        val differing: Long,
        val fraction: Double,
        val firstRegion: Region,
        val allDiffingBounds: String,
        val differingPixels: IntArray,
    ) {
        fun percentage() = "%.3f%%".format(Locale.ROOT, fraction * 100)
    }

    private fun comparePixels(expected: Pixels, actual: Pixels): Diff {
        val width = expected.width
        val height = expected.height
        val total = width.toLong() * height
        val e = expected.rgba
        val a = actual.rgba
        val mask = BooleanArray(width * height)
        var count = 0
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        var first = -1
        for (y in 0 until height) {
            val row = y * width * 4
            for (x in 0 until width) {
                val o = row + x * 4
                val dr = abs((e[o].toInt() and 0xFF) - (a[o].toInt() and 0xFF))
                val dg = abs((e[o + 1].toInt() and 0xFF) - (a[o + 1].toInt() and 0xFF))
                val db = abs((e[o + 2].toInt() and 0xFF) - (a[o + 2].toInt() and 0xFF))
                val da = abs((e[o + 3].toInt() and 0xFF) - (a[o + 3].toInt() and 0xFF))
                if (dr > CHANNEL_TOLERANCE || dg > CHANNEL_TOLERANCE || db > CHANNEL_TOLERANCE || da > CHANNEL_TOLERANCE) {
                    val p = y * width + x
                    mask[p] = true
                    count++
                    if (first < 0) first = p
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        // A byte difference with no pixel difference (e.g. a different PNG encoder) is not a visual change.
        if (first < 0) return Diff(0, 0.0, Region(0, 0, 0, 0), "none", IntArray(0))
        val pixels = IntArray(count)
        var i = 0
        for (p in mask.indices) if (mask[p]) pixels[i++] = p
        val region = connectedRegion(mask, width, height, first)
        val bounds = "[$minX, $minY, ${maxX + 1}, ${maxY + 1}]"
        return Diff(count.toLong(), count.toDouble() / total, region, bounds, pixels)
    }

    /** The connected component of differing pixels that contains [first]: a flood fill over differing pixels only. */
    private fun connectedRegion(mask: BooleanArray, width: Int, height: Int, first: Int): Region {
        val queue = ArrayDeque<Int>()
        mask[first] = false
        queue += first
        var minX = first % width
        var maxX = minX
        var minY = first / width
        var maxY = minY
        while (queue.isNotEmpty()) {
            val p = queue.removeFirst()
            val x = p % width
            val y = p / width
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
            if (x > 0 && mask[p - 1]) { mask[p - 1] = false; queue += p - 1 }
            if (x + 1 < width && mask[p + 1]) { mask[p + 1] = false; queue += p + 1 }
            if (y > 0 && mask[p - width]) { mask[p - width] = false; queue += p - width }
            if (y + 1 < height && mask[p + width]) { mask[p + width] = false; queue += p + width }
        }
        return Region(minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    private fun writeDiff(file: File, actual: Pixels, diff: Diff) {
        val marked = actual.rgba.copyOf()
        for (p in diff.differingPixels) {
            val o = p * 4
            marked[o] = 0xFF.toByte() // R
            marked[o + 1] = 0x00 // G
            marked[o + 2] = 0xFF.toByte() // B
            marked[o + 3] = 0xFF.toByte() // A
        }
        val info = ImageInfo(actual.width, actual.height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
        val png = Image.makeRaster(info, marked, actual.width * 4).encodeToData(EncodedImageFormat.PNG)?.bytes
            ?: fail("could not write the screenshot diff image ${file.path}")
        file.parentFile.mkdirs()
        file.writeBytes(png)
    }
}
