package com.github.m9w.metaplugins.proxy

import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

object EnvProxyPool : ProxyPool() {
    private val executor = Executors.newFixedThreadPool(32)
    override val proxies: MutableMap<Proxy, Metadata> by lazy { ConcurrentHashMap<Proxy, Metadata>().apply {
        (System.getenv("proxy_list") ?: "")
            .split("[,;\\n\\t]".toRegex())
            .filter { it.isNotEmpty() }
            .map { Proxy(it) }
            .map { Callable { it.publicIp to it } }
            .let { executor.invokeAll(it) }
            .map { it.get() }
            .forEach { (ip, address) -> this[address] = Metadata(EnvProxyPool, ip) }
    } }

    override fun toString(): String = proxies.toString()
}