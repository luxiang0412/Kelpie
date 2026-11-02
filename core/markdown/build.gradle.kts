plugins {
    alias(libs.plugins.herdr.android.library)
    alias(libs.plugins.herdr.android.compose)
}
android { namespace = "dev.herdr.core.markdown" }
dependencies {
    api(project(":core:designsystem"))
    implementation(project(":core:herdr"))
    implementation(libs.kotlinx.coroutines.android)
}
