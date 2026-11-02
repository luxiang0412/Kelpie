plugins {
    alias(libs.plugins.herdr.android.library)
    alias(libs.plugins.ksp)
}
android { namespace = "dev.herdr.core.data" }
dependencies {
    api(project(":core:herdr"))
    api(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.kotlinx.coroutines.test)
}
