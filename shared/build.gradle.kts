import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

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
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Writes docs/home-region-candidates.md, the table used to curate the Home Region.
tasks.register<JavaExec>("curationReport") {
    group = "tools"
    val main = kotlin.targets.getByName("jvm").compilations.getByName("main")
    dependsOn(main.compileTaskProvider)
    classpath = files(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("app.pokeplan.shared.tools.CurationReportKt")
    args(rootProject.file("docs/home-region-candidates.md").absolutePath)
}
