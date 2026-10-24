import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

ksp {
    // One list of all of Aalap's AppFunctions for the system to index.
    arg("appfunctions:aggregateAppFunctions", "true")
}

android {
    namespace = "com.music.aalap"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.music.aalap"
        minSdk = 26
        targetSdk = 34
        // Must exceed every published build (1.6 shipped as versionCode 16).
        // 2.0.0 is the first Aalap release.
        versionCode = 51
        versionName = "4.4.0"
        // Optional Last.fm key for the similar-tracks source (empty = source off).
        buildConfigField("String", "LASTFM_API_KEY", "\"${(project.findProperty("lastfmApiKey") as String?).orEmpty()}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // One fixed release key, kept out of git in keystore.properties (storeFile,
    // storePassword, keyAlias, keyPassword). Releases used to be signed with whatever
    // debug key the building machine had, so 1.4 and 1.5 could not upgrade each other.
    val releaseKeys = rootProject.file("keystore.properties").takeIf { it.exists() }
        ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }
    signingConfigs {
        if (releaseKeys != null) create("release") {
            storeFile = rootProject.file(releaseKeys.getProperty("storeFile"))
            storePassword = releaseKeys.getProperty("storePassword")
            keyAlias = releaseKeys.getProperty("keyAlias")
            keyPassword = releaseKeys.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Falls back to this machine's debug key without keystore.properties; such a
            // build only upgrades installs signed by that same machine.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Spotify metadata + YouTube streaming, ported from Meld (replaces Firebase data layer)
    implementation(project(":spotify"))
    implementation(project(":innertube"))
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    //hilt
    implementation("com.google.dagger:hilt-android:2.57.1")
    ksp("com.google.dagger:hilt-android-compiler:2.57.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    //coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.7.3")
    // Android AppFunctions: Aalap's actions for Gemini and other assistants (Android 16+).
    implementation("androidx.appfunctions:appfunctions:1.0.0-alpha08")
    implementation("androidx.appfunctions:appfunctions-service:1.0.0-alpha08")
    ksp("androidx.appfunctions:appfunctions-compiler:1.0.0-alpha08")

    //glide
    implementation("com.github.bumptech.glide:compose:1.0.0-beta01")

    //splashScreen
    implementation("androidx.core:core-splashscreen:1.0.1")

    //palette
    implementation("androidx.palette:palette-ktx:1.0.0")

    //exoplayer
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.3.1")
    // HLS: Gaana streams are HLS (m3u8).
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    // Hourly background sync of the Spotify + YouTube Music library.
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // PlayerView for the Spotify Canvas looping video on the now-playing screen.
    implementation("androidx.media3:media3-ui:1.3.1")
    // media session + system media notification (lock screen / notification center)
    implementation("androidx.media3:media3-session:1.3.1")
    // The old-style media session API Google Assistant talks to (debug voice test sends through it).
    implementation("androidx.media:media:1.7.0")

    //Storage Access Framework folder enumeration (local music import)
    implementation("androidx.documentfile:documentfile:1.0.1")

    //okhttp + timber (used by the ported YouTube streaming flow)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    //core library desugaring (required by :innertube)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
}