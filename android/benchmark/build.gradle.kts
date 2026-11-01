plugins { id("com.android.test") }

android {
    namespace = "dev.herdr.benchmark"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 目标 App 目前只有 debug 变体（可启动 Fixture）；显式抑制 DEBUGGABLE 检查，
        // 结果标记为 debug 级基线，不能当作 release 性能结论（见 M2-A-REPORT）。
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "DEBUGGABLE"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.4")
}
