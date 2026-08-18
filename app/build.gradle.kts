import java.util.Properties
import io.gitlab.arturbosch.detekt.Detekt

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.gitlab.arturbosch.detekt")
}

// Release signing is read from a gitignored `keystore.properties` (local builds) or
// from env vars (CI secrets) — the keystore and its passwords never live in the repo.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
fun releaseSigning(key: String, env: String): String? =
    (keystoreProps.getProperty(key) ?: System.getenv(env))?.takeIf { it.isNotBlank() }
val hasReleaseKeystore = releaseSigning("storeFile", "RELEASE_STORE_FILE") != null

android {
    namespace = "org.phioster.sanctumd"
    compileSdk = 36 // required by dev.jdtech.mpv:libmpv 1.0.0

    defaultConfig {
        // The code lives in `org.phioster.sanctumd` (see namespace above), but the
        // installed package id stays on the historic name: changing it would install a
        // second app and orphan every existing install's data.
        applicationId = "org.phioster.nexarr"
        minSdk = 26
        targetSdk = 35
        versionCode = 241
        versionName = "1.43.1"

        // libmpv ships native libs for several ABIs; the target device is arm64, so bundle only that
        // to keep the APK small (drop this filter to support 32-bit / x86 devices).
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        // Committed debug keystore so every build (local + CI) signs with the
        // same key — lets the app update in place instead of forcing a reinstall.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            // Populated only when a keystore is configured (CI secrets / keystore.properties).
            releaseSigning("storeFile", "RELEASE_STORE_FILE")?.let { storeFile = file(it) }
            storePassword = releaseSigning("storePassword", "RELEASE_STORE_PASSWORD")
            keyAlias = releaseSigning("keyAlias", "RELEASE_KEY_ALIAS")
            keyPassword = releaseSigning("keyPassword", "RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Real release key when configured; otherwise fall back to the debug key so
            // `assembleRelease` still works for anyone building without the signing secrets.
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release")
                            else signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        // The parsers under test are plain Kotlin; anything that does touch an Android
        // stub should get a default rather than the usual "not mocked" exception.
        unitTests.isReturnDefaultValues = true
        // Compose layout tests run on Robolectric in the normal (fast, emulator-free) unit
        // test job rather than as instrumented tests — they need real resources for that.
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        // Report-only gate: Lint runs in CI and uploads its report, but a warning/error
        // never blocks a build. Tighten (e.g. warningsAsErrors) once the report is clean.
        abortOnError = false
        checkReleaseBuilds = false
        warningsAsErrors = false
        htmlReport = true
        sarifReport = true
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    // Report-only for now: findings surface as a CI artifact without breaking the
    // release pipeline. Flip to false once the reported issues are triaged.
    ignoreFailures = true
    parallel = true
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        sarif.required.set(true)
        txt.required.set(true)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Networking + serialization
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")

    // Persistence
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // App lock (fingerprint/face with device-credential fallback)
    implementation("androidx.biometric:biometric:1.1.0")

    // Background notification polling
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Image loading (posters, cast photos)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Media playback (Jellyfin streaming + offline downloads). Engine sits behind the
    // MediaPlayerEngine interface so libmpv can be swapped in later without touching UI.
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1") // Jellyfin transcode fallback
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1") // background music: MediaSession + notification

    // libmpv — "plays everything" video engine (PGS/ASS subs, any codec, no server transcode).
    // Prebuilt AAR (native .so + MPVLib wrapper); sits behind MediaPlayerEngine with ExoPlayer fallback.
    implementation("dev.jdtech.mpv:libmpv:1.0.0")

    // Homescreen widgets (Compose-style)
    implementation("androidx.glance:glance-appwidget:1.1.1")

    // Unit tests (JVM): API parsing against recorded responses, crypto, pure logic
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    // Compose layout tests. A pure unit test cannot see that a composable measures to the
    // wrong size, which is how a section once grew into a screen-high empty block while every
    // test stayed green. Robolectric keeps these in the fast job — no emulator involved.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
