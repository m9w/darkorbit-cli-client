package com.github.m9w.metaplugins

import com.darkorbit.KillScreenPostCommand
import com.darkorbit.KillScreenRepairRequest
import com.github.m9w.client.GameEngine
import com.github.m9w.client.auth.AuthenticationProvider
import com.github.m9w.config.config
import com.github.m9w.context.context
import com.github.m9w.feature.annotations.OnPackage
import com.github.m9w.feature.annotations.Repeat
import com.github.m9w.feature.waitMs
import com.github.m9w.metaplugins.game.entities.JumpgateImpl
import kotlin.collections.filter

class BasicRepairModule {
    private val entities: EntitiesModule by context
    private val engine: GameEngine by context
    private val map: MapModule by context
    private val authentication: AuthenticationProvider by context
    private val hpPercentalForRepairing: Double by config(0.20)
    private val enabled: Boolean by config(true)

    @OnPackage
    private suspend fun mapUpdate(screen: KillScreenPostCommand) {
        waitMs(1000)
        engine.send<KillScreenRepairRequest> {
            selection = screen.options[0].repairType
            requestModule.apply {
                userID = authentication.userID
                sessionID = authentication.sessionID
                instanceId = authentication.instanceId
                isMiniClient = false
            }
        }
    }

    @Repeat(1000)
    private fun autoRepair() {
        if (!enabled) return

        if ((engine.state == GameEngine.State.NORMAL || engine.state == GameEngine.State.TRAVELING)
            && entities.hero.health.health < entities.hero.health.healthMax*hpPercentalForRepairing)
            engine.state = GameEngine.State.REPAIRING

        if (engine.state == GameEngine.State.REPAIRING && !map.inPeaceArea)
            entities.get<JumpgateImpl>()
                .filter { it.designId == 1 }
                .minBy { it.distanceTo(entities.hero) }
                .takeIf { entities.hero.destination.distanceTo(it) > 100 }
                ?.let { entities.hero.moveTo(it.position ) }

        if (engine.state == GameEngine.State.REPAIRING
            && entities.hero.health.health > entities.hero.health.healthMax-100
            && entities.hero.health.shield > entities.hero.health.shieldMax-100)
            engine.state = GameEngine.State.NORMAL
    }
}
