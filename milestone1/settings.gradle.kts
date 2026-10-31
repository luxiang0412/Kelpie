pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "herdr-m1"
include(":app", ":core:ssh", ":core:herdr", ":core:data", ":feature:board")
