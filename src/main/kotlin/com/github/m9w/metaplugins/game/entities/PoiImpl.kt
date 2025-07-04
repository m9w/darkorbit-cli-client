package com.github.m9w.metaplugins.game.entities

import com.darkorbit.*
import com.github.m9w.metaplugins.EntitiesModule
import com.github.m9w.metaplugins.game.Point
import kotlin.collections.chunked
import kotlin.math.*

class PoiImpl(root: EntitiesModule, id: Long, poi: MapAddPOICommand) : EntityImpl(root, id, poi.poiId, poi.shapeCoordinates[0], poi.shapeCoordinates[1]) {
    val shapeType: ShapeType = poi.shape
    val cords: List<Point> = if (shapeType != ShapeType.CIRCLE) poi.shapeCoordinates.chunked(2).map { it[0] to it[1] } else listOf(-1 to -1)
    private val minX: Int = cords.minOf { it.x }
    private val minY: Int = cords.minOf { it.y }
    private val maxX: Int = cords.maxOf { it.x }
    private val maxY: Int = cords.maxOf { it.y }
    val radius: Int = if (shapeType == ShapeType.CIRCLE) poi.shapeCoordinates[2] else -1
    val type: POIType = poi.poiType.typeValue
    val design: POIDesign = poi.design.designValue
    val typeSpec: String = poi.poiTypeSpecification
    val active: Boolean = poi.active
    val inverted: Boolean = poi.inverted

    override fun toString() = super.toString() +
            "Type $type\n" +
            "Design $design\n" +
            (if (shapeType == ShapeType.CIRCLE) "Radius $radius\n" else "Coordinates $cords\n") +
            (if (typeSpec.isNotEmpty()) "Specification $typeSpec\n" else "") +
            (if (!active) "Inactive\n" else "") +
            (if (inverted) "Inverted\n" else "")

    fun getPoints(): List<Point> {
        return if (shapeType == ShapeType.CIRCLE) {
            val (x, y) = position
            val pointCount = (Math.PI * 2 * radius / 100).toInt()
            (0 until pointCount).map {
                val angle = it * (Math.PI * 2 / pointCount)
                (x - cos(angle) * (radius + 25)).toInt() to (y - sin(angle) * (radius + 25)).toInt()
            }
        } else expandPolygon(cords)
    }

    fun toSide(point: Point): Point {
        return if (shapeType == ShapeType.CIRCLE) {
            val (x, y) = position
            val angle = atan2((y - point.x).toDouble(), (x - point.y).toDouble())
            (x - cos(angle) * radius).toInt() to (y - sin(angle) * radius).toInt()
        } else {
            val diffLeft: Int = point.x - minX + 75
            val diffRight: Int = maxX - point.x - 75
            val diffTop: Int = point.y - minY + 75
            val diffBottom: Int = maxY - point.y - 75
            var newX: Int = point.x
            var newY: Int = point.y
            when (min(min(diffBottom, diffTop), min(diffLeft, diffRight))) {
                diffTop -> newY = minY - MARGIN + 75
                diffBottom -> newY = maxY + MARGIN  - 75
                diffLeft -> newX = minX - MARGIN + 75
                else -> newX = maxX + MARGIN - 75
            }
            newX to newY
        }
    }

    fun intersectsLine(start: Point, end: Point): Boolean {
        return if (shapeType == ShapeType.CIRCLE) {
            val x1 = start.x.toDouble()
            val y1 = start.y.toDouble()
            val x2 = end.x.toDouble()
            val y2 = end.y.toDouble()
            val (cx: Int, cy: Int) = position
            val dx = x2 - x1
            val dy = y2 - y1
            val a = dx * dx + dy * dy
            val b = 2 * (dx * (x1 - cx) + dy * (y1 - cy))
            val c = (x1 - cx) * (x1 - cx) + (y1 - cy) * (y1 - cy) - radius * radius
            val discriminant = b * b - 4 * a * c
            if (discriminant < 0) return false
            val sqrtDisc = sqrt(discriminant)
            val t1 = (-b - sqrtDisc) / (2 * a)
            val t2 = (-b + sqrtDisc) / (2 * a)
            (t1 >= 0 && t1 <= 1) || (t2 >= 0 && t2 <= 1)
        } else {
            for (i in cords.indices) {
                val a = cords[i]
                val b = cords[(i + 1) % cords.size]
                if (linesIntersect(a, b, start, end)) return true
            }
            false
        }
    }

    fun containsPoint(point: Point): Boolean {
        val (x, y) = point
        return if (shapeType == ShapeType.CIRCLE) {
            val (cx, cy) = position
            val x = x - cx
            val y = y - cy
            x * x + y * y < radius * radius
        } else {
            if (cords.size <= 2) return false
            var result = false
            var j = cords.size - 1
            for (i in cords.indices) {
                val a = cords[i]
                val b = cords[j]
                if (a.y > y != b.y > y) {
                    val xIntersect = (b.x - a.x) * (y - a.y) - (x - a.x) * (b.y - a.y)
                    if (b.y > a.y && xIntersect < 0 || b.y <= a.y && xIntersect > 0) result = !result
                }
                j = i
            }
            result
        }
    }

    private fun expandPolygon(polygon: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        if (polygon.size < 3) return polygon.map { it.first to it.second }

        val size = polygon.size
        var area = 0.0
        for (i in polygon.indices) {
            val j = (i + 1) % size
            val (x1, y1) = polygon[i]
            val (x2, y2) = polygon[j]
            area += x1 * y2 - x2 * y1
        }
        area /= 2.0
        val sign = if (area > 0) 1.0 else -1.0
        return polygon.indices.map { i ->
            val prev = polygon[(i - 1 + size) % size]
            val curr = polygon[i]
            val next = polygon[(i + 1) % size]

            val dx1 = curr.first - prev.first
            val dy1 = curr.second - prev.second
            val dx2 = next.first - curr.first
            val dy2 = next.second - curr.second

            val norm1x = sign * dy1
            val norm1y = -sign * dx1
            val norm2x = sign * dy2
            val norm2y = -sign * dx2

            var unit1x = 0.0
            var unit1y = 0.0
            val len1 = sqrt(norm1x * norm1x + norm1y * norm1y)
            if (len1 > 1e-10) {
                unit1x = norm1x / len1
                unit1y = norm1y / len1
            }

            var unit2x = 0.0
            var unit2y = 0.0
            val len2 = sqrt(norm2x * norm2x + norm2y * norm2y)
            if (len2 > 1e-10) {
                unit2x = norm2x / len2
                unit2y = norm2y / len2
            }

            val sumx = unit1x + unit2x
            val sumy = unit1y + unit2y
            val lenSum = sqrt(sumx * sumx + sumy * sumy)

            val dirX: Double
            val dirY: Double
            if (lenSum < 1e-10) {
                if (len1 > 1e-10) {
                    dirX = unit1x
                    dirY = unit1y
                } else if (len2 > 1e-10) {
                    dirX = unit2x
                    dirY = unit2y
                } else {
                    dirX = 0.0
                    dirY = 0.0
                }
            } else {
                dirX = sumx / lenSum
                dirY = sumy / lenSum
            }

            (curr.first + dirX * MARGIN).toInt() to (curr.second + dirY * MARGIN).toInt()
        }
    }

    companion object {
        private const val MARGIN = 50
        private fun linesIntersect(x1: Int, y1: Int, x2: Int, y2: Int, x3: Int, y3: Int, x4: Int, y4: Int): Boolean {
            val v = (y4 - y3) * (x2 - x1) - (x4 - x3) * (y2 - y1)
            if (v == 0) return false
            val uA = (x4 - x3) * (y1 - y3) - (y4 - y3) * (x1 - x3)
            val uB = (x2 - x1) * (y1 - y3) - (y2 - y1) * (x1 - x3)
            if (uA < 0 && v > 0 || uA > 0 && v < 0 || uB < 0 && v > 0 || uB > 0 && v < 0) return false
            return abs(uA) <= abs(v) && abs(uB) <= abs(v)
        }

        private fun linesIntersect(start1: Point, end1: Point, start2: Point, end2: Point): Boolean {
            return linesIntersect(start1.x, start1.y, end1.x, end1.y, start2.x, start2.y, end2.x, end2.y)
        }
    }
}