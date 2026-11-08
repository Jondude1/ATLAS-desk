plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val appVersion = System.getenv("APP_VERSION") ?: "0.2.0-alpha.1"

android {
    namespace = "com.jondude1.atlasdesk"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jondude1.atlasdesk"
        minSdk = 29
        targetSdk = 35
        versionCode = runNumber
        versionName = appVersion
    }

    // Signing material never lives in the repo. CI restores the keystore
    // from the KEYSTORE_BASE64 GitHub secret (KEYSTORE_FILE points at it)
    // and passes the passwords via env vars. Locally, the same env vars
    // work with a keystore at keystore/atlas-desk-release.jks (gitignored).
    signingConfigs {
        create("atlasdesk") {
            storeFile = rootProject.file(
                System.getenv("KEYSTORE_FILE") ?: "keystore/atlas-desk-release.jks"
            )
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: "atlasdesk"
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Only sign when the keystore is actually present (CI tag
            // builds restore it; a bare local assembleRelease stays
            // unsigned instead of failing on a missing file).
            val ks = signingConfigs.getByName("atlasdesk").storeFile
            if (ks != null && ks.exists()) {
                signingConfig = signingConfigs.getByName("atlasdesk")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
}
