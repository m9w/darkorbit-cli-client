package com.github.m9w.metaplugins.game.entities

import com.darkorbit.*
import com.github.m9w.metaplugins.EntitiesModule

class PoiImpl(root: EntitiesModule, poi: MapAddPOICommand) : EntityImpl(root, 0, poi.poiId, poi.shapeCoordinates[0], poi.shapeCoordinates[1]) {
    val type: POIType = poi.poiType.typeValue
    val design: POIDesign = poi.design.designValue
    val shapeType: ShapeType = poi.shape
    val radius: Int = if (shapeType == ShapeType.CIRCLE) poi.shapeCoordinates[2] else -1
    val cords: List<Pair<Int, Int>> = if (shapeType != ShapeType.CIRCLE) poi.shapeCoordinates.chunked(2).map { it[0] to it[1] } else emptyList()
    val active: Boolean = poi.active
    val inverted: Boolean = poi.inverted

    override fun toString() = super.toString() +
            "Type $type\n" +
            "Design $design\n" +
            if (shapeType == ShapeType.CIRCLE) "Radius $radius\n" else "Coordinates $cords\n" +
            "Active $active" +
            "Inverted $inverted"
}