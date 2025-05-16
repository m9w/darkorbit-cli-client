package com.github.m9w.feature


interface Future<T> {
    val isDone: Boolean
    val hasError: Boolean
    fun getResult(): T
    fun resume(result: Any = Unit)
    fun interrupt(block: ()->Exception)
}