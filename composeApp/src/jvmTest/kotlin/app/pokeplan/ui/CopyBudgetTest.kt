package app.pokeplan.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * No sentence-length copy in the UI (DESIGN_SYSTEM 12A rules 1 and 11, owner, 13 November 2026): a string of eight or more words in a
 * screen is a caption that explains, and the screen should not need it. Help, What's New and the story are reading, so they are not
 * counted; nor are a field's error, a confirmation, an accessibility label or feedback through `say`, which answer something the user did.
 * A sentence that is the answer itself (a result, a warning, a game line) stays when its line says so with `// copy-ok: <why>`, so keeping
 * one is a decision someone wrote down. A failure names the file and line: cut the sentence or fix the layout that needed it.
 */
class CopyBudgetTest {
    private val root = File("src/commonMain/kotlin/app/pokeplan/ui/screens")

    /** Files that are reading, not screens. */
    private val reading = setOf("Help.kt", "WhatsNew.kt", "StoryPlayer.kt", "StoryCues.kt", "Cutscenes.kt", "Onboarding.kt", "SkyBarks.kt")

    /** A line holding one of these answers the user or a screen reader, and may say a sentence. */
    private val answers = listOf(
        "contentDescription", "stateDescription", "onClickLabel", "onLongClickLabel", "say(", "Report(", "error", "takes =", "reject",
        "Confirm(", "\"Delete", "\"Erase", "\"Remove", "throw ", "require(", "check(", "Log.", "println",
    )

    private val literal = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
    private val template = Regex("\\$\\{[^}]*\\}|\\$[A-Za-z_][A-Za-z0-9_.]*")

    private fun words(s: String) = template.replace(s, "x").trim().split(Regex("\\s+")).count { it.any(Char::isLetter) }

    @Test
    fun noScreenShowsASentenceLongCaption() {
        val offenders = root.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name !in reading }.flatMap { f ->
            var inComment = false
            f.readLines().mapIndexedNotNull { i, raw ->
                val line = raw.trim()
                if (line.startsWith("/*")) inComment = true
                val skip = inComment || line.startsWith("//") || line.startsWith("*")
                if (line.contains("*/")) inComment = false
                if (skip || "copy-ok" in line || answers.any { it in line }) null
                else literal.findAll(line).map { it.groupValues[1] }.firstOrNull { words(it) >= 8 }?.let { "${f.relativeTo(root)}:${i + 1}: \"$it\"" }
            }.asSequence()
        }.toList()
        assertTrue(offenders.isEmpty(), "Captions of eight or more words (cut them, or fix the layout that needs them):\n" + offenders.joinToString("\n"))
    }
}
