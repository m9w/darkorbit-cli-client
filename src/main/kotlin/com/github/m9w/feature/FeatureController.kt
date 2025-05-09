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
    fun <T> runCoroutine(block: suspend () -> T): Future<T> = FutureImpl(block)

    fun Future<*>.getReason() = if (this is FutureImpl) interruptReason else null

    suspend fun waitMs(ms: Long) = suspendWithInterrupt<Unit> {
        object : SuspendFlow {
            override fun <T> getFuture(): Future<T> = it as Future<T>
            override fun schedule(timerCtrl: TimerController<*>, packetCtrl: PacketController<*>, future: Future<*>) = timerCtrl.resumeIn(future, ms)
            override fun toString() = "Wait $ms ms"
        }
    }

    suspend fun <T> waitOnPackage(waitFor: Set<KClass<*>>, exceptBy: Set<KClass<*>> = emptySet(), timeout: Long = -1, postExecute: () -> Unit = {}) : T {
        return suspendWithInterrupt {
            object : SuspendFlow {
                override fun <T> getFuture(): Future<T> = it as Future<T>
                override fun schedule(timerCtrl: TimerController<*>, packetCtrl: PacketController<*>, future: Future<*>) {
                    timerCtrl.interruptIn(future, timeout) {
                        InterruptedIOException("Timeout waiting $timeout ms")
                    }
                    postExecute()
                }
                override fun toString() = "Wait $waitFor events, fail on $exceptBy, timeout $timeout ms"
            }
        }
    }

    private suspend fun <T> suspendWithInterrupt(reason: (Future<T>) -> SuspendFlow): T =
        suspendCoroutine { it.getFuture().apply { interruptReason = reason.invoke(this) }.setContinuation(it) }

    private fun <T> Continuation<T>.getFuture(): FutureImpl<T> = (this.context[FutureContext]?.future ?: error("No future in context")) as FutureImpl<T>

    private class FutureImpl<T>(block: suspend () -> T) : Future<T> {
        private var continuation: Continuation<T>? = null
        private var result: Result<T>? = null
        var interruptReason: SuspendFlow? = null

        init {
            block.startCoroutine(object : Continuation<T> {
                override val context: CoroutineContext = FutureContext(this@FutureImpl)
                override fun resumeWith(r: Result<T>) = this@FutureImpl.run { result = r; continuation = null }
            })
        }

        override val isDone: Boolean get() = result != null
        override val hasError: Boolean get() = result?.isFailure == true

        override fun resume() {
            if (isDone) return
            val cont = continuation ?: throw IllegalStateException("No continuation to resume")
            continuation = null
            interruptReason = null
            cont.resume(Unit as T)
        }

        override fun interrupt(block: () -> Exception) {
            if (isDone) return
            val cont = continuation ?: throw IllegalStateException("No continuation to interrupt")
            continuation = null
            interruptReason = null
            cont.resumeWith(Result.failure(block.invoke()))
        }

        override fun getResult(): T = result?.getOrThrow() ?: throw IllegalStateException("Not completed")

        fun setContinuation(cont: Continuation<T>) {
            continuation = cont
        }
    }

    private class FutureContext(val future: FutureImpl<*>) : AbstractCoroutineContextElement(FutureContext) {
        companion object : CoroutineContext.Key<FutureContext>
    }
}
