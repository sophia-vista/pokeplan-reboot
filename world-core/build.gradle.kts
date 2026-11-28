import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":kit-core"))
            api(project(":pokemon-core"))
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.register<JavaExec>("simulateWorldEconomy") {
    group = "tools"
    val main = kotlin.targets.getByName("jvm").compilations.getByName("main")
    dependsOn(main.compileTaskProvider)
    classpath = files(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("app.pokeplan.world.tools.WorldEconomySimulatorKt")
    args(rootProject.file("docs/world-economy-report.md").absolutePath)
}

tasks.register<JavaExec>("storyScript") {
    group = "tools"
    val main = kotlin.targets.getByName("jvm").compilations.getByName("main")
    dependsOn(main.compileTaskProvider)
    classpath = files(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("app.pokeplan.world.tools.StoryScriptExportKt")
    args(rootProject.file("docs/world-story-script.md").absolutePath)
}
