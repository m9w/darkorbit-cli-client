package com.github.m9w.protocol

import com.darkorbit.ProtocolPacket
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.cast
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.javaSetter

object Factory {
    private val classLoader = Factory::class.java.classLoader
    private val packetTypes: MutableMap<KClass<*>, Unit> = ConcurrentHashMap()
    private val mapping: MutableMap<Method, (MutableMap<String, Any?>, Array<Any?>?)->Any?> = ConcurrentHashMap()

    fun <T : ProtocolPacket> build(packetType: KClass<T>, data: Map<String, Any?> = HashMap()): T {
        if (!packetTypes.containsKey(packetType)) storeMethodMapping(packetType)
        val map = HashMap<String, Any?>(data)
        return packetType.cast(Proxy.newProxyInstance(classLoader, arrayOf(packetType.java, Metadata::class.java)) { _, method, args ->
            mapping[method]?.let { return@newProxyInstance it.invoke(map, args) }
            when (method.name) {
                "cls" -> packetType.simpleName
                "map" -> map
                "toString" -> packetType.simpleName + map.toString()
                else -> null
            }
        })
    }

    inline fun <reified T : ProtocolPacket> build(block: T.()->Unit) = build(T::class).apply { block() }

    private fun storeMethodMapping(packetType: KClass<*>) {
        packetType.memberProperties.forEach { prop ->
            mapping[prop.javaGetter!!] = { map, _ -> map.computeIfAbsent(prop.name) { default(prop.javaGetter!!.returnType) } }
            if (prop is KMutableProperty<*>) mapping[prop.javaSetter!!] = { map, value -> map[prop.name] = value!![0] }
        }
        packetTypes[packetType] = Unit
    }

    private fun default(returnType: Class<*>): Any? {
        if (returnType.isEnum) return returnType.enumConstants[0]
        return when (returnType) {
            Boolean::class.java -> false
            Byte::class.java -> 0.toByte()
            Short::class.java -> 0.toShort()
            Int::class.java -> 0
            Long::class.java -> 0L
            Float::class.java -> 0F
            Double::class.java -> 0.0
            String::class.java -> ""
            MutableList::class.java -> mutableListOf<Any>()
            else -> build(returnType.kotlin as KClass<ProtocolPacket>)
        }
    }

    fun getData(proxy: Any): Map<String, Any> = if (proxy is Metadata) proxy.map() else emptyMap()

    val ProtocolPacket.className get() = (this as Metadata).cls()

    fun getClass(proxy: Any) = if (proxy is Metadata) ProtocolParser.getClass(proxy.className) else null

    private interface Metadata : ProtocolPacket {
        fun map(): Map<String, Any>
        fun cls(): String
    }
}
