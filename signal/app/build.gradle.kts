import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "dev.herdr.signal"
    compileSdk = 37
    defaultConfig {
        // 与旧版 dev.herdr.app 共存：两套数据、两个图标，互不影响
        applicationId = "dev.herdr.signal"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"
    }
    buildFeatures { compose = true; buildConfig = true }
    val keystorePropsFile = rootProject.file("keystore.properties")
    val allowDebugSigning = providers.gradleProperty("signal.allowDebugSigning").orNull == "true"
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 没有正式密钥时不再悄悄用 debug 签名（签名不一致的包装上去以后无法覆盖升级）。
            // 只有显式 -Psignal.allowDebugSigning=true（CI 没配密钥时）才允许。
            signingConfig = when {
                keystorePropsFile.exists() -> signingConfigs.getByName("releaseSigned")
                allowDebugSigning -> signingConfigs.getByName("debug")
                else -> null
            }
        }
    }
    // minified：release 语义（R8、不可调试）+ debug 的 adb 导入钩子，用来在真机上验证混淆后的 SSH / 性能。
    buildTypes.create("minified") {
        initWith(buildTypes.getByName("release"))
        applicationIdSuffix = ".min"
        versionNameSuffix = "-min"
        signingConfig = signingConfigs.getByName("debug")
        matchingFallbacks += listOf("release")
    }
    sourceSets.getByName("minified") {
        kotlin.srcDir("src/debug/kotlin")
        manifest.srcFile("src/debug/AndroidManifest.xml")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        // Roborazzi / Robolectric 截图测试需要真实资源
        unitTests.isIncludeAndroidResources = true
    }
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
    implementation("androidx.metrics:metrics-performance:1.0.0-beta01")
    // 扫码配对：相机扫描（不依赖 Google Play 服务）+ 从图片识别
    implementation("com.journeyapps:zxing-android-embedded:4.3.0") { isTransitive = false }
    implementation("com.google.zxing:core:3.5.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")
    testImplementation("junit:junit:4.13.2")
    // 截图测试：./gradlew :app:recordRoborazziDebug 录制，verifyRoborazziDebug 对比
    testImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.76.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.76.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// release 没有签名配置时直接失败，并说明怎么办（而不是产出一个装不上的 unsigned 包）
val releaseSigningMissing = !rootProject.file("keystore.properties").exists() &&
    providers.gradleProperty("signal.allowDebugSigning").orNull != "true"
tasks.matching { it.name == "packageRelease" }.configureEach {
    val missing = releaseSigningMissing
    doFirst {
        if (missing) throw GradleException(
            "release 需要正式签名：缺少 signal/keystore.properties（见 signal/README.md 的“发版”）。" +
                "只是想试构建可以加 -Psignal.allowDebugSigning=true（产物是 debug 签名，不能用来发布）。",
        )
    }
}
