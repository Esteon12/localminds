plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.localmind.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.localmind.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures { buildConfig = true }

    packaging {
        jniLibs { useLegacyPackaging = true }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("dev.ffmpegkit-maintained:llama-android:0.1.1")
}
