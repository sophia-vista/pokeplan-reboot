plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// The version lives in one file at the repo root (VERSION, "MAJOR.MINOR.PATCH"), and the Android versionCode is made
// from it alone: MAJOR * 1,000,000 + MINOR * 1,000 + PATCH. It is deliberately not the CI run number (POKEPLAN_BUILD) or
// any other hand-set number. A hand build once used 9,001 as its build number; every later release, numbered in the
// hundreds, was then refused with INSTALL_FAILED_VERSION_DOWNGRADE. Every build of one version shares its code, which
// Android accepts as an update, so a build that must install over one already on the phone needs a VERSION bump, not a
// bigger build number.
val appVersion = rootProject.file("VERSION").readText().trim()
val versionParts = appVersion.split('.')
require(versionParts.size == 3) { "VERSION must be MAJOR.MINOR.PATCH, got '$appVersion'" }
val versionNumbers = versionParts.map { part ->
    part.toIntOrNull() ?: error("VERSION must be MAJOR.MINOR.PATCH, got '$appVersion'")
}
require(versionNumbers[0] in 0..2099 && versionNumbers[1] in 0..999 && versionNumbers[2] in 0..999) {
    "VERSION $appVersion does not fit the versionCode scheme (major 0 to 2099, minor and patch 0 to 999)"
}
val appVersionCode = versionNumbers[0] * 1_000_000 + versionNumbers[1] * 1_000 + versionNumbers[2]

android {
    namespace = "app.pokeplan"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.pokeplan"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
    }
    // Two keys: the committed debug key (its password is public by design, like every Android debug key) signs local and
    // "Latest build" debug APKs, and a release key kept OUTSIDE the repo signs release builds. The release key comes from CI
    // secrets: POKEPLAN_KEYSTORE (path to the .jks), POKEPLAN_KEYSTORE_PASSWORD, POKEPLAN_KEY_ALIAS and POKEPLAN_KEY_PASSWORD.
    // With no release key a release build is left unsigned: it is never quietly signed with the debug key.
    // Owner action before a public release: store the key as a secret, build release APKs with these variables set and
    // re-register the new key's SHA-1 (INTEGRATIONS_AND_PRIVACY.md 4.4).
    val releaseKeystore = System.getenv("POKEPLAN_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        create("debugKey") {
            storeFile = file("pokeplan-debug.keystore")
            storePassword = "pokeplan"
            keyAlias = "pokeplan"
            keyPassword = "pokeplan"
        }
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("POKEPLAN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("POKEPLAN_KEY_ALIAS") ?: "pokeplan"
                keyPassword = System.getenv("POKEPLAN_KEY_PASSWORD") ?: System.getenv("POKEPLAN_KEYSTORE_PASSWORD")
            }
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debugKey")
        }
        release {
            isMinifyEnabled = false
            signingConfig = if (releaseKeystore != null) signingConfigs.getByName("release") else null
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
    implementation(libs.kotlinx.serialization.json)
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
