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

    @Test
    fun dungeonTownsAreWallsAndWaterInWholeRectanglesWithGroundWhereThePokemonWalk() {
        for (seed in 0 until 200) {
            val map = TownLayout.build(13, 26, seed)
            // The band along the top is wall, and no pond or clump reaches the strip the Pokémon walk in.
            assertEquals(TownCell.WALL, map.at(6, 0))
            val walkTop = (26 * TownLayout.WALK_TOP).toInt()
            val walkBottom = (26 * TownLayout.WALK_BOTTOM).toInt()
            for (y in walkTop..walkBottom) for (x in 0 until 13) assertEquals(TownCell.GROUND, map.at(x, y), "seed $seed at $x,$y")
            // Wall and water squares take their tiles from the atlas blocks on rows 0 to 2, ground from row 3.
            for (y in 0 until 26) for (x in 0 until 13) {
                val (ax, ay) = map.atlas(x, y, seed)
                assertTrue(ax in 0..5 && ay in 0..3)
                if (map.at(x, y) == TownCell.GROUND) assertEquals(3, ay) else assertTrue(ay in 0..2)
            }
        }
    }

    @Test
    fun aTownLooksTheSameEveryTime() {
        val a = TownLayout.build(13, 26, 7)
        val b = TownLayout.build(13, 26, 7)
        for (y in 0 until 26) for (x in 0 until 13) assertEquals(a.atlas(x, y, 7), b.atlas(x, y, 7))
    }

    @Test
    fun theTownScaleIsAWholeNumberAndPositionsSnapToIt() {
        assertEquals(1, townScale(100f))
        assertEquals(3, townScale(1080f))
        assertEquals(4, townScale(1440f))
        for (pixel in 1..6) for (v in listOf(0f, 1f, 17.4f, 333.3f, 1079f)) assertEquals(0, snap(v, pixel) % pixel)
    }

    @Test
    fun appsWithNoDeclaredCategoryAreSortedByTheirNames() {
        assertEquals(AppCategory.GAMES, AppCategory.guess(0, "com.some.thing", "Thing", false))
        assertEquals(AppCategory.COMMUNICATION, AppCategory.guess(-1, "com.google.android.apps.messaging", "Messages", false))
        assertEquals(AppCategory.SOCIAL, AppCategory.guess(-1, "com.instagram.android", "Instagram", false))
        assertEquals(AppCategory.MEDIA, AppCategory.guess(-1, "com.spotify.music", "Spotify", false))
        assertEquals(AppCategory.FINANCE, AppCategory.guess(-1, "com.revolut.revolut", "Revolut", false))
        assertEquals(AppCategory.TRAVEL, AppCategory.guess(-1, "com.google.android.apps.maps", "Maps", false))
        assertEquals(AppCategory.TOOLS, AppCategory.guess(-1, "com.android.settings", "Settings", true))
        assertEquals(AppCategory.TOOLS, AppCategory.guess(-1, "com.android.something", "Mystery", true))
        assertEquals(AppCategory.OTHER, AppCategory.guess(-1, "org.example.app", "Mystery", false))
        // A declared category wins over the words.
        assertEquals(AppCategory.PRODUCTIVITY, AppCategory.guess(7, "com.instagram.notes", "Notes", false))
    }

    @Test
    fun anAppsHomeIconIsAGameItemOrItsOwnLogo() {
        val prefs = LauncherPrefs().withIcon("a", "poke-ball").withIcon("b", "not-an-item")
        assertEquals(mapOf("a" to "poke-ball"), prefs.icons)
        assertEquals(prefs, LauncherPrefs.decode(prefs.encode()))
        assertEquals(emptyMap(), prefs.withIcon("a", null).icons)
    }

    @Test
    fun launcherSettingsAreRememberedAndOnlyNonDefaultsAreWritten() {
        assertEquals("", LauncherPrefs().encode())
        val prefs = LauncherPrefs().withWalking(false).nextTownSize().withShowStatus(false).withEdgeDrag(false).pin("a").withIcon("a", "poke-ball")
        assertEquals(8, prefs.townSize)
        assertEquals(prefs, LauncherPrefs.decode(prefs.encode()))
        assertEquals(16, prefs.nextTownSize().townSize)
        assertEquals(24, prefs.nextTownSize().nextTownSize().townSize)
        assertEquals(LauncherPrefs().withWalking(false).nextTownSize().withShowStatus(false).withEdgeDrag(false), prefs.withoutPins())
        assertEquals(24, LauncherPrefs.decode("K townSize\t9\n").townSize)
    }
}
