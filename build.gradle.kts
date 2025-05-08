group = "com.github.m9w"

plugins {
    kotlin("jvm") version "2.1.20"
}
version = "1.0-SNAPSHOT"

apply<Darkorbit>()

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    api("com.google.code.gson", "gson","2.12.1")
    api("org.jetbrains.kotlin", "kotlin-reflect", "2.1.20")
    api("io.netty", "netty-buffer", "4.2.0.Final")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}

