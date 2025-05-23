package com.github.m9w.feature.suspend

import com.github.m9w.feature.Future

interface SuspendFlow {
    fun <T> getFuture(): Future<T>
    fun <T> schedule(future: Future<T>)
}