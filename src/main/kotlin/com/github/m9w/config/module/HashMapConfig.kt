package com.github.m9w.config.module

import com.github.m9w.config.accountConfig
import com.github.m9w.context.optionalContext
import kotlin.reflect.KType

@Suppress("UNCHECKED_CAST")
open class HashMapConfig : ConfigModule {
    protected val map = HashMap<String, Any?>()
    override val monitor: ConfigMonitor? by optionalContext
    override var configName: String by accountConfig("DEFAULT")

    override fun <T> readProperty(key: String, type: KType): Result<T> {
        return (if (map.containsKey(key)) Result.success(map[key]) as Result<T>
        else Result.failure(IllegalStateException("$key does not exist.")))
            .also { if (it.getOrNull() !is Set<*>) monitor?.configRead(key, it) }
    }

    override fun <T> writeProperty(key: String, type: KType, value: T): T? {
        val old = (if (value == null) map.remove(key) else map.put(key, value)) as T?
        monitor?.configWrite(key, old, value)
        return old
    }
}