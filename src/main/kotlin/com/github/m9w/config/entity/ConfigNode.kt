package com.github.m9w.config.entity

import com.github.m9w.config.marker.ConfigInterface
import com.github.m9w.config.marker.DefaultValue
import com.github.m9w.config.module.ConfigModule
import com.github.m9w.config.property.Prefix
import com.github.m9w.context.context
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.KVariance
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.jvmErasure

open class ConfigNode<K> private constructor(val field: K, private val parent: ConfigNode<K>? = null) {
    private val root: Root = findRoot()

    open val kType: KType by lazy { parent?.calcType(field.toString())!! }

    val type: KClass<*> by lazy { kType.classifier as KClass<*> }

    val isComplex by lazy { !type.run { javaPrimitiveType != null || this == String::class || isEnum } || isCollection }

    val isCollection by lazy { type.isSubclassOf(Collection::class) }

    val isEnum by lazy { type.isSubclassOf(Enum::class) }

    val isMap by lazy { type.isSubclassOf(Map::class) }

    protected open val storage: ConfigModule get() = (this@ConfigNode.root as ConfigNode<*>).storage

    protected var cache: String? = null

    protected var cacheVersion = 0

    private var local: Any? = null

    private val branches = mutableMapOf<K, ConfigNode<*>>()

    fun branch(key: K): ConfigNode<*> = branches.computeIfAbsent(key) { ConfigNode(it, this) }

    fun read(): Any? = if (isComplex) local else storage.readProperty<Any?>(toString(), kType).getOrNull().enumDecoder.takeIf { it !is Set<*> }

    fun write(value: Any?) {
        if (isComplex) { local = value } else {
            storage.writeProperty(toString(), kType, value.enumEncoder ?: Unit)
        }
    }

    @Suppress("KotlinConstantConditions", "USELESS_CAST")
    fun remove(): Set<String>? {
        branches.remove(field)
        return storage.writeProperty(toString(), kType, null) as? Set<String>
    }

    private val keysetType = Set::class.createType(listOf(KTypeProjection(KVariance.INVARIANT, String::class.createType())))

    @Suppress("UNCHECKED_CAST")
    val keys: MutableSet<K> get() = storage.readProperty<K>(toString(), keysetType)
        .getOrElse { (if (isCollection) sortedSetOf() else mutableSetOf<K>())
            .also { storage.writeProperty(toString(), keysetType, it) } } as MutableSet<K>

    val isExist: Boolean get() = if (isComplex) storage.readProperty<Any>(toString(), keysetType).isSuccess else false

    protected open val calcNode get() = "$parent/${field.toString().encode}"

    override fun toString(): String {
        if (cache != null && this@ConfigNode.root.cacheVersion == cacheVersion) return cache!!
        cache = calcNode
        cacheVersion = this@ConfigNode.root.cacheVersion
        return cache!!
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> wrap(value: T): T {
        val isDefaultValue = value is DefaultValue<*>
        val value = if (isDefaultValue) value.value as T else value
        if (value !is ConfigInterface && value !is Map<*, *> && value !is Set<*> && value !is List<*>) return value.also { write(it) }
        return ConfigMapImpl<K, T>(this, value, isDefaultValue) { wrap(it) }.decorator
            .also { write(it) } as T
    }

    @Suppress("UNCHECKED_CAST")
    private val enumClass by lazy { kType.jvmErasure.java as Class<out Enum<*>> }

    private val Any?.enumDecoder get() = if (isEnum && this is String) runCatching { java.lang.Enum.valueOf(enumClass, this) }.getOrNull() else this
    private val Any?.enumEncoder get() = if (isEnum && this is Enum<*>) this.name else this

    private fun findRoot(): Root {
        var cursor = this
        while (cursor.parent != null) cursor = cursor.parent
        return cursor as Root
    }

    private fun ConfigNode<*>.calcType(key: String): KType = when {
        isMap -> kType.arguments[1].type!!
        isCollection -> kType.arguments[0].type!!
        else -> type.memberProperties.first { it.name == key }.returnType
    }


    class Root(val className: String, val prop: KProperty<*>, val isPersist: Boolean, val prefixType: Prefix) : ConfigNode<Any>(prop.name) {
        override val storage: ConfigModule by context

        override val kType: KType = prop.returnType
        override val calcNode get() = (if (isPersist) "" else "!") + "$prefix/${className.encode}/${field.toString().encode}"
        val prefix: String get() = prefixType.build()

        fun prefixUpdate() {
            cacheVersion++
            cache = null
        }
    }

    companion object {
        val String.encode: String get() = URLEncoder.encode(this, "UTF-8")
        val String.decode: String get() = URLDecoder.decode(this, "UTF-8")
    }
}