package com.github.m9w.util

import java.io.File
import java.lang.management.ManagementFactory
import kotlin.system.exitProcess

private val pidFile = File("pid")

object ProcessIdentifier {
    fun check() {
        val pid = if (pidFile.isFile) pidFile.readText().toLong() else -1L
        val handler = ProcessHandle.of(pid)
        if (pid != -1L && handler.isPresent) {
            val cfg = System.getProperty("pidManager", "interrupt")
            when (cfg) {
                "interrupt" -> {
                    println("Already exist another one application instance $pid")
                    println("Add JVM flag `-DpidManager=kill` for destroying previous application instance")
                    println("Add JVM flag `-DpidManager=ignore` for ignore existing application instance")
                    exitProcess(1)
                }

                "kill" -> {
                    val result = handler.get().destroy()
                    println("Previous $pid instance destroyed ($result)")
                }

                "ignore" -> return

                else -> {
                    println("Unknown value $cfg of pidManager JVM variable. Available values is [interrupt, kill, ignore]")
                    println("`interrupt` - is interrupt creating new application instance")
                    println("`kill` - is kill previous application instance and start new one")
                    println("`ignore` - is ignore existing parallel application instances")
                }
            }
        }
        File("pid").apply { deleteOnExit() }.writeText(ManagementFactory.getRuntimeMXBean().name.split("@").first())
    }
}