package com.github.m9w.protocol

import com.darkorbit.ProtocolPacket
import com.google.gson.Gson
import java.util.Collections
import kotlin.arrayOf
import kotlin.reflect.KClass

class ProtocolStruct() {
    private val gson: Gson = Gson()
    private val struct: MutableMap<Int, ProtocolClass> = HashMap()
    private val nameStruct: MutableMap<String, Int> = HashMap()
    var hash: String = ""; private set

    init {
        load(String(ProtocolStruct::class.java.classLoader.resources("darkorbit-protocol.json").findFirst().get().openStream().readAllBytes()))
    }

    fun load(structJson: String) {
        struct.clear()
        nameStruct.clear()
        val map = gson.fromJson<Map<String, Any>>(structJson, Map::class.java)
        hash = map["hash"].toString()
        val mapClasses = map.filter { it.key.contains('#') && it.key.split("#")[1].toInt() > 0 } as Map<String, Map<String, String>>
        mapClasses.forEach(::ProtocolClass)
        nameStruct.putAll(struct.entries.associate { it.value.name to it.key })
    }

    fun getClass(id: Int): ProtocolClass? = struct[id]

    fun getClass(name: String): ProtocolClass? = nameStruct[name]?.let { getClass(it) }

    inner class ProtocolClass private constructor(private val type: String, data: Map<String, String>, private val list: MutableList<ProtocolField>) : List<ProtocolClass.ProtocolField> by Collections.unmodifiableList(list) {
        constructor (type: String, data: Map<String, String>) : this(type, data, ArrayList())

        val name: String = type.split("#".toRegex())[0]
        val id: Int = type.split("#".toRegex())[1].toInt()

        init {
            struct[id] = this
            data.map { (k, v) -> ProtocolField(k, v) }.forEach { list.add(it) }
        }

        fun <T : ProtocolPacket> getClass() : KClass<T> = Class.forName("com.darkorbit.$name").kotlin as KClass<T>

        operator fun get(name: String) = this.find { it.name == name }

        inner class ProtocolField internal constructor(val name: String, val type: String) {
            private val pattern = Regex("(?:^|:)([A-z0-9]+)(?:$|/)")
            private val typeVal = pattern.find(type)?.groupValues?.get(1) ?: type
            val isList: Boolean = type.startsWith("List")
            val isEnum: Boolean = type.startsWith("Enum:")
            val enumConstants: Array<*> = (if(isEnum) try { Class.forName("com.darkorbit.$typeVal").enumConstants } catch (_: Exception) {arrayOf<Any>()} else arrayOf<Any>())
            val listLengthSize: Int = if (isList) if (type.startsWith("List2:")) 2 else 1 else 0
            val intLength: Int = when (typeVal) { "i8" -> 8; "i16" -> 16; "i32" -> 32; "i64" -> 64; else -> 0 }
            val isInt: Boolean = intLength != 0
            val isFloat: Boolean = typeVal == "Float"
            val isDouble: Boolean = typeVal == "Double"
            val isBoolean: Boolean = typeVal == "Boolean"
            val isString: Boolean = typeVal == "String"
            val intShift: Int = if ((isInt && type.contains("/"))) type.split("/".toRegex())[1].toInt() else 0

            fun getSubType(id: Int = 0): ProtocolField {
                if (isList) return ProtocolField("$name[$id]", type.split(":".toRegex(), limit = 2)[1])
                throw UnsupportedOperationException("Type $this is don't have subtype")
            }

            fun getClass() = this@ProtocolStruct.getClass(type)

            fun getEnum(i: Int): Enum<*> = if (i < enumConstants.size) enumConstants[i] as Enum<*> else DummyEnum.DUMMY

            override fun toString(): String = "[${this@ProtocolClass.type}.$name: $type]"
        }

        override fun toString(): String = type
    }

    enum class DummyEnum { DUMMY }
}
