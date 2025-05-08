package com.github.m9w.client.network

import com.github.m9w.protocol.Factory
import com.github.m9w.protocol.ProtocolParser
import com.github.m9w.util.timePrefix
import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import java.io.Closeable
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

class NetworkLayer(addr: InetSocketAddress, private val handler: NetworkLayer.(Any) -> Unit) : Closeable {
    private val client = AsynchronousSocketChannel.open()
    private val pool = PooledByteBufAllocator(true)
    private val lengthBuf: ByteBuf = pool.buffer(3)

    init {
        if (addr.port != 0) {
            client.connect(addr).get(5, TimeUnit.SECONDS)
            println("[${timePrefix}] Connected to $addr")
            onRead()
        }
    }

    private var isFirst = true
    fun <T : Any> send(type: KClass<T>, changes: T.() -> Unit) {
        val raw = ProtocolParser.serialize(Factory.build(type, changes = changes))
        val buffer = if (!isFirst) pool.buffer(raw.size + 3)
        else { isFirst = false; pool.buffer(raw.size + 4).also { it.writeByte(0) } }
        try {
            client.write(buffer.writeMedium(raw.size).writeBytes(raw).nioBuffer()).get()
        } finally {
            buffer.release()
        }
    }

    inline fun <reified T : Any> send(noinline changes: T.() -> Unit) = send(T::class, changes)

    private fun onRead() {
        lengthBuf.clear()
        val dst = lengthBuf.nioBuffer(0, 3)

        client.read(dst, lengthBuf, object : CompletionHandler<Int, ByteBuf> {
            override fun completed(bytesRead: Int, buf: ByteBuf) {
                if (bytesRead == -1) { close(); return }
                buf.writerIndex(buf.writerIndex() + bytesRead)
                if (buf.readableBytes() < 3) {
                    client.read(buf.nioBuffer(buf.writerIndex(), 3 - buf.readableBytes()), buf, this)
                    return
                }
                val length = buf.readUnsignedMedium()
                val payloadBuf = pool.buffer(length)
                readPayload(payloadBuf)
            }

            override fun failed(exc: Throwable, buf: ByteBuf) {
                exc.printStackTrace()
                close()
            }
        })
    }

    private fun readPayload(payloadBuf: ByteBuf) {
        val dst = payloadBuf.nioBuffer(payloadBuf.writerIndex(), payloadBuf.writableBytes())
        client.read(dst, payloadBuf, object : CompletionHandler<Int, ByteBuf> {
            override fun completed(bytesRead: Int, buf: ByteBuf) {
                if (bytesRead == -1) { close(); return }
                buf.writerIndex(buf.writerIndex() + bytesRead)
                if (buf.readableBytes() < buf.capacity()) {
                    client.read(buf.nioBuffer(buf.writerIndex(), buf.writableBytes()), buf, this)
                    return
                }

                try {
                    val parsed = ProtocolParser.deserialize(buf) ?: throw RuntimeException("Parsed object is null")
                    handler(this@NetworkLayer, parsed)
                } finally {
                    buf.release()
                }
                onRead()
            }

            override fun failed(exc: Throwable, buf: ByteBuf) {
                exc.printStackTrace()
                buf.release()
                close()
            }
        })
    }

    fun isAlive(): Boolean = try { client.isOpen && client.remoteAddress != null } catch (_: Exception) { false }

    override fun close() = try { lengthBuf.release(); client.close() } catch (_: Exception) {}
}
