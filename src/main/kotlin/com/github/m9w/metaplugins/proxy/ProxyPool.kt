package com.github.m9w.metaplugins.proxy

import com.github.m9w.feature.Classifier
import java.lang.System.currentTimeMillis
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

abstract class ProxyPool : Classifier<ProxyPool>  {
    protected open val DEGRADATION_THRESHOLD: Int = 10
    protected abstract val proxies: MutableMap<Proxy, Metadata>
    private val connections: MutableMap<String, AtomicInteger> = ConcurrentHashMap()

    open fun getProxy(): Proxy {
        proxies.values.forEach {
            while (it.degradationLevel > 0 && it.decreaseAfter < currentTimeMillis()) {
                it.decreaseAfter += 60_000
                it.degradationLevel--
            }
        }
        return proxies.filter { it.value.connections.get() < 5 && it.value.degradationLevel < DEGRADATION_THRESHOLD }
            .minByOrNull { it.value.connections.get() * 100 + it.value.degradationLevel }
            ?.also { it.value.connections.incrementAndGet() }
            ?.key ?: throw NoAvailableProxyException()
    }

    fun addProxy(proxy: Proxy) {
        proxies[proxy] = Metadata(this, proxy.publicIp)
    }

    fun removeProxy(proxy: Proxy) {
        proxies.remove(proxy)
    }

    fun releaseProxy(proxy: Proxy) {
        proxies[proxy]?.run { connections.decrementAndGet() }
    }

    fun degradationReport(address: Proxy?): Boolean = proxies[address]?.run {
        degradationLevel++
        decreaseAfter = currentTimeMillis() + 60_000
        if (degradationLevel >= DEGRADATION_THRESHOLD) {
            connections.decrementAndGet()
            true
        } else false
    } ?: false

    fun ipRestricted(address: Proxy?): Unit = proxies[address]?.run {
        degradationLevel = 10
        decreaseAfter = currentTimeMillis() + 600_000
        connections.decrementAndGet()
        Unit
    } ?: Unit

    protected data class Metadata(val pool: ProxyPool, val ip: String, var degradationLevel: Int = 0, var decreaseAfter: Long = 0) {
        val connections: AtomicInteger = pool.connections.computeIfAbsent(ip) { AtomicInteger() }
    }

    protected class NoAvailableProxyException : RuntimeException()
}