
plugins {
    application
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(24)
}

application {
    mainClass.set("org.example.TelegramKt")
}

tasks.shadowJar {
    manifest {
        attributes(
            "Main-Class" to "org.example.TelegramKt"
        )
    }
    // Убираем суффиксы из имени файла
    archiveClassifier.set("")
    archiveVersion.set("")
}