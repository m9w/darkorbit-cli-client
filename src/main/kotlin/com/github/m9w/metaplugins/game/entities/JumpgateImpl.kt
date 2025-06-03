package com.github.m9w.metaplugins.game.entities

import com.darkorbit.*
import com.github.m9w.jumpRequest
import com.github.m9w.metaplugins.EntitiesModule

class JumpgateImpl(root: EntitiesModule, jumpgate: JumpgateCreateCommand) : EntityImpl(root, jumpgate.gateId.toLong(), "Jumpgate", jumpgate.x, jumpgate.y) {
    override val designId: Int = jumpgate.designId
    override val faction = Faction.entries[jumpgate.factionId]
    var initiated: Boolean = false; private set
    private var activable: Boolean = false

    override fun update(packet: ProtocolPacket) {
        super.update(packet)
        when(packet) {
            is MapAssetActionAvailableCommand -> activable = packet.activatable
            is JumpInitiatedCommand -> initiated = true
        }
    }

    override fun canInvoke(): Boolean {
        return activable
    }

    override fun invoke(): Boolean {
        return if (canInvoke()) {
            root.gameEngine.jumpRequest()
            true
        } else false
    }

    override fun toString() = super.toString() +
            (if(activable) "Ready to use\n" else "") +
            (if(initiated) "Initiated\n" else "")

}
