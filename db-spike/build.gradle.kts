// REP-006: a throwaway spike, not part of the app. It checks that SQLDelight and SQLite work with this project's
// Kotlin and AGP versions and that the Android driver compiles; the findings are in docs/pokelife/evidence/REP-006-sqldelight-spike.md.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidLibrary {
        namespace = "app.pokeplan.spike"
        compileSdk = 37
        minSdk = 26
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.sqldelight.runtime)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

sqldelight {
    databases {
        create("SpikeDb") {
            packageName.set("app.pokeplan.spike")
        }
    }
}
