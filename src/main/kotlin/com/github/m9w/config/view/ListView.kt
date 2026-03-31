package com.github.m9w.config.view

import com.github.m9w.config.entity.ConfigMap
import java.util.Spliterator

class ListView<T : Any>(internalRootMap: ConfigMap<Int, T>) : SetView<T>(internalRootMap), MutableList<T> {
    private fun convertIndex(index: Int): Int {
        return (internalRootMap.keys.toList().getOrNull(index) ?: this.index.getAndIncrement())
    }

    override fun add(element: T): Boolean {
        add(index.getAndIncrement(), element)
        return true
    }

    override fun add(index: Int, element: T) {
        internalRootMap.put(convertIndex(index), element)
    }

    override fun addAll(index: Int, elements: Collection<T>): Boolean {
        elements.forEachIndexed { i, t -> internalRootMap.put(convertIndex(i+index), t) }
        return true
    }

    override fun set(index: Int, element: T): T = internalRootMap.put(convertIndex(index), element) ?: throw NoSuchElementException()

    override fun removeAt(index: Int): T = internalRootMap.remove(convertIndex(index)) ?: throw NoSuchElementException()

    override fun listIterator(): MutableListIterator<T> {
        return toMutableList().listIterator()
    }

    override fun listIterator(index: Int): MutableListIterator<T> {
        return toMutableList().listIterator(index)
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> {
        return toMutableList().subList(fromIndex, toIndex)
    }

    override fun get(index: Int): T {
        return internalRootMap[convertIndex(index)] ?: throw NoSuchElementException()
    }

    override fun indexOf(element: T): Int {
        for (i in 0 until size) if (internalRootMap[convertIndex(i)] == element) return i
        return -1
    }

    override fun lastIndexOf(element: T): Int {
        for (i in size-1 downTo 0) if (internalRootMap[convertIndex(i)] == element) return i
        return -1
    }

    override fun spliterator(): Spliterator<T?> = super<MutableList>.spliterator()
}