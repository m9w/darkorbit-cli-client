package com.github.m9w.client.auth

import com.github.m9w.context
import com.github.m9w.metaplugins.LoginModule
import com.github.m9w.util.Http
import com.github.m9w.util.Http.Companion.asJson
import com.github.m9w.util.Http.Companion.content
import com.google.gson.Gson

open class ServerSidAuthenticationProvider(protected open val server: String, protected open val sid: String) : AuthenticationProvider {
    protected val loginModule: LoginModule by context
    protected val loginParams: Map<String, Any> by lazy { when (loginModule.type) {
        LoginModule.Type.UNITY -> getUnityLoginParams()
        LoginModule.Type.FLASH -> getFlashLoginParams()
    } }
    val userAgent by lazy { when (loginModule.type) {
        LoginModule.Type.UNITY -> "DarkOrbit Unity Client $getLastUnityVersion"
        LoginModule.Type.FLASH -> "BigpointClient/$getLastFlashVersion"
    } }

    final override val host get() = loginParams["host"]?.toString() ?: "https://$server.darkorbit.com"
    final override val sessionID get() = loginParams["sessionID"]?.toString() ?: throw RuntimeException("SessionID is null")
    final override val userID: Int get() = loginParams["userID"]?.toString()?.toInt() ?: throw RuntimeException("User is null")
    final override val instanceId: Int get() = loginParams["pid"]?.toString()?.toInt() ?: throw RuntimeException("InstanceId is null")
    final override var mapId: Int = -1; get() = if (field == -1) loginParams["mapID"]?.toString()?.toInt() ?: 1 else field

    private val getLastUnityVersion get() = Http("https://alicdn-oss-prod.darkorbit.com/vc/current.txt").connect.content

    private fun getUnityLoginParams(): Map<String, Any> {
        return Http("https://${server}.darkorbit.com/unityApi/login.php")
                .setUserAgent(userAgent)
                .setSid(sid).connect
                .asJson<Map<String, String>>()
                .let { Gson().fromJson<Map<String, Any>>(it["data"], Map::class.java) }
    }

    private val getLastFlashVersion get() = Http("http://darkorbit-22-client.bpsecure.com/bpflashclient/windows.x64/repository/Updates.xml").connect.content.substringAfter("<Version>").substringBefore("</Version>")

    private fun getFlashLoginParams(): Map<String, Any> {
        val content = Http("https://${server}.darkorbit.com/indexInternal.es?action=internalMapRevolution")
                .setUserAgent(userAgent).setSid(sid).connect.content
        return if (content.contains("flashembed("))
            content.substringAfter("flashembed(").substringBefore(")")
                .let { Gson().fromJson<List<Any>>("[$it]", List::class.java)[2] as Map<String, Any> }
        else "userID\":\"(\\d+)\",\"instanceID\":\"(\\d+)".toRegex().find(content)
                ?.let {mapOf("userID" to it.groupValues[1], "pid" to it.groupValues[2], "sessionID" to sid)}
                ?: throw RuntimeException("Identifiers not found")
    }
}
