plugins { alias(libs.plugins.herdr.android.library) }
android { namespace = "dev.herdr.core.ssh" }
dependencies {
    api(libs.sshj)
    implementation(libs.bouncycastle.prov)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.slf4j.nop)
}
