package com.github.m9w.metaplugins

import com.darkorbit.KillScreenPostCommand
import com.darkorbit.KillScreenRepairRequest
import com.github.m9w.client.GameEngine
import com.github.m9w.client.auth.AuthenticationProvider
import com.github.m9w.context
import com.github.m9w.feature.annotations.OnPackage
import com.github.m9w.feature.waitMs

class BasicRepairModule {
    private val gameEngine: GameEngine by context
    private val authentication: AuthenticationProvider by context

    @OnPackage
    private suspend fun mapUpdate(screen: KillScreenPostCommand) {
        waitMs(1000)
        if(gameEngine.state != GameEngine.State.DESTROYED) return
        gameEngine.send<KillScreenRepairRequest> {
            selection = screen.options[0].repairType
            requestModule = requestModule.apply {
                userID = authentication.userID
                sessionID = authentication.sessionID
                instanceId = authentication.instanceId
                isMiniClient = false
            }
        }
    }
}
