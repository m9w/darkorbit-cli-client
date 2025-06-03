package com.github.m9w.metaplugins.game.entities

import com.darkorbit.*
import com.github.m9w.assetActivate
import com.github.m9w.instantRepairActivate
import com.github.m9w.metaplugins.EntitiesModule
import com.github.m9w.questGiverActivate

class AssetImpl(root: EntitiesModule, asset: AssetCreateCommand) : EntityImpl(root, asset.assetId.toLong(), asset.userName, asset.posX, asset.posY, !asset.invisible) {
    override val diplomacy: Type = asset.clanRelation.type
    override val faction = Faction.entries[asset.factionId]
    override val clanTag: String = asset.clanTag
    override val clanId: Int = asset.clanId
    override val designId = asset.designId
    override val modifiers: MutableList<VisualModifierCommand> = asset.modifier
    override val isSafe: Boolean get() = super.isSafe || faction == Faction.NONE
    val type: AssetType = asset.type.typeValue
    private var canInvoke = false

    override fun update(packet: ProtocolPacket) {
        super.update(packet)
        when (packet) {
            is MapAssetActionAvailableCommand -> canInvoke = packet.activatable && packet.state == AvailabilityState.ON
            is QuestGiversAvailableCommand -> canInvoke = packet.questGivers.count { it.mapObjectId == id.toInt() } != 0
        }
    }

    override fun canInvoke() = canInvoke

    override fun invoke(): Boolean {
        if (!canInvoke()) return false
        when(type) {
            AssetType.INSTANT_REPAIR_HOME,
            AssetType.INSTANT_REPAIR_OUTPOST -> root.gameEngine.instantRepairActivate(id.toInt())
            AssetType.QUESTGIVER -> root.gameEngine.questGiverActivate(id.toInt())
            else -> root.gameEngine.assetActivate(id.toInt())
        }
        return true
    }

    override fun toString() = super.toString() + "${type.name}\n" + (if(canInvoke) "Ready to use\n" else "")
}
