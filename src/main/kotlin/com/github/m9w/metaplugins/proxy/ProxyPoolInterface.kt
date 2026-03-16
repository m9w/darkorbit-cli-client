package com.github.m9w.metaplugins.proxy

@FunctionalInterface
interface ProxyPoolInterface {
    fun getProxy(): Proxy
    fun releaseProxy(proxy: Proxy) {}
    fun degradationReport(address: Proxy?): Boolean = false
    fun ipRestricted(address: Proxy?) {}
}