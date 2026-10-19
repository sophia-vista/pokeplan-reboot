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
    from(rootProject.file("assets/fonts")) { include("*.ttf"); into("font") }
}

compose.resources {
    customDirectory("commonMain", composeAssets.map { layout.buildDirectory.dir("generated/composeAssets").get() })
}

tasks.matching { it.name.startsWith("generateResourceAccessors") || it.name.startsWith("prepareComposeResources") || it.name.startsWith("convertXmlValueResources") }
    .configureEach { dependsOn(composeAssets) }

kotlin {
    androidLibrary {
        namespace = "app.pokeplan.ui"
        compileSdk = 37
        minSdk = 26
    }
    jvm()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":app-core"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.pokeplan.ui.MainKt"
    }
}
