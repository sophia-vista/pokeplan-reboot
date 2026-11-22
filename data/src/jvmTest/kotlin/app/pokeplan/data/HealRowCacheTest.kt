package app.pokeplan.data

import app.cash.sqldelight.db.QueryResult
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Heal saves without opening every sealed row (21 November 2026): the rows as last written are kept in memory and used only while they provably still match the
 * database. Counts how many times the key is used, so a regression shows as a number. Synthetic data only.
 */
class HealRowCacheTest {
    private class Counting(private val inner: RowCipher) : RowCipher {
        var seals = 0; var opens = 0
        override fun seal(plain: String): String { seals++; return inner.seal(plain) }
        override fun open(sealed: String): String? { opens++; return inner.open(sealed) }
    }

    private fun key(seed: Int) = AesGcmCipher(SecretKeySpec(ByteArray(32) { (it * 7 + seed).toByte() }, "AES"), "pokelife-heal")
    private fun doc(n: Int, tweak: Int = -1) = """{"steps":[${(1..n).joinToString(",") { """{"id":"s$it","day":"2026-10-${(it % 28 + 1).toString().padStart(2, '0')}","steps":${if (it == tweak) 999 else it * 10},"updatedMillis":1}""" }}],"settings":{"a":1}}"""

    private fun sealedStore(c: RowCipher): DataStore = JvmDatabase.open().also { s ->
        s.live.activate(null, null, 1); s.live.moveHealToTables(2)
        s.healCipher = c
        assertTrue(s.healTables.seal(3))
    }

    @Test fun aSaveOpensNothingItDidNotChange() {
        val c = Counting(key(1)); val s = sealedStore(c)
        s.healTables.write(doc(300))
        c.seals = 0; c.opens = 0
        s.healTables.write(doc(300, tweak = 7))
        assertEquals(1, c.seals, "only the changed row is sealed again")
        assertEquals(0, c.opens, "no stored row is opened to find that out")
        assertTrue("999" in assertNotNull(s.healTables.read()))
    }

    @Test fun readsAndTheLockedCheckUseTheRowsTheyAlreadyHave() {
        val c = Counting(key(1)); val s = sealedStore(c)
        s.healTables.write(doc(100))
        c.opens = 0
        repeat(3) { assertNotNull(s.healTables.readOrNull()) }
        assertEquals(0, c.opens)
    }

    @Test fun aFirstSaveAfterAStartOpensTheRowsOnceAndLaterOnesDoNot() {
        val c = Counting(key(1)); val s = sealedStore(c)
        s.healTables.write(doc(50))
        val fresh = JvmDatabase.open() // a cold start has no cache: it opens the rows once
        fresh.healCipher = null
        s.healTables.write(doc(50, tweak = 3))
        c.opens = 0
        s.healTables.write(doc(50, tweak = 4))
        assertEquals(0, c.opens)
    }

    @Test fun aChangeMadeBehindItsBackIsNoticedAndNeverOverwrittenWithAStaleCopy() {
        val c = Counting(key(1)); val s = sealedStore(c)
        s.healTables.write(doc(20))
        s.driver.execute(null, "DELETE FROM heal_steps WHERE key = 's5'", 0) // another writer removes a row
        s.healTables.write(doc(20)) // the wanted document has it again
        val back = assertNotNull(s.healTables.read())
        assertTrue("\"s5\"" in back, "the row the cache did not know was gone is written back")
        assertEquals(20L, s.driver.executeQuery(null, "SELECT COUNT(*) FROM heal_steps", { cur -> QueryResult.Value(if (cur.next().value) cur.getLong(0) else null) }, 0).value)
    }

    @Test fun aDifferentKeyOrNoKeyIsNeverServedFromTheCache() {
        val c = Counting(key(1)); val s = sealedStore(c)
        s.healTables.write(doc(10))
        s.healCipher = key(2) // another phone's key: the rows cannot be opened
        assertTrue(s.healTables.locked(), "the cache is not used for a key it was not made with")
        s.healCipher = null
        assertFailsWith<HealLockedException> { s.healTables.write(doc(10, tweak = 2)) }
    }

    @Test fun erasingAKindOrAllOfHealForgetsTheCache() {
        val c = Counting(key(1)); val s = sealedStore(c)
        s.healTables.write(doc(10))
        s.healTables.erase("steps")
        assertEquals(null, s.healTables.read()?.takeIf { "\"steps\":[" in it && "s1" in it }, "nothing of the erased kind comes back from memory")
        s.healTables.write(doc(10))
        s.healTables.clear()
        assertEquals(null, s.healTables.read())
    }
}
