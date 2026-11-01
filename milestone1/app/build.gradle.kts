import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "dev.herdr.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.herdr.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    // 正式签名：仓库根有 keystore.properties 时使用（不进 git）；否则回退 debug key（仅内部试用）
    val keystorePropsFile = rootProject.file("keystore.properties")
    signingConfigs {
        create("releaseSigned") {
            if (keystorePropsFile.exists()) {
                val props = Properties()
                keystorePropsFile.inputStream().use { input -> props.load(input) }
                storeFile = rootProject.file(props.getProperty("storeFile").orEmpty())
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        // benchmark：基于 release 语义（不可调试 + R8），用于 Macrobenchmark 与 UI 测试。
        // Fixture 只进入 debug 与 benchmark 变体，正式 release 包不含任何 fixture 代码。
        create("benchmark") {
            initWith(getByName("release"))
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
        getByName("release") {
            // 0.1.0：首个可安装版本，先不开 R8（sshj/JSON 反射风险未验证完）
            isMinifyEnabled = false
            signingConfig = if (keystorePropsFile.exists()) {
                signingConfigs.getByName("releaseSigned")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    sourceSets {
        // benchmark 变体复用 debug 源集里的 Fixture / TestHost Activity
        getByName("benchmark") {
            kotlin.srcDir("src/debug/kotlin")
            java.srcDir("src/debug/kotlin")
            manifest.srcFile("src/debug/AndroidManifest.xml")
        }
    }
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
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    implementation("androidx.room:room-runtime:2.8.5")
    // 让 CompilationMode.Partial()（baseline profile）可用的官方安装器
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
}
