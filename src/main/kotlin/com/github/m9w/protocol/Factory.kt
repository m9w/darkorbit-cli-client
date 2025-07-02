package com.github.m9w.protocol

import com.darkorbit.ProtocolPacket
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.cast
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.javaSetter

object Factory {
    private val classLoader = Factory::class.java.classLoader
    private val packetTypes: MutableSet<KClass<*>> = HashSet()
    private val getters: MutableMap<Method, String> = HashMap()
    private val setters: MutableMap<Method, String> = HashMap()

    fun <T : ProtocolPacket> build(packetType: KClass<T>, data: Map<String, Any?> = HashMap()): T {
        if (!packetTypes.contains(packetType)) storeMethodMapping(packetType)
        val map = HashMap<String, Any?>(data)
        return packetType.cast(Proxy.newProxyInstance(classLoader, arrayOf(packetType.java, Metadata::class.java)) { proxy, method, args ->
            getters[method]?.let { return@newProxyInstance map[it] ?: default(method.returnType) }
            setters[method]?.let { map[it] = args[0]; return@newProxyInstance null; }
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
        packetType.memberProperties.forEach {
            getters.put(it.javaGetter!!, it.name)
            if (it is KMutableProperty<*>) setters.put(it.javaSetter!!, it.name)
        }
        packetTypes.add(packetType)
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

    fun getClassName(proxy: Any) = if (proxy is Metadata) proxy.cls() else ""

    fun getClass(proxy: Any) = if (proxy is Metadata) ProtocolParser.getClass(getClassName(proxy)) else null

    private interface Metadata {
        fun map(): Map<String, Any>
        fun cls(): String
    }
}
