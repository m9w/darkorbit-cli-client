package com.github.m9w.metaplugins.game

import com.darkorbit.AttributeShipSpeedUpdateCommand
import com.darkorbit.MoveCommand
import com.darkorbit.ProtocolPacket
import kotlin.math.sqrt

open class PositionImpl(private var startX: Int, private var startY: Int) {
    private var targetX: Int = startX
    private var targetY: Int = startY
    private var moveStart: Long = 0
    private var timeToTarget: Int = 0

    open fun update(packet: ProtocolPacket) {
        if (packet is MoveCommand) {
            val (curX, curY) = position
            startX = curX; startY = curY
            moveStart = System.currentTimeMillis()
            timeToTarget = packet.timeToTarget
            targetX = packet.x; targetY = packet.y
        } else if (packet is AttributeShipSpeedUpdateCommand) {
            if (isMoving) moveTo(PositionImpl(targetX, targetY), packet.newSpeed)
        }
    }

    protected fun moveTo(pos: PositionImpl, speed: Int) {
        val from = position
        val dest = pos.position
        startX = from.first; startY = from.second
        moveStart = System.currentTimeMillis()
        timeToTarget = (distance(from, dest) * 1000 / speed).toInt()
        targetX = dest.first; targetY = dest.second
    }

    val position: Pair<Int, Int> get() {
        if (timeToTarget <= 0) return targetX to targetY
        val percent = (System.currentTimeMillis() - moveStart).toDouble() / timeToTarget
        return if (percent >= 1.0) {
            timeToTarget = 0
            targetX to targetY
        } else {
            startX + ((targetX - startX) * percent).toInt() to  startY + ((targetY - startY) * percent).toInt()
        }
    }

    val isMoving: Boolean get() = position.run { timeToTarget == 0 }

    fun distanceTo(other: PositionImpl): Double = distance(position, other.position)

    fun distance(from: Pair<Int, Int>, dest: Pair<Int, Int>): Double {
        val vector = from.first - dest.first to from.second - dest.second
        return sqrt((vector.first * vector.first + vector.second * vector.second).toDouble())
    }

    override fun toString() = "Position$position"
}
