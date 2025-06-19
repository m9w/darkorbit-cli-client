package com.github.m9w.metaplugins.game.entities

import com.darkorbit.*
import com.github.m9w.abortAttackRequest
import com.github.m9w.attackRequest
import com.github.m9w.metaplugins.EntitiesModule
import com.github.m9w.protocol.Factory
import com.github.m9w.selectRequest

open class ShipImpl(root: EntitiesModule, ship: ShipCreateCommand) : EntityImpl(root, ship.userId.toLong(), ship.userName, ship.x, ship.y, ship.cloaked) {
    val isNpc: Boolean = ship.npc
    override val diplomacy: Type = ship.clanDiplomacy.type
    override val faction = Faction.entries[ship.factionId]
    override val clanTag: String = ship.clanTag
    override val clanId: Int = ship.clanId
    override val modifiers: MutableList<VisualModifierCommand> = ship.modifier
    val motherShip get() = root[motherShipId]
    val specialNpcType: String = ship.specialNpcType
    val typeId: String = ship.typeId
    private var motherShipId: Long = ship.motherShipId.toLong()
    var laserAttackTarget: EntityImpl? = null; private set
    var formation: Int? = null; private set

    constructor(root: EntitiesModule, ship: ShipInitializationCommand)
            : this(root, Factory.build(ShipCreateCommand::class, Factory.getData(ship)).apply {
        clanDiplomacy = clanDiplomacy.apply { type = Type.NONE }
    })

    override fun canInvoke() = root.mapModule.frameRect.let { (start, end) ->
        position.let { (x, y) -> x in start.x..end.x && y in start.y..end.y }
    }

    override fun invoke(attack: Boolean): Boolean {
        return if (canInvoke()) {
            if (root.hero.target != this) root.gameEngine.selectRequest(root.hero, this)
            return if (root.hero.laserAttackTarget != this && attack) {
                root.gameEngine.attackRequest(this)
                true
            } else if (root.hero.laserAttackTarget == this && !attack) {
                root.gameEngine.abortAttackRequest()
                true
            } else false
        } else {
            root.hero.moveTo(this.position)
            false
        }
    }

    override fun update(packet: ProtocolPacket) {
        super.update(packet)
        when (packet) {
            is VisualModifierCommand -> modifiers.removeIf { it.modifier == packet.modifier }.also { if (packet.activated) modifiers.add(packet) }
            is AttackLaserRunCommand -> laserAttackTarget = root[packet.targetId]
            is AttackAbortLaserCommand -> laserAttackTarget = null
            is DroneFormationChangeCommand -> formation = packet.selectedFormationId
        }
    }

    override fun toString() = super.toString() + (if(isNpc) "NPC\n" else "") +
            (if (typeId.isNotEmpty()) "Type $typeId\n" else "") +
            (if (specialNpcType.isNotEmpty()) "Special type $specialNpcType\n" else "") +
            (if (motherShip != null) "Mother ship ${motherShip?.name}\n" else "")
}
