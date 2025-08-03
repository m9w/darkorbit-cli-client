package com.github.m9w.client.auth

import com.github.m9w.util.Http
import com.github.m9w.util.Http.Companion.asJson
import kotlin.lazy

class ExternalAuthenticationProvider(private val login: String) : AuthenticationProvider {
    private val loginParams by lazy {
        Http(System.getenv("external_login_endpoint"), "POST")
            .apply { System.getenv("external_auth_value")?.let { setRawHeaders("Authorization" to it) } }
            .setParams("login" to login)
            .connect.asJson<Map<String, Any>>()
    }

    override val host get() = loginParams["host"]?.toString() ?: throw RuntimeException("host is null")
    override val sessionID get() = loginParams["sessionID"]?.toString() ?: throw RuntimeException("SessionID is null")
    override val userID: Int get() = loginParams["userID"]?.toString()?.toInt() ?: throw RuntimeException("User is null")
    override val instanceId: Int get() = loginParams["pid"]?.toString()?.toInt() ?: throw RuntimeException("InstanceId is null")
    override var mapId: Int = -1; get() = if (field == -1) loginParams["mapID"]?.toString()?.toInt() ?: 1 else field
}
