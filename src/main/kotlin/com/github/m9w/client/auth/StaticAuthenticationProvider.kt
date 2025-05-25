package com.github.m9w.client.auth

import java.net.InetSocketAddress
import com.github.m9w.client.auth.AuthenticationProvider.Companion.getMapAddress

class StaticAuthenticationProvider(
    override val userID: Int,
    override val server: String,
    override val sessionID: String,
    override val instanceId: Int,
    override var mapId: Int = 1
) : AuthenticationProvider {
    override val address: InetSocketAddress = getMapAddress(server, mapId)
}
