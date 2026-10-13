plugins {
    application
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

group = "com.theveloper.pixelplay"
version = providers.gradleProperty("APP_VERSION_NAME").getOrElse("0.1.0")

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("net.dv8tion:JDA:6.7.0")
    implementation("dev.arbjerg:lavalink-client:3.4.0")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")

    testImplementation(kotlin("test"))
}

application {
    mainClass = "com.theveloper.pixelplay.bot.PixelPlayerBotKt"
    applicationDefaultJvmArgs = listOf("-Xms64m", "-Xmx256m", "-Dfile.encoding=UTF-8")
}

tasks.test {
    useJUnitPlatform()
}
