package com.github.m9w.client

import com.github.m9w.protocol.Factory
import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import com.github.m9w.protocol.StreamParser
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.LinkedList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

class GameClient(host: String = "localhost", port: Int = 9999, private val handler: GameClient.(Any) -> Unit) {
    private val selector = Selector.open()
    private val channel = SocketChannel.open().apply {
        configureBlocking(false)
        connect(InetSocketAddress(host, port))
        register(selector, SelectionKey.OP_CONNECT or SelectionKey.OP_READ or SelectionKey.OP_WRITE)
    }

    private val parser = StreamParser()
    private val pool = PooledByteBufAllocator(true)
    private val queue = LinkedList<ByteBuf>()

    init {
        channel.socket().tcpNoDelay = true
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({
            selector.select ({
                try {
                    if (it.isConnectable) onConnect(it)
                    if (it.isReadable) onRead(it)
                    if (it.isWritable) onWrite(it)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },15000)
        }, 0, 1, TimeUnit.NANOSECONDS)
    }

    fun <T : Any> send(type: KClass<T>, changes: T.() -> Unit) {
        val raw = StreamParser.serialize(Factory.build(type, changes = changes))
        queue.addLast(pool.buffer(raw.size + 3).apply { writeMedium(raw.size); writeBytes(raw) })
    }

    private fun onConnect(key: SelectionKey) {
        val ch = key.channel() as SocketChannel
        if (ch.isConnectionPending) ch.finishConnect()
    }

    private val lengthBuf: ByteBuf = pool.buffer(3)
    private var payload: ByteBuf? = null
    private fun onRead(key: SelectionKey) {
        val ch = key.channel() as SocketChannel
        if (payload == null) {
            lengthBuf.clear()
            val bytesRead = ch.read(lengthBuf.nioBuffer(0,3))
            if (bytesRead == -1) { ch.close(); return }
            lengthBuf.writerIndex(lengthBuf.writerIndex() + bytesRead)
            if (lengthBuf.readableBytes() < 3) return
            payload = pool.buffer(lengthBuf.readUnsignedMedium())
        }

        payload?.apply {
            val bytesRead = ch.read(nioBuffer(writerIndex(), writableBytes()))
            if (bytesRead == -1) { ch.close(); return@apply }
            writerIndex(writerIndex() + bytesRead)

            if (readableBytes() < capacity()) return
            try {
                handler(this@GameClient, parser.parse(this) ?: throw RuntimeException("Parsed object is null"))
            } finally {
                release()
                payload = null
            }
        }
    }

    private var isFirst = true
    private fun onWrite(key: SelectionKey) {
        val ch = key.channel() as SocketChannel
        while (queue.isNotEmpty()) {
            if (isFirst) { ch.write(ByteBuffer.allocate(1)); isFirst = false }
            val buf = queue.first()
            val written = ch.write(buf.nioBuffer())
            buf.readerIndex(buf.readerIndex() + written)
            if (!buf.isReadable) {
                buf.release()
                queue.removeFirst()
            } else break
        }
    }
}
