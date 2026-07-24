plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val enableWhisper = (project.findProperty("quietstudio.enableWhisper") as? String) == "true"

android {
    namespace = "com.quietstudio"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.quietstudio"
        minSdk = 26
        targetSdk = 35
        versionCode = 18
        versionName = "1.15"

        vectorDrawables { useSupportLibrary = true }
        buildConfigField("boolean", "WHISPER_ENABLED", enableWhisper.toString())

        if (enableWhisper) {
            ndk { abiFilters += listOf("arm64-v8a") }
            externalNativeBuild {
                cmake { cppFlags += "-O3" }
            }
        }
    }

    if (enableWhisper) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }

    signingConfigs {
        // Committed, stable debug key so every build — local and CI — is signed
        // with the SAME certificate. Without this, CI uses the runner's
        // per-run auto-generated debug key, so each APK has a different
        // signature; Android then refuses to install-over-top and the user must
        // uninstall (wiping all project data). The debug password is the
        // well-known "android" and carries no security value.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    androidResources {
        // Keep bundled scene videos uncompressed so MediaMetadataRetriever can
        // open them by asset file descriptor for the export path.
        noCompress += "mp4"
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Robolectric loads a full Android runtime per test class; give
                // the forked test JVM room so it doesn't OOM/crash the daemon.
                it.maxHeapSize = "2g"
            }
        }
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    implementation(libs.media3.muxer)
    implementation(libs.media3.common)
    implementation(libs.media3.ui)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.video)
    implementation(libs.camera.view)

    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.work)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.ui.tooling)
}
