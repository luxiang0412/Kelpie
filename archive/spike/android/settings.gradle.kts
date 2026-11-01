pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "herdr-spike-android"
include(":app")
