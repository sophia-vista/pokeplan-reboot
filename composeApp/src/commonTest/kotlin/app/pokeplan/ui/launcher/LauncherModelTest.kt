package app.pokeplan.ui.launcher

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.pokemon.Hatching
import app.pokeplan.core.dex.HatchOption
import app.pokeplan.core.pokemon.OwnedPokemon
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun aNewFloorLayoutCountsForItsDayOnlyAndIsRemembered() {
        val prefs = LauncherPrefs().withNewLayout(20_000).withNewLayout(20_000)
        assertEquals(2, prefs.layoutRollOn(20_000))
        assertEquals(0, prefs.layoutRollOn(20_001), "a new day starts on the day's own floor")
        assertEquals(1, prefs.withNewLayout(20_001).layoutRollOn(20_001))
        assertEquals(prefs, LauncherPrefs.decode(prefs.encode()))
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
            assertEquals(TownCell.WALL, map.at(6, 0))
            val walkTop = (26 * TownLayout.WALK_TOP).toInt()
            val walkBottom = (26 * TownLayout.WALK_BOTTOM).toInt()
            for (y in walkTop..walkBottom) for (x in 0 until 13) assertEquals(TownCell.GROUND, map.at(x, y), "seed $seed at $x,$y")
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
        assertEquals(7, LauncherPrefs.decode(LauncherPrefs().withActivity(7).encode()).activity)
        assertEquals(10, LauncherPrefs.decode("K activity\t42\n").activity)
        assertEquals("", LauncherPrefs().withActivity(TownBehavior.ACTIVITY_DEFAULT).encode())
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
    fun oldFoldersAreIgnoredWhenLoading() {
        val old = "P pkg.a\nF Work\tpkg.a\tpkg.b\nK gesture.SWIPE_UP\tFOLDERS\nG HOME_BOTTOM\tFOLDER\tWork\tLARGE\nG HOME_BOTTOM\tNAV\t\tLARGE\nX HOME\tFOLDER\tWork\t0\t0\t4\t2\nH pkg.z\n"
        val prefs = LauncherPrefs.decode(old)
        assertEquals(listOf("pkg.a"), prefs.pinned)
        assertEquals(setOf("pkg.z"), prefs.hidden)
        assertEquals(listOf(WidgetKind.NAV), prefs.widgets.map { it.kind })
        assertEquals(null, prefs.grid)
        assertEquals(GestureAction.APPS, prefs.action(GestureTrigger.SWIPE_UP))
        assertEquals(false, prefs.encode().contains("FOLDER"))
        assertEquals(false, prefs.encode().contains("F Work"))
    }

    @Test
    fun newChoicesSurviveTheRoundTrip() {
        val prefs = LauncherPrefs().recordLaunch("pkg.a").withPlace(GeoPlace("Manila, Philippines", 14.6, 121.0)).withFahrenheit(true).copy(weather = Weather.LIVE)
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
        var prefs = LauncherPrefs()
        val forecast = WidgetSlot(WidgetArea.HOME_BOTTOM, WidgetKind.FORECAST)
        prefs = prefs.addWidget(forecast).addWidget(forecast)
        assertEquals(WidgetKind.FORECAST, prefs.widgetsIn(WidgetArea.HOME_BOTTOM).last().kind)
        assertEquals(1, prefs.widgets.count { it.kind == WidgetKind.FORECAST })
        prefs = prefs.moveWidget(forecast, -1)
        assertEquals(listOf(WidgetKind.NAV, WidgetKind.OFTEN, WidgetKind.PINNED, WidgetKind.FORECAST, WidgetKind.ACTIONS), prefs.widgetsIn(WidgetArea.HOME_BOTTOM).map { it.kind })
        assertEquals(prefs, prefs.moveWidget(WidgetSlot(WidgetArea.HOME_TOP, WidgetKind.CLOCK), -1))
        assertEquals(0, prefs.removeWidget(forecast).widgets.count { it.kind == WidgetKind.FORECAST })
        assertEquals(0, prefs.widgetsIn(WidgetArea.HOME_TOP).size - 2)
    }

    @Test
    fun layoutsSurviveTheRoundTripEvenWhenEmpty() {
        val custom = LauncherPrefs().addWidget(WidgetSlot(WidgetArea.TODAY, WidgetKind.FORECAST)).removeWidget(WidgetSlot(WidgetArea.HOME_TOP, WidgetKind.CLOCK))
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
        assertEquals("10/31/2026", formatDate(day, DateStyle.MONTH_DAY_YEAR))
        assertEquals("31/10/2026", formatDate(day, DateStyle.DAY_MONTH_YEAR_SLASH))
        assertEquals(listOf("EEE d MMM", "EEEE d MMMM", "d MMM yyyy", "MMM d", "yyyy-MM-dd", "MM/dd/yyyy", "dd/MM/yyyy"), DateStyle.entries.map { it.pattern })
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
        val prefs = LauncherPrefs().withShowSeconds(true)
        assertEquals(prefs, LauncherPrefs.decode(prefs.encode()))
        assertEquals(kotlinx.datetime.DayOfWeek.SUNDAY, weekDays(kotlinx.datetime.DayOfWeek.SUNDAY).first())
        assertEquals(kotlinx.datetime.DayOfWeek.MONDAY, weekDays(kotlinx.datetime.DayOfWeek.MONDAY).first())
        assertEquals(kotlinx.datetime.DayOfWeek.SATURDAY, weekDays(kotlinx.datetime.DayOfWeek.SATURDAY).first())
        assertEquals(7, weekDays(kotlinx.datetime.DayOfWeek.SATURDAY).distinct().size)
        assertEquals(
            listOf(
                kotlinx.datetime.DayOfWeek.SATURDAY, kotlinx.datetime.DayOfWeek.SUNDAY, kotlinx.datetime.DayOfWeek.MONDAY,
                kotlinx.datetime.DayOfWeek.TUESDAY, kotlinx.datetime.DayOfWeek.WEDNESDAY, kotlinx.datetime.DayOfWeek.THURSDAY,
                kotlinx.datetime.DayOfWeek.FRIDAY,
            ),
            weekDays(kotlinx.datetime.DayOfWeek.SATURDAY),
        )
    }

    @Test
    fun resetKeepsTheUsersAppsAndPlaceButNothingElse() {
        val place = GeoPlace("Manila", 14.6, 121.0)
        val messy = LauncherPrefs().pin("a").withIcon("a", "poke-ball").hide("b").withWalking(false).nextTownSize().withGesture(GestureTrigger.LONG_PRESS, GestureAction.WIDGETS).copy(place = place, fahrenheit = true)
        val reset = messy.reset()
        assertEquals(LauncherPrefs().copy(pinned = listOf("a"), icons = mapOf("a" to "poke-ball"), hidden = setOf("b"), place = place), reset)
        assertEquals(GestureAction.NONE, reset.action(GestureTrigger.LONG_PRESS))
    }

    @Test
    fun theNewGesturesDefaultToNothingAndCanBeGivenAnyJob() {
        val p = LauncherPrefs()
        for (t in listOf(GestureTrigger.TWO_FINGER_UP, GestureTrigger.TWO_FINGER_DOWN, GestureTrigger.LONG_PRESS)) assertEquals(GestureAction.NONE, p.action(t))
        val set = p.withGesture(GestureTrigger.TWO_FINGER_UP, GestureAction.NOTIFICATIONS).withGesture(GestureTrigger.LONG_PRESS, GestureAction.EDIT)
        assertEquals(set, LauncherPrefs.decode(set.encode()))
        assertEquals(GestureAction.NOTIFICATIONS, set.action(GestureTrigger.TWO_FINGER_UP))
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

class LauncherGestureTest {
    @Test fun gesturesHaveUsualJobsAndOldSwitchesStillCount() {
        val p = LauncherPrefs()
        assertEquals(GestureAction.APPS, p.action(GestureTrigger.EDGE_DRAG))
        assertEquals(GestureAction.GATHER, p.action(GestureTrigger.TRIPLE_TAP))
        assertEquals(GestureAction.NONE, p.action(GestureTrigger.DOUBLE_TAP))
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
        assertEquals(GestureAction.NONE, LauncherPrefs.decode("K gesture.DOUBLE_TAP\tFLY\nK gesture.SHAKE\tAPPS\n").action(GestureTrigger.DOUBLE_TAP))
    }

    @Test
    fun townPicksTheBuddyThenFavoritesAndPutsPokemonWithoutSpritesLast() {
        fun mon(id: String, species: Int, level: Int) =
            Hatching.create(id, HatchOption(species, Dex.defaultForm(species).identifier), OwnedPokemon.PARTY_SIZE, 0L, 60, Random(species)).copy(level = level)
        val buddy = mon("buddy", 1, 1)
        val fav = mon("fav", 4, 2)
        val strong = mon("strong", 7, 50)
        val weak = mon("weak", 25, 3)
        val spriteless = mon("none", 133, 99)
        val order = townResidents(
            listOf(spriteless, weak, strong, fav, buddy), "buddy", favorites = setOf("fav"), hasSprite = { it.id != "none" },
        ).map { it.id }
        assertEquals(listOf("buddy", "fav", "strong", "weak", "none"), order)
        assertEquals(listOf("buddy", "fav"), townResidents(listOf(spriteless, weak, fav, buddy), "buddy", cap = 2, favorites = setOf("fav"), hasSprite = { it.id != "none" }).map { it.id })
    }

    private fun townMon(id: String, species: Int, level: Int) =
        Hatching.create(id, HatchOption(species, Dex.defaultForm(species).identifier), OwnedPokemon.PARTY_SIZE, 0L, 60, Random(species)).copy(level = level)

    @Test
    fun theTownShowsAtMostOneGuardianAndTheBuddyWinsIfItIsOne() {
        val kyogre = townMon("kyogre", 382, 40)
        val groudon = townMon("groudon", 383, 60)
        val rayquaza = townMon("rayquaza", 384, 50)
        val plain = townMon("plain", 25, 5)
        val all = listOf(plain, kyogre, groudon, rayquaza)
        val guardians = listOf("kyogre", "groudon", "rayquaza")
        assertEquals(listOf("groudon", "plain"), townResidents(all, null, guardianIds = guardians).map { it.id })
        assertEquals(listOf("kyogre", "plain"), townResidents(all, "kyogre", guardianIds = guardians).map { it.id })
        assertEquals(4, townResidents(all, null).size)
    }

    @Test
    fun theGuardiansTakeTurnsADayAtATimeUnlessTheBuddyIsOne() {
        val all = listOf(townMon("kyogre", 382, 40), townMon("groudon", 383, 60), townMon("rayquaza", 384, 50), townMon("plain", 25, 5))
        val guardians = listOf("kyogre", "groudon", "rayquaza")
        assertEquals(listOf("groudon", "rayquaza", "kyogre", "groudon"), (0..3).map { shownGuardian(all, guardians, null, it)?.id })
        assertEquals("kyogre", shownGuardian(all, guardians, "kyogre", 1)?.id)
        assertEquals(listOf("rayquaza", "plain"), townResidents(all, null, guardianIds = guardians, rotation = 1).map { it.id })
    }

    @Test
    fun aGuardianBringsItsOwnWeatherOnlyUnderGuardiansWeather() {
        assertEquals(Weather.RAIN, guardianWeather(townMon("k", 382, 40)))
        assertEquals(Weather.CLEAR, guardianWeather(townMon("p", 25, 5)), "no weather of its own: a clear sky")
        assertEquals(Weather.CLEAR, guardianWeather(null as OwnedPokemon?))
        assertEquals(Weather.GUARDIAN, LauncherPrefs.decode(LauncherPrefs().copy(weather = Weather.GUARDIAN).encode()).weather)
    }

    @Test
    fun chirpsAreOnByDefaultAndTheSwitchIsRemembered() {
        assertTrue(LauncherPrefs().chirps)
        assertFalse(LauncherPrefs.decode(LauncherPrefs().withChirps(false).encode()).chirps)
        assertTrue(LauncherPrefs.decode("").chirps)
    }
}
