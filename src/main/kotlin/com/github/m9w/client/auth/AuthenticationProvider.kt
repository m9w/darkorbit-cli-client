package com.github.m9w.client.auth

import com.github.m9w.feature.Classifier
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI

interface AuthenticationProvider : Classifier<AuthenticationProvider> {
    val userID: Int
    val host: String
    val sessionID: String
    val instanceId: Int
    var mapId: Int
    val address: InetSocketAddress get() = getMapAddress(host, mapId)
    val type: ClientType

    companion object {
        //Map<Server, <creation time, <MapId, IP>>>
        private val cache = HashMap<String, Pair<Long, Map<Int, InetSocketAddress>>>()
        private val mapRegex = Regex("""<map\s+id="(\d+)">.*?<gameserverIP>([^<]+)</gameserverIP>.*?</map>""", RegexOption.DOT_MATCHES_ALL)
        fun byServerSid(server: String, sessionID: String, clientType: ClientType) = ServerSidAuthenticationProvider(server, sessionID, clientType)
        fun byLoginPassword(login: String, password: String, clientType: ClientType) = LoginPasswordAuthenticationProvider(login, password, clientType)
        fun byLoginExternal(login: String) = ExternalAuthenticationProvider(login)
        fun byStatic(userID: Int, server: String, sessionID: String, instanceId: Int, mapId: Int = 1, clientType: ClientType) =
            StaticAuthenticationProvider(userID, server, sessionID, instanceId, mapId, clientType)

        @Synchronized
        fun getMapAddress(host: String, mapId: Int): InetSocketAddress {
            val records = cache.computeIfAbsent(host) {
                val xml = String(URI("$host/spacemap/xml/maps.php").toURL().readBytes())
                System.currentTimeMillis() to mapRegex.findAll(xml).associate { match ->
                    val (host, port) = match.groupValues[2].split(":")
                    match.groupValues[1].toInt() to findReachableIp(host, port.toInt())
                }
            }

            return if (records.first + 15*60*1000 < System.currentTimeMillis()) //TTL 15 min
                cache.remove(host).let { getMapAddress(host, mapId) }
            else records.second[mapId] ?: throw IllegalArgumentException("Map $mapId not found")
        }

        fun findReachableIp(host: String, port: Int): InetSocketAddress {
            InetAddress.getAllByName(host).forEach {
                if (it.isReachable(15000))
                    return InetSocketAddress(host, port)
                else println("A record ${it.hostAddress} of $host is not reachable")
            }
            throw IllegalArgumentException("Host $host is unreachable")
        }
    }
}
