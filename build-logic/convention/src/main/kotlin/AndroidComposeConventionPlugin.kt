import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * `kelpie.android.compose`：在 application / library 约定插件之后应用。
 * 打开 Compose、加 Compose 编译器插件和 BOM（版本由 BOM 统一）。
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.findByType(ApplicationExtension::class.java)?.buildFeatures?.compose = true
        extensions.findByType(LibraryExtension::class.java)?.buildFeatures?.compose = true
        dependencies {
            val bom = libs.findLibrary("compose-bom").get()
            add("implementation", platform(bom))
            add("testImplementation", platform(bom))
            add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
            add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
        }
    }
}
