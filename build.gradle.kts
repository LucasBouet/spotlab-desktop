import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.2.10"
    kotlin("plugin.serialization") version "2.2.10"
    id("org.jetbrains.compose") version "1.9.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
}

group = "com.ugnbt.spotlabdesktop"
version = "0.1.0"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("com.squareup.okhttp3:okhttp-sse:5.4.0")

    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")

    // JVM bindings for libVLC — plays the stream URL with a custom Authorization
    // header and supports seeking, unlike javafx.scene.media's limited codec set.
    implementation("uk.co.caprica:vlcj:4.12.1")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

compose.desktop {
    application {
        mainClass = "com.ugnbt.spotlabdesktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Rpm, TargetFormat.Deb, TargetFormat.AppImage, TargetFormat.Msi)
            packageName = "spotlab-desktop"
            packageVersion = "0.1.0"

            // jlink's automatic module detection (jdeps) can't see what VLCJ
            // loads dynamically through JNA, so it under-trims the bundled
            // runtime — the embedded JVM ends up missing a module and
            // refuses to start ("Failed to launch JVM"). Bundling every
            // module sidesteps the detection entirely, at the cost of a
            // larger installer.
            includeAllModules = true
        }
    }
}
