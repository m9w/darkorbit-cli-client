package com.github.m9w.protocol

import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import java.util.LinkedList
import java.util.function.Consumer

class StreamParser() {
    private lateinit var buffer: ByteBuf

    fun parse(b: ByteBuf): Any? {
        buffer = b
        val id = buffer.readUnsignedShort()
        if (id == 0) return null
        val cls = struct.getClass(id) ?: throw IllegalArgumentException("#$id not found")
        val map = cls.associate { it.name to parseField(it) }.toMutableMap()
        map.remove("super")?.let { map.putAll(Factory.getData(it)) }
        return Factory.build(cls.getClass(), map) {}
    }

    private fun parseField(field: ProtocolStruct.ProtocolClass.ProtocolField): Any? {
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
                try {
                    x.toString(Charsets.UTF_8)
                } finally {
                    x.release()
                }
            }
            field.isInt -> when (field.intLength) {
                8 -> buffer.readByte().rotateRight(field.intShift)
                16 -> buffer.readShort().rotateRight(field.intShift)
                32 -> buffer.readInt().rotateRight(field.intShift)
                64 -> buffer.readDouble().toLong()
                else -> throw IllegalArgumentException("Invalid int length: ${field.intLength}")
            }
            else -> parse(buffer)
        }
    }

    companion object {
        private val struct: ProtocolStruct = ProtocolStruct()
        fun serialize(value: Any?, field: ProtocolStruct.ProtocolClass.ProtocolField? = null): ByteArray {
            return if (field == null && value == null) ByteArray(2)
            else if (field == null) {
                val data = Factory.getData(value!!)
                val classStack = LinkedList<ProtocolStruct.ProtocolClass>()
                var superType = Factory.getClass(value)?.also(classStack::addFirst)
                if (superType == null) throw RuntimeException("Class struct not found")
                while (superType != null) superType = superType["super"]?.getClass()?.also { classStack.addFirst(it) }
                val res = classStack.map { cls -> cls.filter { it.name != "super" }.map { serialize(data[it.name], it) } }
                byteBuffer(res.sumOf { it.sumOf(ByteArray::size) } + classStack.size*2) { buf ->
                    classStack.reversed().forEach { buf.putShort(it.id.toShort()) }
                    res.forEach { it.forEach { buf.put(it) } }
                }
            } else if (field.isList) {
                val list = (value as List<*>?)?.mapIndexed { i, v -> serialize(v, field.getSubType(i)) } ?: emptyList()
                val length = list.sumOf { it.size }
                byteBuffer(length + field.listLengthSize) {
                    if (field.listLengthSize == 2) it.putShort(list.size.toShort()) else it.put(list.size.toByte())
                    list.forEach(it::put)
                }
            } else if (field.isInt) byteBuffer(field.intLength/8) {
                when (field.intLength) {
                    8 -> it.put((value as Byte?)?.rotateLeft(field.intShift) ?: 0)
                    16 -> it.putShort((value as Short?)?.rotateLeft(field.intShift) ?: 0)
                    32 -> it.putInt((value as Int?)?.rotateLeft(field.intShift) ?: 0)
                    64 -> it.putDouble((value as Long?)?.toDouble() ?: 0.0)
                }
            }
            else if (field.isString) {
                val bytes = ((value as String?) ?: "").toByteArray()
                if (bytes.size > 0xFFFF) throw IllegalArgumentException("String is too long ${bytes.size} bytes")
                byteBuffer(bytes.size + 2) {
                    it.putShort(bytes.size.toShort())
                    it.put(bytes)
                }
            }
            else if (field.isBoolean) { ByteArray(1).also { it[0] = if (value == true) 1 else 0 } }
            else if (field.isEnum) byteBuffer(2) { it.putShort(((value as Short?) ?: 0)) }
            else if (field.isFloat) byteBuffer(4) { it.putFloat(((value as Float?) ?: 0F)) }
            else if (field.isDouble) byteBuffer(8) { it.putDouble(((value as Double?) ?: 0.0)) }
            else serialize(value)
        }

        private fun byteBuffer(size: Int, action: Consumer<ByteBuffer>): ByteArray {
            val result = ByteArray(size)
            val buf = ByteBuffer.wrap(result)
            action.accept(buf)
            return result
        }
    }
}
