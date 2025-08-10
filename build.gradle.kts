group = "com.github.m9w"

plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    id("org.gradle.application")
    id("io.ktor.plugin") version "3.2.1"
}
version = "1.0.0-Beta"
val ktorVersion = "3.2.0"
val kotlinVersion = "2.2.0"
application {
    applicationName = "darkorbit-cli-client"
    mainClass.set("com.github.m9w.MainKt")
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    api("com.github.m9w", "darkorbit-protocol", "1.1.57")

    testImplementation(kotlin("test"))
    implementation("com.google.code.gson", "gson","2.12.1")
    implementation("org.jetbrains.kotlin", "kotlin-reflect", kotlinVersion)
    implementation("org.jetbrains.kotlinx", "kotlinx-serialization-core", "1.9.0")
    implementation("io.netty", "netty-buffer", "4.2.0.Final")
    implementation("io.ktor", "ktor-server-core-jvm", ktorVersion)
    implementation("io.ktor", "ktor-server-netty-jvm", ktorVersion)
    implementation("io.ktor", "ktor-serialization-gson", ktorVersion)
    implementation("io.ktor", "ktor-server-websockets", ktorVersion)
    implementation("io.ktor", "ktor-server-content-negotiation-jvm", ktorVersion)
    implementation("org.jetbrains.kotlin", "kotlin-scripting-jvm", kotlinVersion)
    implementation("org.jetbrains.kotlin", "kotlin-scripting-jvm-host", kotlinVersion)
    implementation("org.jetbrains.kotlin", "kotlin-scripting-common", kotlinVersion)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    val cfg by configurations.creating {
        isCanBeResolved = true
        isCanBeConsumed = false
        extendsFrom(configurations["api"])
    }

    from(cfg.map { if (it.isDirectory) it else zipTree(it) })

    manifest {
        attributes["Main-Class"] = application.mainClass
    }
}