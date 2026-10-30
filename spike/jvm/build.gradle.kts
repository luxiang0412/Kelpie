plugins {
    kotlin("jvm") version "2.2.10"
    application
}

repositories { mavenCentral() }

dependencies {
    implementation("com.hierynomus:sshj:0.41.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.13")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("herdrspike.SshSpikeKt")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }
}
