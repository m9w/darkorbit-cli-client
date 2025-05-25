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
        fun byServerSid(server: String, sessionID: String) = ServerSidAuthenticationProvider(server to sessionID)
        fun byLoginPassword(login: String, password: String) = LoginPasswordAuthenticationProvider(login, password)
        fun byStatic(userID: Int, server: String, sessionID: String, instanceId: Int, mapId: Int = 1) =
            StaticAuthenticationProvider(userID, server, sessionID, instanceId, mapId)

        fun getMapAddress(server: String, mapId: Int): InetSocketAddress {
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
