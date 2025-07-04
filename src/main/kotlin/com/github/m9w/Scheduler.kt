package com.github.m9w

import com.darkorbit.ProtocolPacket
import com.github.m9w.client.GameEngine
import com.github.m9w.feature.Future
import com.github.m9w.feature.SchedulerEntity
import com.github.m9w.feature.annotations.OnEvent
import com.github.m9w.feature.annotations.OnPackage
import com.github.m9w.feature.annotations.Repeat
import com.github.m9w.feature.suspend.ExceptPacketException
import com.github.m9w.protocol.Factory
import java.io.Closeable
import java.lang.RuntimeException
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.jvmErasure


class Scheduler(vararg ctx: Any) : Runnable, Closeable {
    private val eventPacketQueue = LinkedList<ProtocolPacket>()
    private val eventQueue = LinkedList<Pair<String, String>>()
    private val eventHandlers: MutableMap<String, MutableSet<PendingFuture>> = HashMap()
    private val timerQueue = TreeMap<Long, MutableList<() -> Unit>>()
    private val timerCancellationKeys = HashMap<String, MutableList<((()->Exception)?)->Unit>>()
    private val lock = Object()
    private val hasEvents get() = !eventPacketQueue.isEmpty() || !eventQueue.isEmpty()
    private lateinit var thread: Thread
    @Volatile private var isRun = true
    private val engine: GameEngine by context
    private val rawContext: Set<Any> = mutableSetOf<Any>(this).apply { addAll(ctx) }

    fun performTimer() {
        if (timerQueue.isEmpty()) return
        System.currentTimeMillis().let { currentTime ->
            if (timerQueue.firstKey() <= currentTime) timerQueue.pollFirstEntry().value.forEach { it() }
        }
    }

    fun performHandler() {
        while (hasEvents) {
            synchronized(eventPacketQueue) { eventPacketQueue.toList().also { eventPacketQueue.clear() } }.forEach { packet ->
                eventHandlers[Factory.getClassName(packet)]?.iterator()?.apply {
                    while (hasNext()) next().apply { if(!persist) remove() }.perform(packet)
                }
            }
            synchronized(eventQueue) { eventQueue.toList().also { eventQueue.clear() } }.forEach { (event, body) ->
                eventHandlers["@$event"]?.forEach { it.perform(body) }
            }
        }
    }

    fun resumeIn(future: Future<*>, ms: Long, interruptKey: String) {
        val resume: AtomicReference<()->Unit> = AtomicReference()
        val interrupt: ((()->Exception)?)->Unit = {
            timerQueue[ms]?.remove (resume.get())
            future.interrupt { it?.invoke() ?: RuntimeException("External interrupt") }
        }
        resume.set {
            if (interruptKey.isNotEmpty()) timerCancellationKeys[interruptKey]?.remove(interrupt)
            future.resume()
        }
        if (interruptKey.isNotEmpty()) timerCancellationKeys.getOrPut(interruptKey) { mutableListOf() } += interrupt
        schedule(ms, resume.get())
    }

    fun cancelWaitMs(key: String, block: (()-> Exception)? = null) = timerCancellationKeys.remove(key)?.forEach { it.invoke(block) }

    fun interruptIn(future: Future<*>, ms: Long, block: () -> Exception) = schedule(ms) { future.interrupt(block) }

    fun handleEvent(packet: ProtocolPacket) {
        synchronized(eventPacketQueue) {
            eventPacketQueue.addLast(packet)
        }
        synchronized(lock) { lock.notifyAll() }
    }

    fun handleEvent(event: String, body: String = "") {
        if (thread == Thread.currentThread()) {
            eventHandlers["@$event"]?.forEach { it.perform(body) }
        } else {
            eventQueue.add(event to body)
            synchronized(lock) { lock.notifyAll() }
        }
    }

    fun addPendingFuture(future: Future<*>, waitFor: Set<String>, exceptOn: Set<String> = emptySet()) : PendingFuture {
        val pendingFuture = PendingFuture( waitFor::contains, future::resume,
            { future.interrupt { ExceptPacketException(it) } })
        (waitFor + exceptOn).forEach { eventHandlers.getOrPut(it) { mutableSetOf() }.add(pendingFuture) }
        return pendingFuture
    }

    fun remove(pendingFuture: PendingFuture) {
        eventHandlers.values.forEach { it.remove(pendingFuture) }
    }

    private fun loadContextTasks() {
        rawContext.flatMap { instance ->
            instance::class.memberFunctions.mapNotNull { method ->
                if (method.hasAnnotation<OnPackage>()) {
                    if (method.parameters.size != 2) throw IllegalArgumentException("Unexpected argument count in $method")
                    val packetType = method.parameters[1].type.jvmErasure
                    Handler(packetType.simpleName!!, method, instance)
                } else if (method.hasAnnotation<Repeat>()) {
                    if (method.parameters.size != 1) throw IllegalArgumentException("Unexpected argument count in $method")
                    method.findAnnotation<Repeat>()?.let { Repeatable(it.ms, method, instance, it.noInitDelay) }
                } else if (method.hasAnnotation<OnEvent>()) {
                    if (method.parameters.size != 2) throw IllegalArgumentException("Unexpected argument count in $method")
                    Handler("@"+method.findAnnotation<OnEvent>()!!.event, method, instance)
                } else null
            }
        }.forEach(SchedulerEntity::schedule)
    }

    override fun run() {
        Context.apply(rawContext)
        loadContextTasks()
        engine.connect()
        val delay = 500
        var last = System.currentTimeMillis()
        var delta = 0L
        while (isRun) {
            try {
                while (hasEvents || (delay - (System.currentTimeMillis() - last)).also { delta = it } > 0) {
                    performHandler()
                    synchronized(lock) { if(!hasEvents && delta > 0) lock.wait(delta) }
                }
                performTimer()
                last = System.currentTimeMillis()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        Context.clear()
    }

    fun schedule(delay: Long = 0, callback: () -> Unit) {
        timerQueue.getOrPut(System.currentTimeMillis() + delay) { mutableListOf() } += callback
        if (delay == 0L) synchronized(lock) { lock.notifyAll() }
    }

    fun start() {
        thread = Thread(this, "Scheduler instance")
        thread.start()
    }

    override fun close() {
        isRun = false
        engine.disconnect()
        thread.interrupt()
    }

    data class PendingFuture(private val isSuccess: (String) -> Boolean = { true },
                             private val onSuccess: (Any) -> Unit,
                             private val onException: (Any) -> Unit = {},
                             val persist: Boolean = false) {
        fun perform(packet: Any) {
            if (isSuccess(Factory.getClassName(packet))) onSuccess(packet)
            else onException(packet)
        }
    }

    private inner class Repeatable(private val ms: Long, private val method: KFunction<*>, private val instance: Any, private var noInitDelay: Boolean = false) : SchedulerEntity(this, method.isSuspend) {
        init { method.isAccessible = true }
        override fun postAction() = schedule()
        override fun schedule(){
            schedule(if (noInitDelay) 0 else ms) {
                run({ method.callSuspend(instance) }, { method.call(instance) })
            }
            noInitDelay = false
        }
    }

    private inner class Handler(private val packetType: String, private val method: KFunction<*>, private val instance: Any) : SchedulerEntity(this, method.isSuspend) {
        init { method.isAccessible = true }
        override fun schedule() {
            val pf = PendingFuture(onSuccess = {
                run({ method.callSuspend(instance, it) }, { method.call(instance, it) })
            }, persist = true)
            eventHandlers.getOrPut(packetType) { mutableSetOf() }.add(pf)
        }
    }
}
