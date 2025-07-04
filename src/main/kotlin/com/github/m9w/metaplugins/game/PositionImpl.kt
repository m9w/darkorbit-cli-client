package com.github.m9w.metaplugins.game

import com.darkorbit.AttributeShipSpeedUpdateCommand
import com.darkorbit.MoveCommand
import com.darkorbit.ProtocolPacket
import kotlin.math.sqrt
typealias Point = Pair<Int, Int>

open class PositionImpl(private var startX: Int, private var startY: Int) {
    private var targetX: Int = startX
    private var targetY: Int = startY
    private var moveStart: Long = 0
    private var timeToTarget: Int = 0

    open fun stopHandler(point: Point) {}

    open fun destinationTimeUpdateHandler(time: Int) {}

    open fun update(packet: ProtocolPacket) {
        if (packet is MoveCommand) {
            val (curX, curY) = position
            startX = curX; startY = curY
            moveStart = System.currentTimeMillis()
            timeToTarget = packet.timeToTarget
            targetX = packet.x; targetY = packet.y
            if (timeToTarget == 0) stopHandler(targetX to targetY)
            else destinationTimeUpdateHandler(timeToTarget)
        } else if (packet is AttributeShipSpeedUpdateCommand) {
            if (isMoving) moveTo(targetX to targetY, packet.newSpeed)
        }
    }

    protected fun moveTo(destination: Point, speed: Int) {
        val from = position
        startX = from.x; startY = from.y
        moveStart = System.currentTimeMillis()
        timeToTarget = (distance(from, destination) * 1000 / speed).toInt()
        destinationTimeUpdateHandler(timeToTarget)
        targetX = destination.x; targetY = destination.y
    }

    val position: Point get() {
        if (timeToTarget <= 0) return targetX to targetY
        val percent = (System.currentTimeMillis() - moveStart).toDouble() / timeToTarget
        return if (percent >= 1.0) {
            timeToTarget = 0
            (targetX to targetY).also { stopHandler(it) }
        } else {
            startX + ((targetX - startX) * percent).toInt() to  startY + ((targetY - startY) * percent).toInt()
        }
    }

    val destination get() = PositionImpl(targetX, targetY)

    val isMoving: Boolean get() = position.run { timeToTarget != 0 }

    fun distanceTo(other: PositionImpl): Double = distance(position, other.position)

    fun distance(from: Point, dest: Point): Double = from.distanceTo(dest)

    override fun toString() = "Position$position"

    companion object {
        val Point.x get() = first
        val Point.y get() = second

        fun Point.distanceTo(loc: Point): Double {
            val ox = loc.x - x
            val oy = loc.y - y
            return sqrt((ox * ox + oy * oy).toDouble())
        }
    }
}
