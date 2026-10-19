import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// The sprites live in assets/sprites for the whole repo; they are copied into the shape Compose resources expect
// (files/sprites/<formId>.png) so every target can load them with Res.readBytes.
val spriteResources = tasks.register<Sync>("prepareSpriteResources") {
    from(rootProject.file("assets/sprites")) { include("*.png") }
    into(layout.buildDirectory.dir("generated/spriteResources/files/sprites"))
}

compose.resources {
    customDirectory("commonMain", spriteResources.map { layout.buildDirectory.dir("generated/spriteResources").get() })
}

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
            api(project(":pokemon-core"))
            api(project(":plan-core"))
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
