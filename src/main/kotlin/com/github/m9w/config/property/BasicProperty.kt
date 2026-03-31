package com.github.m9w.config.property

import com.github.m9w.config.entity.ConfigMapImpl.Companion.unitToNull
import com.github.m9w.config.entity.ConfigNode
import com.github.m9w.config.entity.ConfigNode.Companion.encode
import com.github.m9w.config.marker.DefaultValue
import java.util.WeakHashMap
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@Suppress("UNCHECKED_CAST")
open class BasicProperty<T>(val root: ConfigNode.Root, private val default: T, externalUpdate: (String)->Unit = {}) : ReadWriteProperty<Any, T> {
    init {
        allAliveProperties[this] = externalUpdate
    }
    
    override fun getValue(thisRef: Any, property: KProperty<*>): T {
        root.read()?.let { return it.unitToNull as T }
        return (if (!root.isComplex) default?.also { it.unitToNull?.let(root::write) } ?: Unit
        else root.wrap(DefaultValue(default)) as Any) as T
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        if (root.isComplex) root.wrap(value ?: Unit)
        else root.write(value)
    }

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = this@BasicProperty.root.prop.hashCode()

    companion object {
        val allAliveProperties: WeakHashMap<BasicProperty<*>, (String)->Unit> = WeakHashMap()

        fun sendExternalEventUpdate(path: List<String>) {
            allAliveProperties.forEach { (prop, action) ->
                when (prop.root.prefixType) {
                    Prefix.STATIC ->
                        if (prop.root.prefix == path[0] && path[1] == prop.root.className && path[2] == prop.root.field)
                            path.subList(3, path.size).joinToString("/") { it.encode }
                        else null
                    Prefix.ACCOUNT,
                    Prefix.CONFIG_SET->
                        if (prop.root.prefix == "${path[0]}/${path[1]}" && path[2] == prop.root.className && path[3] == prop.root.field)
                            path.subList(4, path.size).joinToString("/") { it.encode }
                        else null
                }?.runCatching { action(this) }
            }
        }
    }
}