package com.github.m9w.metaplugins

import com.darkorbit.MenuActionRequest
import com.darkorbit.MenuActionRequestActionType
import com.darkorbit.SourceType
import com.github.m9w.metaplugins.game.HealthHolder
import com.github.m9w.client.GameEngine
import com.github.m9w.feature.annotations.Inject


class CPU_Rep : Runnable {

    @Inject
    lateinit var entities: EntitiesModule

    @Inject
    lateinit var gameEngine: GameEngine
    private var running = true

    fun stop() {
        running = false
    }

    override fun run() {
        while (running) {
            checkHp()
            Thread.sleep(1000) // prüft jede Sekunde
        }
    }

    fun checkHp() {
        val heroHealth = entities.hero?.health ?: return

        val currentHp = heroHealth.health
        val maxHp = heroHealth.healthMax

        if (currentHp <= maxHp * 0.3) {
            gameEngine.send<MenuActionRequest> {
                menuItemId = "equipment_extra_repbot_rep"
                sourceType = SourceType.ITEM_BAR
                actionType = MenuActionRequestActionType.ACTIVATE
            }
        }
    }
}
