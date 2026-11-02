plugins { alias(libs.plugins.kelpie.android.library) }
android { namespace = "io.github.luxiang0412.kelpie.core.ssh" }
dependencies {
    api(libs.sshj)
    implementation(libs.bouncycastle.prov)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.slf4j.nop)
}
