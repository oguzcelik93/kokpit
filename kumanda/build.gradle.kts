import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Kokpit Kumanda: the phone-side remote for the panel (Bluetooth instead of Wi-Fi).
android {
    namespace = "io.github.oguzcelik93.kokpit.kumanda"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.oguzcelik93.kokpit93.kumanda"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.1.1"
    }

    // Same key and same sources as the Kokpit app (see app/build.gradle.kts).
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
}

dependencies {
    implementation(libs.play.code.scanner)
    implementation(libs.play.text.recognition)
    implementation(libs.play.document.scanner)
    testImplementation(libs.junit)
}
