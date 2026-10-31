plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "dev.herdr.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.herdr.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-m1"
    }
    buildFeatures { compose = true }
    buildTypes {
        getByName("release") {
            // M3 只用 debug 版验收；release 的 R8/Baseline Profile 留到 M4 再开
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module")
    }
}
dependencies {
    implementation(project(":feature:board"))
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.room:room-runtime:2.8.5")
}
