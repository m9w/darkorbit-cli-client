package com.github.m9w

import com.github.m9w.feature.Future
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

class PacketController {
    private val packetQueue = mutableListOf<Any>()
    lateinit var timer: TimerController

    data class PendingFuture(
        val future: Future<*>,
        val waitFor: Set<KClass<*>>,
        val timeout: Long
    )

    fun process(packet: Any) {
        synchronized(packetQueue) {
            packetQueue.add(packet)
        }
    }

    fun perform() {

    }

    fun addPendingFuture(future: Future<*>, waitFor: Set<KClass<*>>, timeout: Long) {

    }

    inner class Handler(val packetType: KClass<*>, val method: KFunction<*>, val instance: Any) : Runnable {
        override fun run() {

        }

        fun schedule() {

        }
    }
}
