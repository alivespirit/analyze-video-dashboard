import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Release signing credentials live in `keystore.properties` at the project root (gitignored), so
// passwords never sit in the build file or VCS. If the file is absent, the release build falls back
// to the debug key so it still installs locally.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}

android {
    namespace = "com.spoglyadayko.dashboard"
    compileSdk = 35

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.spoglyadayko.dashboard"
        minSdk = 26
        targetSdk = 35
        // versionCode scheme: major*10000 + minor*100 + patch (3.0.0 -> 30000).
        versionCode = 30000
        versionName = "3.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Use the real release key when keystore.properties is present; otherwise fall back to the
            // debug key so the release variant still builds and installs on your own device.
            signingConfig = if (keystorePropsFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }

    lint {
        // Re-enabled after bumping AGP to 8.10.x (lint 32.x), which can load the Compose 1.11 /
        // lifecycle lint-check jars that crashed AGP 8.7.3's older lint engine. Release builds run
        // lintVitalRelease again and abort on error-severity findings.
        checkReleaseBuilds = true
        abortOnError = true
        // Persisted findings to acknowledge/triage live here; uncomment to snapshot the current set
        // so the build stops failing on pre-existing issues while we work through them.
        // baseline = file("lint-baseline.xml")
    }
}

dependencies {
    // Compose BOM (2026.04.01 → compose-ui 1.11.0; aligns with material3 1.5.0-alpha18)
    val composeBom = platform("androidx.compose:compose-bom:2026.04.01")
    implementation(composeBom)
    // material3 pinned to the last 1.5.0-alpha on the compose-ui 1.11 line (alpha19+ requires
    // compileSdk 37 / AGP 9). alpha18 carries the full M3 Expressive API: MaterialExpressiveTheme,
    // MotionScheme, MaterialShapes, FAB menu, floating toolbar, button group, loading indicator.
    implementation("androidx.compose.material3:material3:1.5.0-alpha18")
    // Shape morphing primitives (RoundedPolygon / Morph) used by MaterialShapes
    implementation("androidx.graphics:graphics-shapes:1.0.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Activity & lifecycle
    // 1.10.1: first release with androidx.activity.compose.LocalActivity (replaces the lint-flagged
    // `LocalContext.current as Activity` cast). Stays on the lifecycle 2.8.x line to match the pins below.
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Ktor client
    val ktorVersion = "3.0.3"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Koin DI
    val koinVersion = "4.0.1"
    implementation("io.insert-koin:koin-android:$koinVersion")
    implementation("io.insert-koin:koin-androidx-compose:$koinVersion")

    // DataStore preferences
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Coil image loading
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

    // Media3 ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    // Vico charts
    implementation("com.patrykandpatrick.vico:compose-m3:2.0.1")

    // Haze — backdrop blur for the frosted-glass floating nav. 1.6.0 is the last release built against
    // Kotlin 2.1 (newer needs 2.2); blur uses RenderEffect on API 31+, with a graceful scrim fallback below.
    implementation("dev.chrisbanes.haze:haze:1.6.0")

    // Pull-to-refresh
    // (included in material3)

    // Core
    implementation("androidx.core:core-ktx:1.15.0")
}
