// 约定插件：各模块共用的 Android 配置只写在这里。
plugins { `kotlin-dsl` }

java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }

dependencies {
    compileOnly(libs.android.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = libs.plugins.herdr.android.application.get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.herdr.android.library.get().pluginId
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = libs.plugins.herdr.android.compose.get().pluginId
            implementationClass = "AndroidComposeConventionPlugin"
        }
    }
}
