package app.pokeplan.ui.gear

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The generator is checked with an independent decoder (ZXing): every code it draws must read back as the text it was made from. */
class QrCodeTest {
    private fun decode(q: QrCode): String {
        val scale = 4; val border = 4
        val n = (q.size + border * 2) * scale
        val pixels = IntArray(n * n) { i ->
            val x = i % n / scale - border; val y = i / n / scale - border
            if (q.dark(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(n, n, pixels)))
        return QRCodeReader().decode(bitmap).text
    }

    @Test
    fun codesReadBackAcrossVersionsAndLevels() {
        val samples = listOf(
            "A", "Hello, PokéLife!", "https://example.com/a?b=c&d=e", "x".repeat(40), "WIFI:T:WPA;S:Home network;P:correct horse battery;;",
            "The quick brown fox jumps over the lazy dog. ".repeat(3), "日本語のテキスト 12345", "Z".repeat(120),
        )
        for (level in QrCode.Level.entries) for (text in samples) {
            val q = QrCode.encode(text, level) ?: continue
            assertEquals(text, decode(q), "version ${q.version} level $level mask ${q.mask}")
        }
    }

    @Test
    fun theSmallestVersionIsUsedAndTooMuchIsRefused() {
        assertEquals(1, QrCode.encode("Hi", QrCode.Level.M)!!.version)
        assertEquals(21, QrCode.encode("Hi", QrCode.Level.M)!!.size)
        val v10 = QrCode.encode("y".repeat(QrCode.capacity(QrCode.Level.L)), QrCode.Level.L)
        assertNotNull(v10); assertEquals(10, v10.version); assertEquals(57, v10.size)
        assertEquals("y".repeat(QrCode.capacity(QrCode.Level.L)), decode(v10))
        assertNull(QrCode.encode("y".repeat(QrCode.capacity(QrCode.Level.L) + 1), QrCode.Level.L))
    }
}
