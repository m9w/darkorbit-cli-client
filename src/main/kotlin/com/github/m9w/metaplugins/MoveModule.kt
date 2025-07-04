package com.github.m9w.metaplugins

import com.github.m9w.client.GameEngine
import com.github.m9w.context
import com.github.m9w.feature.annotations.OnEvent
import com.github.m9w.feature.annotations.SystemEvents
import com.github.m9w.feature.waitMs
import com.github.m9w.metaplugins.PathTracerModule
import com.github.m9w.metaplugins.game.Point
import com.github.m9w.metaplugins.game.PositionImpl.Companion.distanceTo
import com.github.m9w.metaplugins.game.PositionImpl.Companion.x
import com.github.m9w.metaplugins.game.PositionImpl.Companion.y
import com.github.m9w.moveRequest
import java.lang.System.currentTimeMillis
import java.nio.channels.CancelledKeyException
import java.util.*
import kotlin.math.hypot

@Suppress("unused")
class MoveModule {
    private val gameEngine: GameEngine by context
    private val pathTracer: PathTracerModule by context
    private val entities: EntitiesModule by context
    private val map: MapModule by context
    private var onComplete: (Point) -> Unit = {}
    private var updatePosition: (Point) -> Unit = {}
    private var startFrom: Point = Pair(0,0)
    private var lastMove = 0L
    private var scheduledPoint: Point? = null
    val nextPoints: LinkedList<Point> = LinkedList()

    fun destinationTimeUpdateEvent(time: Int) = gameEngine.handleEvent(SystemEvents.ON_HERO_MOVING_UPDATE, time.toString())

    @OnEvent(SystemEvents.ON_HERO_MOVING_UPDATE)
    suspend fun onHeroUpdateMovingTime(ms: String) {
        gameEngine.cancelWaitMs(SystemEvents.ON_HERO_MOVING_UPDATE) { CancelledKeyException() }
        try {
            waitMs(ms.toLong(), SystemEvents.ON_HERO_MOVING_UPDATE)
            entities.hero.position
        } catch (_: CancelledKeyException) {}
    }

    fun stopEvent(point: Point) {
        if (nextPoints.isEmpty()) return
        val heroPosition = entities.hero.position
        if (nextPoints.first().distanceTo(heroPosition) < 50) {
            nextPoints.removeFirst()
            moveToNext()
            if (nextPoints.isEmpty()) onComplete.invoke(heroPosition)
        } else if(distanceToSegment(startFrom, nextPoints.first(), heroPosition) < 50) {
            moveToNext()
        } else {
            moveTo(nextPoints.last(), updatePosition, onComplete)
        }
    }

    fun moveTo(point: Point, block: (Point) -> Unit, update: (Point) -> Unit) {
        updatePosition = update
        onComplete = block

        if (point.x == Int.MIN_VALUE && point.y == Int.MIN_VALUE) moveRequestDelay(entities.hero.position)
        else if (point.x == Int.MAX_VALUE && point.y == Int.MAX_VALUE) moveTo((0..map.map.width).random() to (0..map.map.height).random(), block, update)
        else {
            startFrom = entities.hero.position
            nextPoints.clear()
            nextPoints.addAll(pathTracer.traceTo(point))
            moveToNext()
        }
    }

    private fun moveRequestDelay(dest: Point) {
        if (lastMove + 100 > currentTimeMillis()) {
            if (scheduledPoint == null) gameEngine.handleEvent("MoveModule_delay")
            scheduledPoint = dest
        } else {
            gameEngine.moveRequest(entities.hero.position, dest)
            lastMove = currentTimeMillis()
        }
    }

    @OnEvent("MoveModule_delay")
    private suspend fun onEvent(body: String) {
        waitMs(100)
        scheduledPoint?.let { moveRequestDelay(it) }
        scheduledPoint = null
    }

    private fun moveToNext() = if (nextPoints.isNotEmpty()) {
        updatePosition.invoke(nextPoints.first())
        moveRequestDelay(nextPoints.first())
    } else Unit

    private fun distanceToSegment(start: Point, end: Point, point: Point): Double {
        val (x1, y1) = start
        val (x2, y2) = end
        val (px, py) = point
        val dx = x2 - x1
        val dy = y2 - y1
        if (dx == 0 && dy == 0) return hypot(px - x1.toDouble(), py - y1.toDouble())
        val t = ((px - x1) * dx + (py - y1) * dy).toDouble() / (dx * dx + dy * dy)
        val closest = when {
            t < 0 -> Pair(x1.toDouble(), y1.toDouble())
            t > 1 -> Pair(x2.toDouble(), y2.toDouble())
            else -> Pair(x1 + t * dx, y1 + t * dy)
        }
        val (cx, cy) = closest
        return hypot(px - cx, py - cy)
    }
}
