package com.github.m9w.metaplugins.proxy

import java.lang.System.currentTimeMillis
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

object EnvProxyPool : ProxyPool {
    private val executor = Executors.newFixedThreadPool(32)
    private const val DEGRADATION_THRESHOLD: Int = 10
    private val proxies: MutableMap<Proxy, Metadata> by lazy { ConcurrentHashMap<Proxy, Metadata>().apply {
        (System.getenv("proxy_list") ?: "")
            .split(",|;|\\s".toRegex())
            .filter { it.isNotEmpty() }
            .map { Proxy(it) }
            .map { Callable { it.publicIp to it } }
            .let { executor.invokeAll(it) }
            .map { it.get() }
            .forEach { (ip, address) -> this[address] = Metadata(ip) }
    } }
    private val connections: MutableMap<String, AtomicInteger> = ConcurrentHashMap()

    override fun addProxy(proxy: Proxy) {
        proxies[proxy] = Metadata(proxy.publicIp)
    }

    override fun removeProxy(address: Proxy) {
        proxies.remove(address)
    }

    override fun getProxy(): Proxy {
        proxies.values.forEach {
            while (it.degradationLevel > 0 && it.decreaseAfter < currentTimeMillis()) {
                it.decreaseAfter += 60_000
                it.degradationLevel--
            }
        }
        return proxies.filter { it.value.connections.get() < 5 && it.value.degradationLevel < DEGRADATION_THRESHOLD }
            .minByOrNull { it.value.connections.get() * 100 + it.value.degradationLevel }
            ?.also { it.value.connections.incrementAndGet() }
            ?.key ?: throw RuntimeException("No available proxy")
    }

    override fun releaseProxy(proxy: Proxy) {
        proxies[proxy]?.run { connections.decrementAndGet() }
    }

    override fun degradationReport(address: Proxy?): Boolean = proxies[address]?.run {
        degradationLevel++
        decreaseAfter = currentTimeMillis() + 60_000
        if (degradationLevel >= DEGRADATION_THRESHOLD) {
            connections.decrementAndGet()
            true
        } else false
    } ?: false

    override fun ipRestricted(address: Proxy?): Unit = proxies[address]?.run {
        degradationLevel = 10
        decreaseAfter = currentTimeMillis() + 600_000
        connections.decrementAndGet()
        Unit
    } ?: Unit

    override fun toString(): String = proxies.toString()

    private data class Metadata(val ip: String, var degradationLevel: Int = 0, var decreaseAfter: Long = 0) {
        val connections: AtomicInteger = EnvProxyPool.connections.computeIfAbsent(ip) { AtomicInteger() }
    }
}