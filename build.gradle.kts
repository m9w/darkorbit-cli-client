group = "com.github.m9w"

plugins {
    kotlin("jvm") version "2.1.20"
}
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    testImplementation(kotlin("test"))
    api("com.google.code.gson", "gson","2.12.1")
    api("org.jetbrains.kotlin", "kotlin-reflect", "2.1.20")
    api("io.netty", "netty-buffer", "4.2.0.Final")
    api("com.github.m9w", "darkorbit-protocol", "1.1.45")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
