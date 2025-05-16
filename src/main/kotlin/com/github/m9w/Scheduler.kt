package com.github.m9w

import com.darkorbit.ProtocolPacket
import com.github.m9w.feature.Future
import com.github.m9w.feature.SchedulerEntity
import com.github.m9w.feature.suspend.ExceptPacketException
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

class Scheduler : Runnable {
    private val eventQueue = LinkedList<ProtocolPacket>()
    private val eventHandlers: MutableMap<KClass<out ProtocolPacket>, MutableSet<PendingFuture>> = HashMap()
    private val onRemove: MutableSet<PendingFuture> = HashSet()
    private val timerQueue = TreeMap<Long, MutableList<() -> Unit>>()
    private val lock = Object()
    @Volatile private var isRun = true
    @Volatile private var eventPending = false

    fun performTimer() {
        System.currentTimeMillis().let { currentTime ->
            if (timerQueue.firstKey() <= currentTime) timerQueue.pollFirstEntry().value.forEach { it() }
        }
    }

    fun performHandler() {
        if (eventQueue.isEmpty()) return
        val packet = synchronized(eventQueue) { eventQueue.removeFirst() }
        eventHandlers[packet::class]?.forEach { if(!it.persist) onRemove.add(it); it.perform(packet) }
        onRemove.forEach(this::remove)
    }

    fun resumeIn(future: Future<*>, ms: Long) = ms.schedule { future.resume() }

    fun interruptIn(future: Future<*>, ms: Long, block: () -> Exception) = ms.schedule { future.interrupt(block) }

    fun handleEvent(packet: ProtocolPacket) {
        synchronized(eventQueue) {
            eventQueue.addLast(packet)
        }
        synchronized(lock) {
            eventPending = true
            lock.notifyAll()
        }
    }

    fun addPendingFuture(future: Future<*>, waitFor: Set<KClass<out ProtocolPacket>>, exceptOn: Set<KClass<out ProtocolPacket>> = emptySet()) : PendingFuture {
        val pendingFuture = PendingFuture( { waitFor.contains(it) },
            { future.resume(it) },
            { future.interrupt { ExceptPacketException(it) } }
        )
        (waitFor + exceptOn).forEach { eventHandlers.getOrPut(it) { mutableSetOf() }.add(pendingFuture) }
        return pendingFuture
    }

    fun remove(pendingFuture: PendingFuture) {
        eventHandlers.values.forEach { it.remove(pendingFuture) }
    }

    override fun run() {
        var last = System.currentTimeMillis()
        fun handleEvents() = synchronized(lock) {
            if (eventPending) {
                eventPending = false
                performHandler()
            }
        }
        while (isRun) {
            try {
                handleEvents()
                var delta = 100 - (System.currentTimeMillis() - last)
                while (delta > 0) {
                    synchronized(lock) {
                        lock.wait(delta)
                        handleEvents()
                    }
                    delta = 100 - (System.currentTimeMillis() - last)
                }
                performTimer()
                last = System.currentTimeMillis()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun Long.schedule(callback: () -> Unit) {
        timerQueue.getOrPut(System.currentTimeMillis() + this) { mutableListOf() } += callback
    }

    data class PendingFuture(private val isSuccess: (KClass<out ProtocolPacket>) -> Boolean,
                             private val onSuccess: (ProtocolPacket) -> Unit,
                             private val onException: (ProtocolPacket) -> Unit = {},
                             val persist: Boolean = false) {
        fun perform(packet: ProtocolPacket) {
            if (isSuccess(packet::class)) onSuccess(packet) else onException(packet)
        }
    }

    inner class Repeatable(private val ms: Long, method: KFunction<*>, instance: Any) : SchedulerEntity(this, method, instance) {
        override fun postAction() = schedule()
        override fun schedule() = ms.schedule(this::run)
    }

    inner class Handler(private val packetType: KClass<out ProtocolPacket>, method: KFunction<*>, instance: Any) : SchedulerEntity(this, method, instance) {
        override fun schedule() {
            eventHandlers.getOrPut(packetType) { mutableSetOf() }
                .add(PendingFuture({ true }, { run() }, persist = true))
        }
    }
}