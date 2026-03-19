package com.github.m9w.metaplugins.proxy

import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.putByteString
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
    fun write(buf: ByteBuffer) { while (buf.hasRemaining()) channel.write(buf).get().takeIf { it >= 0 } ?: throw RuntimeException("Channel closed on write") }
    fun read(len: Int): ByteArray = ByteBuffer.allocate(len).also { buf -> while (buf.hasRemaining()) channel.read(buf).get().takeIf { it >= 0 } ?: throw RuntimeException("Channel closed on read") }.array()
    fun readByte(): Int = read(1)[0].toInt() and 0xFF
    write(ByteBuffer.wrap(if (proxy.user != null) byteArrayOf(0x05, 0x02, 0x00, 0x02) else byteArrayOf(0x05, 0x01, 0x00)))
    val methodResp = read(2).takeIf { it[0] == 0x05.toByte() } ?: throw RuntimeException("Invalid SOCKS version")
    val method = methodResp[1].toInt() and 0xFF
    if (method == 0x02) {
        val user = proxy.user ?: throw RuntimeException("Proxy requires auth")
        val userBytes = user.toByteArray(Charsets.UTF_8)
        val passBytes = proxy.pass.toByteArray(Charsets.UTF_8)
        val buf = ByteBuffer.allocate(3 + userBytes.size + passBytes.size)
        buf.putByteString(ByteString(0x01, userBytes.size.toByte(), *userBytes, passBytes.size.toByte(), *passBytes))
        write(buf.flip())
        val authResp = read(2)
        if (authResp[1].toInt() != 0x00) throw RuntimeException("SOCKS5 auth failed")
    } else if (method != 0x00) throw RuntimeException("Unsupported auth method: $method")
    val hostBytes = dest.hostString.toByteArray(Charsets.UTF_8)
    val req = ByteBuffer.allocate(hostBytes.size + 7)
    req.putByteString(ByteString(0x05, 0x01, 0x00, 0x03, hostBytes.size.toByte(), *hostBytes))
    req.putShort(dest.port.toShort())
    write(req.flip())
    val header = read(4)
    if (header[0] != 0x05.toByte()) throw RuntimeException("Invalid response version")
    if (header[1] != 0x00.toByte()) throw RuntimeException("SOCKS5 connect failed: ${header[1]}")
    when (val atyp = header[3].toInt() and 0xFF) {
        0x01 -> read(4)
        0x03 -> read(readByte())
        0x04 -> read(16)
        else -> throw RuntimeException("Unknown ATYP: $atyp")
    }
    read(2)
}