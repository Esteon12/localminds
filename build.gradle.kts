name: Build LocalMind APK

on:
  push:
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Install Android SDK packages
        run: |
          SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
          yes | "$SDKMANAGER" --licenses || true
          "$SDKMANAGER" "platforms;android-36" "build-tools;36.0.0"

      - name: Prepare Android project
        run: |
          mkdir -p app/src/main/java/com/localmind/app
          mkdir -p app/src/main/res/values
          mkdir -p app/src/main/assets/runtime

          cp MainActivity.kt app/src/main/java/com/localmind/app/MainActivity.kt
          cp AndroidManifest.xml app/src/main/AndroidManifest.xml
          cp styles.xml app/src/main/res/values/styles.xml
          cp index.html app/src/main/assets/runtime/index.html
          cp app.js app/src/main/assets/runtime/app.js
          cp style.css app/src/main/assets/runtime/style.css

          cat > settings.gradle.kts <<'EOF'
          pluginManagement {
              repositories {
                  google()
                  mavenCentral()
                  gradlePluginPortal()
              }
          }

          dependencyResolutionManagement {
              repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
              repositories {
                  google()
                  mavenCentral()
              }
          }

          rootProject.name = "LocalMind"
          include(":app")
          EOF

          cat > build.gradle.kts <<'EOF'
          plugins {
              id("com.android.application") version "8.10.1" apply false
              id("org.jetbrains.kotlin.android") version "2.1.20" apply false
          }
          EOF

          cat > app/build.gradle.kts <<'EOF'
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

              compileOptions {
                  sourceCompatibility = JavaVersion.VERSION_17
                  targetCompatibility = JavaVersion.VERSION_17
              }

              kotlinOptions {
                  jvmTarget = "17"
              }

              packaging {
                  jniLibs {
                      useLegacyPackaging = true
                  }
              }
          }

          dependencies {
              implementation("androidx.core:core-ktx:1.17.0")
              implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
              implementation("dev.ffmpegkit-maintained:llama-android:0.1.1")
          }
          EOF

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '8.11.1'

      - name: Build APK
        run: gradle :app:assembleDebug --stacktrace

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: LocalMind-APK
          path: app/build/outputs/apk/debug/app-debug.apk
