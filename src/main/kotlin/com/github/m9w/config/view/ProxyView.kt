package com.github.m9w.config.view

import com.github.m9w.config.entity.ConfigMap
import com.github.m9w.config.marker.ConfigInterface
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.javaSetter

object ProxyView {
    @Suppress("UNCHECKED_CAST")
    fun ConfigMap<*, *>.toInterface(target: KClass<*>): Any {
        if (!target.java.isInterface || target.isSubclassOf(Map::class)) return this
        if (target.isSubclassOf(List::class)) return ListView(this as ConfigMap<Int, *>)
        if (target.isSubclassOf(Set::class)) return SetView(this as ConfigMap<Int, *>)
        val access = HashMap<Method, (Array<Any?>) -> Any?>().also { map ->
            target.memberProperties.forEach { prop ->
                map[prop.javaGetter!!] = { this[prop.name] }
                if (prop is KMutableProperty<*>) map[prop.javaSetter!!] = { (this as MutableMap<String, Any>).put(prop.name, it[0] as Any) }
            }
        }
        return Proxy.newProxyInstance(target.java.classLoader, arrayOf(target.java, ConfigInterface::class.java)) { _, method, args ->
            when (method.name) {
                "toString" -> toString()
                "getInternalRootMap" -> this
                else -> access[method]?.let { it(args ?: emptyArray()) }
            }
        } as Any
    }

    val KClass<*>.getAtomicWrapper: Any get() = AtomicInterface(this).toInterface(this)
    inline fun <reified R : Any> build(block: R.()->Unit = {}): R = (R::class.getAtomicWrapper as R).apply { block() }

    private class AtomicInterface(cls: KClass<*>) : ConfigMap<String, Any> {
        private val storage = ConcurrentHashMap<String, Any>()
        override val keys: MutableSet<String> get() = storage.keys
        override fun get(key: String): Any? = storage[key]
        override fun set(key: String, value: Any?) { if (value == null) storage.remove(key) else storage[key] = value }
        override val typeName: String = cls.simpleName!!
        override val decorator: Any = toInterface(cls)
        override fun toString() = str
    }
}


