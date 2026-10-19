plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "app.pokeplan"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.pokeplan"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
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
    implementation(libs.androidx.activity.compose)
}
