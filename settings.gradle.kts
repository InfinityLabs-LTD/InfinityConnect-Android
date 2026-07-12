// Настройки Gradle-сборки проекта Infinity Connect Android
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
        // Локальные AAR движков (Xray/Hysteria2) кладутся в app/libs
        flatDir { dirs("app/libs") }
    }
}

rootProject.name = "InfinityConnect"
include(":app")
include(":libv2ray-stub")
include(":libhysteria2-stub")
