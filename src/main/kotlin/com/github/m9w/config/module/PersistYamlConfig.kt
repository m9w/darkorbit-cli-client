package com.github.m9w.config.module

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.github.m9w.config.entity.ConfigNode.Companion.decode
import com.github.m9w.config.entity.ConfigNode.Companion.encode
import com.github.m9w.feature.annotations.Repeat
import com.github.m9w.util.isTimeout
import java.io.File
import java.util.SortedSet
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.reflect.KType


object PersistYamlConfig : HashMapConfig() {
    private val cfgFile = File(System.getProperty("config_file") ?: "config.yaml")
    private val factory = YAMLFactory()
    private val mapper = ObjectMapper(factory)
    private val mutex = ReentrantLock()
    private var lastChange: Long = 0
    private var lastSave: Long = 0
    private var snapshot: MutableMap<String, Any>? = null
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val monitorInstance = Object()

    private fun <T> ReentrantLock.atomic(block: () -> T): T = lock().run { runCatching { block() }.also { unlock() }.getOrThrow() }

    override fun <T> writeProperty(key: String, type: KType, value: T): T? {
        return super.writeProperty(key, type, value).also {
            if (!key.startsWith("!")) lastChange = System.currentTimeMillis()
        }
    }

    init {
        if (cfgFile.isFile && cfgFile.exists()) load()
        Runtime.getRuntime().addShutdownHook(Thread { save() })
    }

    fun load() {
        val temp = HashMap<String, Any?>()
        val parser = factory.createParser(cfgFile)
        fun read(path: String, tree: JsonNode, isFirst: Boolean = false) {
            if (tree.isObject) {
                if (!isFirst) temp[path] = tree.fieldNames().asSequence().toSet()
                tree.fields().asSequence().forEach { (key, tree) -> read("$path/${key.encode}", tree) }
            } else if (tree.isArray) {
                if (!isFirst) temp[path] = (0 until tree.size()).toSortedSet()
                (0 until tree.size()).forEach { read("$path/$it", tree.get(it)) }
            } else if (tree.isTextual) {
                temp[path] = tree.textValue()
            } else if (tree.isBoolean) {
                temp[path] = tree.booleanValue()
            } else if (tree.isNumber) {
                temp[path] = if (tree.isDouble || tree.isFloat) tree.doubleValue() else tree.longValue()
            } else if (tree.isNull) {
                temp[path] = Unit
            } else println(tree)
        }

        while (parser.nextToken() != null)
            if (parser.currentToken().isStructStart)
                read((parser.typeId ?: continue).replace('=', '/'), mapper.readTree(parser), true)

        map.clear()
        map.putAll(temp)
    }

    @Suppress("UNCHECKED_CAST")
    fun save() {
        mutex.atomic {
            val snapshot = snapshot ?: createSnapshot()
            val result = mutableMapOf<String, Any?>()

            snapshot.entries
                .filter { it.value !is Set<*> }
                .forEach { (path, value) ->
                    var cursor: MutableMap<String, Any?> = result
                    val path = path.split("/").map { it.decode }
                    path.dropLast(1).forEach { key ->
                        cursor = cursor.computeIfAbsent(key) { sortedMapOf<String, Any>() } as MutableMap<String, Any?>
                    }
                    cursor[path.last()] = if (value == Unit) null else value
                }

            snapshot.entries
                .filter { it.value is SortedSet<*> && (it.value as Set<Any?>).isNotEmpty() && (it.value as Set<Any?>).first()!!::class == Int::class }
                .associate { it.key to it.value as Set<Int> }
                .keys
                .groupBy { key -> key.count { it == '/' } }
                .toSortedMap(reverseOrder())
                .values
                .flatten()
                .forEach { string ->
                    val path = string.split("/").map { it.decode }
                    var cursor: MutableMap<String, Any?> = result
                    path.dropLast(1).forEach { key -> cursor = cursor[key] as MutableMap<String, Any?> }
                    cursor[path.last()] =
                        (cursor[path.last()] as MutableMap<String, Any?>).mapKeys { it.key.toInt() }.values.toList()
                }

            val staticSection =
                result["STATIC"]?.let { mapper.writeValueAsString(it).replaceFirst("---", "--- !STATIC") }
                    ?.let { listOf(it) } ?: emptyList()

            val accountSection = (result["ACCOUNT"] as? MutableMap<String, Any>?)?.map { (k, v) ->
                mapper.writeValueAsString(v).replaceFirst("---", "--- !ACCOUNT=$k")
            } ?: emptyList()

            val configSection = (result["CONFIG"] as? MutableMap<String, Any>?)?.map { (k, v) ->
                mapper.writeValueAsString(v).replaceFirst("---", "--- !CONFIG=$k")
            } ?: emptyList()

            cfgFile.writeText((staticSection + accountSection + configSection).joinToString("\n"))
            lastSave = System.currentTimeMillis()
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun createSnapshot(): MutableMap<String, Any> {
        return map.mapValues { if (it.value is List<*>) (it.value as Set<Comparable<Any>>).toSortedSet() else it.value }.toSortedMap()
    }

    @Repeat(10_000)
    private fun autosave() {
        if (isTimeout(lastSave, sec = 10) && mutex.tryLock()) try {
            if (snapshot != null || lastChange < lastSave) return
            snapshot = createSnapshot()
            synchronized(monitorInstance) { monitorInstance.notifyAll() }
        } finally {
            snapshot = null
            mutex.unlock()
        }
    }

    @Suppress("unused")
    private val thread: Thread = thread(isDaemon = true, name = "Background config persisting thread") {
        while (true) try {
            synchronized(monitorInstance) { monitorInstance.wait() }
            save()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}