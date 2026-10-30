package app.pokeplan.ui.launcher

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LauncherModelTest {
    private fun app(label: String, category: AppCategory = AppCategory.OTHER) = LauncherApp("pkg.${label.lowercase()}", label, category)
    private val apps = listOf(app("Maps", AppCategory.TRAVEL), app("camera"), app("1Password"), app("Chess", AppCategory.GAMES), app("Calendar", AppCategory.PRODUCTIVITY), app("Äpfel"))

    @Test
    fun sortedAToZIgnoringCaseWithSymbolsFirst() {
        val names = LauncherList.visible(apps, LauncherPrefs()).map { it.label }
        assertEquals(listOf("1Password", "Äpfel", "Calendar", "camera", "Chess", "Maps"), names)
    }

    @Test
    fun hiddenAppsAreLeftOutButCanBeListed() {
        val prefs = LauncherPrefs().hide("pkg.chess")
        assertTrue(LauncherList.visible(apps, prefs).none { it.label == "Chess" })
        assertEquals(listOf("Chess"), LauncherList.visible(apps, prefs, hidden = true).map { it.label })
        assertTrue(LauncherList.categories(apps, prefs).none { it.first == AppCategory.GAMES })
    }

    @Test
    fun searchPutsNamesThatStartWithItFirst() {
        val found = LauncherList.visible(listOf(app("Photo Maps"), app("Maps"), app("Camera")), LauncherPrefs(), query = " ma ")
        assertEquals(listOf("Maps", "Photo Maps"), found.map { it.label })
    }

    @Test
    fun movedAppsChangeCategoryAndMovingBackForgetsIt() {
        val chess = apps.first { it.label == "Chess" }
        val moved = LauncherPrefs().move(chess, AppCategory.PRODUCTIVITY)
        assertEquals(listOf("Calendar", "Chess"), LauncherList.visible(apps, moved, category = AppCategory.PRODUCTIVITY).map { it.label })
        assertEquals(LauncherPrefs(), moved.move(chess, AppCategory.GAMES))
    }

    @Test
    fun hidingAnAppUnpinsIt() {
        val prefs = LauncherPrefs().pin("a").pin("b").pin("a").hide("a")
        assertEquals(listOf("b"), prefs.pinned)
    }

    @Test
    fun pinnedKeepsPinOrderAndDropsUninstalledApps() {
        val prefs = LauncherPrefs().pin("pkg.maps").pin("gone").pin("pkg.camera")
        assertEquals(listOf("Maps", "camera"), LauncherList.pinned(apps, prefs).map { it.label })
    }

    @Test
    fun lettersPointAtTheirFirstApp() {
        val sorted = LauncherList.visible(apps, LauncherPrefs())
        assertEquals(listOf('#' to 0, 'A' to 1, 'C' to 2, 'M' to 5), LauncherList.letters(sorted))
    }

    @Test
    fun theTownSceneIsRememberedAndAnUnknownOneIgnored() {
        val prefs = LauncherPrefs().withScene("beach")
        assertEquals(prefs, LauncherPrefs.decode(prefs.encode()))
        assertEquals("meadow", LauncherPrefs().withScene("nowhere").scene)
        assertEquals("meadow", LauncherPrefs.decode("S nowhere\n").scene)
        assertEquals("", LauncherPrefs().encode())
    }

    @Test
    fun androidCategoriesMapAndUnknownIsOther() {
        assertEquals(AppCategory.GAMES, AppCategory.fromAndroid(0))
        assertEquals(AppCategory.MEDIA, AppCategory.fromAndroid(2))
        assertEquals(AppCategory.OTHER, AppCategory.fromAndroid(-1))
        assertEquals(AppCategory.OTHER, AppCategory.fromAndroid(99))
    }

    @Test
    fun prefsSurviveEncodingAndDamagedTextIsSkipped() {
        val prefs = LauncherPrefs(listOf("a/b|0", "c"), setOf("h"), mapOf("m" to AppCategory.NEWS))
        assertEquals(prefs, LauncherPrefs.decode(prefs.encode()))
        val damaged = LauncherPrefs.decode("garbage\nP \nM x\tNOPE\nM y\nX z\nH ok\n")
        assertEquals(LauncherPrefs(hidden = setOf("ok")), damaged)
        assertEquals(LauncherPrefs(), LauncherPrefs.decode(null))
    }
}
