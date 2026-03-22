package com.github.m9w.client.auth

import com.github.m9w.client.auth.AuthenticationProvider.Companion.deserialize
import com.github.m9w.client.auth.AuthenticationProvider.Companion.serialize
import kotlin.text.toInt

class StaticAuthenticationProvider(
    override val userID: Int,
    override val host: String,
    override val sessionID: String,
    override val instanceId: Int,
    override var mapId: Int = 1,
    override val type: ClientType = ClientType.FLASH
) : AuthenticationProvider {
    constructor(userID: String, host: String, sessionID: String, instanceId: String, mapId: String, type: String) : this (userID.toInt(), host, sessionID, instanceId.toInt(), mapId.toInt(), ClientType.valueOf(type))

    override val serialized: String = serialize("Static", userID, host, sessionID, instanceId, mapId, type)

    companion object {
        fun deserialize(data: String): StaticAuthenticationProvider {
            val (userID, host, sessionID, instanceId, mapId) = data.deserialize
            return StaticAuthenticationProvider(userID, host, sessionID, instanceId, mapId, data.deserialize[5])
        }
    }
}
