package com.github.m9w.metaplugins

import com.github.m9w.client.GameEngine
import com.github.m9w.feature.annotations.Inject
import com.github.m9w.feature.annotations.OnEvent
import com.github.m9w.feature.annotations.SystemEvents
import com.github.m9w.feature.waitMs
import com.github.m9w.metaplugins.game.PathTracerModule
import com.github.m9w.metaplugins.game.Point
import com.github.m9w.metaplugins.game.PositionImpl.Companion.distanceTo
import com.github.m9w.metaplugins.game.PositionImpl.Companion.x
import com.github.m9w.metaplugins.game.PositionImpl.Companion.y
import com.github.m9w.moveRequest
import java.nio.channels.CancelledKeyException
import java.util.*
import kotlin.math.hypot

@Suppress("unused")
class MoveModule {
    @Inject private lateinit var gameEngine: GameEngine
    @Inject private lateinit var pathTracer: PathTracerModule
    @Inject private lateinit var entities: EntitiesModule

    private var onComplete: (Point) -> Unit = {}
    private var updatePosition: (Point) -> Unit = {}
    private var startFrom: Point = Pair(0,0)
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
        println("Stop Point $point")
        if (nextPoints.isEmpty()) return
        val heroPosition = entities.hero.position
        if (nextPoints.first().distanceTo(heroPosition) < 50) {
            nextPoints.removeFirst()
            moveToNext(heroPosition)
            if (nextPoints.isEmpty()) onComplete.invoke(heroPosition)
        } else if(distanceToSegment(startFrom, nextPoints.first(), heroPosition) < 50) {
            moveToNext(heroPosition)
        } else {
            moveTo(nextPoints.last(), updatePosition, onComplete)
        }
    }

    fun moveTo(point: Point, block: (Point) -> Unit, update: (Point) -> Unit) {
        updatePosition = update
        onComplete = block
        val heroPosition = entities.hero.position
        if (point.x == Int.MIN_VALUE && point.y == Int.MIN_VALUE) gameEngine.moveRequest(heroPosition, heroPosition)
        else {
            startFrom = heroPosition
            nextPoints.clear()
            nextPoints.addAll(pathTracer.traceTo(point))
            moveToNext(heroPosition)
        }
    }

    private fun moveToNext(hero: Point) = if (nextPoints.isNotEmpty()) {
        updatePosition.invoke(nextPoints.first())
        gameEngine.moveRequest(hero, nextPoints.first())
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