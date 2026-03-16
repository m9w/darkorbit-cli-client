package com.github.m9w.metaplugins.proxy

import com.github.m9w.feature.Classifier

interface ProxyPool : Classifier<ProxyPool>  {
    fun addProxy(proxy: Proxy)
    fun removeProxy(address: Proxy)
    fun getProxy(): Proxy
    fun releaseProxy(proxy: Proxy) {}
    fun degradationReport(address: Proxy?): Boolean = false
    fun ipRestricted(address: Proxy?) {}
}