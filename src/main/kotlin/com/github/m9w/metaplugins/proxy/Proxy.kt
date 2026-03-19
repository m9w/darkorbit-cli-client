package com.github.m9w.metaplugins.proxy

import kotlinx.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousByteChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit


data class Proxy(val host: String, val port: Int, val user: String? = null, val pass: String = "", val type: ProxyType = ProxyType.HTTP) {
    constructor(proxy: Proxy) : this(proxy.host, proxy.port, proxy.user, proxy.pass, proxy.type)
    constructor(uri: String) : this(parse(uri))

    val socket = InetSocketAddress(host, port)
    val publicIp by lazy { this.requestPublicIp() }

    fun openTunnel(dest: InetSocketAddress): AsynchronousByteChannel {
        val channel = AsynchronousSocketChannel.open()
        channel.connect(socket).get(5, TimeUnit.SECONDS)
        val tunnel = type.tunnel(channel)
        type.establish(tunnel, this, dest)
        return tunnel
    }

    fun requestPublicIp(): String =
        this.openTunnel(InetSocketAddress("api.ipify.org", 80)).use { client ->
            try {
                client.write(ByteBuffer.wrap("GET / HTTP/1.1\r\nHost: api.ipify.org\r\nConnection: close\r\n\r\n".toByteArray())).get()
                val result = ByteBuffer.allocate(1024)
                val rawResponse = StringBuilder()
                while (true) {
                    val bytesRead = client.read(result).get()
                    if (bytesRead == -1) return "-"
                    result.flip()
                    rawResponse.append(StandardCharsets.UTF_8.decode(result))
                    result.clear()
                    if ("\r\n\r\n" in rawResponse) break
                }
                rawResponse.split("\r\n\r\n")[1].trim()
            } catch (_: IOException) {
                "-"
            }
        }

    override fun toString(): String = "$type://$user:$pass@$host:$port"

    companion object {
        private val pattern = Regex("""^(?i)(\w+)://(?:([^:@]+)(?::([^@]+))?@)?([^:@]+)(?::(\d+))?$""")

        fun parse(uri: String): Proxy {
            val match = pattern.matchEntire(uri) ?: throw IllegalArgumentException("Invalid proxy uri: $uri")
            val (typeStr, user, pass, host, portStr) = match.destructured
            val type = ProxyType.valueOf(typeStr.uppercase())
            return Proxy(host, portStr.takeIf { it.isNotEmpty() }?.toInt() ?: type.defaultPort, user.ifEmpty { null }, pass.ifEmpty { "" }, type)
        }
    }
}