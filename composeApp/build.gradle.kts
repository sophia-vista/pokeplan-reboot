import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// The sprites (assets/sprites) and the pixel font (assets/fonts) live outside the module; they are copied into the shape
// Compose resources expect (files/sprites/<formId>.png, font/<name>.ttf) so every target can load them.
val composeAssets = tasks.register<Sync>("prepareComposeAssets") {
    into(layout.buildDirectory.dir("generated/composeAssets"))
    from(rootProject.file("assets/sprites")) { include("*.png"); into("files/sprites") }
    from(rootProject.file("assets/backgrounds")) { include("*.png"); into("files/backgrounds") }
    from(rootProject.file("assets/npc")) { include("*.png"); into("files/npc") }
    from(rootProject.file("assets/types")) { include("*.png"); into("files/types") }
    from(rootProject.file("assets/items")) { include("*.png"); into("files/items") }
    from(rootProject.file("assets/animated")) { include("*.png", "*.tsv"); into("files/animated") }
    from(rootProject.file("assets/pmd")) { include("**/*.png", "**/*.tsv"); into("files/pmd") }
    from(rootProject.file("assets/scenes")) { include("*.png"); into("files/scenes") }
    from(rootProject.file("assets/world")) { include("*.png"); into("files/world") }
    from(rootProject.file("assets/tiles")) { include("*.png"); into("files/tiles") }
    from(rootProject.file("assets/cries")) { include("*.ogg"); into("files/cries") }
    from(rootProject.file("assets/sfx")) { include("*.ogg"); into("files/sfx") }
    from(rootProject.file("assets/status")) { include("*.png"); exclude("sheet.png", "sheet2.png"); into("files/status") }
    from(rootProject.file("assets/weather")) { include("*.png"); into("files/weather") }
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
