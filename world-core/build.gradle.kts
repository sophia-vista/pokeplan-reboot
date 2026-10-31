import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

// The World module (docs/world.md): a Pokémon Mystery Dungeon style exploration game with idle and incremental
// mechanics. Dungeons, expedition teams, offline progress, Exploration Finds, rank and the story. Rules only, no UI; it
// knows nothing about Plan or Care and meets the shared collection through the Pokémon it is given and the finds it returns.
kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":pokemon-core"))
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
