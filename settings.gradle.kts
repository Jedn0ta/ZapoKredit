pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "KlubPOS"
// ZK_DESKTOP_ONLY=1 (Windows build in CI): skip the Android app, it needs the Android SDK
if (System.getenv("ZK_DESKTOP_ONLY") == null) include(":app")
include(":shared", ":desktop")
