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
            // JitPack hosts libraries like PhotoView
            maven(url = "https://jitpack.io")
    }
}

rootProject.name = "MedicalQuiz"
include(":app")
include(":composeApp")
