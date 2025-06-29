package com.github.m9w.client.auth

class StaticAuthenticationProvider(
    override val userID: Int,
    override val host: String,
    override val sessionID: String,
    override val instanceId: Int,
    override var mapId: Int = 1
) : AuthenticationProvider
