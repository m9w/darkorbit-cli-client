package com.github.m9w

import com.github.m9w.feature.FeatureController
import com.github.m9w.feature.Future
import java.io.PrintWriter
import java.io.StringWriter
import java.util.*
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend

class TimerController {
    private val queue = TreeMap<Long, MutableList<() -> Unit>>()
    lateinit var packet: PacketController

    fun perform() {
        System.currentTimeMillis().let { currentTime ->
            if (queue.firstKey() <= currentTime) queue.pollFirstEntry().value.forEach { it() }
        }
    }

    private fun Long.schedule(callback: () -> Unit) {
        queue.getOrPut(System.currentTimeMillis() + this) { mutableListOf() } += callback
    }

    fun resumeIn(future: Future<*>, ms: Long) = ms.schedule { future.resume() }

    fun interruptIn(future: Future<*>, ms: Long, block: () -> Exception) = ms.schedule { future.interrupt(block) }

    inner class Repeatable(val ms: Long, val method: KFunction<*>, val instance: Any) : Runnable {
        var status: String = ""
        override fun run() {
            if (method.isSuspend)
                FeatureController.runCoroutine(this@TimerController, packet) {
                    status = try {
                        method.callSuspend(instance)?.toString() ?: ""
                    } catch (e: Exception) {
                        StringWriter().let { e.printStackTrace(PrintWriter(it)) }.toString()
                    } finally { schedule() }
                }
            else {
                status = try {
                    method.call( instance)?.toString() ?: ""
                } catch (e: Exception) {
                    StringWriter().let { e.printStackTrace(PrintWriter(it)) }.toString()
                } finally { schedule() }
            }
        }

        fun schedule() = ms.schedule(this::run)
    }
}