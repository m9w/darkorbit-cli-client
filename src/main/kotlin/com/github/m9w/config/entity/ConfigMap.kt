package com.github.m9w.config.entity

import com.github.m9w.config.marker.ConfigInterface

interface ConfigMap<K, T: Any> : MutableMap<K, T>, ConfigInterface {
    override val values: MutableCollection<T> get() = keys.mapNotNull(::get).toMutableList()

    override val entries: MutableSet<MutableMap.MutableEntry<K, T>> get() = keys.associateWith { get(it)!! }.toMutableMap().entries

    override val size: Int get() = keys.size

    override fun put(key: K, value: T): T? = get(key).also { set(key, value) }

    override fun putAll(from: Map<out K, T>) = from.forEach(::set)

    override fun remove(key: K): T? = get(key).also { set(key, null) }

    override fun clear() = keys.forEach { set(it, null) }

    override fun isEmpty(): Boolean = keys.isEmpty()

    override fun containsKey(key: K): Boolean = keys.contains(key)

    override fun containsValue(value: T): Boolean = keys.any { get(it) == value }

    override operator fun get(key: K): T?

    operator fun set(key: K, value: T?)

    val typeName: String

    val str: String get() = "[$typeName]::" + keys.associateWith { get(it) }

    val decorator: Any

    override val internalRootMap: Map<K, Any?> get() = this
}