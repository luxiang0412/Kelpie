plugins { alias(libs.plugins.herdr.android.library) }
android { namespace = "dev.herdr.core.herdr" }
dependencies {
    api(project(":core:ssh"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlinx.coroutines.test)
}
