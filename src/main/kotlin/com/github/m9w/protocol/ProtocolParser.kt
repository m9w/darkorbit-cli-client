package com.github.m9w.protocol

import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import java.util.LinkedList

object ProtocolParser {
    private val struct: ProtocolStruct = ProtocolStruct()

    fun getClass(name: String) = struct.getClass(name)

    fun deserialize(buffer: ByteBuf): Any? {
        fun parseField(field: ProtocolStruct.ProtocolClass.ProtocolField): Any? {
            return when {
                field.isList -> {
                    val len = if (field.listLengthSize == 2) buffer.readUnsignedShort() else buffer.readUnsignedByte().toInt()
                    List(len) { parseField(field.getSubType(it)) }
                }
                field.isBoolean -> buffer.readByte() != 0.toByte()
                field.isEnum -> buffer.readUnsignedShort()
                field.isFloat -> buffer.readFloat()
                field.isDouble -> buffer.readDouble()
                field.isString -> {
                    val x= buffer.readBytes(buffer.readUnsignedShort())
                    try { x.toString(Charsets.UTF_8) } finally { x.release() }
                }
                field.isInt -> when (field.intLength) {
                    8 -> buffer.readByte().rotateRight(field.intShift)
                    16 -> buffer.readShort().rotateRight(field.intShift)
                    32 -> buffer.readInt().rotateRight(field.intShift)
                    64 -> buffer.readDouble().toLong()
                    else -> throw IllegalArgumentException("Invalid int length: ${field.intLength}")
                }
                else -> deserialize(buffer)
            }
        }
        val id = buffer.readUnsignedShort()
        if (id == 0) return null
        val cls = struct.getClass(id) ?: throw IllegalArgumentException("#$id not found")
        val map = cls.associate { it.name to parseField(it) }.toMutableMap()
        map.remove("super")?.let { map.putAll(Factory.getData(it)) }
        return Factory.build(cls.getClass(), map)
    }

    fun serialize(value: Any?, field: ProtocolStruct.ProtocolClass.ProtocolField? = null): ByteArray {
        return if (field == null && value == null) ByteArray(2)
        else if (field == null) {
            val data = Factory.getData(value!!)
            val classStack = LinkedList<ProtocolStruct.ProtocolClass>()
            var superType = Factory.getClass(value)?.also(classStack::addFirst)
            if (superType == null) throw RuntimeException("Class struct not found")
            while (superType != null) superType = superType["super"]?.getClass()?.also { classStack.addFirst(it) }
            val res = classStack.map { cls -> cls.filter { it.name != "super" }.map { serialize(data[it.name], it) } }
            byteBuffer(res.sumOf { it.sumOf(ByteArray::size) } + classStack.size*2) {
                classStack.reversed().forEach { putShort(it.id.toShort()) }
                res.forEach { it.forEach { put(it) } }
            }
        } else if (field.isList) {
            val list = (value as List<*>?)?.mapIndexed { i, v -> serialize(v, field.getSubType(i)) } ?: emptyList()
            byteBuffer(list.sumOf { it.size } + field.listLengthSize) {
                if (field.listLengthSize == 2) putShort(list.size.toShort()) else put(list.size.toByte())
                list.forEach(this::put)
            }
        } else if (field.isInt) byteBuffer(field.intLength/8) {
            when (field.intLength) {
                8 -> put((value as Byte?)?.rotateLeft(field.intShift) ?: 0)
                16 -> putShort((value as Short?)?.rotateLeft(field.intShift) ?: 0)
                32 -> putInt((value as Int?)?.rotateLeft(field.intShift) ?: 0)
                64 -> putDouble((value as Long?)?.toDouble() ?: 0.0)
            }
        }
        else if (field.isString) {
            val bytes = ((value as String?) ?: "").toByteArray()
            if (bytes.size > 0xFFFF) throw IllegalArgumentException("String is too long ${bytes.size} bytes")
            byteBuffer(bytes.size + 2) { putShort(bytes.size.toShort()).put(bytes) }
        }
        else if (field.isBoolean) { ByteArray(1).also { it[0] = if (value == true) 1 else 0 } }
        else if (field.isEnum) byteBuffer(2) { putShort(((value as Int?) ?: 0).toShort()) }
        else if (field.isFloat) byteBuffer(4) { putFloat(((value as Float?) ?: 0F)) }
        else if (field.isDouble) byteBuffer(8) { putDouble(((value as Double?) ?: 0.0)) }
        else serialize(value)
    }

    private fun byteBuffer(size: Int, action: ByteBuffer.() -> Unit): ByteArray {
        val result = ByteArray(size)
        val buf = ByteBuffer.wrap(result)
        action.invoke(buf)
        return result
    }
}
