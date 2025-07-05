package com.github.m9w.metaplugins.proxy

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class HttpProxyModule() : ProxyModule {
    private val InetSocketAddress.socket: String get() = "$hostName:$port"
    private var proxy: InetSocketAddress? = null

    override fun performConnect(channel: AsynchronousSocketChannel, address: InetSocketAddress) {
        try {
            if (proxy == null) proxy = ProxyPool.getProxy()
            channel.connect(proxy).get(5, TimeUnit.SECONDS)
            val connectRequest = "CONNECT ${address.socket} HTTP/1.1\r\nHost: ${address.socket}\r\nProxy-Connection: Keep-Alive\r\n\r\n"
            channel.write(ByteBuffer.wrap(connectRequest.toByteArray(StandardCharsets.UTF_8))).get()
            val responseBuf = ByteBuffer.allocate(1024)
            val rawResponse = StringBuilder()

            while (true) {
                val bytesRead = channel.read(responseBuf).get()
                if (bytesRead == -1) throw RuntimeException("Proxy closed connection")
                responseBuf.flip()
                rawResponse.append(StandardCharsets.UTF_8.decode(responseBuf))
                responseBuf.clear()
                if ("\r\n\r\n" in rawResponse) break
            }

            val statusLine = rawResponse.lineSequence().firstOrNull() ?: throw RuntimeException("Invalid proxy response: no status line")
            if (!statusLine.contains("200")) throw RuntimeException("Proxy CONNECT failed: $statusLine")
        } catch (t: Throwable) {
            degradationReport()
            throw t
        }
    }

    override fun degradationReport() {
        if (ProxyPool.degradationReport(proxy)) proxy = null
    }

    override fun ipRestricted() = ProxyPool.ipRestricted(proxy).also { proxy = null }
    override fun releaseProxy() = proxy?.let(ProxyPool::releaseProxy).also { proxy = null } ?: Unit
    override fun toString(): String = proxy?.run { "HTTP $socket" } ?: ""
}
