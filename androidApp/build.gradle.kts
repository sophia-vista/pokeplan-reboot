plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// The version lives in one file at the repo root (VERSION, "MAJOR.MINOR.PATCH"). CI sets POKEPLAN_BUILD to its run number,
// which becomes the Android versionCode so a newer build always installs over an older one.
val appVersion = rootProject.file("VERSION").readText().trim()
val appBuild = System.getenv("POKEPLAN_BUILD")?.toIntOrNull() ?: 0

android {
    namespace = "app.pokeplan"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.pokeplan"
        minSdk = 26
        targetSdk = 36
        versionCode = appBuild.coerceAtLeast(1)
        versionName = appVersion
    }
    // One fixed key for every build (local and CI), so the app's signature, and anything registered against it such as
    // Google sign-in, never changes. It is a private-use key kept in this private repo. TODO before any public release:
    // replace it with a secret key kept outside the repo (CI secrets) and re-register its SHA-1.
    signingConfigs {
        create("shared") {
            storeFile = file("pokeplan-debug.keystore")
            storePassword = "pokeplan"
            keyAlias = "pokeplan"
            keyPassword = "pokeplan"
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(project(":data"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.glance.appwidget)
    // The daily backup runs on its own schedule, whether or not the app is open (Glance already pulls WorkManager in; this names it).
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    // The QR scanner: Google's own scanning screen, which needs no camera permission from this app.
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    // HEAL's optional, read-only Health Connect import (androidx, no telemetry, no network of its own; reviewed under INTEGRATIONS_AND_PRIVACY.md 12.1).
    implementation("androidx.health.connect:connect-client:1.1.0")
}
