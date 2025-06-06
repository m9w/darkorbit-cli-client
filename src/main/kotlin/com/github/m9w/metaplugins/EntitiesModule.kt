package com.github.m9w.metaplugins

import com.darkorbit.*
import com.github.m9w.client.GameEngine
import com.github.m9w.feature.annotations.Inject
import com.github.m9w.feature.annotations.OnPackage
import com.github.m9w.metaplugins.game.entities.*
import com.github.m9w.metaplugins.game.PathTracerModule

@Suppress("unused")
class EntitiesModule(private val entities: MutableMap<Long, EntityImpl> = HashMap()) : Map<Long, EntityImpl> by entities {
    @Inject lateinit var gameEngine: GameEngine
    @Inject lateinit var mapModule: MapModule
    @Inject private lateinit var pathTracer: PathTracerModule

    val hero: HeroShip? get() = values.firstOrNull { it is HeroShip } as HeroShip?

    @OnPackage
    private fun onBoxCreate(box: AddBoxCommand) { BoxImpl(this, box).let { entities[it.id] = it } }

    @OnPackage
    private fun onOreCreate(ore: AddOreCommand) { BoxImpl(this, ore).let { entities[it.id] = it } }

    @OnPackage
    private fun onMineCreate(mine: AddMineCommand) { BoxImpl(this, mine).let { entities[it.id] = it } }

    @OnPackage
    private fun onShipCreate(ship: ShipCreateCommand) { this[ship.userId] = ShipImpl(this, ship) }

    @OnPackage
    private fun onPoiCreate(poi: MapAddPOICommand) {
        entities[poi.poiId.hashCode().toLong() - Int.MAX_VALUE.toLong()] = PoiImpl(this, poi)
        if (poi.poiType.typeValue == POIType.NO_ACCESS) pathTracer.onChange()
    }

    @OnPackage
    private fun onPoiCreate(poi: MapAddControlPOIZoneCommand) { entities[poi.poiId.hashCode().toLong() - Int.MAX_VALUE.toLong()]  = PoiImpl(this, poi) }

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
    private fun onJumpgateInitiation(jumpgate: JumpInitiatedCommand) { this[jumpgate.gateId]?.update(jumpgate) }

    @OnPackage
    private fun onHeroInit(init: ShipInitializationCommand) { entities.clear(); this[init.userId] = HeroShip(this, init) }

    @OnPackage
    private fun onAssetCreate(createAsset: AssetCreateCommand) { this[createAsset.assetId] = AssetImpl(this, createAsset) }

    @OnPackage
    private fun onAssetActivation(assetActivation: MapAssetActionAvailableCommand) { this[assetActivation.mapAssetId]?.update(assetActivation) }

    @OnPackage
    private fun onQuestGiverActivation(questGiver: QuestGiversAvailableCommand) = this.values.filter {it is AssetImpl && it.type == AssetType.QUESTGIVER }.forEach { it.update(questGiver) }

    @OnPackage
    private fun onShipRemove(ship: ShipRemoveCommand) = remove(ship.userId)

    @OnPackage
    private fun onJumpgateRemove(jumpgate: JumpGateRemoveCommand) = remove(jumpgate.gateId)

    @OnPackage
    private fun onAssetRemove(remove: AssetRemoveCommand) = remove(remove.uid)

    @OnPackage
    private fun onMineRemove(remove: RemoveMineCommand) = remove(remove.hash)

    @OnPackage
    private fun onCollectableRemove(remove: RemoveCollectableCommand) = remove(remove.hash)

    @OnPackage
    private fun onPoiRemove(poi: MapRemovePOICommand) { entities.remove(poi.poiId.hashCode().toLong() - Int.MAX_VALUE.toLong()) }

    operator fun get(id: Int) = entities[id.toLong()]
    fun <T : EntityImpl> getLong(id: Int): T? = entities[id.toLong()].let { it as? T }
    private operator fun set(id: Int, entityImpl: EntityImpl) { entities[id.toLong()] = entityImpl }
    private fun remove(hash: String) { entities.remove(hash.toLong(36)+100000000) }
    private fun remove(id: Int) { entities.remove(id.toLong()) }
}
