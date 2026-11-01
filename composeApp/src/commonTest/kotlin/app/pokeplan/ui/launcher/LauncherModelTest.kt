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

class LauncherNiagaraTest {
    private fun app(name: String) = LauncherApp("pkg.$name", name, AppCategory.OTHER)

    @Test
    fun suggestsTheMostOpenedUnpinnedApps() {
        val apps = listOf(app("a"), app("b"), app("c"), app("d"))
        var prefs = LauncherPrefs()
        repeat(5) { prefs = prefs.recordLaunch("pkg.b") }
        repeat(3) { prefs = prefs.recordLaunch("pkg.a") }
        repeat(2) { prefs = prefs.recordLaunch("pkg.c") }
        assertEquals(listOf("b", "a"), prefs.suggested(apps).map { it.label })
        assertEquals(listOf("a"), prefs.pin("pkg.b").suggested(apps).map { it.label })
        assertEquals(listOf("b"), prefs.hide("pkg.a").suggested(apps).map { it.label })
    }

    @Test
    fun oldCountsFadeWhenOneGrowsTooBig() {
        var prefs = LauncherPrefs().copy(launches = mapOf("pkg.a" to 60, "pkg.b" to 1))
        prefs = prefs.recordLaunch("pkg.a")
        assertEquals(30, prefs.launches["pkg.a"])
        assertEquals(null, prefs.launches["pkg.b"])
    }

    @Test
    fun foldersKeepOrderRenameAndDelete() {
        var prefs = LauncherPrefs().addFolder(" Work ").addFolder("work").addFolder("").addFolder("Play")
        assertEquals(listOf("Work", "Play"), prefs.folders.map { it.name })
        prefs = prefs.addToFolder("Work", "pkg.a").addToFolder("Work", "pkg.b").addToFolder("Work", "pkg.a")
        assertEquals(listOf("pkg.a", "pkg.b"), prefs.folders.first().apps)
        prefs = prefs.removeFromFolder("Work", "pkg.a").renameFolder("Work", "Office").renameFolder("Office", "Play")
        assertEquals(listOf("Office", "Play"), prefs.folders.map { it.name })
        assertEquals(listOf("pkg.b"), prefs.folders.first().apps)
        assertEquals(listOf("Play"), prefs.deleteFolder("Office").folders.map { it.name })
    }

    @Test
    fun newChoicesSurviveTheRoundTrip() {
        val prefs = LauncherPrefs().addFolder("Work").addToFolder("Work", "pkg.a").recordLaunch("pkg.a").withPlace(GeoPlace("Manila, Philippines", 14.6, 121.0)).withFahrenheit(true).copy(weather = Weather.LIVE)
        assertEquals(prefs, LauncherPrefs.decode(prefs.encode()))
        assertEquals(LauncherPrefs(), LauncherPrefs.decode("W broken\nW x\t1\nL pkg\tzero\nF \n"))
    }

    @Test
    fun forecastsRoundTripAndMapToTheTown() {
        val forecast = Forecast("Manila", 28.4, 61, listOf(DayForecast("2026-10-31", 61, 30.0, 25.5)), 123L)
        assertEquals(forecast, Forecast.decode(forecast.encode()))
        assertEquals(null, Forecast.decode("nonsense"))
        assertEquals(Weather.RAIN, WeatherCodes.town(63))
        assertEquals(Weather.SNOW, WeatherCodes.town(73))
        assertEquals(Weather.CLOUDS, WeatherCodes.town(3))
        assertEquals("28 C", degrees(28.4, false))
        assertEquals("83 F", degrees(28.4, true))
    }
}

class LauncherWidgetsTest {
    @Test
    fun startsWithTheDefaultsAndFollowsTheOldSwitches() {
        val prefs = LauncherPrefs()
        assertEquals(listOf(WidgetKind.CLOCK, WidgetKind.BUDDY), prefs.widgetsIn(WidgetArea.HOME_TOP).map { it.kind })
        assertEquals(listOf(WidgetKind.CALENDAR, WidgetKind.AGENDA), prefs.widgetsIn(WidgetArea.TODAY).map { it.kind })
        assertEquals(listOf(WidgetKind.BUDDY), prefs.withShowClock(false).widgetsIn(WidgetArea.HOME_TOP).map { it.kind })
        assertEquals("", prefs.encode())
    }

    @Test
    fun addsMovesAndRemovesWidgets() {
        var prefs = LauncherPrefs().addFolder("Work")
        val folder = WidgetSlot(WidgetArea.HOME_BOTTOM, WidgetKind.FOLDER, "Work")
        prefs = prefs.addWidget(folder).addWidget(folder).addWidget(WidgetSlot(WidgetArea.HOME_BOTTOM, WidgetKind.FOLDER, "Nope"))
        assertEquals(WidgetKind.FOLDER, prefs.widgetsIn(WidgetArea.HOME_BOTTOM).last().kind)
        assertEquals(1, prefs.widgets.count { it.kind == WidgetKind.FOLDER })
        prefs = prefs.moveWidget(folder, -1)
        assertEquals(listOf(WidgetKind.NAV, WidgetKind.OFTEN, WidgetKind.PINNED, WidgetKind.FOLDER, WidgetKind.ACTIONS), prefs.widgetsIn(WidgetArea.HOME_BOTTOM).map { it.kind })
        assertEquals(prefs, prefs.moveWidget(WidgetSlot(WidgetArea.HOME_TOP, WidgetKind.CLOCK), -1))
        prefs = prefs.renameFolder("Work", "Office")
        assertEquals("Office", prefs.widgets.first { it.kind == WidgetKind.FOLDER }.arg)
        assertEquals(0, prefs.deleteFolder("Office").widgets.count { it.kind == WidgetKind.FOLDER })
        assertEquals(0, prefs.widgetsIn(WidgetArea.HOME_TOP).size - 2)
    }

    @Test
    fun layoutsSurviveTheRoundTripEvenWhenEmpty() {
        val custom = LauncherPrefs().addFolder("Work").addWidget(WidgetSlot(WidgetArea.TODAY, WidgetKind.FOLDER, "Work")).removeWidget(WidgetSlot(WidgetArea.HOME_TOP, WidgetKind.CLOCK))
        assertEquals(custom, LauncherPrefs.decode(custom.encode()))
        val empty = LauncherPrefs().copy(layout = emptyList())
        assertEquals(empty, LauncherPrefs.decode(empty.encode()))
        assertEquals(LauncherPrefs(), LauncherPrefs.decode("G NOWHERE\tCLOCK\t\nG TODAY\tNOPE\t\n").copy(layout = null))
    }
}

class LauncherHostedWidgetTest {
    @Test
    fun androidWidgetsKeepTheirIdsAndOrder() {
        val first = WidgetSlot(WidgetArea.TODAY, WidgetKind.ANDROID, "12")
        val second = WidgetSlot(WidgetArea.TODAY, WidgetKind.ANDROID, "15")
        val prefs = LauncherPrefs().addWidget(first).addWidget(second).addWidget(first).moveWidget(second, -1)
        assertEquals(listOf("15", "12"), prefs.widgetsIn(WidgetArea.TODAY).filter { it.kind == WidgetKind.ANDROID }.map { it.arg })
        assertEquals(prefs, LauncherPrefs.decode(prefs.encode()))
        assertEquals(listOf("15"), prefs.removeWidget(first).widgetsIn(WidgetArea.TODAY).filter { it.kind == WidgetKind.ANDROID }.map { it.arg })
    }
}

class LauncherEditWidgetsTest {
    @Test
    fun widgetsResizeAndKeepTheirSizeThroughTheRoundTrip() {
        val pinned = WidgetSlot(WidgetArea.HOME_BOTTOM, WidgetKind.PINNED)
        val prefs = LauncherPrefs().resizeWidget(pinned, WidgetSize.SMALL)
        assertEquals(WidgetSize.SMALL, prefs.widgets.first { it.kind == WidgetKind.PINNED }.size)
        assertEquals(prefs, LauncherPrefs.decode(prefs.encode()))
        assertEquals(WidgetSize.LARGE, WidgetSize.SMALL.next().next())
        assertEquals(prefs, prefs.addWidget(pinned))
        assertEquals(0, prefs.removeWidget(pinned).widgets.count { it.kind == WidgetKind.PINNED })
    }

    @Test
    fun dragPastTheEndMovesAWidgetToTheOtherPartOfTheHomePage() {
        var prefs = LauncherPrefs()
        val status = WidgetSlot(WidgetArea.HOME_TOP, WidgetKind.BUDDY)
        prefs = prefs.moveWidget(status, 1)
        assertEquals(WidgetArea.HOME_BOTTOM, prefs.widgets.first { it.kind == WidgetKind.BUDDY }.area)
        assertEquals(WidgetKind.BUDDY, prefs.widgetsIn(WidgetArea.HOME_BOTTOM).first().kind)
        prefs = prefs.moveWidget(prefs.widgets.first { it.kind == WidgetKind.BUDDY }, -1)
        assertEquals(listOf(WidgetKind.CLOCK, WidgetKind.BUDDY), prefs.widgetsIn(WidgetArea.HOME_TOP).map { it.kind })
        val calendar = WidgetSlot(WidgetArea.TODAY, WidgetKind.CALENDAR)
        assertEquals(prefs, prefs.moveWidget(calendar, -1))
    }
}

class LauncherDateTextTest {
    @Test
    fun writesTheDateInEachStyle() {
        val day = kotlinx.datetime.LocalDate(2026, 10, 31)
        assertEquals("Sat 31 Oct", formatDate(day, DateStyle.SHORT))
        assertEquals("Saturday 31 October", formatDate(day, DateStyle.LONG))
        assertEquals("31 Oct 2026", formatDate(day, DateStyle.DAY_MONTH_YEAR))
        assertEquals("Oct 31", formatDate(day, DateStyle.MONTH_DAY))
        assertEquals("2026-10-31", formatDate(day, DateStyle.ISO))
    }

    @Test
    fun writesTheTimeOnEitherClock() {
        assertEquals("13:05", formatTime(13, 5))
        assertEquals("13:05:09", formatTime(13, 5, 9))
        assertEquals("1:05 PM", formatTime(13, 5, null, true))
        assertEquals("12:00 AM", formatTime(0, 0, null, true))
        assertEquals("12:30:00 PM", formatTime(12, 30, 0, true))
    }

    @Test
    fun theNewChoicesSurviveTheRoundTripAndWeeksStartWhereAsked() {
        val prefs = LauncherPrefs().nextDateStyle().withHour12(true).withShowSeconds(true).withSundayFirst(false)
        assertEquals(prefs, LauncherPrefs.decode(prefs.encode()))
        assertEquals(kotlinx.datetime.DayOfWeek.SUNDAY, weekDays(true).first())
        assertEquals(kotlinx.datetime.DayOfWeek.MONDAY, weekDays(false).first())
        assertEquals(7, weekDays(true).distinct().size)
    }
}

class LauncherBatterySaverTest {
    @Test
    fun automaticSavesOnALowUnchargedBatteryOrThePhonesOwnSaving() {
        val low = BatteryStatus(20, charging = false)
        assertEquals(true, batterySaving(BatterySaver.AUTO, low, false))
        assertEquals(false, batterySaving(BatterySaver.AUTO, low.copy(charging = true), false))
        assertEquals(false, batterySaving(BatterySaver.AUTO, BatteryStatus(21, false), false))
        assertEquals(true, batterySaving(BatterySaver.AUTO, BatteryStatus(90, false), true))
        assertEquals(false, batterySaving(BatterySaver.AUTO, null, false))
        assertEquals(true, batterySaving(BatterySaver.ON, BatteryStatus(100, true), false))
        assertEquals(false, batterySaving(BatterySaver.OFF, low, true))
        val prefs = LauncherPrefs().nextBatterySaver()
        assertEquals(BatterySaver.ON, prefs.batterySaver)
        assertEquals(prefs, LauncherPrefs.decode(prefs.encode()))
    }
}

class CalculatorTest {
    private fun Calculator.type(keys: String) = keys.forEach { k ->
        when (k) {
            in '0'..'9' -> digit(k)
            '.' -> point()
            '=' -> equals()
            'C' -> clear()
            '<' -> delete()
            '~' -> negate()
            else -> operate(k)
        }
    }

    @Test
    fun worksLikeADeskCalculator() {
        assertEquals("20", Calculator().apply { type("2+3x4=") }.display)
        assertEquals("0.3", Calculator().apply { type("0.1+0.2=") }.display)
        assertEquals("7", Calculator().apply { type("12-5=") }.display)
        assertEquals("2.5", Calculator().apply { type("5/2=") }.display)
        assertEquals("10", Calculator().apply { type("5+10") }.display)
        assertEquals("15", Calculator().apply { type("5+10=") }.display)
    }

    @Test
    fun editsTheNumberBeingTyped() {
        assertEquals("12", Calculator().apply { type("123<") }.display)
        assertEquals("0", Calculator().apply { type("5<") }.display)
        assertEquals("-8", Calculator().apply { type("8~") }.display)
        assertEquals("0.5", Calculator().apply { type(".5") }.display)
        assertEquals("1.5", Calculator().apply { type("1.5.") }.display)
    }

    @Test
    fun aDivideByZeroShowsErrorUntilTheNextKey() {
        val calc = Calculator().apply { type("5/0=") }
        assertEquals("Error", calc.display)
        calc.type("7")
        assertEquals("7", calc.display)
        assertEquals("0", Calculator().apply { type("9C") }.display)
        assertEquals("Error", Calculator.format(Double.POSITIVE_INFINITY))
    }
}

class LauncherGestureTest {
    @Test fun gesturesHaveUsualJobsAndOldSwitchesStillCount() {
        val p = LauncherPrefs()
        assertEquals(GestureAction.APPS, p.action(GestureTrigger.EDGE_DRAG))
        assertEquals(GestureAction.GATHER, p.action(GestureTrigger.TRIPLE_TAP))
        assertEquals(GestureAction.NONE, p.action(GestureTrigger.DOUBLE_TAP))
        // A save from before the choices: the old off switches mean "nothing".
        val old = LauncherPrefs.decode("K edgeDrag\t0\nK gatherTap\t0\n")
        assertEquals(GestureAction.NONE, old.action(GestureTrigger.EDGE_DRAG))
        assertEquals(GestureAction.NONE, old.action(GestureTrigger.TRIPLE_TAP))
    }

    @Test fun aGestureCanBeGivenAnotherJobAndItIsSaved() {
        val p = LauncherPrefs().withGesture(GestureTrigger.DOUBLE_TAP, GestureAction.GEAR).withGesture(GestureTrigger.TRIPLE_TAP, GestureAction.NONE)
        assertEquals(GestureAction.GEAR, p.action(GestureTrigger.DOUBLE_TAP))
        val back = LauncherPrefs.decode(p.encode())
        assertEquals(GestureAction.GEAR, back.action(GestureTrigger.DOUBLE_TAP))
        assertEquals(GestureAction.NONE, back.action(GestureTrigger.TRIPLE_TAP))
        // A job it does not know is ignored, not a crash.
        assertEquals(GestureAction.NONE, LauncherPrefs.decode("K gesture.DOUBLE_TAP\tFLY\nK gesture.SHAKE\tAPPS\n").action(GestureTrigger.DOUBLE_TAP))
    }
}
