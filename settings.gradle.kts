pluginManagement {
    repositories {
        mavenCentral()
        maven("https://dl.google.com/dl/android/maven2/")
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://dl.google.com/dl/android/maven2/")
    }
}
rootProject.name = "PocketBucket"
include(":core", ":composeApp")
