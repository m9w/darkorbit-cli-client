package com.github.m9w

import com.github.m9w.feature.Classifier
import kotlin.reflect.KClassifier
import kotlin.reflect.KProperty

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
        private val ctx = ThreadLocal.withInitial { HashMap<String, Any>() }.get().computeIfAbsent("LOCAL_CONTEXT") { HashMap<KClassifier, Any>() } as MutableMap<KClassifier, Any>
        fun findInContext(classifier: KClassifier): Any = ctx[classifier] ?: ClassNotFoundException(classifier.toString())
        fun apply(context: Set<Any>) = ctx.putAll(context.associateBy { (if (it is Classifier<*>) it.classifier else it::class) })
        inline fun <reified T : Any> get(): T = findInContext(T::class) as T
    }
}