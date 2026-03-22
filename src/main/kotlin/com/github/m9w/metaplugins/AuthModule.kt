package com.github.m9w.metaplugins

import com.github.m9w.client.GameEngine
import com.github.m9w.client.auth.AuthenticationProvider
import com.github.m9w.client.auth.AuthenticationProvider.Companion.deserialize
import com.github.m9w.client.auth.ClientType
import com.github.m9w.client.auth.ExternalAuthenticationProvider
import com.github.m9w.client.auth.LoginPasswordAuthenticationProvider
import com.github.m9w.client.auth.ServerSidAuthenticationProvider
import com.github.m9w.client.auth.StaticAuthenticationProvider
import com.github.m9w.context.context
import com.github.m9w.feature.annotations.OnEvent
import com.github.m9w.feature.annotations.SystemEvents

class AuthModule : AuthenticationProvider {
    private lateinit var result: String
    private val gameEngine: GameEngine by context
    private lateinit var downstreamProvider: AuthenticationProvider

    override val userID: Int get() = downstreamProvider.userID
    override val host: String get() = downstreamProvider.host
    override val sessionID: String get() = downstreamProvider.sessionID
    override val instanceId: Int get() = downstreamProvider.instanceId
    override var mapId: Int get() = downstreamProvider.mapId
        set(value) { downstreamProvider.mapId = value }
    override val type: ClientType get() = downstreamProvider.type
    override val serialized: String get() = downstreamProvider.serialized

    @OnEvent(SystemEvents.ON_AUTH_SELECT_EVENT)
    private fun auth(credentials: String) {
        val (method, data) = credentials.deserialize
        downstreamProvider = when (method) {
           "External" -> ExternalAuthenticationProvider.deserialize(data)
           "LoginPassword" -> LoginPasswordAuthenticationProvider.deserialize(data)
           "ServerSid" -> ServerSidAuthenticationProvider.deserialize(data)
           "Static" -> StaticAuthenticationProvider.deserialize(data)
            else -> throw IllegalArgumentException("Unknown method: $method")
        }

        result = runCatching { downstreamProvider.userID }.exceptionOrNull()?.message ?: gameEngine.connect().let { "done" }
    }

    override fun toString(): String = "AuthModule($userID) :: $result"
}