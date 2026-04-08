package com.github.m9w.client.auth

import com.github.m9w.client.auth.AuthenticationProvider.Companion.deserialize
import com.github.m9w.client.auth.AuthenticationProvider.Companion.serialize
import com.github.m9w.util.Http
import com.github.m9w.util.Http.Companion.asJson

class ExternalAuthenticationProvider(private val externalIdentifier: String) : AuthenticationProvider {
    private val loginParams by lazy {
        Http(System.getenv("external_login_endpoint"), "POST")
            .apply { System.getenv("external_auth_value")?.let { setRawHeaders("Authorization" to it) } }
            .setParams("login" to externalIdentifier)
            .connect.asJson<Map<String, Any>>()
    }

    override val host get() = loginParams["host"]?.toString() ?: throw RuntimeException("host is null, $loginParams")
    override val sessionID get() = loginParams["sessionID"]?.toString() ?: throw RuntimeException("SessionID is null, $loginParams")
    override val userID: Int get() = loginParams["userID"]?.toString()?.toInt() ?: throw RuntimeException("User is null, $loginParams")
    override val instanceId: Int get() = loginParams["pid"]?.toString()?.toInt() ?: throw RuntimeException("InstanceId is null, $loginParams")
    override val type: ClientType get() = loginParams["type"]?.toString()?.let(ClientType::valueOf) ?: ClientType.FLASH
    override var mapId: Int = -1; get() = if (field == -1) loginParams["mapID"]?.toString()?.toInt() ?: 1 else field
    override val serialized: String = serialize("External", externalIdentifier)

    companion object {
        fun getAccounts(): List<String> = Http(System.getenv("external_accounts_endpoint"))
                .apply { System.getenv("external_auth_value")?.let { setRawHeaders("Authorization" to it) } }
                .connect.asJson<List<String>>()

        fun deserialize(data: String) = ExternalAuthenticationProvider(data.deserialize[0])
    }
}
