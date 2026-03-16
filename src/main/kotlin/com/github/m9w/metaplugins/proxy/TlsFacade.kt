package com.github.m9w.metaplugins.proxy

import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import kotlinx.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousByteChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult.HandshakeStatus.*
import javax.net.ssl.SSLEngineResult.Status.*

class TlsFacade(private val channel: AsynchronousSocketChannel, private val engine: SSLEngine) : AsynchronousByteChannel {
    private val alloc = PooledByteBufAllocator.DEFAULT
    private val session get() = engine.session
    private val netIn: ByteBuf = alloc.directBuffer(session.packetBufferSize * 2)
    private val appIn: ByteBuf = alloc.heapBuffer(session.applicationBufferSize * 2)
    private val ioLock = Any()
    private val readPending = AtomicBoolean(false)
    private val writePending = AtomicBoolean(false)
    @Volatile private var inboundClosed = false
    @Volatile private var outboundClosed = false

    private enum class UnwrapResult { DONE, NEED_NET_DATA, NEED_WRAP }

    init {
        engine.beginHandshake()
        val scratch = alloc.heapBuffer(session.applicationBufferSize)
        try {
            while (true) when (engine.handshakeStatus) {
                NEED_WRAP -> synchronized(ioLock) { netWriteEmpty() }
                NEED_UNWRAP, NEED_UNWRAP_AGAIN -> netReadBlocking(scratch)
                NEED_TASK -> runTasksLoop()
                FINISHED, NOT_HANDSHAKING -> break
            }
        } finally {
            scratch.release()
        }
    }

    private fun runTasksLoop() {
        while (true) engine.delegatedTask?.run() ?: break
    }

    private fun netWriteEmpty() {
        val pkt = alloc.directBuffer(session.packetBufferSize)
        try {
            while (true) {
                pkt.clear()
                val nio = pkt.internalNioBuffer(0, pkt.capacity())
                val r = engine.wrap(EMPTY, nio)
                pkt.writerIndex(r.bytesProduced())
                if (pkt.isReadable) flushToSocketBlocking(pkt)
                when (r.status!!) {
                    OK -> if (engine.handshakeStatus != NEED_WRAP) break
                    BUFFER_OVERFLOW -> pkt.capacity(pkt.capacity() * 2)
                    CLOSED, BUFFER_UNDERFLOW -> break
                }
            }
        } finally {
            pkt.release()
        }
    }

    private fun flushToSocketBlocking(buf: ByteBuf) {
        while (buf.isReadable) {
            val nio = buf.nioBuffer()
            val n = channel.write(nio).get()
            if (n < 0) throw IOException("socket closed")
            buf.skipBytes(n)
        }
    }

    private fun netReadBlocking(dst: ByteBuf) {
        while (true) {
            if (!netIn.isReadable) readFromSocketBlocking()
            if (inboundClosed) return
            when (synchronized(ioLock) { unwrapOnce(dst) }) {
                UnwrapResult.DONE -> return
                UnwrapResult.NEED_WRAP -> return // Any other action prevents establish connection
                UnwrapResult.NEED_NET_DATA -> readFromSocketBlocking()
            }
        }
    }

    private fun readFromSocketBlocking() {
        netIn.discardSomeReadBytes()
        netIn.ensureWritable(session.packetBufferSize)
        val nio = netIn.internalNioBuffer(netIn.writerIndex(), netIn.writableBytes())
        val n = channel.read(nio).get()
        if (n < 0) { inboundClosed = true; return }
        netIn.writerIndex(netIn.writerIndex() + n)
    }

    private fun unwrapOnce(dst: ByteBuf): UnwrapResult {
        while (true) {
            if (!netIn.isReadable) return UnwrapResult.NEED_NET_DATA
            val netNio = netIn.internalNioBuffer(netIn.readerIndex(), netIn.readableBytes())
            val initPos = netNio.position()
            dst.ensureWritable(session.applicationBufferSize)
            val appNio = dst.internalNioBuffer(dst.writerIndex(), dst.writableBytes())
            val r = engine.unwrap(netNio, appNio)
            val consumed = netNio.position() - initPos
            consumed.takeIf { it > 0 } ?.let(netIn::skipBytes)
            if (r.bytesProduced() > 0) dst.writerIndex(dst.writerIndex() + r.bytesProduced())
            if (r.handshakeStatus == NEED_TASK) runTasksLoop()
            when (r.status!!) {
                OK -> {
                    if (r.handshakeStatus == NEED_WRAP) return UnwrapResult.NEED_WRAP
                    if (r.bytesProduced() > 0) return UnwrapResult.DONE
                    if (consumed == 0 && r.bytesProduced() == 0) return UnwrapResult.NEED_NET_DATA
                    if (!netIn.isReadable) return UnwrapResult.NEED_NET_DATA
                }
                BUFFER_UNDERFLOW -> return UnwrapResult.NEED_NET_DATA
                BUFFER_OVERFLOW -> dst.ensureWritable(session.applicationBufferSize * 2)
                CLOSED -> { inboundClosed = true; return UnwrapResult.DONE }
            }
        }
    }

    override fun read(dst: ByteBuffer): Future<Int?> = SettableFuture<Int?>().apply {
        read(dst, null, object : CompletionHandler<Int?, Nothing?> {
            override fun completed(result: Int?, attachment: Nothing?) = set(result)
            override fun failed(exc: Throwable, attachment: Nothing?) = setException(exc)
        })
    }

    override fun <A> read(dst: ByteBuffer, attachment: A?, handler: CompletionHandler<Int?, in A>?) {
        check(readPending.compareAndSet(false, true))
        asyncRead(dst, attachment, handler)
    }

    override fun write(src: ByteBuffer): Future<Int?> = SettableFuture<Int?>().apply {
        write(src, null, object : CompletionHandler<Int?, Nothing?> {
            override fun completed(result: Int?, attachment: Nothing?) = set(result)
            override fun failed(exc: Throwable, attachment: Nothing?) = setException(exc)
        })
    }

    override fun <A> write(src: ByteBuffer, attachment: A?, handler: CompletionHandler<Int?, in A>?) {
        check(writePending.compareAndSet(false, true))
        asyncWrite(src, attachment, handler)
    }

    private fun <A> asyncRead(dst: ByteBuffer, attachment: A?, handler: CompletionHandler<Int?, in A>?) {
        val cached = synchronized(ioLock) { if (appIn.isReadable) drainAppIn(dst) else null }
        if (cached != null) {
            readPending.set(false)
            handler?.completed(cached, attachment)
            return
        }
        readNetworkAsync(dst, attachment, handler)
    }

    private fun <A> readNetworkAsync(dst: ByteBuffer, attachment: A?, handler: CompletionHandler<Int?, in A>?) {
        val result = synchronized(ioLock) {
            if (netIn.isReadable) unwrapOnce(appIn) else UnwrapResult.NEED_NET_DATA
        }
        when (result) {
            UnwrapResult.DONE -> {
                val n = synchronized(ioLock) { drainAppIn(dst) }
                readPending.set(false)
                handler?.completed(n, attachment)
            }
            UnwrapResult.NEED_NET_DATA -> fetchNetDataAsync(dst, attachment, handler)
            UnwrapResult.NEED_WRAP -> wrapThenReadAsync(dst, attachment, handler)
        }
    }

    private fun <A> fetchNetDataAsync(dst: ByteBuffer, attachment: A?, handler: CompletionHandler<Int?, in A>?) {
        val nio = synchronized(ioLock) {
            netIn.discardSomeReadBytes()
            netIn.ensureWritable(session.packetBufferSize)
            netIn.internalNioBuffer(netIn.writerIndex(), netIn.writableBytes())
        }
        channel.read(nio, attachment, object : CompletionHandler<Int, A?> {
            override fun completed(bytesRead: Int, att: A?) {
                if (bytesRead < 0) {
                    inboundClosed = true
                    readPending.set(false)
                    handler?.completed(-1, att)
                    return
                }
                synchronized(ioLock) { netIn.writerIndex(netIn.writerIndex() + bytesRead) }
                readNetworkAsync(dst, att, handler)
            }
            override fun failed(exc: Throwable, att: A?) {
                readPending.set(false)
                handler?.failed(exc, att)
            }
        })
    }

    private fun <A> wrapThenReadAsync(dst: ByteBuffer, attachment: A?, handler: CompletionHandler<Int?, in A>?) {
        val pkt = alloc.directBuffer(session.packetBufferSize)
        val success = try {
            synchronized(ioLock) {
                while (true) {
                    pkt.ensureWritable(session.packetBufferSize)
                    val nio = pkt.internalNioBuffer(pkt.writerIndex(), pkt.writableBytes())
                    val r = engine.wrap(EMPTY, nio)
                    pkt.writerIndex(pkt.writerIndex() + r.bytesProduced())
                    when (r.status!!) {
                        OK -> if (engine.handshakeStatus != NEED_WRAP) break
                        BUFFER_OVERFLOW -> pkt.ensureWritable(session.packetBufferSize * 2)
                        CLOSED, BUFFER_UNDERFLOW -> break
                    }
                }
            }
            true
        } catch (t: Throwable) {
            pkt.release()
            readPending.set(false)
            handler?.failed(t, attachment)
            false
        }
        if (!success) return
        flushToSocketAsync(pkt,
            onError = { readPending.set(false); handler?.failed(it, attachment) },
            onDone = { readNetworkAsync(dst, attachment, handler) }
        )
    }

    private fun <A> asyncWrite(src: ByteBuffer, attachment: A?, handler: CompletionHandler<Int?, in A>?) {
        val pkt = alloc.directBuffer(session.packetBufferSize)
        val consumed: Int
        try {
            synchronized(ioLock) {
                var total = 0
                while (true) {
                    pkt.ensureWritable(session.packetBufferSize)
                    val nio = pkt.internalNioBuffer(pkt.writerIndex(), pkt.writableBytes())
                    val r = engine.wrap(src, nio)
                    total += r.bytesConsumed()
                    pkt.writerIndex(pkt.writerIndex() + r.bytesProduced())
                    when (r.status!!) {
                        OK -> if (!src.hasRemaining()) break
                        BUFFER_OVERFLOW -> pkt.ensureWritable(session.packetBufferSize * 2)
                        CLOSED, BUFFER_UNDERFLOW -> break
                    }
                }
                consumed = total
            }
        } catch (t: Throwable) {
            pkt.release()
            writePending.set(false)
            handler?.failed(t, attachment)
            return
        }
        flushToSocketAsync(pkt,
            onError = { writePending.set(false); handler?.failed(it, attachment) },
            onDone = { writePending.set(false); handler?.completed(consumed, attachment) }
        )
    }

    private fun flushToSocketAsync(pkt: ByteBuf, onError: (Throwable) -> Unit, onDone: () -> Unit) {
        if (!pkt.isReadable) {
            pkt.release()
            onDone()
            return
        }
        val nio = pkt.nioBuffer()
        channel.write(nio, Unit, object : CompletionHandler<Int, Unit> {
            override fun completed(bytes: Int, att: Unit) {
                if (bytes < 0) {
                    pkt.release()
                    onError(IOException("socket closed"))
                    return
                }
                pkt.skipBytes(bytes)
                flushToSocketAsync(pkt, onError, onDone)
            }
            override fun failed(exc: Throwable, att: Unit) {
                pkt.release()
                onError(exc)
            }
        })
    }

    private fun drainAppIn(dst: ByteBuffer): Int {
        val n = minOf(appIn.readableBytes(), dst.remaining())
        if (n == 0) return if (inboundClosed && !appIn.isReadable) -1 else 0
        val slice = dst.duplicate().also { it.limit(dst.position() + n) }
        appIn.readBytes(slice)
        dst.position(dst.position() + n)
        return n
    }

    override fun isOpen(): Boolean = channel.isOpen && !outboundClosed

    override fun close() {
        synchronized(ioLock) {
            if (!outboundClosed) {
                engine.closeOutbound()
                runCatching { netWriteEmpty() }
                outboundClosed = true
            }
        }
        netIn.release()
        appIn.release()
        channel.close()
    }

    private class SettableFuture<T> : FutureTask<T>({ null }) {
        public override fun set(v: T) = super.set(v)
        public override fun setException(t: Throwable) = super.setException(t)
    }

    companion object {
        private val EMPTY = ByteBuffer.allocate(0)
    }
}