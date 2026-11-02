// Macrobenchmark：冷启动 + 长对话滚动。目标是 :app 的 minified 包（R8、不可调试，和发布包同样的代码路径）。
//   ./gradlew :benchmark:connectedMinifiedAndroidTest
// MIUI 会拦截被测 Activity 的启动，所以在模拟器 / 原生系统上跑；模拟器上的绝对数值只用来看趋势。
plugins {
    id("com.android.test")
}

android {
    namespace = "dev.herdr.signal.benchmark"
    compileSdk = 37
    defaultConfig {
        minSdk = 28
        targetSdk = 34
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
    implementation("androidx.benchmark:benchmark-macro-junit4:1.5.0")
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
}

androidComponents {
    beforeVariants(selector().all()) { it.enable = it.buildType == "minified" }
}
