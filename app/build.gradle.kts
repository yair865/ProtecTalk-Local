// app/build.gradle.kts

import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.protectalk"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.protectalk"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Load your API key from local.properties (never checked into VCS)
        val props = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.also {
                FileInputStream(it).use { load(it) }
            }
        }
        buildConfigField(
            "String",
            "GOOGLE_SPEECH_API_KEY",
            "\"${props.getProperty("google_speech_api_key", "")}\""
        )
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // AndroidX core & UI
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")

    // HTTP client for Google Speech-to-Text
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    // Kotlin coroutines for background work
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
