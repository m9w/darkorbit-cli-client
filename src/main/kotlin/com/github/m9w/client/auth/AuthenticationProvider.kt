package com.github.m9w.client.auth

import java.net.InetSocketAddress
import java.net.URI

interface AuthenticationProvider {
    fun getUserId(): Int
    fun getSid(): String
    fun getServer(): InetSocketAddress
    var mapId: Int

    companion object {
        fun static(userId: Int, server: String, sid: String) = object : AuthenticationProvider {
            override fun getUserId() = userId
            override fun getServer() = getMapAddress(server, mapId)
            override fun getSid() = sid
            override var mapId: Int = 1
        }

        private fun getMapAddress(server: String, mapId: Int): InetSocketAddress {
            val mapRegex = Regex("""<map\s+id="(\d+)">.*?<gameserverIP>([^<]+)</gameserverIP>.*?</map>""", RegexOption.DOT_MATCHES_ALL)
            val xml = String(URI("https://$server.darkorbit.com/spacemap/xml/maps.php").toURL().readBytes())
            for (match in mapRegex.findAll(xml)) {
                val (host, port) = match.groupValues[2].split(":")
                if (mapId == match.groupValues[1].toInt()) return InetSocketAddress(host, port.toInt())
            }
            throw IllegalArgumentException("Map $mapId not found")
        }

    }
}
