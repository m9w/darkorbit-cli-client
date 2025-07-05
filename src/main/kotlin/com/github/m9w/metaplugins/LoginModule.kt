package com.github.m9w.metaplugins

import com.darkorbit.*
import com.github.m9w.client.GameEngine
import com.github.m9w.client.auth.AuthenticationProvider
import com.github.m9w.context
import com.github.m9w.feature.annotations.OnEvent
import com.github.m9w.feature.annotations.OnPackage
import com.github.m9w.feature.annotations.SystemEvents
import com.github.m9w.feature.waitMs
import com.github.m9w.protocol.ProtocolParser
import com.github.m9w.feature.waitOnPackage
import com.github.m9w.metaplugins.proxy.ProxyModule
import com.github.m9w.optionalContext
import java.io.InterruptedIOException

@Suppress("unused")
class LoginModule(val type: Type = Type.UNITY) {
    private var unsuccessfulLoginCount = 0
    private val gameEngine: GameEngine by context
    private val authentication: AuthenticationProvider by context
    private val proxy: ProxyModule? by optionalContext

    enum class Type { UNITY, FLASH }

    @OnEvent(SystemEvents.ON_CONNECT)
    suspend fun onConnect(body: String) {
        gameEngine.state = GameEngine.State.NO_LOGIN
        val versionCommand = waitOnPackage<VersionCommand> {
            gameEngine.send<VersionRequest> { version = ProtocolParser.getVersion() }
        }
        if (versionCommand.equal) {
            val status = try {
                gameLogin()
            } catch (e: InterruptedIOException) {
                proxy?.degradationReport()
                gameEngine.reconnect(5000)
            }
            if (unsuccessfulLoginCount > 0) println("Connection error: $status")
        } else {
            gameEngine.disconnect()
            println("Close, server expected ${versionCommand.version}, client is ${ProtocolParser.getVersion()}")
            ProtocolParser.reload()
            println("Protocol updated to latest version ${ProtocolParser.getVersion()}")
            gameEngine.connect()
        }
    }

    private suspend fun gameLogin(delayBefore: Long = 0): LoginResponseStatus {
        gameEngine.cancelWaitMs("LoginModule_gameLogin")
        delayBefore.takeIf { it > 0 }?.let { waitMs(it, "LoginModule_gameLogin") }
        val loginResponse = waitOnPackage<LoginResponse>(timeout = 10000) {
            gameEngine.send<LoginRequest> {
                userID = authentication.userID
                sessionID = authentication.sessionID
                instanceId = authentication.instanceId
                isMiniClient = true
            }
        }
        unsuccessfulLoginCount++
        when (loginResponse.status) {
            LoginResponseStatus.Success -> unsuccessfulLoginCount = 0
            LoginResponseStatus.ShipIsDestroyed -> gameEngine.state = GameEngine.State.DESTROYED
            LoginResponseStatus.Error,
            LoginResponseStatus.ShuttingDown,
            LoginResponseStatus.WrongServer,
            LoginResponseStatus.PlayerIsLoggedOut,
            LoginResponseStatus.InvalidSessionId -> gameEngine.reconnect()
            LoginResponseStatus.InvalidData -> return gameLogin(1000)
            LoginResponseStatus.IPRestricted -> { proxy?.ipRestricted(); gameEngine.reconnect() }
            LoginResponseStatus.WrongInstanceId -> gameEngine.disconnect()
        }
        return loginResponse.status
    }

    @OnPackage
    private fun onInit(init: ShipInitializationCommand){
        gameEngine.send<ReadyRequest> { readyType = ReadyMessage.MAP_LOADED_2D }
        gameEngine.send<ReadyRequest> { readyType = ReadyMessage.UI_READY }
        gameEngine.state = GameEngine.State.REPAIRING
    }

    @OnEvent(SystemEvents.ON_DISCONNECT)
    private suspend fun onDisconnect(body: String) {
        if (gameEngine.state == GameEngine.State.STOPED) return
        if (unsuccessfulLoginCount in 5..10) {
            println("Unsuccessful attempts > 5, wait 10 sec to next connect")
            waitMs(10000)
        } else if (unsuccessfulLoginCount > 10) {
            println("Unsuccessful attempts > 10, wait 60 sec to next connect")
            waitMs(60000)
        }
        gameEngine.connect()
    }

    @OnPackage
    fun mapUpdate(l: LegacyModule) {
        if (l.message.startsWith("0|i|")) authentication.mapId = l.message.removePrefix("0|i|").toInt()
    }

    @OnPackage
    suspend fun onRelogin(l: ReloginCommand) {
        authentication.mapId = l.mapID
        gameEngine.send<ChannelCloseRequest> { close = true }
        gameEngine.reconnect(l.delayInMillis.toLong(), true)
    }
}
