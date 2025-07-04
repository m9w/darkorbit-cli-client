package com.github.m9w

import com.github.m9w.feature.Classifier
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

val context get() = Context()
val optionalContext get() = Context().Optional()

@Suppress("unchecked_cast")
class Context() {
    private lateinit var classifier: KClassifier
    val instance: Any by lazy { findInContext(classifier) }

    operator fun <T> getValue(thisRef: Any?, property: KProperty<*>): T {
        if (!this::classifier.isInitialized) classifier = property.returnType.classifier ?: throw RuntimeException("Property type unknown")
        return instance as T
    }

    inner class Optional() {
        private var isEmpty = false
        operator fun <T> getValue(thisRef: Any?, property: KProperty<*>): T? {
            if (isEmpty) return null
            return try { this@Context.getValue(thisRef, property) } catch (_: Exception) { isEmpty = true; null }
        }
    }

    companion object {
        private val ctx = ThreadLocal.withInitial { mutableMapOf<KClassifier, Any>() }
        fun findInContext(classifier: KClassifier): Any = ctx.get()[classifier] ?: throw ClassNotFoundException(classifier.toString())
        fun apply(context: Set<Any>) {
            ctx.get().putAll(context.associateBy { (if (it is Classifier<*>) it.classifier else it::class) })
            context.map { module -> module::class.declaredMemberProperties.filter { d -> context.any { (d.returnType.classifier as KClass<*>).isInstance(it) } }.map { it.apply { isAccessible = true }.getter.call(module) } }
        }
        inline fun <reified T : Any> get(): T = findInContext(T::class) as T
        fun clear() = ctx.remove()
    }
}