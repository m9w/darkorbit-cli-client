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

@Suppress("unused")
class LoginModule(val type: Type = Type.UNITY) {
    private var unsuccessfulLoginCount = 0
    private val gameEngine: GameEngine by context
    private val authentication: AuthenticationProvider by context

    enum class Type { UNITY, FLASH }

    @OnEvent(SystemEvents.ON_CONNECT)
    suspend fun onConnect(body: String) {
        gameEngine.state = GameEngine.State.NO_LOGIN
        val versionCommand = waitOnPackage<VersionCommand> {
            gameEngine.send<VersionRequest> { version = ProtocolParser.getVersion() }
        }
        if (versionCommand.equal) {
            val loginResponse = waitOnPackage<LoginResponse> {
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
                LoginResponseStatus.InvalidSessionId -> gameEngine.disconnect(true)
                LoginResponseStatus.InvalidData,
                LoginResponseStatus.WrongInstanceId,
                LoginResponseStatus.IPRestricted -> gameEngine.disconnect()
            }
            if (unsuccessfulLoginCount > 0) println("Connection error: ${loginResponse.status}")
        } else {
            gameEngine.disconnect()
            println("Close, server expected ${versionCommand.version}, client is ${ProtocolParser.getVersion()}")
            ProtocolParser.reload()
            println("Protocol updated to latest version ${ProtocolParser.getVersion()}")
            gameEngine.connect()
        }
    }

    @OnPackage
    private fun onInit(init: ShipInitializationCommand){
        gameEngine.send<ReadyRequest> { readyType = ReadyMessage.MAP_LOADED_2D }
        gameEngine.send<ReadyRequest> { readyType = ReadyMessage.UI_READY }
        gameEngine.state = GameEngine.State.NORMAL
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
        if ( l.delayInMillis > 0 ) waitMs(l.delayInMillis.toLong())
        gameEngine.send<ChannelCloseRequest> { close = true }
        gameEngine.disconnect(true)
    }
}
