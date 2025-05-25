package com.github.m9w.client.auth

import com.github.m9w.util.Http
import com.github.m9w.util.Http.Companion.content

class LoginPasswordAuthenticationProvider(login: String, password: String) : ServerSidAuthenticationProvider(login(login, password)) {
    companion object {
        fun login(login: String, password: String): Pair<String, String> {
            val mainPage = Http("https://www.darkorbit.com").connect.content
            val token = mainPage.substringAfter("token=").substringBefore("\"")
            val auth = Http("https://sas.bpsecure.com/Sas/Authentication/Bigpoint?authUser=22&token=$token", "POST")
                .setRawHeaders("Origin" to "https://www.darkorbit.com", "Referer" to "https://www.darkorbit.com/")
                .setParams("username" to login.take(20), "password" to password.take(45))
                .connect
            val sessionID = auth.content
            if (sessionID.length != 32) throw RuntimeException("Unexpected sessionID length")
            val server = auth.uri().host.split(".")[0]
            return server to sessionID
        }
    }
}
