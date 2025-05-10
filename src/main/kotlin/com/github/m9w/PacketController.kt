package com.github.m9w

import com.github.m9w.feature.Future
import com.github.m9w.util.ThreadLocal
import java.util.function.Consumer
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

class PacketController<T> {
    private val packetQueue = mutableListOf<Any>()
    lateinit var timer: TimerController<*>

    data class PendingFuture(
        val future: Future<*>,
        val waitFor: Set<KClass<*>>,
        val timeout: Long
    )

    fun process(l: T, packet: Any) {
        ThreadLocal["networkLayer"] = l as Any
        ThreadLocal["currentPacket"] = packet
        synchronized(packetQueue) {
            packetQueue.add(packet)
        }
    }

    fun perform(arg: T) {

    }

    fun addPendingFuture(future: Future<*>, waitFor: Set<KClass<*>>, timeout: Long) {

    }

    inner class Handler(val packetType: KClass<*>, val method: KFunction<*>, val instance: Any) : Consumer<T> {
        override fun accept(arg: T) {

        }

        fun schedule() {

        }
    }
}
