package com.github.m9w.client.network

import com.darkorbit.ProtocolPacket
import com.github.m9w.protocol.ProtocolParser
import com.github.m9w.feature.timePrefix
import com.github.m9w.metaplugins.proxy.ProxyModule
import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import io.netty.buffer.Unpooled
import java.io.Closeable
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class NetworkLayer(address: InetSocketAddress = InetSocketAddress(0), proxyModule: ProxyModule? = null) : Closeable {
    private val client = AsynchronousSocketChannel.open()
    private var isDisconnected = AtomicBoolean(false)
    var onConnectHandler: () -> Unit = {}
    var onDisconnect: () -> Unit = {}
    var onPackageHandler: (ProtocolPacket)->Unit = {}

    init {
        if (address.port != 0) {
            proxyModule?.performConnect(client, address) ?: client.connect(address).get(5, TimeUnit.SECONDS)
            println("[$timePrefix] Connected to ${address.hostName}:${address.port}${if(proxyModule == null) "" else " over $proxyModule"}")
            onConnectHandler.invoke()
            onRead()
        }
    }

    private var isFirst = true
    fun send(packageObject: Any) {
        if (debug) println("<<$packageObject")
        val raw = ProtocolParser.serialize(packageObject)
        val buffer = if (!isFirst) pool.buffer(raw.size + 3)
        else { isFirst = false; pool.buffer(raw.size + 4).also { it.writeByte(0) } }
        try {
            client.write(buffer.writeMedium(raw.size).writeBytes(raw).nioBuffer()).get()
        } catch (_: Exception) {
            close()
        } finally {
            buffer.release()
        }
    }

    private fun onRead() {
        val lengthBuf: ByteBuf = Unpooled.buffer(3)
        val dst = lengthBuf.nioBuffer(0, 3)
        if (isAlive()) client.read(dst, lengthBuf, object : CompletionHandler<Int, ByteBuf> {
            override fun completed(bytesRead: Int, buf: ByteBuf) {
                if (bytesRead == -1) { close(); return }
                buf.writerIndex(buf.writerIndex() + bytesRead)
                if (buf.readableBytes() < 3) {
                    client.read(buf.nioBuffer(buf.writerIndex(), 3 - buf.readableBytes()), buf, this)
                    return
                }
                val length = buf.readUnsignedMedium()
                buf.release()
                val payloadBuf = pool.buffer(length)
                readPayload(payloadBuf)
            }

            override fun failed(exc: Throwable, buf: ByteBuf) {
                buf.release()
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
                    if (debug) println(">>$parsed")
                    onPackageHandler(parsed)
                } catch (_: Exception) {
                    close()
                } finally {
                    buf.release()
                }
                onRead()
            }

            override fun failed(exc: Throwable, buf: ByteBuf) {
                buf.release()
                close()
            }
        })
    }

    fun isAlive(): Boolean = try { client.isOpen && client.remoteAddress != null } catch (_: Exception) { false }

    override fun close() {
        try { client.close() } catch (_: Exception) {}
        if (!isDisconnected.getAndSet(true)) onDisconnect()
    }

    companion object {
        var debug = false
        private val pool = PooledByteBufAllocator(true)
        private val metric = pool.metric()
        val memoryUsage: Long get() = metric.usedDirectMemory()
    }
}
