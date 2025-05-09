package com.github.m9w

import com.github.m9w.client.network.NetworkLayer
import com.github.m9w.feature.FeatureController
import com.github.m9w.feature.FeatureController.getReason
import com.github.m9w.feature.FeatureEvent
import com.github.m9w.feature.Future
import com.github.m9w.util.ThreadLocal
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend

class PacketController<T> {
    private val packetQueue = mutableListOf<Any>()
    val handlers = mutableMapOf<KClass<*>, FeatureEvent<T>>()
    private val pendingFutures = mutableListOf<PendingFuture>()

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

    fun perform(arg: T, timer: TimerController<T>) {
        val currentTime = System.currentTimeMillis()
        val packetsToProcess = synchronized(packetQueue) {
            val list = packetQueue.toList()
            packetQueue.clear()
            list
        }

        packetsToProcess.forEach { packet ->
            val packetClass = packet::class
            val handler = handlers[packetClass]
            if (handler != null) {
                val t = FeatureController.runCoroutine { handler.exec(arg) }
                if (!t.isDone) t.getReason()?.schedule(timer, this, t)
            }

            synchronized(pendingFutures) {
                val iterator = pendingFutures.iterator()
                while (iterator.hasNext()) {
                    val pending = iterator.next()
                    if (packetClass in pending.waitFor) {
                        ThreadLocal.set("currentPacket", packet)
                        pending.future.resume()
                        iterator.remove()
                    }
                }
            }
        }

        synchronized(pendingFutures) {
            val iterator = pendingFutures.iterator()
            while (iterator.hasNext()) {
                val pending = iterator.next()
                if (pending.timeout != -1L && currentTime >= pending.timeout) {
                    pending.future.resume()
                    iterator.remove()
                }
            }
        }
    }

    fun addPendingFuture(future: Future<*>, waitFor: Set<KClass<*>>, timeout: Long) {
        synchronized(pendingFutures) {
            pendingFutures.add(PendingFuture(future, waitFor, timeout))
        }
    }

    inner class Handler(val packetType: KClass<*>, val method: KFunction<*>, val instance: Any) : FeatureEvent<T> {
        override suspend fun exec(network: T) {
            val networkLayer = ThreadLocal.get<NetworkLayer>("networkLayer")
            val packet = ThreadLocal.get<Any>("currentPacket")
            try {
                if (method.isSuspend) method.callSuspend(instance, networkLayer, packet)
                else method.call(instance, networkLayer, packet)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
