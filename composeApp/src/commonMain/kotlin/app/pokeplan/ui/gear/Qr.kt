package app.pokeplan.ui.gear

class QrCode private constructor(val version: Int, val level: Level, val size: Int, private val modules: Array<BooleanArray>, val mask: Int) {
    enum class Level(val formatBits: Int) { L(1), M(0), Q(3), H(2) }

    fun dark(x: Int, y: Int): Boolean = x in 0 until size && y in 0 until size && modules[y][x]

    companion object {
        const val MAX_VERSION = 10

        private val ECC_PER_BLOCK = arrayOf(
            intArrayOf(-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18),
            intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26),
            intArrayOf(-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24),
            intArrayOf(-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28),
        )
        private val BLOCKS = arrayOf(
            intArrayOf(-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4),
            intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5),
            intArrayOf(-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8),
            intArrayOf(-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8),
        )

        private fun rawModules(ver: Int): Int {
            var r = (16 * ver + 128) * ver + 64
            if (ver >= 2) {
                val n = ver / 7 + 2
                r -= (25 * n - 10) * n - 55
                if (ver >= 7) r -= 36
            }
            return r
        }

        private fun dataCodewords(ver: Int, level: Level): Int = rawModules(ver) / 8 - ECC_PER_BLOCK[level.ordinal][ver] * BLOCKS[level.ordinal][ver]

        fun capacity(level: Level): Int = (dataCodewords(MAX_VERSION, level) * 8 - 4 - 16) / 8

        fun encode(text: String, level: Level = Level.M): QrCode? {
            val bytes = text.encodeToByteArray()
            for (ver in 1..MAX_VERSION) {
                val countBits = if (ver < 10) 8 else 16
                val capacityBits = dataCodewords(ver, level) * 8
                if (4 + countBits + bytes.size * 8 > capacityBits) continue
                return build(ver, level, bytes, countBits, capacityBits)
            }
            return null
        }

        private fun build(ver: Int, level: Level, bytes: ByteArray, countBits: Int, capacityBits: Int): QrCode {
            val bits = ArrayList<Int>()
            fun put(v: Int, n: Int) { for (i in n - 1 downTo 0) bits.add((v ushr i) and 1) }
            put(0b0100, 4); put(bytes.size, countBits)
            bytes.forEach { put(it.toInt() and 0xFF, 8) }
            put(0, minOf(4, capacityBits - bits.size))
            while (bits.size % 8 != 0) bits.add(0)
            var pad = 0xEC
            while (bits.size < capacityBits) { put(pad, 8); pad = pad xor 0xEC xor 0x11 }
            val data = ByteArray(bits.size / 8) { i -> var v = 0; for (b in 0 until 8) v = (v shl 1) or bits[i * 8 + b]; v.toByte() }
            val all = addEccAndInterleave(data, ver, level)

            val size = ver * 4 + 17
            val grid = Array(size) { BooleanArray(size) }
            val fn = Array(size) { BooleanArray(size) }
            fun setFn(x: Int, y: Int, dark: Boolean) { grid[y][x] = dark; fn[y][x] = true }
            for (i in 0 until size) { setFn(6, i, i % 2 == 0); setFn(i, 6, i % 2 == 0) }
            fun finder(cx: Int, cy: Int) {
                for (dy in -4..4) for (dx in -4..4) {
                    val d = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)); val x = cx + dx; val y = cy + dy
                    if (x in 0 until size && y in 0 until size) setFn(x, y, d != 2 && d != 4)
                }
            }
            finder(3, 3); finder(size - 4, 3); finder(3, size - 4)
            val pos = alignmentPositions(ver, size)
            for (i in pos.indices) for (j in pos.indices) {
                if ((i == 0 && j == 0) || (i == 0 && j == pos.size - 1) || (i == pos.size - 1 && j == 0)) continue
                for (dy in -2..2) for (dx in -2..2) setFn(pos[i] + dx, pos[j] + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
            }
            fun formatBits(mask: Int): Int {
                val d = (level.formatBits shl 3) or mask
                var rem = d
                repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
                return ((d shl 10) or rem) xor 0x5412
            }
            fun drawFormat(mask: Int) {
                val b = formatBits(mask)
                fun bit(i: Int) = (b ushr i) and 1 != 0
                for (i in 0..5) setFn(8, i, bit(i))
                setFn(8, 7, bit(6)); setFn(8, 8, bit(7)); setFn(7, 8, bit(8))
                for (i in 9..14) setFn(14 - i, 8, bit(i))
                for (i in 0..7) setFn(size - 1 - i, 8, bit(i))
                for (i in 8..14) setFn(8, size - 15 + i, bit(i))
                setFn(8, size - 8, true)
            }
            drawFormat(0)
            if (ver >= 7) {
                var rem = ver
                repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
                val b = (ver shl 12) or rem
                for (i in 0 until 18) {
                    val dark = (b ushr i) and 1 != 0; val a = size - 11 + i % 3; val c = i / 3
                    setFn(a, c, dark); setFn(c, a, dark)
                }
            }
            var k = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) for (j in 0..1) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vert else vert
                    if (!fn[y][x] && k < all.size * 8) { grid[y][x] = (all[k ushr 3].toInt() ushr (7 - (k and 7))) and 1 != 0; k++ }
                }
                right -= 2
            }
            fun applyMask(mask: Int) {
                for (y in 0 until size) for (x in 0 until size) {
                    val invert = when (mask) {
                        0 -> (x + y) % 2 == 0; 1 -> y % 2 == 0; 2 -> x % 3 == 0; 3 -> (x + y) % 3 == 0
                        4 -> (x / 3 + y / 2) % 2 == 0; 5 -> x * y % 2 + x * y % 3 == 0
                        6 -> (x * y % 2 + x * y % 3) % 2 == 0; else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                    }
                    if (!fn[y][x] && invert) grid[y][x] = !grid[y][x]
                }
            }
            var best = 0; var bestScore = Int.MAX_VALUE
            for (m in 0 until 8) {
                applyMask(m); drawFormat(m)
                val score = penalty(grid, size)
                if (score < bestScore) { bestScore = score; best = m }
                applyMask(m)
            }
            applyMask(best); drawFormat(best)
            return QrCode(ver, level, size, grid, best)
        }

        private fun alignmentPositions(ver: Int, size: Int): IntArray {
            if (ver == 1) return IntArray(0)
            val n = ver / 7 + 2
            val step = (ver * 4 + n * 2 + 1) / (n * 2 - 2) * 2
            val r = IntArray(n); r[0] = 6
            var p = size - 7
            for (i in n - 1 downTo 1) { r[i] = p; p -= step }
            return r
        }

        private fun mul(x: Int, y: Int): Int { var z = 0; for (i in 7 downTo 0) { z = (z shl 1) xor ((z ushr 7) * 0x11D); z = z xor (((y ushr i) and 1) * x) }; return z }

        private fun divisor(degree: Int): IntArray {
            val r = IntArray(degree); r[degree - 1] = 1
            var root = 1
            repeat(degree) {
                for (j in 0 until degree) { r[j] = mul(r[j], root); if (j + 1 < degree) r[j] = r[j] xor r[j + 1] }
                root = mul(root, 2)
            }
            return r
        }

        private fun remainder(data: IntArray, div: IntArray): IntArray {
            val r = IntArray(div.size)
            for (b in data) {
                val factor = b xor r[0]
                for (i in 0 until r.size - 1) r[i] = r[i + 1]
                r[r.size - 1] = 0
                for (i in r.indices) r[i] = r[i] xor mul(div[i], factor)
            }
            return r
        }

        private fun addEccAndInterleave(data: ByteArray, ver: Int, level: Level): ByteArray {
            val nBlocks = BLOCKS[level.ordinal][ver]; val eccLen = ECC_PER_BLOCK[level.ordinal][ver]
            val raw = rawModules(ver) / 8
            val shortBlocks = nBlocks - raw % nBlocks; val shortLen = raw / nBlocks
            val div = divisor(eccLen)
            val blocks = ArrayList<IntArray>()
            var k = 0
            for (i in 0 until nBlocks) {
                val len = shortLen - eccLen + if (i < shortBlocks) 0 else 1
                val dat = IntArray(len) { data[k + it].toInt() and 0xFF }
                k += len
                val ecc = remainder(dat, div)
                val padded = if (i < shortBlocks) dat + intArrayOf(0) else dat
                blocks.add(padded + ecc)
            }
            val out = ArrayList<Byte>()
            for (i in 0 until blocks[0].size) for (j in blocks.indices) {
                if (i != shortLen - eccLen || j >= shortBlocks) out.add(blocks[j][i].toByte())
            }
            return out.toByteArray()
        }

        private fun penalty(g: Array<BooleanArray>, size: Int): Int {
            var p = 0
            for (y in 0 until size) {
                var run = 1
                for (x in 1 until size) { if (g[y][x] == g[y][x - 1]) { run++; if (run == 5) p += 3 else if (run > 5) p++ } else run = 1 }
            }
            for (x in 0 until size) {
                var run = 1
                for (y in 1 until size) { if (g[y][x] == g[y - 1][x]) { run++; if (run == 5) p += 3 else if (run > 5) p++ } else run = 1 }
            }
            for (y in 0 until size - 1) for (x in 0 until size - 1) if (g[y][x] == g[y][x + 1] && g[y][x] == g[y + 1][x] && g[y][x] == g[y + 1][x + 1]) p += 3
            val dark = g.sumOf { row -> row.count { it } }
            val total = size * size
            p += (kotlin.math.abs(dark * 20 - total * 10) + total - 1) / total * 10
            return p
        }
    }
}
