package com.github.m9w.plugins

import com.darkorbit.ShipCreateCommand
import com.darkorbit.ShipRemoveCommand
import com.github.m9w.context
import com.github.m9w.feature.annotations.OnPackage
import com.github.m9w.feature.annotations.Repeat
import com.github.m9w.metaplugins.EntitiesModule
import com.github.m9w.metaplugins.game.PositionImpl.Companion.distanceTo
import com.github.m9w.metaplugins.game.entities.ShipImpl
import kotlin.math.cos
import kotlin.math.sin
import com.github.m9w.plugins.NpcData

import com.github.m9w.attackRequest
import com.github.m9w.metaplugins.MapModule
import com.github.m9w.moveRequest
import com.github.m9w.selectRequest

import kotlin.math.atan2


@Suppress("unused")
class NpcKiller {
    var enabled = false
    private val entities: EntitiesModule by context
    private val map: MapModule by context
    private var target: ShipImpl? = null
    private val circleAngleIncrement = 0.5 // Wie viel Winkel hinzugefügt werden soll
    private var angle = 0.0

    @OnPackage
    private fun onShipCreate(ship: ShipCreateCommand) {
        if (ship.npc) {
            NpcData.getNpcInfo(ship.userName) // Ensure NPC is registered in NpcData
            if (target == null) {
                findNextTarget()
            }
        }
    }

    @OnPackage
    private fun onNpcRemove(event: ShipRemoveCommand) {
        if (target?.id?.toInt() == event.userId) {
            target = null
        }
    }

    @Repeat(100)
    private fun tick() {
        if (!enabled || !entities.isHeroInitialized()) return

        if (target == null) {
            findNextTarget()
            // If still no target, move randomly
            if (target == null && !entities.hero.isMoving) {
                entities.hero.moveRandom()
            }
            return
        }

        val currentTarget = target ?: return
        val currentAttackRadius = NpcData.getNpcInfo(currentTarget.name).attackRadius
        println("Angriffsradius für ${currentTarget.name}: $currentAttackRadius")
        val distance = entities.hero.position.distanceTo(currentTarget.position)


        if (distance > currentAttackRadius) {
            entities.hero.moveTo(currentTarget.position)
        } else {
            // Circle logic
            val newPos = circleAround(currentTarget.position, currentAttackRadius, angle)
            entities.hero.moveTo(newPos)
            angle += 15
            if (angle >= 360) {
                angle = 0.0
            }
            // Attack logic
            if (entities.hero.target != currentTarget) {
                entities.gameEngine.selectRequest(entities.hero, currentTarget)
            }
            if (entities.hero.laserAttackTarget != currentTarget) {
                entities.gameEngine.attackRequest(currentTarget)
            }
        }
    }

    private fun findNextTarget() {
        target = entities.values
            .filterIsInstance<ShipImpl>()
            .filter { it.isNpc && it.id != entities.hero.id && NpcData.getNpcInfo(it.name).killable }
            .minByOrNull { entities.hero.position.distanceTo(it.position) }
    }

    private fun circleAround(center: Pair<Int, Int>, radius: Int, angle: Double): Pair<Int, Int> {
        val angleRad = Math.toRadians(angle)
        val x = center.first + (radius * cos(angleRad)).toInt()
        val y = center.second + (radius * sin(angleRad)).toInt()
        return x to y
    }
}