package app.pokeplan.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class CopyBudgetTest {
    private val root = File("src/commonMain/kotlin/app/pokeplan/ui/screens")

    private val reading = setOf("Help.kt", "WhatsNew.kt", "StoryPlayer.kt", "StoryCues.kt", "Cutscenes.kt", "Onboarding.kt", "SkyBarks.kt", "Credits.kt")

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
