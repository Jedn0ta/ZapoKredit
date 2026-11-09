import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// ZapoKredit for Windows: the SAME app as on the tablet. The shared sources come straight from the
// Android module; only the platform layer (files *Desktop.kt here) is PC-specific.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.ksp)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xskip-prerelease-check")
    }
}

/** Android-only files (their PC versions are in this module as *Desktop.kt). */
val androidOnly = listOf(
    "cz/klubpos/KlubPosApp.kt", "cz/klubpos/MainActivity.kt",
    "cz/klubpos/core/crypto/DatabaseKeyManager.kt", "cz/klubpos/core/crypto/ImageStore.kt",
    "cz/klubpos/data/db/DatabaseFactory.kt", "cz/klubpos/lan/LanService.kt", "cz/klubpos/platform/Platform.kt",
    "cz/klubpos/ui/components/Tilt.kt", "cz/klubpos/ui/keyapp/**", "cz/klubpos/ui/products/MenuPdf.kt",
    "cz/klubpos/ui/theme/Fonts.kt", "cz/klubpos/update/UpdateManager.kt", "cz/klubpos/upload/QrGenerator.kt",
    "cz/klubpos/waiter/**",
)

sourceSets {
    main {
        kotlin.srcDir("../app/src/main/java")
        kotlin.exclude(androidOnly)
        resources.srcDir(layout.buildDirectory.dir("generated/zkres"))
    }
}

val appVersion = (project.findProperty("appVersion") as String?) ?: "0.0.0"

/** Fonts from the Android module + version info into the PC resources. */
val zkResources by tasks.registering(Copy::class) {
    from("../app/src/main/res/font") { into("font") }
    into(layout.buildDirectory.dir("generated/zkres"))
    doLast {
        layout.buildDirectory.file("generated/zkres/zapokredit-version.properties").get().asFile
            .writeText("version=$appVersion\nbuild=${appVersion.substringAfterLast('.')}\n")
    }
}
tasks.named("processResources") { dependsOn(zkResources) }

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.foundation)

    // database: Room (same entities/DAOs as Android) on an encrypted SQLite via JDBC
    implementation("androidx.room:room-runtime:2.7.0")
    ksp("androidx.room:room-compiler:2.7.0")
    implementation("io.github.willena:sqlite-jdbc:3.+")

    implementation("org.nanohttpd:nanohttpd:2.3.1")        // LAN server for waiters + photo upload
    implementation("com.google.zxing:core:3.5.3")          // QR codes
    implementation("net.sf.kxml:kxml2:2.3.0")              // XmlPullParser (Excel import)
    implementation("org.apache.pdfbox:pdfbox:3.0.3")       // drink list PDF
    implementation("net.java.dev.jna:jna-platform:5.14.0") // Windows DPAPI for the master key

    testImplementation("junit:junit:4.13.2")
}

ksp {
    arg("room.generateKotlin", "true")
}

compose.desktop {
    application {
        mainClass = "cz.klubpos.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "ZapoKredit"
            // MSI needs MAJOR.MINOR.BUILD with MAJOR > 0; CI passes 1.0.<run number>
            packageVersion = (project.findProperty("desktopVersion") as String?) ?: "1.0.0"
            description = "ZapoKredit – pokladna"
            vendor = "ZapoKredit"
            includeAllModules = true
            windows {
                menu = true
                shortcut = true
                menuGroup = "ZapoKredit"
                perUserInstall = true
                dirChooser = true
                // fixed id: a newer MSI upgrades the installed version instead of installing a second copy
                upgradeUuid = "6f3a4c8e-2b1d-4e7a-9c55-1d2e3f4a5b6c"
            }
        }
    }
}
