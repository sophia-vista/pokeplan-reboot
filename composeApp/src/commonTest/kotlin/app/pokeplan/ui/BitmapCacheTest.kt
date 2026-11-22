package app.pokeplan.ui

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BitmapCacheTest {
    private fun image() = ImageBitmap(100, 100)

    @Test
    fun theLongestUnusedPictureGoesFirstWhenOverTheBudget() {
        val cache = BitmapCache(100_000)
        cache["a"] = image(); cache["b"] = image()
        assertNotNull(cache["a"])
        cache["c"] = image()
        assertTrue("a" in cache)
        assertTrue("b" !in cache)
        assertTrue("c" in cache)
        assertTrue(cache.usedBytes <= 100_000)
    }

    @Test
    fun theNewestStaysEvenWhenItAloneIsOverTheBudget() {
        val cache = BitmapCache(10)
        cache["a"] = image()
        assertEquals(1, cache.count)
        cache["b"] = image()
        assertEquals(1, cache.count)
        assertNotNull(cache["b"])
    }

    @Test
    fun aMissingPictureIsRememberedWithoutCountingBytes() {
        val cache = BitmapCache(100_000)
        cache["gone"] = null
        assertTrue("gone" in cache)
        assertNull(cache["gone"])
        assertEquals(0L, cache.usedBytes)
        cache["gone"] = image()
        assertEquals(40_000L, cache.usedBytes)
    }
}
