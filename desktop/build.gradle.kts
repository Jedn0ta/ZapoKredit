import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// ZapoKredit for Windows: a full register screen that works with the main register (tablet) over Wi-Fi.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
}

compose.desktop {
    application {
        mainClass = "cz.klubpos.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "ZapoKredit"
            // MSI needs MAJOR.MINOR.BUILD with MAJOR > 0; CI passes 1.0.<run number>
            packageVersion = (project.findProperty("desktopVersion") as String?) ?: "1.0.0"
            description = "ZapoKredit – pokladna pro PC"
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
