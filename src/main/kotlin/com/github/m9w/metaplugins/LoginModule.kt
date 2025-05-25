package com.github.m9w.metaplugins

import com.darkorbit.LegacyModule
import com.darkorbit.LoginRequest
import com.darkorbit.LoginResponse
import com.darkorbit.LoginResponseStatus
import com.darkorbit.ReadyMessage
import com.darkorbit.ReadyRequest
import com.darkorbit.VersionCommand
import com.darkorbit.VersionRequest
import com.github.m9w.client.GameEngine
import com.github.m9w.client.auth.AuthenticationProvider
import com.github.m9w.feature.annotations.OnEvent
import com.github.m9w.feature.annotations.Inject
import com.github.m9w.feature.annotations.OnPackage
import com.github.m9w.feature.annotations.SystemEvents
import com.github.m9w.protocol.ProtocolParser
import com.github.m9w.util.waitOnPackage

object LoginModule {
    @Inject
    private lateinit var gameEngine: GameEngine

    @Inject
    private lateinit var authentication: AuthenticationProvider

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
            when (loginResponse.status) {
                LoginResponseStatus.Success -> {
                    gameEngine.send<ReadyRequest> { readyType = ReadyMessage.MAP_LOADED_2D }
                    gameEngine.send<ReadyRequest> { readyType = ReadyMessage.UI_READY }
                    gameEngine.state = GameEngine.State.NORMAL
                }
                LoginResponseStatus.Error -> gameEngine.connect()
                LoginResponseStatus.ShuttingDown -> gameEngine.connect()
                LoginResponseStatus.InvalidData -> gameEngine.disconnect()
                LoginResponseStatus.WrongInstanceId -> gameEngine.disconnect()
                LoginResponseStatus.WrongServer -> gameEngine.connect()
                LoginResponseStatus.InvalidSessionId -> gameEngine.connect()
                LoginResponseStatus.ShipIsDestroyed -> gameEngine.state = GameEngine.State.DESTROYED
                LoginResponseStatus.PlayerIsLoggedOut -> gameEngine.connect()
                LoginResponseStatus.IPRestricted -> gameEngine.disconnect()
            }
        } else {
            gameEngine.disconnect()
            println("Close, server expected ${versionCommand.version}, client is ${ProtocolParser.getVersion()}")
            ProtocolParser.reload()
            println("Protocol updated to latest version ${ProtocolParser.getVersion()}")
            gameEngine.connect()
        }
    }

    @OnPackage
    fun mapUpdate(l: LegacyModule) {
        if (l.message.startsWith("0|i|")) authentication.mapId = l.message.removePrefix("0|i|").toInt()
    }
}
