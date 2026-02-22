package com.github.m9w.config

import com.github.m9w.feature.Classifier
import kotlin.reflect.KType

interface ConfigModule : Classifier<ConfigModule> {
    var configName: String
    fun <T> readProperty(key: String, type: KType): Result<T>
    fun <T> writeProperty(key: String, type: KType, value: T)
    fun hasUpdates(key: String): Boolean = false
}