// The local database (docs/pokelife ARCHITECTURE/DATA_AND_MIGRATIONS.md): SQLDelight on SQLite, Android and JVM targets only.
// The Android driver is the product's; the JVM target is for tests and tools. The app does not use this module yet:
// the JSON save stays the live store until the migration gate (docs/pokelife/evidence/REP-007-gate-a-review.md).
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
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":app-core")) // to cross-check the importer against the typed legacy model
        }
        // The 0.123.0 save fixtures belong to app-core's tests; the importer's tests read the same files.
        jvmTest { resources.srcDir(rootProject.file("app-core/src/jvmTest/resources")) }
    }
}

sqldelight {
    databases {
        create("DataDb") {
            packageName.set("app.pokeplan.data.db")
            // Every released schema version is committed here (databases/N.db) so that verifyMigration compares each
            // migration with the schema it started from. Without this directory the check verifies nothing.
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}

// A schema edit without a numbered migration, or a migration that does not reach the current schema, fails the tests.
tasks.matching { it.name == "jvmTest" }.configureEach { dependsOn("verifyCommonMainDataDbMigration") }
