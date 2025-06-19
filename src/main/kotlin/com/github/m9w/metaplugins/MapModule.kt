package com.github.m9w.metaplugins

import com.darkorbit.BeaconCommand
import com.darkorbit.JumpInitiatedCommand
import com.darkorbit.ShipInitializationCommand
import com.github.m9w.feature.annotations.OnPackage
import com.github.m9w.metaplugins.game.GameMap
import com.github.m9w.metaplugins.game.entities.JumpgateImpl
import com.github.m9w.util.Http
import com.github.m9w.util.Http.Companion.content
import com.github.m9w.context
import com.github.m9w.metaplugins.game.Point
import com.github.m9w.metaplugins.game.PositionImpl.Companion.x
import com.github.m9w.metaplugins.game.PositionImpl.Companion.y
import com.github.m9w.metaplugins.game.entities.EntityImpl

class MapModule {
    var map: GameMap = UNKNOWN; private set
    var nextMap: GameMap = UNKNOWN; private set
    var inPeaceArea: Boolean = false; private set
    var inRadiationZone: Boolean = false; private set
    var inStation: Boolean = false; private set
    var nearJumpGate: Boolean = false; private set
    private val entitiesModule: EntitiesModule by context
    val frameRect: Pair<Point, Point> get() = entitiesModule.hero.position.run { (x-1150 to y-571) to (x+1149 to y+570) }

    @OnPackage
    private fun onShipInit(init: ShipInitializationCommand) {
        map = findMap(init.mapId)
        nextMap = UNKNOWN
    }

    @OnPackage
    private fun onJump(jump: JumpInitiatedCommand) {
        nextMap = findMap(jump.mapId)
    }

    @OnPackage
    private fun onBeacon(beacon: BeaconCommand) {
        inPeaceArea = beacon.inPeaceArea
        inRadiationZone = beacon.inRadiationZone
        inStation = beacon.inStation
        nearJumpGate = beacon.nearJumpGate
    }

    fun findMap(id: Int): GameMap = struct[id] ?: UNKNOWN

    fun findMap(name: String): GameMap = struct.values.firstOrNull { it.name == name } ?: UNKNOWN

    fun nextPortal(to: GameMap): JumpgateImpl? {
        TODO()
    }

    fun getDisplayPoint(entity: EntityImpl): Point = frameRect.let { (start, end) -> entity.position.let { (x, y) -> ((start.x - x) / end.x.toDouble() * 1920).toInt().coerceIn(0, 1920) to (1080 - (start.y - y) / end.y.toDouble() * 1080).toInt().coerceIn(0, 1080) } }

    companion object {
        val UNKNOWN: GameMap = GameMap(0, "Unknown", 21000, 13100)
        var struct = load(); private set

        private fun loadMapStruct(): Map<Int, Map<String, String>> {
            val xml = Http("https://darkorbit-22.bpsecure.com/spacemap/graphics/maps-config.xml").connect.content
            return "<map\\s+([^>]+)>".toRegex().findAll(xml).associate { tag ->
                "(\\w+)=\"([^\"]*)".toRegex().findAll(tag.groupValues[1]).map{it.groupValues}
                .map { (_, k, v) -> k to v }.associate { it }.toMutableMap()
                .let { it.remove("id")!!.toInt() to it }
            }
        }

        private fun load() = loadMapStruct().entries.associate {
            val scale = it.value["scaleFactor"]?.toDouble() ?: 1.0
            it.key to GameMap(it.key, it.value["name"] ?: "Unknown (${it.key})", (UNKNOWN.width * scale).toInt(), (UNKNOWN.height * scale).toInt())
        }

        fun updateStruct() {
            struct = load()
        }
    }
}
