import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

// The Mint module's rules (docs/pokelife MODULES/MINT.md): accounts, transactions, budgets, bills, reconciliation and review. No UI and
// no game: it knows nothing about Pokémon, rewards or the other modules. The app meets it through MintStore (app-core), which pays Ledger
// Research through the minimal MintEvent only.
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
