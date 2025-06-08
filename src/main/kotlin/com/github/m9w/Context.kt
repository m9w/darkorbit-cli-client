package com.github.m9w

import com.github.m9w.feature.Classifier
import kotlin.reflect.KClassifier
import kotlin.reflect.KProperty

val context get() = Context()

@Suppress("unchecked_cast")
class Context {
    private var isInit = false
    private lateinit var classifier: KClassifier
    val instance: Any by lazy { findInContext(classifier) }

    operator fun <T> getValue(thisRef: Any, property: KProperty<*>): T {
        if (!isInit) { isInit = true; classifier = property.returnType.classifier ?: throw NullPointerException() }
        return instance as T
    }

    companion object {
        private val ctx = ThreadLocal.withInitial { HashMap<String, Any>() }.get().computeIfAbsent("LOCAL_CONTEXT") { HashMap<KClassifier, Any>() } as MutableMap<KClassifier, Any>
        fun findInContext(classifier: KClassifier): Any = ctx[classifier] ?: ClassNotFoundException(classifier.toString())
        fun apply(context: Set<Any>) = ctx.putAll(context.associateBy { (if (it is Classifier<*>) it.classifier else it::class) })
    }
}