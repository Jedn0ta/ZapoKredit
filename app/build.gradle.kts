plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "cz.klubpos"
    compileSdk = 34

    defaultConfig {
        applicationId = "cz.klubpos"
        // minSdk 26 (Android 8.0): covers Android Keystore-backed EncryptedSharedPreferences,
        // EncryptedFile and adaptive icons. Raise/lower once the target tablet is fixed.
        minSdk = 26
        targetSdk = 34
        // CI passes these from the git tag (v1.2.3) and run number; local builds use the defaults.
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Where the in-app updater looks for new releases (GitHub Releases, public repo).
        buildConfigField("String", "UPDATE_REPO", "\"Jedn0ta/ZapoKredit\"")
    }

    // Two apps from one codebase:
    //  pos  – the bar tablet (ZapoKredit)
    //  klic – the owner's phone key (ZapoKredit Klíč): pairs with the tablet and approves protected actions
    flavorDimensions += "app"
    productFlavors {
        create("pos") {
            dimension = "app"
            resValue("string", "app_name", "ZapoKredit")
            buildConfigField("boolean", "IS_KEY_APP", "false")
        }
        create("klic") {
            dimension = "app"
            applicationIdSuffix = ".klic"
            resValue("string", "app_name", "ZapoKredit Klíč")
            buildConfigField("boolean", "IS_KEY_APP", "true")
        }
    }

    // Release signing from environment (GitHub Actions secrets). The SAME key must sign every
    // release, otherwise Android refuses to install the update over the existing app.
    val ksPath = System.getenv("SIGNING_KEYSTORE_PATH")
    signingConfigs {
        if (ksPath != null) create("release") {
            storeFile = file(ksPath)
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
        // TESTING ONLY: fixed, public key committed in the repo so every build (CI and Android Studio)
        // is signed the same way and in-app updates work. Replace with the secret release key
        // (GitHub Secrets) before real use – switching keys later requires one reinstall.
        create("testing") {
            storeFile = file("test-signing.jks")
            storePassword = "zapokredit-test"
            keyAlias = "zapokredit-test"
            keyPassword = "zapokredit-test"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName(if (ksPath != null) "release" else "testing") }
        release {
            // Without the release key (secrets not set yet) fall back to the debug key so the APK
            // is still installable for testing. Such builds can't be updated by properly signed ones.
            signingConfig = if (ksPath != null) signingConfigs.getByName("release") else signingConfigs.getByName("testing")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
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
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Encrypted Room: Room over SQLCipher.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.ktx)

    // Keystore-backed storage for the DB passphrase.
    implementation(libs.androidx.security.crypto)

    // Images, QR generation, local upload server.
    implementation(libs.coil.compose)
    implementation(libs.zxing.core)
    implementation(libs.nanohttpd)
    // QR scanner (used by the owner key app)
    implementation(libs.zxing.embedded)
}
