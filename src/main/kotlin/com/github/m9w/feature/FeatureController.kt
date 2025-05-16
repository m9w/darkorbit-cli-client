package com.github.m9w.feature

import com.darkorbit.ProtocolPacket
import com.github.m9w.Scheduler
import com.github.m9w.feature.suspend.SuspendFlow
import java.io.InterruptedIOException
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.KClass


object FeatureController {
    fun <T> runCoroutine(scheduler: Scheduler, block: suspend () -> T): Future<T> = FutureImpl(scheduler, block)

    suspend fun waitMs(ms: Long) = suspendWithInterrupt<Unit> {
        object : SuspendFlow {
            override fun <T> getFuture(): Future<T> = it as Future<T>
            override fun <T> schedule(future: Future<T>) = it.scheduler.resumeIn(future, ms)
            override fun toString() = "Wait $ms ms"
        }
    }

    suspend fun <T> waitOnPackage(waitFor: Set<KClass<out ProtocolPacket>>, exceptBy: Set<KClass<out ProtocolPacket>> = emptySet(), timeout: Long = -1, postExecute: () -> Unit = {}) : T {
        return suspendWithInterrupt {
            object : SuspendFlow {
                override fun <T> getFuture(): Future<T> = it as FutureImpl<T>
                override fun <T> schedule(future: Future<T>) {
                    val pending = it.scheduler.addPendingFuture(it, waitFor, exceptBy)
                    if (timeout > 0) it.scheduler.interruptIn(future, timeout) {
                        it.scheduler.remove(pending)
                        InterruptedIOException("Timeout waiting $timeout ms")
                    }
                    postExecute()
                }
                override fun toString() = "Wait $waitFor events, fail on $exceptBy, timeout $timeout ms"
            }
        }
    }

    private suspend fun <T> suspendWithInterrupt(reason: (FutureImpl<T>) -> SuspendFlow): T =
        suspendCoroutine { it.getFuture().apply { interruptReason = reason.invoke(this) }.continuation = it as Continuation<Any> }

    private fun <T> Continuation<T>.getFuture(): FutureImpl<T> = (this.context[FutureContext]?.future ?: error("No future in context")) as FutureImpl<T>

    private class FutureImpl<T>(val scheduler: Scheduler, block: suspend () -> T) : Future<T> {
        private var result: Result<T>? = null
        override val isDone: Boolean get() = result != null
        override val hasError: Boolean get() = result?.isFailure == true
        var continuation: Continuation<Any>? = null
        var interruptReason: SuspendFlow? = null

        init {
            block.startCoroutine(object : Continuation<T> {
                override val context: CoroutineContext = FutureContext(this@FutureImpl)
                override fun resumeWith(r: Result<T>) = this@FutureImpl.run { result = r }
            })
            if (!isDone) interruptReason?.schedule(this)
        }

        override fun resume(result: Any) {
            if (!isDone) continuation?.resume(result)
            if (!isDone) interruptReason?.schedule(this)
        }

        override fun interrupt(block: () -> Exception) {
            if (!isDone) continuation?.resumeWith(Result.failure(block.invoke()))
        }

        override fun getResult(): T = result?.getOrThrow() ?: throw IllegalStateException("Not completed")
    }

    private class FutureContext(val future: FutureImpl<*>) : AbstractCoroutineContextElement(FutureContext) {
        companion object : CoroutineContext.Key<FutureContext>
    }
}
