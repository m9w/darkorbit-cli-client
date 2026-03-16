package com.github.m9w.metaplugins.proxy

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousByteChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.charset.StandardCharsets
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlin.io.encoding.Base64

enum class ProxyType(val defaultPort: Int,
                     val tunnel: (AsynchronousSocketChannel) -> AsynchronousByteChannel,
                     val establish: (AsynchronousByteChannel, Proxy, InetSocketAddress) -> Unit) {
    HTTP(80, ::transparentChannel, ::httpTunnel),
    HTTPS(433, ::tlsChannel, ::httpTunnel),
    SOCKS5(1080, ::transparentChannel, ::socks5Tunnel);

    override fun toString(): String = name.lowercase()
}

private val InetSocketAddress.socket: String get() = "$hostName:$port"

private val SSL_CTX: SSLContext = SSLContext.getInstance("TLS").apply {
    init(null, arrayOf(object : X509TrustManager {
        override fun checkClientTrusted(c: Array<X509Certificate>, a: String) = Unit
        override fun checkServerTrusted(c: Array<X509Certificate>, a: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }), null)
}

private val InetSocketAddress.firstRow get() = "CONNECT $socket HTTP/1.1\r\n"
private val InetSocketAddress.hostRow get() = "Host: $hostName\r\n"
private val Proxy.authRow get() = if(user == null) "" else "Proxy-Authorization: Basic " + Base64.encode("$user:$pass".toByteArray(StandardCharsets.UTF_8)) + "\r\n"
private const val connectionRow = "Proxy-Connection: Keep-Alive\r\n"

private fun transparentChannel(channel: AsynchronousSocketChannel): AsynchronousByteChannel = channel

private fun tlsChannel(channel: AsynchronousSocketChannel): AsynchronousByteChannel = TlsFacade(channel, SSL_CTX.createSSLEngine().apply { useClientMode = true })

private fun httpTunnel(channel: AsynchronousByteChannel, proxy: Proxy, dest: InetSocketAddress) {
    val connectRequest = "${dest.firstRow}${dest.hostRow}${proxy.authRow}$connectionRow\r\n"
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
    val statusLine = rawResponse.lineSequence().firstOrNull()
        ?: throw RuntimeException("Invalid proxy response: no status line")
    if (!statusLine.contains("200")) throw RuntimeException("Proxy CONNECT failed: $statusLine")
}

private fun socks5Tunnel(channel: AsynchronousByteChannel, proxy: Proxy, dest: InetSocketAddress) {
    TODO()
}