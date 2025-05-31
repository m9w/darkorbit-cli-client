package com.github.m9w.protocol

import com.darkorbit.ProtocolPacket
import java.lang.reflect.Proxy
import kotlin.collections.emptyList
import kotlin.reflect.KClass
import kotlin.reflect.cast

object Factory {
    private val classLoader = Factory::class.java.classLoader

    fun <T : ProtocolPacket> build(type: KClass<T>, data: Map<String, Any?> = HashMap()): T {
        val map = HashMap<String, Any?>(data)
        return type.cast(Proxy.newProxyInstance(classLoader, arrayOf(type.java, Metadata::class.java)) { proxy, method, args ->
            val methodName = method.name
            when {
                methodName.startsWith("get") && args.isNullOrEmpty() -> {
                    map["is" + methodName.removePrefix("get")] ?: map[methodName.removePrefix("get").replaceFirstChar { it.lowercaseChar() }] ?: default(method.returnType)
                }
                methodName.startsWith("set") && args?.size == 1 -> {
                    val type= method.parameterTypes.first()
                    if (type.isPrimitive && type.name == "boolean")
                        map["is" + methodName.removePrefix("set")] = args[0]
                    else
                        map[methodName.removePrefix("set").replaceFirstChar { it.lowercaseChar() }] = args[0]
                    null
                }
                methodName == "cls" -> type.simpleName
                methodName == "map" -> map
                methodName == "toString" -> type.simpleName + map.toString()
                else -> null
            }
        })
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
            MutableList::class.java -> emptyList<Any>()
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
