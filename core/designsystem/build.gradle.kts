plugins {
    alias(libs.plugins.kelpie.android.library)
    alias(libs.plugins.kelpie.android.compose)
}
android { namespace = "io.github.luxiang0412.kelpie.core.designsystem" }
dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.compose.material3)
}
