pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "herdr-signal"
include(
    ":app",
    ":core:ssh",
    ":core:herdr",
    ":core:data",
    ":core:designsystem",
    ":core:markdown",
    ":benchmark",
)
