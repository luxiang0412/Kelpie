plugins {
    alias(libs.plugins.herdr.android.library)
    alias(libs.plugins.herdr.android.compose)
}
android { namespace = "dev.herdr.core.designsystem" }
dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.compose.material3)
}
