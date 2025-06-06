package com.github.m9w.metaplugins.game

import com.darkorbit.POIType
import com.github.m9w.client.GameEngine
import com.github.m9w.feature.annotations.Inject
import com.github.m9w.metaplugins.EntitiesModule
import com.github.m9w.metaplugins.MapModule
import com.github.m9w.metaplugins.game.entities.PoiImpl
import java.util.ArrayList
import java.util.HashSet
import java.util.LinkedList
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class PathTracerModule() {
    @Inject private lateinit var entities: EntitiesModule
    @Inject private lateinit var mapModule: MapModule

    private var points: MutableSet<Location> = HashSet()
    private var areas: List<PoiImpl> = ArrayList()
    private var changed = true
    private val closedList: MutableSet<Location?> = HashSet<Location?>()
    private val openList: MutableSet<Location> = HashSet<Location>()
    private val fragmentedPath: MutableList<Location> = ArrayList<Location>()

    fun onChange() {
        changed = true
    }

    fun traceTo(destination: Point): List<Point> {
        val current = entities.hero?.position?.loc ?: return listOf(destination)
        if (changed) {
            areas = entities.values.filterIsInstance<PoiImpl>().filter { it.type == POIType.NO_ACCESS }
            points = areas.flatMap { it.getPoints().map { it.loc } }.filter { !isOutOfMap(it) && canMove(it) }.toMutableSet()
            points.forEach { it.fillLineOfSight(this) }
            changed = false
        }
        val fixedCurrent = fixToClosest(current)
        val fixedDestination = fixToClosest(destination.loc)
        val list = LinkedList<Location>()
        if (hasLineOfSight(fixedCurrent, fixedDestination)
            || !calculate(this, fixedCurrent, fixedDestination, list)
        ) list.add(fixedDestination)
        if (current.distanceTo(fixedCurrent) > 50) list.addFirst(fixedCurrent)
        return list.map { Pair(it.x, it.y) }
    }

    private fun calculate(finder: PathTracerModule, from: Location, to: Location, path: LinkedList<Location>): Boolean {
        val isFromNew = finder.insertLocationImpl(from)
        val isToNew = finder.insertLocationImpl(to)
        val foundPath = build(from, to)
        if (foundPath) unfragment(from, to, path)
        if (isFromNew) finder.removeLocationImpl(from)
        if (isToNew) finder.removeLocationImpl(to)
        return foundPath
    }

    private fun fixToClosest(initial: Location): Location {
        var result = Location(initial.x, initial.y)

        var area = areaTo(result)
        if (area != null) {
            result = area.toSide(result.point).loc
            area = areaTo(result)
        }
        if (isOutOfMap(result)) {
            result = Location(
                min(max(result.x, 0), mapModule.map.width),
                min(max(result.y, 0), mapModule.map.height)
            )
            if (canMove(result)) return result
        } else if (area == null) return result

        var angle = 0.0
        var distance = 0.0
        while (distance < 20000) {
            result = Location(initial.x - (cos(angle) * distance).toInt(), initial.y - (sin(angle) * distance).toInt())
            angle += 0.306
            distance += 5.0

            if (!isOutOfMap(result) && (area == null || !area.containsPoint(result.x, result.y)) && (areaTo(result).also { area = it }) == null)
                return result
        }

        if (distance >= 20000) {
            val closest: Location? = closest(initial)
            if (closest != null) return closest
        }
        return initial
    }

    private fun insertLocationImpl(point: Location): Boolean {
        if (points.contains(point)) return false
        points.add(point)
        point.fillLineOfSight(this)
        for (other in point.lineOfSight) other.lineOfSight.add(point)
        return true
    }

    private fun removeLocationImpl(point: Location) {
        if (!points.remove(point)) return
        for (other in point.lineOfSight) other.lineOfSight.remove(point)
    }

    private fun closest(point: Location): Location? = points.minByOrNull { p -> p.distanceTo(point) }

    private fun isOutOfMap(point: Location): Boolean = mapModule.map.isOutOfMap(point.x, point.y)

    private fun canMove(point: Location): Boolean = areas.none { it.containsPoint(point.x, point.y) }

    private fun areaTo(point: Location): PoiImpl? = areas.find { it.containsPoint(point.x, point.y) }

    private fun hasLineOfSight(point1: Location, point2: Location): Boolean = areas.none { it.intersectsLine(point1.point, point2.point) }

    private fun build(from: Location, to: Location): Boolean {
        closedList.clear()
        openList.clear()
        fragmentedPath.clear()
        from.set(from.distanceTo(to).toInt(), 0, 0)
        openList.add(from)
        fragmentedPath.add(from)

        var current: Location? = from
        while (current != to && openList.isNotEmpty()) {
            openList.remove(current)
            closedList.add(current)
            current?.let {
                it.lineOfSight.forEach { neighbor ->
                    if (closedList.contains(neighbor)) return@forEach
                    val g = it.g + it.distanceTo(neighbor).toInt()
                    if (!openList.add(neighbor) && g >= neighbor.g) return@forEach
                    neighbor.set(g + to.distanceTo(neighbor).toInt(), g,current.s + 1)
                    fragmentedPath.add(neighbor)
                }
            }
            current = openList.minByOrNull { it.f }
        }
        return current == to
    }

    private fun unfragment(from: Location?, to: Location, target: LinkedList<Location>) {
        var current = to
        while (current != from) {
            target.addFirst(current)
            current = fragmentedPath.filter { current.lineOfSight.contains(it) && current.s == it.s + 1 }
                .minBy { it.g + it.distanceTo(current) }
        }
    }

    private class Location(val x: Int, val y: Int) {
        val point = x to y
        var f: Int = 0
        var g: Int = 0
        var s: Int = 0

        var lineOfSight: MutableSet<Location> = HashSet()

        override fun hashCode(): Int = 32 * x + y

        override fun toString(): String = "Location($x, $y)"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Location

            if (x != other.x) return false
            if (y != other.y) return false

            return true
        }

        fun set(f: Int, g: Int, s: Int) {
            this.f = f
            this.g = g
            this.s = s
        }

        fun fillLineOfSight(finder: PathTracerModule) {
            lineOfSight.clear()
            for (point in finder.points)
                if (point !== this && finder.hasLineOfSight(point, this))
                    lineOfSight.add(point)
        }

        fun distanceTo(other: Location): Double {
            val ox = other.x - x
            val oy = other.y - y
            return sqrt((ox * ox + oy * oy).toDouble())
        }
    }

    companion object {
        private val Point.loc get() = Location(first, second)
    }
}