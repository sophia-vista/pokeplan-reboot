import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// Neutral building blocks every rule module can share (dates, recurrence, search, import plans,
// reminder timing). It knows nothing about Pokémon, rewards, money or health: a module that needs a
// rule from here depends on this module, never on another life module.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
