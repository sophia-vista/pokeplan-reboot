import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// The ground maps story scenes are set on: the places of Red Rescue Team and Explorers of Sky, a few hundred KB each at most (not all 685).
val storyRescueMaps = arrayOf(
    "POKEMON_SQUARE", "SQUARE", "MAKUHITA_DOJO", "PELIPPER_POST_OFFICE", "HILL_OF_THE_ANCIENTS", "LUMINOUS_CAVE", "COMET", "NIGHT_SKY_1", "NIGHT_SKY_2",
    "SUMMIT_SUNSET", "SKY_TOWER", "SKY_TOWER_ENTRY", "NIGHTMARE", "FUGITIVES_SNOW_FOREST", "FUGITIVES_SNOW_ROAD", "FUGITIVES_BLIZZARD_ROAD",
    "SINISTER_WOODS_ENTRY", "GREAT_CANYON_ENTRY", "MT_THUNDER_ENTRY", "LAPIS_CAVE_ENTRY", "FROSTY_FOREST_ENTRY", "MT_STEEL_ENTRY", "FUGITIVES_LAPIS_CAVE",
).map { "$it.png" }.toTypedArray()

val storySkyMaps = arrayOf(
    "T00P02", "T00P03", "G01P01A", "G01P01B", "G01P01C", "G01P02A", "G01P03A", "G01P06A", "G01P07A", "H01P99A", "V09P04A", "V25P01A", "V26P02A",
    "P05P01A", "D51P41A", "P02P01A", "V31P01A", "V38P06C", "D73P11A",
).map { "$it.png" }.toTypedArray()

// The sprites (assets/sprites) and the pixel font (assets/fonts) live outside the module; they are copied into the shape
// Compose resources expect (files/sprites/<formId>.png, font/<name>.ttf) so every target can load them.
val composeAssets = tasks.register<Sync>("prepareComposeAssets") {
    into(layout.buildDirectory.dir("generated/composeAssets"))
    from(rootProject.file("assets/sprites")) { include("*.png"); into("files/sprites") }
    from(rootProject.file("assets/backgrounds")) { include("*.png"); into("files/backgrounds") }
    from(rootProject.file("assets/npc")) { include("*.png"); into("files/npc") }
    from(rootProject.file("assets/types")) { include("*.png"); into("files/types") }
    from(rootProject.file("assets/items")) { include("*.png"); into("files/items") }
    from(rootProject.file("assets/pixelarticons")) { include("*.png"); into("files/pixelarticons") }
    // The Black/White animations are read only for a form SpriteCollab has no sprite for (the Mystery Dungeon sheet wins), so only those are bundled.
    val pmdForms = rootProject.file("assets/pmd/pmd.tsv").readLines().filter { it.isNotBlank() }.map { it.substringBefore('\t') }.toSet()
    from(rootProject.file("assets/animated")) {
        include("*.png", "*.tsv")
        exclude { it.name.endsWith(".png") && it.name.removeSuffix(".png").removeSuffix("-shiny") in pmdForms }
        into("files/animated")
    }
    from(rootProject.file("assets/pmd")) { include("**/*.png", "**/*.tsv"); into("files/pmd") }
    from(rootProject.file("assets/scenes")) { include("*.png"); into("files/scenes") }
    from(rootProject.file("assets/world")) { include("*.png", "fx/*.png", "fx/*.tsv"); into("files/world") }
    from(rootProject.file("assets/pmd-items")) { include("*.png"); into("files/pmditems") }
    from(rootProject.file("assets/tiles")) { include("*.png"); into("files/tiles") }
    from(rootProject.file("assets/rescue-maps")) { include("FRIEND_AREA_*.png"); into("files/rescue-maps") }
    // The Rescue Team pictures World uses (the team bases outside, in three stages for each of the sixteen partners, and the places in the Square), lower-cased with hyphens.
    from(rootProject.file("assets/rescue-maps")) {
        include("TEAM_BASE_*_BASIC.png", "TEAM_BASE_*_CONSTRUCTION.png", "TEAM_BASE_*_FINAL.png", "POKEMON_SQUARE.png", "SQUARE.png", "MAKUHITA_DOJO.png", "PELIPPER_POST_OFFICE.png")
        exclude("TEAM_BASE_INSIDE_*")
        rename { it.lowercase().replace('_', '-') }
        into("files/world")
    }
    // Ground maps of the two Mystery Dungeon games that story scenes are set on (background "map:<name>", see world-core `StoryPlaces`): the Red Rescue Team ones lower-cased with hyphens.
    from(rootProject.file("assets/rescue-maps")) {
        include(*storyRescueMaps)
        rename { it.lowercase().replace('_', '-') }
        into("files/maps")
    }
    from(rootProject.file("assets/sky-maps")) {
        include(*storySkyMaps)
        rename { "sky-" + it.lowercase() }
        into("files/maps")
    }
    from(rootProject.file("assets/cries")) { include("*.ogg"); into("files/cries") }
    // Sounds the app has no use for yet stay in assets/sfx but are not bundled (reminder.ogg is the notification's res/raw copy).
    from(rootProject.file("assets/sfx")) { include("*.ogg"); exclude("pokemon-center.ogg", "drawer-open.ogg", "drawer-close.ogg", "reminder.ogg", "timer-done.ogg"); into("files/sfx") }
    from(rootProject.file("assets/sky-sfx")) { include("jingle-mission-success.ogg"); into("files/sfx") }
    from(rootProject.file("assets/status")) { include("*.png"); exclude("sheet.png", "sheet2.png", "skull.png"); into("files/status") }
    from(rootProject.file("assets/weather")) { include("*.png", "ramps.tsv"); into("files/weather") }
    from(rootProject.file("assets/fonts")) { include("*.ttf"); into("font") }
    from(rootProject.file("assets/fonts")) { include("pmd-dialogue.*"); into("files/fonts") }
}

compose.resources {
    customDirectory("commonMain", composeAssets.map { layout.buildDirectory.dir("generated/composeAssets").get() })
}

tasks.matching { it.name.startsWith("generateResourceAccessors") || it.name.startsWith("prepareComposeResources") || it.name.startsWith("convertXmlValueResources") }
    .configureEach { dependsOn(composeAssets) }

// The app's version, from the VERSION file at the repo root, as a Kotlin constant every target can show (Settings → About).
// POKEPLAN_BUILD is set by the release workflow to its run number; without it this is a development build.
val generateAppVersion = tasks.register("generateAppVersion") {
    val versionFile = rootProject.file("VERSION")
    val build = System.getenv("POKEPLAN_BUILD")?.toIntOrNull() ?: 0
    val out = layout.buildDirectory.dir("generated/appVersion")
    inputs.file(versionFile)
    inputs.property("build", build)
    outputs.dir(out)
    doLast {
        val file = out.get().file("app/pokeplan/ui/AppVersion.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package app.pokeplan.ui\n\n/** Generated from the VERSION file by Gradle. */\nobject AppVersion {\n" +
                "    const val NAME = \"${versionFile.readText().trim()}\"\n    const val BUILD = $build\n}\n",
        )
    }
}

kotlin {
    androidLibrary {
        namespace = "app.pokeplan.ui"
        compileSdk = 37
        minSdk = 26
        // Compose resources (sprites, fonts, icons) are packaged as Android assets.
        androidResources { enable = true }
    }
    jvm()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.configure { kotlin.srcDir(generateAppVersion) }
        commonMain.dependencies {
            api(project(":app-core"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation("org.jetbrains.compose.ui:ui-backhandler:${libs.versions.compose.get()}")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            // Only to check the QR generator by reading its codes back with an independent decoder.
            implementation("com.google.zxing:core:3.5.3")
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.ktor.client.java)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.pokeplan.ui.MainKt"
    }
}

// ScreenshotTest renders every screen (about 12 of the full gate's 15 minutes), so `jvmTest` skips it unless asked: name the screens a
// change touches (`--tests "*ScreenshotTest.collection*"`, which runs them as before) or pass `-Pscreenshots` to render them all (CI does).
val allScreenshots = providers.gradleProperty("screenshots").isPresent
val namedTests = gradle.startParameter.taskRequests.flatMap { it.args }.any { it == "--tests" || it.startsWith("--tests=") }
tasks.withType<Test>().configureEach {
    if (name == "jvmTest" && !allScreenshots && !namedTests) filter.excludeTestsMatching("app.pokeplan.ui.ScreenshotTest")
}
