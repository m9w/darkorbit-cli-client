package com.github.m9w.metaplugins

import com.darkorbit.KillScreenPostCommand
import com.darkorbit.KillScreenRepairRequest
import com.darkorbit.LoginRequest
import com.github.m9w.client.GameEngine
import com.github.m9w.client.auth.AuthenticationProvider
import com.github.m9w.feature.annotations.Inject
import com.github.m9w.feature.annotations.OnPackage
import com.github.m9w.feature.waitMs
import com.github.m9w.protocol.Factory

object BasicRepairModule {

    @Inject
    private lateinit var gameEngine: GameEngine

    @Inject
    private lateinit var authentication: AuthenticationProvider

    @OnPackage
    private suspend fun mapUpdate(screen: KillScreenPostCommand) {
        waitMs(1000)
        if(gameEngine.state != GameEngine.State.DESTROYED) return
        gameEngine.send<KillScreenRepairRequest> {
            selection = screen.options[0].repairType
            requestModule = Factory.build(LoginRequest::class).apply {
                userID = authentication.userID
                sessionID = authentication.sessionID
                instanceId = authentication.instanceId
                isMiniClient = false
            }
        }
    }
}
