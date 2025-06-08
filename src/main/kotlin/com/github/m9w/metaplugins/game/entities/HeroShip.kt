package com.github.m9w.metaplugins.game.entities

import com.darkorbit.*
import com.github.m9w.changeConfig
import com.github.m9w.metaplugins.EntitiesModule
import com.github.m9w.metaplugins.game.Point

class HeroShip(root: EntitiesModule, ship: ShipInitializationCommand) : ShipImpl(root, ship) {
    private val cargoOres = listOf(OreType.PROMETIUM, OreType.ENDURIUM, OreType.TERBIUM, OreType.PROMETID, OreType.DURANIUM, OreType.PROMERIUM, OreType.SEPROM, OreType.PALLADIUM, OreType.OSMIUM)
    var cargoSpaceMax: Int = ship.cargoSpaceMax; private set
    var cargo: Int = cargoSpaceMax - ship.cargoSpace; private set
    var premium: Boolean = ship.premium; private set
    var experiencePoints: Long = ship.ep; private set
    var honourPoints: Long = ship.honourPoints; private set
    var credits: Long = ship.credits; private set
    var uridium: Long = ship.uridium; private set
    var level: Int = ship.level; private set
    var jackpot: Float = ship.jackpot; private set
    var speed: Int = ship.speed; private set
    var jumpCupons: Int = 0; private set
    private var configVal: Int = 0
    var shipConfig get() = configVal; set(value) { if((value == 1 || value == 2) && value != configVal) root.gameEngine.changeConfig() }
    var target: EntityImpl? = null; set(value) { field = value; lastTarget = value }
    var lastTarget: EntityImpl? = null; private set

    init { update(ship) }

    override fun update(packet: ProtocolPacket) {
        super.update(packet)
        when(packet) {
            is AttributeShipSpeedUpdateCommand -> speed = packet.newSpeed
            is UpdateCargoSpaceCommand -> cargoSpaceMax = packet.cargoSpaceMax
            is AccountStatusChangeCommand -> premium = packet.premium
            is AttributeLevelUpUpdateCommand -> level = packet.level
            is AttributeCreditsUpdateCommand -> { credits = packet.credits.toLong(); uridium = packet.uridium.toLong(); jackpot = packet.jackpot}
            is AttributeOreCountUpdateCommand -> { cargo = packet.oreCountList.filter { cargoOres.contains(it.oreType.typeValue) }.sumOf { it.count.toInt() } }
            is ShipDeselectionCommand -> { target = null }
            is LegacyModule -> {
                packet.check("0|A|C|") { credits = it[0].toLong(); uridium = it[1].toLong() }
                packet.check("0|LM|ST|EP|") { experiencePoints = it[1].toLong() }
                packet.check("0|LM|ST|HON|") { honourPoints = it[1].toLong() }
                packet.check("0|LM|ST|CRE|") { credits = it[1].toLong() }
                packet.check("0|LM|ST|URI|") { uridium = it[1].toLong() }
                packet.check("0|A|JV|") { jumpCupons = it[0].toInt() }
                packet.check("0|S|CFG|") { configVal = it[0].toInt() }
            }
        }
    }

    fun moveTo(destination: Point = Int.MIN_VALUE to Int.MIN_VALUE, block: (Point) -> Unit = {}) = root.moveModule.moveTo(destination, block) { moveTo(it, speed) }

    override fun stopHandler(point: Point) = root.moveModule.stopEvent(point)

    override fun destinationTimeUpdateHandler(time: Int) = root.moveModule.destinationTimeUpdateEvent(time)

    override fun toString() = super.toString() + "Credits $credits URI $uridium\n" +
            "Cargo $cargo/$cargoSpaceMax\n"
}
