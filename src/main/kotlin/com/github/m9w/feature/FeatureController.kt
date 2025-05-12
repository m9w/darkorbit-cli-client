package com.github.m9w.feature

import com.github.m9w.PacketController
import com.github.m9w.TimerController
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
    fun <T> runCoroutine(timer: TimerController, packet: PacketController, block: suspend () -> T): Future<T> = FutureImpl(timer, packet, block)

    suspend fun waitMs(ms: Long) = suspendWithInterrupt<Unit> {
        object : SuspendFlow {
            override fun <T> getFuture(): Future<T> = it as Future<T>
            override fun schedule(future: Future<*>) = it.timer.resumeIn(future, ms)
            override fun toString() = "Wait $ms ms"
        }
    }

    suspend fun <T> waitOnPackage(waitFor: Set<KClass<*>>, exceptBy: Set<KClass<*>> = emptySet(), timeout: Long = -1, postExecute: () -> Unit = {}) : T {
        return suspendWithInterrupt {
            object : SuspendFlow {
                override fun <T> getFuture(): Future<T> = it as FutureImpl<T>
                override fun schedule(future: Future<*>) {
                    it.timer.interruptIn(future, timeout) { InterruptedIOException("Timeout waiting $timeout ms") }
                    postExecute()
                }
                override fun toString() = "Wait $waitFor events, fail on $exceptBy, timeout $timeout ms"
            }
        }
    }

    private suspend fun <T> suspendWithInterrupt(reason: (FutureImpl<T>) -> SuspendFlow): T =
        suspendCoroutine { it.getFuture().apply { setInterruptReason(reason.invoke(this)) }.setContinuation(it) }

    private fun <T> Continuation<T>.getFuture(): FutureImpl<T> = (this.context[FutureContext]?.future ?: error("No future in context")) as FutureImpl<T>

    private class FutureImpl<T>(val timer: TimerController, val packet: PacketController, block: suspend () -> T) : Future<T> {
        override val isDone: Boolean get() = result != null
        override val hasError: Boolean get() = result?.isFailure == true
        private var continuation: Continuation<T>? = null
        private var result: Result<T>? = null
        private var interruptReason: SuspendFlow? = null

        init {
            block.startCoroutine(object : Continuation<T> {
                override val context: CoroutineContext = FutureContext(this@FutureImpl)
                override fun resumeWith(r: Result<T>) = this@FutureImpl.run { result = r; continuation = null }
            })
            if (!isDone) interruptReason?.schedule(this)
        }

        override fun resume() {
            if (isDone) return
            val cont = continuation ?: throw IllegalStateException("No continuation to resume")
            cont.resume(Unit as T)
            if (!isDone) interruptReason?.schedule(this)
        }

        override fun interrupt(block: () -> Exception) {
            if (isDone) return
            val cont = continuation ?: throw IllegalStateException("No continuation to interrupt")
            cont.resumeWith(Result.failure(block.invoke()))
        }

        override fun getResult(): T = result?.getOrThrow() ?: throw IllegalStateException("Not completed")

        fun setContinuation(cont: Continuation<T>) {
            continuation = cont
        }

        fun setInterruptReason(interruptReason: SuspendFlow) {
            this.interruptReason = interruptReason
        }
    }

    private class FutureContext(val future: FutureImpl<*>) : AbstractCoroutineContextElement(FutureContext) {
        companion object : CoroutineContext.Key<FutureContext>
    }
}
