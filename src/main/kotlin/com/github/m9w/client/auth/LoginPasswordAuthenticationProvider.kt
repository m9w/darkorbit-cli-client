package com.github.m9w.client.auth

import com.github.m9w.util.Http
import com.github.m9w.util.Http.Companion.content
import kotlin.lazy

class LoginPasswordAuthenticationProvider(private val login: String, private val password: String) : ServerSidAuthenticationProvider("", "") {
    private val serverSidPair by lazy { login() }
    override val server get() = serverSidPair.first
    override val sid get() = serverSidPair.second

    private fun login(): Pair<String, String> {
        val mainPage = Http("https://www.darkorbit.com").setUserAgent(userAgent).connect.content
        val token = mainPage.substringAfter("token=").substringBefore("\"")
        val auth = Http("https://sas.bpsecure.com/Sas/Authentication/Bigpoint?authUser=22&token=$token", "POST")
            .setUserAgent(userAgent)
            .setRawHeaders("Origin" to "https://www.darkorbit.com", "Referer" to "https://www.darkorbit.com/")
            .setParams("username" to login.take(20), "password" to password.take(45))
            .connect
        val sessionID = auth.content.takeIf { it.matches("^[A-z0-9]{32}$".toRegex()) } ?: auth.headers().map()
            .filter { (k, _) -> k.equals("set-cookie", true) }
            .values.flatten()
            .find { it.startsWith("dosid=", true) }
            ?.substring(6, 6+32) ?: throw RuntimeException("Unexpected sessionID length")
        val server = auth.uri().host.split(".")[0]
        return server to sessionID
    }
}
