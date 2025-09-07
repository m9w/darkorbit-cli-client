package com.github.m9w.context

import java.util.function.Consumer

@FunctionalInterface
interface ContextCreate {
    fun contextCreated(applyToAll: (Consumer<Any>) -> Unit)
}