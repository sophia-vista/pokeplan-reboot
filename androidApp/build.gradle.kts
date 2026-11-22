plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

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
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
            it.systemProperty("robolectric.logging.enabled", "false")
            it.maxHeapSize = "2g"
        }
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(project(":data"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.glance.appwidget)
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("androidx.health.connect:connect-client:1.1.0")

    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation(libs.kotlinx.coroutines.test)
}
