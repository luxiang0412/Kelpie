import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** `herdr.android.library`：core 模块的 SDK 版本、Java 版本和 JUnit。 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            compileSdk = libs.int("compileSdk")
            defaultConfig { minSdk = libs.int("minSdk") }
            compileOptions {
                sourceCompatibility = JAVA_VERSION
                targetCompatibility = JAVA_VERSION
            }
        }
        dependencies {
            add("testImplementation", libs.findLibrary("junit").get())
        }
    }
}
