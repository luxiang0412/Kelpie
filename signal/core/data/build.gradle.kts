plugins { id("com.android.library"); id("com.google.devtools.ksp") }
android {
    namespace = "dev.herdr.core.data"
    compileSdk = 37
    defaultConfig { minSdk = 26; }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    api(project(":core:herdr"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    api("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
