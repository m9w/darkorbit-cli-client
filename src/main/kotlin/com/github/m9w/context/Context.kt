package com.github.m9w.context

import com.github.m9w.feature.Classifier
import java.io.Closeable
import java.util.WeakHashMap
import kotlin.collections.set
import kotlin.reflect.KClassifier
import kotlin.reflect.KProperty

val context get() = Context()
val optionalContext get() = Context().Optional()

@Suppress("unchecked_cast")
class Context {
    private lateinit var classifier: KClassifier
    val instance: Any by lazy { findInContext(classifier) } //todo make weak reference

    operator fun <T> getValue(thisRef: Any?, property: KProperty<*>): T {
        if (!this::classifier.isInitialized) classifier = property.returnType.classifier ?: throw RuntimeException("Property type unknown")
        return instance as T
    }

    inner class Optional {
        private var isEmpty = false
        operator fun <T> getValue(thisRef: Any?, property: KProperty<*>): T? {
            if (isEmpty) return null
            return try { this@Context.getValue(thisRef, property) } catch (_: Exception) { isEmpty = true; null }
        }
    }

    companion object : Closeable {
        private val ctxNav = WeakHashMap<Thread, MutableMap<KClassifier, Any>>()
        private val ctx = ThreadLocal.withInitial { mutableMapOf<KClassifier, Any>().also { ctxNav[Thread.currentThread()] = it } }
        fun findInContext(classifier: KClassifier): Any = ctx.get()[classifier] ?: throw ClassNotFoundException("Cannot found module in context that can classified as $classifier")

        fun apply(newContext: Set<Any>): Set<Any> {
            val removed = ctx.get()?.let { context -> newContext.mapNotNull { newItem -> context.put((newItem as? Classifier<*>)?.classifier ?: newItem::class, newItem) } }?.toSet() ?: emptySet()
            newContext.filterIsInstance<ContextEvents>().forEach { it.addedToContext(newContext::forEach) }
            removed.filterIsInstance<ContextEvents>().forEach { it.removedFromContext(removed::forEach) }
            return removed
        }

        fun <T> Thread.enterToContext(block: () -> T): T {
            ctx.get().let { prev ->
                ctx.set(ctxNav[this])
                try { return block() }
                finally { ctx.set(prev) }
            }
        }

        inline fun <reified T : Any> get(): T = findInContext(T::class) as T

        override fun close() {
            ctx.remove()
            ctxNav.remove(Thread.currentThread())
        }
    }
}