package com.github.m9w.metaplugins.proxy

import java.lang.System.currentTimeMillis
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

object ProxyPool {
    private const val DEGRADATION_THRESHOLD: Int = 10
    private val proxies: MutableMap<InetSocketAddress, Metadata> = ConcurrentHashMap()

    init {
        System.getenv("proxy_list").split(",|;|\\s".toRegex()).mapNotNull { proxy ->
            proxy.takeIf {it.isNotEmpty()} ?.split(":")
                ?.let{ InetSocketAddress(it[0], it[1].toInt())}
                ?.let(::addProxy)
        }
    }

    fun addProxy(address: InetSocketAddress) {
        proxies.put(address, Metadata())
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
        return proxies.filter { it.value.connections < 5 && it.value.degradationLevel < DEGRADATION_THRESHOLD }
            .minByOrNull { it.value.connections * 100 + it.value.degradationLevel }
            ?.also { it.value.connections++ }
            ?.key ?: throw RuntimeException("No proxy available")
    }

    fun releaseProxy(address: InetSocketAddress) {
        proxies[address]?.run { connections-- }
    }

    fun degradationReport(address: InetSocketAddress?): Boolean = proxies[address]?.run {
        degradationLevel++
        decreaseAfter = currentTimeMillis() + 60_000
        if (degradationLevel >= DEGRADATION_THRESHOLD) {
            connections--
            true
        } else false
    } ?: false

    fun ipRestricted(address: InetSocketAddress?): Unit = proxies[address]?.run {
        degradationLevel = 10
        decreaseAfter = currentTimeMillis() + 600_000
        connections--
        Unit
    } ?: Unit

    override fun toString(): String = proxies.toString()

    private data class Metadata(var connections: Int = 0, var degradationLevel: Int = 0, var decreaseAfter: Long = 0)
}