package com.github.m9w.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.m9w.feature.annotations.Repeat
import com.github.m9w.util.isTimeout
import io.ktor.util.date.*
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType

object PersistYamlConfig: HashMapConfig() {
    private val cfgFile = File(System.getProperty("config_file") ?: "config.yaml")
    private val persistMap = HashMap<String, String>()
    private val scheduled = HashMap<String, KType>()

    init {
        load()
        Runtime.getRuntime().addShutdownHook(Thread { persist() })
    }

    override fun <T> readProperty(key: String, type: KType): Result<T> {
        val result = super.readProperty<T>(key, type)
        if (result.isSuccess) return result
        if (persistMap.containsKey(key)) {
            return deserialize<T>(persistMap[key]!!, type).onSuccess { map[key] = it }
        }
        return result
    }

    override fun <T> writeProperty(key: String, type: KType, value: T) {
        super.writeProperty(key, type, value)
        if (!key.startsWith("!")) scheduled[key] = type
    }

    fun load() = cfgFile.takeIf { cfgFile.isFile }?.let { file ->
        var key = ""
        val accumulator = mutableListOf<String>()
        persistMap.clear()
        file.readText().lines().forEach { line ->
            if (line.startsWith("  ")) accumulator.add(line)
            else if (line.isNotEmpty()) {
                if (accumulator.isNotEmpty())
                    persistMap[key] = accumulator.joinToString("\n").also { accumulator.clear(); }
                val parts = line.split(":", limit = 2).also { key = it[0] }
                accumulator.add(parts[1])
            }
        }
        if (accumulator.isNotEmpty()) persistMap[key] = accumulator.joinToString(separator = "\n")
    }

    @Repeat(1_000)
    fun serializeScheduled() {
        if (mutex.tryLock()) try {
            val startWhen = getTimeMillis()
            val i = scheduled.iterator()
            while (i.hasNext() && !isTimeout(startWhen, ms = 100)) {
                val (key, type) = i.next()
                i.remove()
                persistMap[key] = serialize(map[key], type)
            }
        } finally {
            mutex.unlock()
        }
    }

    @Repeat(60_000)
    fun autosave() {
        if (lastSaveTimestamp + 60_000 < getTimeMillis()) save()
    }

    fun save() {
        saver.submit {
            mutex.atomic {
                persist()
            }
        }
    }

    private fun serialize(data: Any?, type: KType): String = data?.let(mapper::writeValueAsString)
        ?.removeSuffix("\n")
        ?.prependIndent("  ")
        ?.removePrefix("  ---") ?: ""

    private fun <T> deserialize(data: String, type: KType): Result<T> {
        if (data.isBlank() && type.isMarkedNullable) return Result.success(null as T)
        if (data.isBlank()) return Result.failure(IllegalStateException("$data is blank."))
        val javaType = mapper.typeFactory.constructType(type.javaType)
        return Result.success(mapper.readValue(data, javaType) as T)
    }


    private fun <T> ReentrantLock.atomic(block: () -> T): T = lock().run { runCatching { block() }.also { unlock() }.getOrThrow() }

    private val mutex = ReentrantLock()
    private val saver = Executors.newSingleThreadExecutor()
    private var lastSaveTimestamp: Long = 0
    private val mapper: ObjectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    private fun persist() {
        lastSaveTimestamp = getTimeMillis()
        persistMap.toSortedMap { string, string1 -> string1.compareTo(string) }.map { (k, v) -> "$k:$v" }
            .joinToString("\n").let(cfgFile::writeText)
    }
}
