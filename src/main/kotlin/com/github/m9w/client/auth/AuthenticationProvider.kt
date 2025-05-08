package com.github.m9w.client.auth

interface AuthenticationProvider {
    fun getUserId(): Int
    fun getServer(): String
    fun getSid(): String

    companion object {
        fun static(userId: Int, server: String, sid: String) = object : AuthenticationProvider {
            override fun getUserId() = userId
            override fun getServer() = server
            override fun getSid() = sid
        }
    }
}
