package com.github.m9w.config.module

import com.github.m9w.feature.Classifier
import kotlin.reflect.KType

interface ConfigModule : Classifier<ConfigModule> {
    var configName: String
    val monitor: ConfigMonitor?
    fun <T> readProperty(key: String, type: KType): Result<T>
    fun <T> writeProperty(key: String, type: KType, value: T): T?
}