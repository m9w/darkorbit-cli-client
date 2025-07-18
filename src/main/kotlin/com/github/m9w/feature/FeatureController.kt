package com.github.m9w.feature

import com.github.m9w.Scheduler
import com.github.m9w.feature.suspend.SuspendFlow
import java.io.InterruptedIOException
import kotlin.coroutines.*

@Suppress("unchecked_cast")
object FeatureController {
    fun <T> runCoroutine(scheduler: Scheduler, block: suspend () -> T): Future<T> = FutureImpl(scheduler, block)

    suspend fun waitMs(ms: Long, interruptKey: String) = suspendWithInterrupt<Unit> {
        object : SuspendFlow {
            override fun <T> getFuture(): Future<T> = it as Future<T>
            override fun <T> schedule(future: Future<T>) = it.scheduler.resumeIn(future, ms, interruptKey)
            override fun toString() = "Wait $ms ms"
        }
    }

    suspend fun <T> waitOnPackage(waitFor: Set<String>, exceptBy: Set<String> = emptySet(), timeout: Long = -1, postExecute: () -> Unit = {}) : T {
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
        suspendCoroutine { it.getFuture().apply { interruptReason = reason.invoke(this) }.continuation = it }

    private fun <T> Continuation<T>.getFuture(): FutureImpl<T> = (this.context[FutureContext]?.future ?: error("No future in context")) as FutureImpl<T>

    private class FutureImpl<T>(val scheduler: Scheduler, block: suspend () -> T) : Future<T> {
        private var result: Result<T>? = null
        override val isDone: Boolean get() = result != null
        override val hasError: Boolean get() = result?.isFailure == true
        var continuation: Continuation<T>? = null
        var interruptReason: SuspendFlow? = null

        init {
            block.startCoroutine(Continuation(FutureContext(this)) { run { result = it } })
            if (!isDone) interruptReason?.schedule(this)
        }

        override fun resume(result: Any) {
            if (!isDone) continuation?.resume(result as T)
            if (!isDone) interruptReason?.schedule(this)
        }

        override fun interrupt(block: () -> Exception) {
            if (!isDone) continuation?.resumeWithException(block.invoke())
            if (!isDone) interruptReason?.schedule(this)
        }

        override fun getResult(): T = result?.getOrThrow() ?: throw IllegalStateException("Not completed")
    }

    private class FutureContext(val future: FutureImpl<*>) : AbstractCoroutineContextElement(FutureContext) {
        companion object : CoroutineContext.Key<FutureContext>
    }
}
