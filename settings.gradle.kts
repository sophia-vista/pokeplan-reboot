rootProject.name = "pokeplan"

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
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

include(":pokemon-core")
include(":plan-core")
include(":care-core")
include(":heal-core")
include(":mint-core")
include(":home-core")
include(":world-core")
include(":app-core")
include(":composeApp")
include(":androidApp")
include(":data")
