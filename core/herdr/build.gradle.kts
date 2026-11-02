plugins { alias(libs.plugins.kelpie.android.library) }
android { namespace = "io.github.luxiang0412.kelpie.core.herdr" }
dependencies {
    api(project(":core:ssh"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlinx.coroutines.test)
}
