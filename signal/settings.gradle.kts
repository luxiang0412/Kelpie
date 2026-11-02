pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "herdr-signal"
include(
    ":core:ssh",
    ":core:herdr",
    ":core:designsystem",
    ":core:markdown",
)
