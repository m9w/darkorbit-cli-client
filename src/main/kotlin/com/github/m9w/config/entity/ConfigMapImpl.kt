package com.github.m9w.config.entity

import com.github.m9w.config.marker.ConfigInterface
import com.github.m9w.config.marker.DefaultValue
import com.github.m9w.config.view.ProxyView.toInterface


@Suppress("UNCHECKED_CAST")
class ConfigMapImpl<K, T> (val node: ConfigNode<K>, val value: T, isDefaultValue: Boolean, val block: ConfigNode<*>.(T) -> T) : ConfigMap<K, Any> {
    override val keys: MutableSet<K> get() = node.keys
    override val decorator: Any get() = toInterface(node.type)
    override val typeName: String = node.type.simpleName!!

    init {
        if (!isDefaultValue || !node.isExist) {
            val mapData = when (value) {
                is ConfigInterface -> value.internalRootMap as Map<K, Any>
                is Map<*, *> -> value as Map<K, Any>
                is Set<*> -> (value as Set<Any>).mapIndexed { index, any -> index to any }.associate { it } as Map<K, Any>
                is List<*> -> (value as List<Any>).mapIndexed { index, any -> index to any }.associate { it } as Map<K, Any>
                else -> emptyMap()
            }
            keys.minus(mapData.keys).forEach { cascadeRemove(node.branch(it), true) }
            putAll((if (isDefaultValue) mapData.mapValues { DefaultValue(it.value) } else mapData))
        }
    }

    override fun get(key: K): Any? {
        val nested = node.branch(key)
        nested.read()?.let { return it.unitToNull }
        return if (nested.isExist) nested.block(DefaultValue(value) as T)?.unitToNull else null
    }

    override fun set(key: K, value: Any?) {
        val nested = node.branch(key)
        if (value == null) cascadeRemove(nested)
        else nested.block(value as T).also { keys.add(key); }
    }

    private fun <T> cascadeRemove(node: ConfigNode<T>, full: Boolean = false) {
        keys.remove(node.field as K)
        if (full) node.remove() else node.write(Unit)
        node.remove()?.forEach { cascadeRemove(node.branch(it as T), true) }
    }

    override fun toString() = str

    companion object {
        val Any.unitToNull: Any? get() = if (this is Unit) null else this
    }
}