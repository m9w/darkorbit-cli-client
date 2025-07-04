package com.github.m9w.metaplugins

import com.darkorbit.*
import com.github.m9w.context
import com.github.m9w.client.GameEngine
import com.github.m9w.feature.annotations.OnPackage
import com.github.m9w.metaplugins.game.entities.*
import com.github.m9w.protocol.Factory

@Suppress("unused")
class EntitiesModule(private val entities: MutableMap<Long, EntityImpl> = HashMap()) : Map<Long, EntityImpl> by entities {
    val gameEngine: GameEngine by context
    val mapModule: MapModule by context
    val moveModule: MoveModule by context
    val pathTracer: PathTracerModule by context
    lateinit var hero: HeroShip; private set

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
        entities[poi.poiId.id] = PoiImpl(this, poi.poiId.id, poi)
        if (poi.poiType.typeValue == POIType.NO_ACCESS) pathTracer.onChange()
    }

    @OnPackage
    private fun onPoiCreate(poi: MapAddControlPOIZoneCommand) { entities[poi.poiId.id]  = PoiImpl(this, poi.poiId.id, poi) }

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
    private fun onHeroInit(init: ShipInitializationCommand) { entities.clear(); pathTracer.onChange(); this[init.userId] = HeroShip(this, init).also { hero = it } }

    @OnPackage
    private fun onHeroInit(speedUpdate: AttributeShipSpeedUpdateCommand) { hero.update(speedUpdate) }

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

    @OnPackage
    private fun onHealthUpdate(health: HealthModule) { hero.update(health) }
    @OnPackage
    private fun onHealthUpdate(health: ShipSelectionCommand) { this[health.userId]?.update(health) }
    @OnPackage
    private fun onHealthUpdate(health: HealCommand) { this[health.healedId]?.update(health) }
    @OnPackage
    private fun onHealthUpdate(health: HitpointInfoCommand) { hero.update(health) }
    @OnPackage
    private fun onHealthUpdate(health: AttackHitCommand) { this[health.victimId]?.update(health) }
    @OnPackage
    private fun onHealthUpdate(health: PetStatusCommand) { this[health.petId]?.update(health) }
    @OnPackage
    private fun onHealthUpdate(health: AttributeShieldUpdateCommand) { hero.update(health) }
    @OnPackage
    private fun onHealthUpdate(health: AssetInfoCommand) { this[health.assetId]?.update(health) }
    @OnPackage
    private fun onHealthUpdate(health: AttackHitAssetCommand) { this[health.assetId]?.update(health) }
    @OnPackage
    private fun onHealthUpdate(health: StationModuleModule) { this[health.asteroidId]?.update(health) }
    @OnPackage
    private fun onHealthUpdate(health: ModuleStatusUpdateCommand) { this[health.asteroidId]?.update(health) }
    @OnPackage
    private fun onHealthUpdate(health: AttackHitNoLockCommand) { this[health.victimId]?.update(health) }
    @OnPackage
    private fun onHealthUpdate(health: AttributeHitpointUpdateCommand) { hero.update(health) }
    @OnPackage
    private fun onHealthUpdate(health: PetHitpointsUpdateCommand) { hero.pet?.update(health) }
    @OnPackage
    private fun onHealthUpdate(health: PetShieldUpdateCommand) { hero.pet?.update(health) }
    @OnPackage
    private fun onFuelUpdate(fuel: PetFuelUpdateCommand) { hero.pet?.update(fuel) }
    @OnPackage
    private fun onExpUpdate(expUpdate: PetExperiencePointsUpdateCommand) { hero.pet?.update(expUpdate) }
    @OnPackage
    private fun onLevelUpdate(levelUpdate: PetLevelUpdateCommand) { hero.pet?.update(levelUpdate) }
    @OnPackage
    private fun onHeatUpdate(heat: PetHeatUpdateCommand) { hero.pet?.update(heat) }
    @OnPackage
    private fun onPetActivation(activation: PetHeroActivationCommand) { this[activation.petId] = HeroPet(this, activation).also { hero.pet = it } }
    @OnPackage
    private fun onPetDeactivation(deactivation: PetDeactivationCommand) { if (this::hero.isInitialized && hero.pet?.id?.toInt() == deactivation.petId) hero.pet = null; remove(deactivation.petId) }
    @OnPackage
    private fun onPetDestroyed(destroyed: PetIsDestroyedCommand) { hero.pet?.update(destroyed) }
    @OnPackage
    private fun onGearAdd(gearAdd: PetGearAddCommand) { hero.pet?.update(gearAdd) }
    @OnPackage
    private fun onLocatorInit(locator: PetLocatorGearInitializationCommand) { hero.pet?.update(locator) }
    @OnPackage
    private fun onGearRemove(gearRemove: PetGearRemoveCommand) { hero.pet?.update(gearRemove) }
    @OnPackage
    private fun onGearReset(gearReset: PetGearResetCommand) { hero.pet?.update(gearReset) }
    @OnPackage
    private fun onGearSelect(gearSelect: PetGearSelectCommand) { hero.pet?.update(gearSelect) }
    @OnPackage
    private fun onRepairComplete(repairComplete: PetRepairCompleteCommand) { hero.pet?.update(repairComplete) }
    @OnPackage
    private fun onBlockUI(blockUI: PetBlockUICommand) { hero.pet?.update(blockUI) }
    @OnPackage
    private fun onIdleMode(idleMode: PetIdleModeCommand) { get(idleMode.petId)?.update(idleMode) }
    @OnPackage
    private fun onLegacyEvent(event: LegacyModule) { if (this::hero.isInitialized) hero.update(event) }
    @OnPackage
    private fun onCaptchaTriggerEvent(event: CaptchaTriggerCommand) {
        onPoiCreate(Factory.build<MapAddPOICommand> {
            poiId = "Captcha zone"
            shape = ShapeType.CIRCLE
            inverted = true
            design = design.apply { designValue = POIDesign.NONE }
            poiType = poiType.apply { typeValue = POIType.FACTION_NO_ACCESS }
            poiTypeSpecification = "${event.type} [${event.blackBox}, ${event.redBox}] ${event.captchaTimer}"
            shapeCoordinates = mutableListOf(event.posX, event.posY, event.Radius)
        })
    }
    @OnPackage
    private fun onCaptchaResolvedEvent(event: CaptchaResolvedCommand) = entities.remove("Captcha zone".id)

    private val String.id get() = hashCode().toLong() - Int.MAX_VALUE.toLong()
    operator fun get(id: Int) = entities[id.toLong()]
    fun <T : EntityImpl> getLong(id: Int): T? = entities[id.toLong()].let { it as? T }
    private operator fun set(id: Int, entityImpl: EntityImpl) { entities[id.toLong()] = entityImpl }
    private fun remove(hash: String) { entities.remove(hash.toLong(36)+Int.MAX_VALUE) }
    private fun remove(id: Int) { entities.remove(id.toLong()) }
}
