plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidLibrary {
        namespace = "app.pokeplan.data"
        compileSdk = 37
        minSdk = 26
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.sqldelight.runtime)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain { kotlin.srcDir("src/javaMain/kotlin") }
        jvmMain { kotlin.srcDir("src/javaMain/kotlin") }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":app-core"))
        }
        jvmTest { resources.srcDir(rootProject.file("app-core/src/jvmTest/resources")) }
    }
}

sqldelight {
    databases {
        create("DataDb") {
            packageName.set("app.pokeplan.data.db")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}

tasks.matching { it.name == "jvmTest" }.configureEach { dependsOn("verifyCommonMainDataDbMigration") }
