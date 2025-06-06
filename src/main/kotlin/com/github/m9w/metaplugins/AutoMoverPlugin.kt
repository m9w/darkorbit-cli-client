package com.github.m9w.metaplugins

import com.darkorbit.ShipInitializationCommand
import com.github.m9w.feature.annotations.Inject
import com.github.m9w.feature.annotations.OnPackage
import com.github.m9w.metaplugins.game.PositionImpl
import java.util.*
import kotlin.concurrent.schedule
import kotlin.random.Random

class AutoMoverPlugin {

    @Inject lateinit var entitiesModule: EntitiesModule
    private var moveTimer: Timer? = null

    @OnPackage
    fun onShipInitialized(cmd: ShipInitializationCommand) {
        startRandomMove()
    }

    private fun startRandomMove(
        mapWidth: Int = 21000,
        mapHeight: Int = 13000,
        intervalMillis: Long = 10000L
    ) {
        val hero = entitiesModule.hero ?: return

        moveTimer?.cancel()
        moveTimer = Timer()
        moveTimer?.schedule(delay = 0L, period = intervalMillis) {
            val x = Random.nextInt(0, mapWidth)
            val y = Random.nextInt(0, mapHeight)
            println("[AutoMover] Moving randomly to: ($x, $y)")
            hero.moveTo(PositionImpl(x, y))
        }
    }
}
