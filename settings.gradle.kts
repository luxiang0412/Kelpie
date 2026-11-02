pluginManagement {
    includeBuild("build-logic")
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "kelpie"
include(
    ":app",
    ":core:ssh",
    ":core:herdr",
    ":core:data",
    ":core:designsystem",
    ":core:markdown",
    ":benchmark",
)
