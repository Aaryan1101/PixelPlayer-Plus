import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.theveloper.pixelplay"
version = providers.gradleProperty("APP_VERSION_NAME").getOrElse("0.1.0")

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jaudiotagger)
    implementation(libs.okhttp)
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation("com.github.hypfvieh:dbus-java-core:5.2.2")
    implementation("com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.2.2")

    testImplementation(kotlin("test"))
}

tasks.named<ProcessResources>("processResources") {
    from(rootProject.file("assets/icon.png"))
}

compose.desktop {
    application {
        mainClass = "com.theveloper.pixelplay.desktop.MainKt"
        jvmArgs += listOf("-Xmx768m", "-Dfile.encoding=UTF-8")

        nativeDistributions {
            modules("java.naming", "java.management", "jdk.unsupported", "jdk.security.auth")
            targetFormats(TargetFormat.Deb)
            packageName = "pixelplayer-desktop"
            packageVersion = "0.4.5"
            description = "PixelPlayer desktop music player"
            vendor = "PixelPlayer"
            linux {
                iconFile.set(project.file("../assets/icon.png"))
                menuGroup = "Audio"
            }
        }
    }
}
