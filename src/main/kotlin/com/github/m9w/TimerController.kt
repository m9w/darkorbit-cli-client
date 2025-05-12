package com.github.m9w

import com.github.m9w.feature.FeatureController
import com.github.m9w.feature.Future
import java.util.*
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend

class TimerController {
    private val queue = TreeMap<Long, MutableList<() -> Unit>>()
    lateinit var packet: PacketController

    fun perform() {
        System.currentTimeMillis().let { currentTime ->
            queue.headMap(currentTime, true).values.flatten().forEach { it() }
            queue.headMap(currentTime, true).clear()
        }
    }

    private fun Long.schedule(callback: () -> Unit) {
        queue.getOrPut(System.currentTimeMillis() + this) { mutableListOf() } += callback
    }

    fun resumeIn(future: Future<*>, ms: Long) = ms.schedule { future.resume() }

    fun interruptIn(future: Future<*>, ms: Long, block: () -> Exception) = ms.schedule { future.interrupt(block) }

    inner class Repeatable(val ms: Long, val method: KFunction<*>, val instance: Any) : Runnable {
        override fun run() {
            if (method.isSuspend)
                FeatureController.runCoroutine(this@TimerController, packet) { method.callSuspend(instance); schedule()}
            else {
                method.call( instance)
                schedule()
            }
        }

        fun schedule() = ms.schedule(this::run)
    }
}