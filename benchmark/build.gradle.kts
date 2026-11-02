// Macrobenchmark：冷启动 + 长对话滚动。目标是 :app 的 minified 包（R8、不可调试，和发布包同样的代码路径）。
//   ./gradlew :benchmark:connectedMinifiedAndroidTest
// MIUI 会拦截被测 Activity 的启动，所以在模拟器 / 原生系统上跑；模拟器上的绝对数值只用来看趋势。
plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "io.github.luxiang0412.kelpie.benchmark"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = 28
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 模拟器上跑会被 benchmark 库当成错误；这里明确接受（结果只看相对变化）
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,LOW-BATTERY"
    }
    targetProjectPath = ":app"
    buildTypes {
        create("minified") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    experimentalProperties["android.experimental.self-instrumenting"] = true
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
}

androidComponents {
    beforeVariants(selector().all()) { it.enable = it.buildType == "minified" }
}
