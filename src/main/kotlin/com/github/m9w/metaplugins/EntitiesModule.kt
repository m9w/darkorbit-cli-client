package com.github.m9w.metaplugins

import com.darkorbit.*
import com.github.m9w.client.GameEngine
import com.github.m9w.feature.annotations.Inject
import com.github.m9w.feature.annotations.OnPackage
import com.github.m9w.metaplugins.game.entities.AssetImpl
import com.github.m9w.metaplugins.game.entities.EntityImpl
import com.github.m9w.metaplugins.game.entities.HeroShip
import com.github.m9w.metaplugins.game.entities.JumpgateImpl
import com.github.m9w.metaplugins.game.entities.ShipImpl

@Suppress("unused")
class EntitiesModule(private val entities: MutableMap<Long, EntityImpl> = HashMap()) : Map<Long, EntityImpl> by entities {
    @Inject lateinit var gameEngine: GameEngine
    @Inject lateinit var mapModule: MapModule

    val hero: HeroShip? get() = values.firstOrNull { it is HeroShip } as HeroShip?

    @OnPackage
    private fun onShipCreate(ship: ShipCreateCommand) { this[ship.userId] = ShipImpl(this, ship) }

    @OnPackage
    private fun onMoveCommand(move: MoveCommand) = this[move.userId]?.update(move)

    @OnPackage
    private fun onVisualModifier(visualModifier: VisualModifierCommand) = this[visualModifier.userId]?.update(visualModifier)

    @OnPackage
    private fun onLaserAttack(laser: AttackLaserRunCommand) = this.getLong<ShipImpl>(laser.attackerId)?.update(laser)

    @OnPackage
    private fun onLaserAttackAbort(laser: AttackAbortLaserCommand) = this.getLong<ShipImpl>(laser.uid)?.update(laser)

    @OnPackage
    private fun onJumpgateCreate(jumpgate: JumpgateCreateCommand) { this[jumpgate.gateId] = JumpgateImpl(this, jumpgate) }

    @OnPackage
    private fun onHeroInit(init: ShipInitializationCommand) { entities.clear(); this[init.userId] = HeroShip(this, init) }

    @OnPackage
    private fun onAssetCreate(createAsset: AssetCreateCommand) { this[createAsset.assetId] = AssetImpl(this, createAsset) }

    @OnPackage
    private fun onAssetActivation(assetActivation: MapAssetActionAvailableCommand) { this[assetActivation.mapAssetId]?.update(assetActivation) }

    @OnPackage
    private fun onShipRemove(ship: ShipRemoveCommand) = remove(ship.userId)

    @OnPackage
    private fun onJumpgateRemove(jumpgate: JumpGateRemoveCommand) = remove(jumpgate.gateId)

    @OnPackage
    private fun onAssetRemove(remove: AssetRemoveCommand) = remove(remove.uid)

    operator fun get(id: Int) = entities[id.toLong()]
    fun <T : EntityImpl> getLong(id: Int): T? = entities[id.toLong()].let { it as? T }
    private operator fun set(id: Int, entityImpl: EntityImpl) { entities[id.toLong()] = entityImpl }
    private fun remove(id: Int) { entities.remove(id.toLong()) }
}
