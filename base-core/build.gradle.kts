import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

// The Home module's rules (docs/pokelife MODULES/HOME.md): chores and their schedules, supplies, assets and maintenance, warranties and
// reference notes. No UI and no game: it knows nothing about Pokémon, rewards or the other modules. The app meets it through BaseStore
// (app-core), which pays Habitat Discovery through the minimal BaseEvent only.
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
