package com.github.m9w.metaplugins.proxy

import com.github.m9w.metaplugins.proxy.HttpProxyModule.Companion.getRealIp
import java.lang.System.currentTimeMillis
import java.net.InetSocketAddress
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

object ProxyPool {
    private val executor = Executors.newFixedThreadPool(32)
    private const val DEGRADATION_THRESHOLD: Int = 10
    private val proxies: MutableMap<InetSocketAddress, Metadata> = ConcurrentHashMap()
    private val connections: MutableMap<String, AtomicInteger> = ConcurrentHashMap()

    init {
        System.getenv("proxy_list")
            .split(",|;|\\s".toRegex())
            .mapNotNull { it.takeIf {it.isNotEmpty()} ?.split(":") }
            .map { InetSocketAddress(it[0], it[1].toInt())}
            .map { Callable { getRealIp(it) to it } }
            .let { executor.invokeAll(it) }
            .map { it.get() }
            .forEach { (ip, address) -> proxies[address] = Metadata(ip) }
    }

    fun addProxy(address: InetSocketAddress) {
        proxies[address] = Metadata(getRealIp(address))
    }

    fun removeProxy(address: InetSocketAddress) {
        proxies.remove(address)
    }

    fun getProxy(): InetSocketAddress {
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

    fun releaseProxy(address: InetSocketAddress) {
        proxies[address]?.run { connections.decrementAndGet() }
    }

    fun degradationReport(address: InetSocketAddress?): Boolean = proxies[address]?.run {
        degradationLevel++
        decreaseAfter = currentTimeMillis() + 60_000
        if (degradationLevel >= DEGRADATION_THRESHOLD) {
            connections.decrementAndGet()
            true
        } else false
    } ?: false

    fun ipRestricted(address: InetSocketAddress?): Unit = proxies[address]?.run {
        degradationLevel = 10
        decreaseAfter = currentTimeMillis() + 600_000
        connections.decrementAndGet()
        Unit
    } ?: Unit

    override fun toString(): String = proxies.toString()

    private data class Metadata(val ip: String, var degradationLevel: Int = 0, var decreaseAfter: Long = 0) {
        val connections: AtomicInteger = ProxyPool.connections.computeIfAbsent(ip) { AtomicInteger() }
    }
}