package com.github.m9w

import com.darkorbit.ProtocolPacket
import com.github.m9w.client.GameEngine
import com.github.m9w.context.Context
import com.github.m9w.context.Context.Companion.enterToContext
import com.github.m9w.context.context
import com.github.m9w.feature.Classifier
import com.github.m9w.feature.Future
import com.github.m9w.feature.SchedulerEntity
import com.github.m9w.feature.annotations.OnEvent
import com.github.m9w.feature.annotations.OnPackage
import com.github.m9w.feature.annotations.Repeat
import com.github.m9w.feature.suspend.ExceptPacketException
import com.github.m9w.protocol.Factory.className
import java.io.Closeable
import java.lang.RuntimeException
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.jvmErasure


class Scheduler : Runnable, Closeable, Classifier<Scheduler> {
    private val eventPacketQueue = ConcurrentLinkedQueue<ProtocolPacket>()
    private val eventQueue = ConcurrentLinkedQueue<Pair<String, String>>()
    private val eventHandlers: MutableMap<String, MutableSet<PendingFuture>> = HashMap()
    private val timerQueue = TreeMap<Long, MutableList<() -> Unit>>()
    private val timerCancellationKeys = HashMap<String, MutableList<((()->Exception)?)->Unit>>()
    private val lock = Object()
    private val hasEvents get() = !eventPacketQueue.isEmpty() || !eventQueue.isEmpty()
    private val engine: GameEngine by context
    private val modules = mutableSetOf<Any>()
    private val thread: Thread = Thread(this, "Scheduler instance")
    private var isClosed = false

    fun init(context: Set<Any>) {
        modules.addAll(context)
        thread.start()
    }

    private fun performTimer(): Long {
        if (timerQueue.isEmpty()) return Long.MAX_VALUE
        val key = timerQueue.firstKey() - System.currentTimeMillis()
        if (key <= 0) timerQueue.pollFirstEntry().value.forEach { it() }
        return key
    }

    private fun performHandler() {
        while (true) {
            val packet = eventPacketQueue.poll() ?: break
            eventHandlers[packet.className]?.let { handlerSet ->
                synchronized(handlerSet) {
                    handlerSet.removeIf { handler ->
                        handler.perform(packet)
                        !handler.persist
                    }
                }
            }
        }

        while (true) {
            val (event, body) = eventQueue.poll() ?: break
            eventHandlers["@$event"]?.forEach { it.perform(body) }
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

    fun sendEvent(packet: ProtocolPacket) {
        if (isClosed) return
        eventPacketQueue.offer(packet)
        synchronized(lock) { lock.notifyAll() }
    }

    fun sendEvent(event: String, body: String = "", async: Boolean = false) {
        if (isClosed) return
        val sameThread = thread == Thread.currentThread()
        if (!async && sameThread) {
            eventHandlers["@$event"]?.forEach { it.perform(body) }
        } else {
            eventQueue.offer(event to body)
            if (!sameThread) synchronized(lock) { lock.notifyAll() }
        }
    }

    fun addPendingFuture(future: Future<*>, waitFor: Set<String>, exceptOn: Set<String> = emptySet()) : PendingFuture {
        val pendingFuture = PendingFuture( waitFor::contains, future::resume, { future.interrupt { ExceptPacketException(it) } })
        (waitFor + exceptOn).forEach { addHandler(it, pendingFuture) }
        return pendingFuture
    }

    private fun addHandler(key: String, handler: PendingFuture) {
        val handlers = eventHandlers[key]
        if (handlers != null) synchronized(handlers) {
            handlers.add(handler)
        } else {
            eventHandlers[key] = mutableSetOf(handler)
        }
    }

    fun remove(pendingFuture: PendingFuture) {
        eventHandlers.values
            .filter { it.contains(pendingFuture) }
            .forEach { synchronized(it) { it.remove(pendingFuture) } }
    }

    private fun loadContextTasks() {
        modules.flatMap { instance ->
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
        Context.add(modules)
        loadContextTasks()
        while (!isClosed) {
            try {
                while (hasEvents) performHandler()
                val nextIteration = performTimer()
                if (nextIteration > 0) {
                    val delta = if (nextIteration > 10000) 5000
                    else if (nextIteration > 100) nextIteration
                    else nextIteration/2 + 5
                    synchronized(lock) { lock.wait(delta) }
                }
            } catch (_: InterruptedException) {
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        runCatching { engine.disconnect() }
        Context.close()
    }

    fun schedule(delay: Long = 0, callback: () -> Unit) {
        timerQueue.getOrPut(System.currentTimeMillis() + delay) { mutableListOf() } += callback
        if (delay == 0L) synchronized(lock) { lock.notifyAll() }
    }

    fun <T> runWithContext(block: () -> T): T = thread.enterToContext(block)

    override fun close() {
        isClosed = true
        thread.interrupt()
    }

    class PendingFuture(private val isSuccess: ((String) -> Boolean)? = null,
                        private val onSuccess: (Any) -> Unit,
                        private val onException: ((ProtocolPacket) -> Unit)? = null,
                        val persist: Boolean = false) {
        fun perform(packet: ProtocolPacket) {
            if (isSuccess?.invoke(packet.className) ?: true) onSuccess(packet)
            else onException?.invoke(packet)
        }

        fun perform(body: String) = onSuccess(body)
    }

    private inner class Repeatable(private val interval: Long, private val method: KFunction<*>, private val instance: Any, private var noDelay: Boolean = false) : SchedulerEntity(this, method.isSuspend) {
        init { method.isAccessible = true }
        override fun postAction() = schedule()
        override fun schedule() {
            schedule(if (noDelay) 0 else interval) {
                run({ method.callSuspend(instance) }, { method.call(instance) })
            }
            noDelay = false
        }
    }

    private inner class Handler(private val packetType: String, private val method: KFunction<*>, private val instance: Any) : SchedulerEntity(this, method.isSuspend) {
        init { method.isAccessible = true }
        override fun schedule() {
            val pf = PendingFuture(onSuccess = {
                run({ method.callSuspend(instance, it) }, { method.call(instance, it) })
            }, persist = true)
            addHandler(packetType, pf)
        }
    }
}
