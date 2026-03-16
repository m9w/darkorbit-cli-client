package com.github.m9w.metaplugins.proxy

import com.github.m9w.feature.Classifier
import java.io.Closeable
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousByteChannel
import java.nio.channels.AsynchronousSocketChannel

interface ProxyModule : Classifier<ProxyModule> {
    fun performConnect(dest: InetSocketAddress): AsynchronousByteChannel
    fun degradationReport()
    fun releaseProxy()
    fun ipRestricted()
}