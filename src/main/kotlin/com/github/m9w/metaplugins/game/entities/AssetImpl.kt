package com.github.m9w.metaplugins.game.entities

import com.darkorbit.AssetCreateCommand
import com.darkorbit.AssetTypeModule
import com.darkorbit.Faction
import com.darkorbit.Type
import com.darkorbit.VisualModifierCommand
import com.github.m9w.metaplugins.EntitiesModule

class AssetImpl(root: EntitiesModule, asset: AssetCreateCommand) : EntityImpl(root, asset.assetId.toLong(), asset.userName, asset.posX, asset.posY, !asset.invisible) {
    override val diplomacy: Type = asset.clanRelation.type
    override val faction = Faction.entries[asset.factionId]
    override val clanTag: String = asset.clanTag
    override val designId = asset.designId
    override val modifiers: MutableList<VisualModifierCommand> = asset.modifier
    val type: AssetTypeModule = asset.type
}
