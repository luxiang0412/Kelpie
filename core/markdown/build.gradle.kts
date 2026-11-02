plugins {
    alias(libs.plugins.kelpie.android.library)
    alias(libs.plugins.kelpie.android.compose)
}
android { namespace = "io.github.luxiang0412.kelpie.core.markdown" }
dependencies {
    api(project(":core:designsystem"))
    implementation(project(":core:herdr"))
    implementation(libs.kotlinx.coroutines.android)
}
