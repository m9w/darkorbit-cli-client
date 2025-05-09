package com.github.m9w

import com.github.m9w.feature.FeatureController
import com.github.m9w.feature.FeatureController.getReason
import com.github.m9w.feature.Future
import java.util.*
import java.util.function.Consumer
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend

class TimerController<T> {
    val queue: TreeMap<Long, MutableList<(T)->Unit>> = TreeMap()
    lateinit var packet: PacketController<T>
    fun perform(arg: T, packetCtrl: PacketController<T>) {
        packet = packetCtrl
        var e= queue.firstEntry()
        while (e != null && e.key < System.currentTimeMillis()) {
            e.value.forEach { it.invoke(arg) }
            queue.remove(e.key)
            e = queue.firstEntry()
        }
    }

    private fun Long.schedule(featureEvent: (T) -> Unit) {
        queue.computeIfAbsent(System.currentTimeMillis() + this) { ArrayList() }.add(featureEvent)
    }

    fun resumeIn(future: Future<*>, ms: Long) = ms.schedule { future.resume() }

    fun interruptIn(future: Future<*>, ms: Long, block: () -> Exception) = ms.schedule { future.interrupt(block) }

    inner class Repeatable(val ms: Long, val method: KFunction<*>, val instance: Any) : Consumer<T> {
        override fun accept(arg: T) {
            if (method.isSuspend) {
                val t = FeatureController.runCoroutine { method.callSuspend( arg); schedule(ms) }
                if (!t.isDone) t.getReason()?.schedule(this@TimerController, packet, t)
                if (t.isDone && t.hasError) t.getResult()
            }
            else {
                method.call( instance, arg)
                schedule(ms)
            }
        }
        fun schedule(ms: Long = 0) = ms.schedule { this }
    }
}
