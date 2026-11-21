import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

// The World module (docs/pokelife/MODULES/WORLD.md section 14): a Pokémon Mystery Dungeon style exploration game with idle and incremental
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

// Plays teams through the dungeon simulation and writes docs/world-economy-report.md (generated on demand, git-ignored).
tasks.register<JavaExec>("simulateWorldEconomy") {
    group = "tools"
    val main = kotlin.targets.getByName("jvm").compilations.getByName("main")
    dependsOn(main.compileTaskProvider)
    classpath = files(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("app.pokeplan.world.tools.WorldEconomySimulatorKt")
    args(rootProject.file("docs/world-economy-report.md").absolutePath)
}

// Writes every chapter's summary and the scenes' scripts as readable Markdown: docs/world-story-script.md (generated on demand, git-ignored).
tasks.register<JavaExec>("storyScript") {
    group = "tools"
    val main = kotlin.targets.getByName("jvm").compilations.getByName("main")
    dependsOn(main.compileTaskProvider)
    classpath = files(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("app.pokeplan.world.tools.StoryScriptExportKt")
    args(rootProject.file("docs/world-story-script.md").absolutePath)
}
