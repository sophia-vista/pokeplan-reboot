package app.pokeplan.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmojiTest {
    @Test
    fun plainTextHasNoEmoji() {
        assertFalse(hasEmoji("Finish the report: 3 of 5 (café) & more"))
        assertEquals(listOf("Home" to false), splitEmoji("Home").map { it.text to it.emoji })
    }

    @Test
    fun anEmojiIsOneRunWithItsJoinersAndTheTextAroundItIsKept() {
        assertEquals(listOf("" to false).drop(1) + listOf("🏠" to true, "Home" to false), splitEmoji("🏠Home").map { it.text to it.emoji })
        assertEquals(listOf("🏠" to true, " WFH" to false), splitEmoji("🏠 WFH").map { it.text to it.emoji })
        // A family (person, joiner, person) and a heart with its variation selector are single runs.
        val family = "👨‍👩‍👧"
        assertEquals(listOf(family to true), splitEmoji(family).map { it.text to it.emoji })
        assertEquals(listOf("❤️" to true), splitEmoji("❤️").map { it.text to it.emoji })
        assertTrue(hasEmoji("Run 🏃 today"))
    }
}
