package com.github.m9w.config.view

import com.github.m9w.config.entity.ConfigMap
import com.github.m9w.config.marker.ConfigInterface
import java.util.concurrent.atomic.AtomicInteger

open class SetView<T : Any>(override val internalRootMap: ConfigMap<Int, T>) : MutableSet<T>, ConfigInterface {
    protected val index = AtomicInteger((internalRootMap.keys.maxOrNull() ?: 0) + 1 )

    override fun iterator(): MutableIterator<T> = object : MutableIterator<T> {
        val keys = internalRootMap.keys.iterator()
        var currentKey = -1
        override fun hasNext(): Boolean = keys.hasNext()
        override fun next(): T {
            currentKey = keys.next()
            return internalRootMap[currentKey] ?: throw NoSuchElementException()
        }
        override fun remove() { internalRootMap.remove(currentKey) }
    }

    override fun add(element: T): Boolean {
        if (internalRootMap.containsValue(element)) return false
        internalRootMap.put(index.getAndIncrement(), element)
        return true
    }

    override fun remove(element: T): Boolean {
        return internalRootMap.entries.find { entry -> entry.value == element }?.let { internalRootMap.remove(it.key) != null } ?: false
    }

    override fun addAll(elements: Collection<T>): Boolean {
        return elements.map(::add).all { it }
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        return elements.map(::remove).all { it }
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        return internalRootMap.values.minus(elements.toSet()).also { removeAll(it) }.isEmpty()
    }

    override fun clear() {
        index.set(0)
        internalRootMap.clear()
    }

    override val size: Int get() = internalRootMap.size

    override fun isEmpty(): Boolean {
        return internalRootMap.isEmpty()
    }

    override fun contains(element: T): Boolean {
        return internalRootMap.containsValue(element)
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        return elements.all(::contains)
    }
}