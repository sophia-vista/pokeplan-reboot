import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

// The Heal module's rules (docs/pokelife MODULES/HEAL.md): manual activity, hydration, sleep and body measurements, and optional goals. No UI and
// no game: it knows nothing about Pokémon, rewards or the other modules. The app meets it through HealStore (app-core), which pays Field
// Research through the minimal HealEvent only.
kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
