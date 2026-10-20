pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("com.github.MuntashirAkon")
                includeGroup("com.github.MuntashirAkon.spake2-java")
            }
        }
    }
}
rootProject.name = "SetEditPlus"
include(":app")
