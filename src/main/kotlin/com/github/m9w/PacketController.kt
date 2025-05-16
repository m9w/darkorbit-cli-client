package com.github.m9w

import com.darkorbit.ProtocolPacket
import com.github.m9w.feature.FeatureController
import com.github.m9w.feature.Future
import com.github.m9w.feature.suspend.ExceptPacketException
import java.io.PrintWriter
import java.io.StringWriter
import java.util.LinkedList
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend

class PacketController {
    private val packetQueue = LinkedList<ProtocolPacket>()
    private val packetHandlers: MutableMap<KClass<out ProtocolPacket>, MutableSet<PendingFuture>> = HashMap()
    private val onRemove: MutableSet<PendingFuture> = HashSet()
    lateinit var timer: TimerController

    data class PendingFuture(private val isSuccess: (KClass<out ProtocolPacket>) -> Boolean,
                             private val onSuccess: (ProtocolPacket) -> Unit,
                             private val onException: (ProtocolPacket) -> Unit = {},
                             val persist: Boolean = false) {
        fun perform(packet: ProtocolPacket) {
            if (isSuccess(packet::class)) onSuccess(packet) else onException(packet)
        }
    }

    fun handle(packet: ProtocolPacket) {
        synchronized(packetQueue) {
            packetQueue.addLast(packet)
        }
    }

    fun perform() {
        if (packetQueue.isEmpty()) return
        val packet = synchronized(packetQueue) { packetQueue.removeFirst() }
        packetHandlers[packet::class]?.forEach { if(!it.persist) onRemove.add(it); it.perform(packet) }
        onRemove.forEach(this::remove)
    }

    fun addPendingFuture(future: Future<*>, waitFor: Set<KClass<out ProtocolPacket>>, exceptOn: Set<KClass<out ProtocolPacket>> = emptySet()) : PendingFuture {
        val pendingFuture = PendingFuture( { waitFor.contains(it) },
            { future.resume(it) },
            { future.interrupt { ExceptPacketException(it) } }
        )
        (waitFor + exceptOn).forEach { packetHandlers.getOrPut(it) { mutableSetOf() }.add(pendingFuture) }
        return pendingFuture
    }

    fun remove(pendingFuture: PendingFuture) {
        packetHandlers.values.forEach { it.remove(pendingFuture) }
    }

    inner class Handler(val packetType: KClass<out ProtocolPacket>, val method: KFunction<*>, val instance: Any) : Runnable {
        var status: String = ""
        override fun run() {
            if (method.isSuspend) FeatureController.runCoroutine(timer, this@PacketController) {
                status = try {
                    method.callSuspend(instance)?.toString() ?: ""
                } catch (e: Exception) {
                    StringWriter().let { e.printStackTrace(PrintWriter(it)) }.toString()
                }
            } else {
                status = try {
                    method.call( instance)?.toString() ?: ""
                } catch (e: Exception) {
                    StringWriter().let { e.printStackTrace(PrintWriter(it)) }.toString()
                }
            }
        }

        fun schedule() {
            packetHandlers.getOrPut(packetType) { mutableSetOf() }
                .add(PendingFuture({ true }, { run() }, persist = true))
        }
    }
}
