plugins {
    // AGP 8.10.x ships lint 32.x — recent enough to load the Compose 1.11 / lifecycle lint-check jars
    // that crashed AGP 8.7.3's older lint engine (so `lint { checkReleaseBuilds }` could be re-enabled).
    // 8.10 is the ceiling for Gradle 8.11.1; going higher (8.11+) would also need Gradle 8.13+.
    id("com.android.application") version "8.10.1" apply false
    // Kotlin 2.1.20: minimum required by the compose 1.11 runtime (kotlin-stdlib floor) pulled in
    // via material3 1.5.0-alpha18. Patch bump within 2.1.x; compose compiler plugin matches Kotlin.
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20" apply false
}
