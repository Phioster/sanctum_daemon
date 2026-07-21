import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
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
    compileSdk = 35

    defaultConfig {
        // The code lives in `org.phioster.sanctumd` (see namespace above), but the
        // installed package id stays on the historic name: changing it would install a
        // second app and orphan every existing install's data.
        applicationId = "org.phioster.nexarr"
        minSdk = 26
        targetSdk = 35
        versionCode = 165
        versionName = "1.0.1"
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

    // Homescreen widgets (Compose-style)
    implementation("androidx.glance:glance-appwidget:1.1.1")
}
