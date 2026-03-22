package com.github.m9w.context

import java.util.function.Consumer

@FunctionalInterface
interface ContextEvents {
    fun addedToContext(applyToAll: (Consumer<Any>) -> Unit) {}
    fun removedFromContext(applyToAll: (Consumer<Any>) -> Unit) {}
}