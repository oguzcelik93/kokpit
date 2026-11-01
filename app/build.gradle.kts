import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.yorulmazsinan.kokpit"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.oguzcelik93.kokpit93"
        minSdk = 27
        targetSdk = 35
        versionCode = 26
        versionName = "0.11.2"
    }

    // The signing key never lives in the repository. It comes from, in order:
    // 1. environment variables (e.g. secrets of a cloud build environment):
    //    KOKPIT_KEYSTORE_B64 (base64 of the .jks), KOKPIT_KEYSTORE_PASSWORD, KOKPIT_KEY_ALIAS
    // 2. keystore.properties next to this project, or ~/.config/kokpit/keystore.properties
    // Every release must use the same key, or Android refuses to install it as an update.
    val envKeystore = System.getenv("KOKPIT_KEYSTORE_B64")?.takeIf { it.isNotBlank() }?.let { b64 ->
        layout.buildDirectory.file("signing/kokpit.jks").get().asFile.apply {
            parentFile.mkdirs()
            writeBytes(Base64.getMimeDecoder().decode(b64))
        }
    }
    val keystoreFile = listOf(
        rootProject.file("keystore.properties"),
        File(System.getProperty("user.home"), ".config/kokpit/keystore.properties"),
    ).firstOrNull { it.exists() }
    signingConfigs {
        if (envKeystore != null) {
            create("release") {
                storeFile = envKeystore
                storePassword = System.getenv("KOKPIT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KOKPIT_KEY_ALIAS") ?: "kokpit"
                keyPassword = System.getenv("KOKPIT_KEY_PASSWORD") ?: System.getenv("KOKPIT_KEYSTORE_PASSWORD")
            }
        } else if (keystoreFile != null) {
            create("release") {
                val props = Properties().apply { keystoreFile.inputStream().use(::load) }
                // Relative paths are relative to the properties file's folder.
                storeFile = File(props.getProperty("storeFile")).let { if (it.isAbsolute) it else File(keystoreFile.parentFile, it.path) }
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // With a key configured, debug builds carry the same signature so either can update the other.
        debug {
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.profileinstaller)
    implementation(libs.zxing.core)
    implementation(libs.dadb)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
