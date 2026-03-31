package com.github.m9w.config.property

import com.github.m9w.config.entity.ConfigNode
import com.github.m9w.config.marker.ConfigInterface
import kotlin.properties.PropertyDelegateProvider
import kotlin.reflect.KProperty

abstract class BasicFactory<T, R : BasicProperty<T>>(private val propertyConstructor: (root: ConfigNode.Root) -> R, val isPersist: Boolean) : PropertyDelegateProvider<Any, R> {
    abstract val prefix: Prefix

    private fun createPath(thisRef: Any, property: KProperty<*>): ConfigNode.Root = ConfigNode.Root(thisRef::class.qualifiedName!!, property, isPersist, prefix)

    override fun provideDelegate(thisRef: Any, property: KProperty<*>): R = propertyConstructor(createPath(thisRef, property))

    @Suppress("UNCHECKED_CAST")
    protected fun <T> T.deepClone(): T = when (this) {
        is ConfigInterface -> this.internalRootMap.deepClone()
        is Map<*, *> -> this.mapValues { it.value?.deepClone() }
        is Set<*> -> this.mapIndexed { k, v -> k to v.deepClone() }.associate { it }
        is List<*> -> this.mapIndexed { k, v -> k to v.deepClone() }.associate { it }
        else -> this
    } as T
}