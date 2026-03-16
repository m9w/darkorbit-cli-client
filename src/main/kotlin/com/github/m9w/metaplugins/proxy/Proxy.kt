package com.github.m9w.metaplugins.proxy

import java.net.InetSocketAddress

data class Proxy(val host: String,
                 val port: Int,
                 val user: String? = null,
                 val pass: String? = null,
                 val type: ProxyType = ProxyType.HTTP) {

    val socket get() = InetSocketAddress(host, port)

    override fun toString(): String {
        return "$type://$user:$pass@$host:$port"
    }

    enum class ProxyType {
        HTTP, HTTPS, SOCKS5;

        override fun toString(): String {
            return name.lowercase()
        }
    }
}