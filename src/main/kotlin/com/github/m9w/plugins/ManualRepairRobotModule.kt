package com.github.m9w.plugins

import com.darkorbit.AttackHitCommand
import com.darkorbit.BeaconCommand
import com.darkorbit.MenuActionRequest
import com.darkorbit.MenuActionRequestActionType
import com.darkorbit.SourceType
import com.github.m9w.client.GameEngine
import com.github.m9w.context.context
import com.github.m9w.feature.annotations.OnPackage
import com.github.m9w.feature.waitMs
import com.github.m9w.feature.waitOnPackage
import com.github.m9w.metaplugins.EntitiesModule
import java.io.InterruptedIOException

class ManualRepairRobotModule {
    private val syncEventName = ManualRepairRobotModule::class.simpleName!!
    private val entities: EntitiesModule by context
    private val engine: GameEngine by context
    private var repairRobotActive: Boolean = false
    private var repairRobotLootId: String = ""
    private var repairBotSkilled: Boolean = false

    @OnPackage
    private suspend fun onAttackEvent(attack: AttackHitCommand) {
        if (!repairBotSkilled) return
        if (attack.victimId.toLong() != entities.hero.id) return
        if (attack.victimHitpoints >= entities.hero.health.healthMax * 0.9) return
        engine.cancelWaitMs(syncEventName)
        waitMs(10000, syncEventName)
        if (repairRobotActive) return
        if (repairRobotLootId.isEmpty()) return
        try {
            waitOnPackage<BeaconCommand>(timeout = 3000) {
                engine.send<MenuActionRequest> {
                    menuItemId = repairRobotLootId
                    actionType = MenuActionRequestActionType.ACTIVATE
                    sourceType = SourceType.ITEM_BAR
                }
            }.takeIf { !it.repairRobotActive }?.let { onAttackEvent(attack) }
        } catch (e: InterruptedIOException) {
            onAttackEvent(attack)
        }
    }

    @OnPackage
    private fun onBeacon(beacon: BeaconCommand) {
        repairRobotActive = beacon.repairRobotActive
        repairRobotLootId = beacon.repairRobotLootId
        repairBotSkilled = beacon.repairBotSkilled
    }
}