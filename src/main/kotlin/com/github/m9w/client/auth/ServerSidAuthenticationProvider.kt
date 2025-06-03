package com.github.m9w.client.auth

import com.github.m9w.client.auth.AuthenticationProvider.Companion.getMapAddress
import com.github.m9w.util.Http
import com.github.m9w.util.Http.Companion.asJson
import com.google.gson.Gson
import java.net.InetSocketAddress
import kotlin.text.toInt

open class ServerSidAuthenticationProvider(serverSid: Pair<String, String>) : AuthenticationProvider {
    override val server = serverSid.first
    override val sessionID = serverSid.second
    private val http = Http("https://$server.darkorbit.com/unityApi/login.php").setSid(sessionID)
    private val data = http.connect.asJson<Map<String, String>>()["data"]
    private val login = Gson().fromJson<Map<String, String>>(data, Map::class.java)
    override val userID: Int = login["userID"]?.toInt() ?: throw RuntimeException("User is null")
    override val instanceId: Int = login["pid"]?.toInt() ?: throw RuntimeException("InstanceId is null")
    override var mapId: Int = login["mapID"]?.toInt() ?: 1
    override val address: InetSocketAddress get() = getMapAddress(server, mapId)
}