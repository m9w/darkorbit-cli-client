package com.github.m9w.client.auth

import java.net.InetSocketAddress
import java.net.URI

interface AuthenticationProvider {
    val userID: Int
    val server: String
    val sessionID: String
    val address: InetSocketAddress
    val instanceId: Int
    var mapId: Int

    companion object {
        //Map<Server, <creation time, <MapId, IP>>>
        private val cache = HashMap<String, Pair<Long, Map<Int, InetSocketAddress>>>()
        private val mapRegex = Regex("""<map\s+id="(\d+)">.*?<gameserverIP>([^<]+)</gameserverIP>.*?</map>""", RegexOption.DOT_MATCHES_ALL)
        fun byServerSid(server: String, sessionID: String) = ServerSidAuthenticationProvider(server to sessionID)
        fun byLoginPassword(login: String, password: String) = LoginPasswordAuthenticationProvider(login, password)
        fun byStatic(userID: Int, server: String, sessionID: String, instanceId: Int, mapId: Int = 1) =
            StaticAuthenticationProvider(userID, server, sessionID, instanceId, mapId)

        fun getMapAddress(server: String, mapId: Int): InetSocketAddress {
            val records = cache.computeIfAbsent(server) {
                val xml = String(URI("https://$server.darkorbit.com/spacemap/xml/maps.php").toURL().readBytes())
                System.currentTimeMillis() to mapRegex.findAll(xml).associate { match ->
                    val (host, port) = match.groupValues[2].split(":")
                   match.groupValues[1].toInt() to InetSocketAddress(host, port.toInt())
                }
            }

            if (records.first + 30*60*60 < System.currentTimeMillis()) {
                cache.remove(server)
                return getMapAddress(server, mapId)
            } else {
                return records.second[mapId] ?: throw IllegalArgumentException("Map $mapId not found")
            }
        }
    }
}
