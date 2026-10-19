import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()

    // The same rules run on the server (Supabase Edge Functions) as compiled JavaScript.
    js(IR) {
        nodejs()
        useEsModules()
        binaries.library()
        generateTypeScriptDefinitions()
    }

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

// Plays months of simulated players through the reward engine and writes docs/economy-report.md.
tasks.register<JavaExec>("simulateEconomy") {
    group = "tools"
    val main = kotlin.targets.getByName("jvm").compilations.getByName("main")
    dependsOn(main.compileTaskProvider)
    classpath = files(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("app.pokeplan.plan.tools.EconomySimulatorKt")
    args(rootProject.file("docs/economy-report.md").absolutePath)
}
