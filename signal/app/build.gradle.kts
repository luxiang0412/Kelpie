import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "dev.herdr.signal"
    compileSdk = 37
    defaultConfig {
        // 与旧版 dev.herdr.app 共存：两套数据、两个图标，互不影响
        applicationId = "dev.herdr.signal"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.2.0-dev"
    }
    buildFeatures { compose = true; buildConfig = true }
    val keystorePropsFile = rootProject.file("keystore.properties")
    signingConfigs {
        create("releaseSigned") {
            if (keystorePropsFile.exists()) {
                val props = Properties().apply { keystorePropsFile.inputStream().use { load(it) } }
                storeFile = rootProject.file(props.getProperty("storeFile").orEmpty())
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        getByName("debug") { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = if (keystorePropsFile.exists()) signingConfigs.getByName("releaseSigned") else signingConfigs.getByName("debug")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module")
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:markdown"))
    implementation(project(":core:data"))
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-process:2.9.1")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")
    testImplementation("junit:junit:4.13.2")
}
