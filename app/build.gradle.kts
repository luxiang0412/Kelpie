import java.util.Properties

plugins {
    alias(libs.plugins.kelpie.android.application)
    alias(libs.plugins.kelpie.android.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "io.github.luxiang0412.kelpie"
    defaultConfig {
        applicationId = "io.github.luxiang0412.kelpie"
        versionCode = 6
        versionName = "0.4.2"
    }
    buildFeatures { buildConfig = true }
    val keystorePropsFile = rootProject.file("keystore.properties")
    val allowDebugSigning = providers.gradleProperty("kelpie.allowDebugSigning").orNull == "true"
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
            // 只有显式 -Pkelpie.allowDebugSigning=true（CI 没配密钥时）才允许。
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
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.metrics.performance)
    // 扫码配对：相机扫描（不依赖 Google Play 服务）+ 从图片识别
    implementation(libs.zxing.android.embedded) { isTransitive = false }
    implementation(libs.zxing.core)
    testImplementation(libs.junit)
    // 截图测试：./gradlew :app:recordRoborazziDebug 录制，verifyRoborazziDebug 对比
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.compose.ui.test.manifest)
}

// release 没有签名配置时直接失败，并说明怎么办（而不是产出一个装不上的 unsigned 包）
val releaseSigningMissing = !rootProject.file("keystore.properties").exists() &&
    providers.gradleProperty("kelpie.allowDebugSigning").orNull != "true"
tasks.matching { it.name == "packageRelease" }.configureEach {
    val missing = releaseSigningMissing
    doFirst {
        if (missing) throw GradleException(
            "release 需要正式签名：缺少 keystore.properties（见 README.md 的“发版”）。" +
                "只是想试构建可以加 -Pkelpie.allowDebugSigning=true（产物是 debug 签名，不能用来发布）。",
        )
    }
}
