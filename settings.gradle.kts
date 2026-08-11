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
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "Amie"

// Include the Android module as ':app' (Standard name)
include(":app")
project(":app").projectDir = file("amie/app")

// Include other projects as independent builds
// Rename the 'app' included build to avoid name clash with the Android module
includeBuild("app") {
    name = "kobweb-site"
}
includeBuild("packageRepository")
