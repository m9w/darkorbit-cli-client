package com.github.m9w

import com.github.m9w.protocol.ProtocolParser
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

fun main(vararg args: String) {
    val executor = Executors.newFixedThreadPool(16)
    val server = AsynchronousServerSocketChannel.open()
    val port = if (args.isNotEmpty()) args[0].toInt() else 8080
    server.bind(InetSocketAddress(port))
    println("Proxy started on 127.0.0.1:$port")

    fun acceptNext() {
        server.accept(Unit, object : CompletionHandler<AsynchronousSocketChannel, Unit> {
            override fun completed(client: AsynchronousSocketChannel, x: Unit) {
                acceptNext()
                executor.submit { handleClient(client) }
            }

            override fun failed(exc: Throwable, x: Unit) {
                exc.printStackTrace()
                acceptNext()
            }
        })
    }
    acceptNext()
    Thread.currentThread().join()
}

fun handleClient(client: AsynchronousSocketChannel) {
    val buffer = ByteBuffer.allocate(8192)
    val request = StringBuilder()
    while (true) {
        buffer.clear()
        val read = client.read(buffer).get()
        if (read == -1) return
        buffer.flip()
        request.append(Charsets.UTF_8.decode(buffer))
        if (request.contains("\r\n\r\n")) break
    }

    val firstLine = request.lineSequence().first()
    val targetHostPort = when {
        firstLine.startsWith("CONNECT") -> firstLine.split(" ")[1]
        firstLine.startsWith("GET") || firstLine.startsWith("POST") -> {
            val hostHeader = request.lineSequence().firstOrNull { it.startsWith("Host:") } ?: return
            hostHeader.substringAfter("Host:").trim()
        }
        else -> return
    }

    val (host, portStr) = if (targetHostPort.contains(":")) {
        targetHostPort.split(":", limit = 2)
    } else {
        listOf(targetHostPort, "80")
    }
    val port = portStr.toInt()

    val remote = AsynchronousSocketChannel.open()
    remote.connect(InetSocketAddress(host, port)).get()

    if (firstLine.startsWith("CONNECT")) {
        client.write(ByteBuffer.wrap("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())).get()
    } else {
        remote.write(ByteBuffer.wrap(request.toString().toByteArray())).get()
    }

    val now = LocalDateTime.now()
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

    val filePath = Path.of("dump-${now.format(formatter)}.dump")
    println("Caught session and start dumping to $filePath")
    pipe(client, remote, true, filePath)
    pipe(remote, client, false, filePath)
}

fun pipe(source: AsynchronousSocketChannel, dest: AsynchronousSocketChannel, isClientToServer: Boolean, filePath: Path) {
    val buf = ByteBuffer.allocateDirect(10*1024*1024)//10 mb
    val byteBuf: ByteBuf = Unpooled.buffer(10*1024*1024)//10 mb
    var length = 0
    var isFirst = true

    fun loop() {
        buf.clear()
        source.read(buf, Unit, object : CompletionHandler<Int, Unit> {
            override fun completed(read: Int, x: Unit) {
                if (read == -1) {
                    source.close()
                    dest.close()
                    return
                }
                buf.flip()
                buf.mark()
                byteBuf.writeBytes(buf)
                if (isClientToServer && byteBuf.readableBytes() > 0 && isFirst) {
                    byteBuf.readByte()
                    isFirst = false
                }
                if (length == 0 && byteBuf.readableBytes() >= 3) length = byteBuf.readUnsignedMedium()

                while (length != 0 && byteBuf.readableBytes() >= length) {
                    if (isClientToServer) fromClient(filePath, byteBuf.readBytes(length))
                    else toClient(filePath, byteBuf.readBytes(length))
                    length = if (byteBuf.readableBytes() >= 3) byteBuf.readUnsignedMedium() else 0
                }

                if (byteBuf.readableBytes() == 0) byteBuf.clear()
                buf.reset()
                dest.write(buf, Unit, object : CompletionHandler<Int, Unit> {
                    override fun completed(i: Int, x: Unit) = loop()
                    override fun failed(exc: Throwable, x: Unit) {
                        exc.printStackTrace()
                        source.close()
                        dest.close()
                        byteBuf.release()
                    }
                })
            }

            override fun failed(exc: Throwable, x: Unit) {
                exc.printStackTrace()
                source.close()
                dest.close()
                byteBuf.release()
            }
        })
    }

    loop()
}

fun fromClient(filePath: Path, buf: ByteBuf) {
    try {
        printPacket(filePath, "<<[${System.currentTimeMillis()}]${ProtocolParser.deserialize(buf)}\n")
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        buf.release()
    }
}

fun toClient(filePath: Path, buf: ByteBuf) {
    try {
        printPacket(filePath, ">>[${System.currentTimeMillis()}]${ProtocolParser.deserialize(buf)}\n")
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        buf.release()
    }
}

fun printPacket(filePath: Path, s: String) {
    Files.writeString(filePath, s, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
}
