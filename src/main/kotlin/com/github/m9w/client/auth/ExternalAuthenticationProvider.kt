package com.github.m9w.client.auth

import com.github.m9w.util.Http
import com.github.m9w.util.Http.Companion.asJson
import kotlin.lazy

class ExternalAuthenticationProvider(private val login: String, private val password: String) : ServerSidAuthenticationProvider("", "") {
    private val serverSidPair by lazy {
        Http(System.getenv("external_login_endpoint"), "POST")
            .setParams("username" to login.take(20), "password" to password.take(45))
            .connect.asJson<Map<String, String>>()
            .let { it["domain"]!!.substringBefore(".") to it["sid"]!! }
    }
    override val server get() = serverSidPair.first
    override val sid get() = serverSidPair.second

    override fun toString() = userID.toString()
}
