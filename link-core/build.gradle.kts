import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

// The Link module's rules (docs/pokelife MODULES/LINK.md): people, notes, important dates and follow-ups; no contact is ever
// contacted by it. No UI and no game: it knows nothing about Pokémon, rewards or the other modules. The app meets it through LinkStore
// (app-core), which pays the Postcard Trail through the minimal LinkEvent only.
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
