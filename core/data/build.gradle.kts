plugins {
    alias(libs.plugins.kelpie.android.library)
    alias(libs.plugins.ksp)
}
android {
    namespace = "io.github.luxiang0412.kelpie.core.data"
    // Room 的存储 / 迁移测试跑在 Robolectric 上
    testOptions { unitTests.isIncludeAndroidResources = true }
}
dependencies {
    api(project(":core:herdr"))
    api(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
