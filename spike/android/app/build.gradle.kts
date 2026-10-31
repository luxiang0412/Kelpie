plugins {
    id("com.android.application")
}

android {
    namespace = "herdrspike.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "herdrspike.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*",
            "META-INF/*.kotlin_module", "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        )
    }
    buildTypes {
        getByName("debug") { isMinifyEnabled = false }
    }
}

dependencies {
    implementation("com.hierynomus:sshj:0.41.2")
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")
    implementation("com.github.mwiede:jsch:0.2.20")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.13")
}
