package com.github.m9w.util

object ThreadLocal {
    private val threadLocal = java.lang.ThreadLocal.withInitial { HashMap<String, Any>() }

    operator fun <T> get(key: String) = threadLocal.get().get(key) as T
    operator fun set(key: String, value: Any) { threadLocal.get().put(key, value) }
}