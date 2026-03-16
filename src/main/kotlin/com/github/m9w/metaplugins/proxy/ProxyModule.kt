package com.github.m9w.metaplugins.proxy

import com.github.m9w.context.optionalContext
import com.github.m9w.feature.Classifier
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousByteChannel


class ProxyModule(private var proxy: Proxy? = null) : Classifier<ProxyModule> {
    val pool: ProxyPool? by optionalContext

    fun performConnect(dest: InetSocketAddress): AsynchronousByteChannel? {
        try {
            return (proxy ?: pool?.getProxy().also { proxy = it })?.openTunnel(dest)
        } catch (t: Throwable) {
            degradationReport()
            throw t
        }
    }

    fun degradationReport() = proxy?.let { if (pool?.degradationReport(it) == true) proxy = null }

    fun releaseProxy() = pool?.apply { proxy?.let(::releaseProxy)?.also { proxy = null } ?: Unit }

    fun ipRestricted() = pool?.ipRestricted(proxy)?.also { proxy = null }

    override fun toString(): String = proxy?.run { "$type://$host:$port" } ?: "DIRECT"
}