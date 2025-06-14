group = "com.github.m9w"

plugins {
    kotlin("jvm") version "2.1.20"
    id("org.gradle.application")
}
version = "1.0.0-Beta"

application {
    applicationName = "darkorbit-cli-client"
    mainClass.set("com.github.m9w.MainKt")
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.google.code.gson", "gson","2.12.1")
    implementation("org.jetbrains.kotlin", "kotlin-reflect", "2.1.20")
    implementation("io.netty", "netty-buffer", "4.2.0.Final")
    api("com.github.m9w", "darkorbit-protocol", "1.1.47")
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
