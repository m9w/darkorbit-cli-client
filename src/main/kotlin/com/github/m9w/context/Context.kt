package com.github.m9w.context

import com.github.m9w.plugins.dao.DynamicModuleInstance
import java.io.Closeable
import java.util.WeakHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KProperty

val context get() = Context()
val optionalContext get() = Context().Optional()

@Suppress("unchecked_cast")
class Context {
    private lateinit var classifier: KClassifier
    var module: DynamicModuleInstance? = null
        get() = field ?: findInContext(classifier).also { field = it; it.usedBy(this) }

    operator fun <T> getValue(thisRef: Any?, property: KProperty<*>): T {
        if (!this::classifier.isInitialized) classifier = property.returnType.classifier ?: throw RuntimeException("Property type unknown")
        return module!!.instance as T
    }

    inner class Optional {
        private var isEmpty = false
        operator fun <T> getValue(thisRef: Any?, property: KProperty<*>): T? {
            if (isEmpty) return null
            return runCatching<T> { this@Context.getValue(thisRef, property) }.onFailure { isEmpty = true }.getOrNull()
        }
    }

    companion object : Closeable {
        private val ctxNav = WeakHashMap<Thread, MutableMap<KClass<*>, DynamicModuleInstance>>()
        private val ctx = ThreadLocal.withInitial { mutableMapOf<KClass<*>, DynamicModuleInstance>().also { ctxNav[Thread.currentThread()] = it } }
        fun findInContext(classifier: KClassifier): DynamicModuleInstance = ctx.get()[classifier] ?: throw ClassNotFoundException("Cannot found module in context that can classified as $classifier")

        fun add(vararg value: DynamicModuleInstance) = add(setOf(*value))

        fun add(newContext: Set<DynamicModuleInstance>): Set<DynamicModuleInstance> {
            val removed = ctx.get()?.let { context -> newContext.mapNotNull { newItem -> context.put(newItem.module.abstraction, newItem) } }?.toSet() ?: emptySet()
            newContext.map { it.instance }.filterIsInstance<ContextEvents>().forEach { it.addedToContext(newContext::forEach) }
            removed.map { it.instance }.filterIsInstance<ContextEvents>().forEach { it.removedFromContext(removed::forEach) }
            return removed
        }

        fun <T> Thread.enterToContext(block: () -> T): T {
            ctx.get().let { prev ->
                ctx.set(ctxNav[this])
                try { return block() }
                finally { ctx.set(prev) }
            }
        }

        inline fun <reified T : Any> get(): T = findInContext(T::class).instance as T

        override fun close() {
            ctx.remove()
            ctxNav.remove(Thread.currentThread())
        }
    }
}