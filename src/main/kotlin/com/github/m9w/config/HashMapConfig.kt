package com.github.m9w.config

import kotlin.reflect.KType

open class HashMapConfig : ConfigModule {
    protected val map = HashMap<String, Any?>()
    override var configName: String by accountConfig("DEFAULT")

    @Suppress("UNCHECKED_CAST")
    override fun <T> readProperty(key: String, type: KType): Result<T> {
        return if (map.containsKey(key)) Result.success(map[key]) as Result<T>
        else Result.failure(IllegalStateException("$key does not exist."))
    }

    override fun <T> writeProperty(key: String, type: KType, value: T) {
        map[key] = value
    }
}