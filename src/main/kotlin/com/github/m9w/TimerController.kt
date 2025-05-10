package com.github.m9w

import com.github.m9w.feature.FeatureController
import com.github.m9w.feature.Future
import java.util.*
import java.util.function.Consumer
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend

class TimerController<T> {
    private val queue = TreeMap<Long, MutableList<(T) -> Unit>>()
    lateinit var packet: PacketController<*>

    fun perform(arg: T) {
        System.currentTimeMillis().let { currentTime ->
            queue.headMap(currentTime, true).values.flatten().forEach { it(arg) }
            queue.headMap(currentTime, true).clear()
        }
    }

    private fun Long.schedule(callback: (T) -> Unit) {
        queue.getOrPut(System.currentTimeMillis() + this) { mutableListOf() } += callback
    }

    fun resumeIn(future: Future<*>, ms: Long) = ms.schedule { future.resume() }

    fun interruptIn(future: Future<*>, ms: Long, block: () -> Exception) = ms.schedule { future.interrupt(block) }

    inner class Repeatable(val ms: Long, val method: KFunction<*>, val instance: Any) : Consumer<T> {
        override fun accept(arg: T) {
            if (method.isSuspend)
                FeatureController.runCoroutine(this@TimerController, packet) { method.callSuspend(arg); schedule()}
            else {
                method.call( arg)
                schedule()
            }
        }

        fun schedule() = ms.schedule { accept(it) }
    }
}