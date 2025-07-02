package com.github.m9w

import com.darkorbit.*
import com.github.m9w.client.GameEngine
import com.github.m9w.metaplugins.game.Point
import com.github.m9w.metaplugins.game.PositionImpl.Companion.x
import com.github.m9w.metaplugins.game.PositionImpl.Companion.y
import com.github.m9w.metaplugins.game.entities.BoxImpl
import com.github.m9w.metaplugins.game.entities.EntityImpl
import com.github.m9w.metaplugins.game.entities.HeroShip


fun GameEngine.setPetActive(isActive: Boolean) {
    if (state.ordinal < 3) return
    send<PetRequest> { this.petRequestType = if (isActive) PetRequestType.LAUNCH else PetRequestType.DEACTIVATE }
}

fun GameEngine.setPetGear(gearType: PetGearType, optional: Int) {
    if (state.ordinal < 3 || gearType == PetGearType.BEHAVIOR || gearType == PetGearType.ADMIN) return
    send<PetGearActivationRequest> {
        gearTypeToActivate = gearTypeToActivate.apply { typeValue = gearType }
        optParam = optional.toShort()
    }
}

fun GameEngine.buyPetFuel() {
    if (state.ordinal < 3) return
    send<PetRequest> { this.petRequestType = PetRequestType.HOTKEY_BUY_FUEL }
}

fun GameEngine.changeConfig(config: Int) {
    if (state.ordinal < 3) return
    send<LegacyModule>{ message = "S|CFG|$config|$userIdAndSid" }
}

fun GameEngine.jumpRequest() {
    if (state.ordinal < 3) return
    send<JumpRequest>{}
}

fun GameEngine.moveRequest(pos: Point, dest: Point) {
    if (state.ordinal < 3) return
    send<MoveRequest> { positionX = pos.x; positionY = pos.y; targetX = dest.x; targetY = dest.y }
}

fun GameEngine.collectRequest(hero: HeroShip, box: BoxImpl) {
    if (state.ordinal < 3) return
    val (hx, hy) = hero.position
    val (bx, by) = box.position
    send<CollectBoxRequest> {
        posX = hx; posY = hy
        boxX = bx; boxY = by
        itemHash = box.hash
    }
}

fun GameEngine.selectRequest(hero: HeroShip, target: EntityImpl) {
    if (state.ordinal < 3) return
    val (hx, hy) = hero.position
    val (tx, ty) = target.position
    val (dx, dy) = target.root.mapModule.getDisplayPoint(target)
    send<ShipSelectRequest> {
        posX = hx; posY = hy
        targetX = tx; targetY = ty - (tx + ty + hx + hy) % 8
        targetId = target.id.toInt()
        clickX = dx; clickY = dy
        radius = 45
    }
}

fun GameEngine.attackRequest(target: EntityImpl) {
    if (state.ordinal < 3) return
    val (tx, ty) = target.position
    send<AttackLaserRequest> {
        targetX = tx; targetY = ty
        targetId = target.id.toInt()
    }
}

fun GameEngine.abortAttackRequest() {
    if (state.ordinal < 3) return
    send<AttackAbortLaserRequest> {}
}

fun GameEngine.instantRepairActivate(id: Int) {
    if (state.ordinal < 3) return
    send<InstantRepairRequest> { repairAssetId = id }
}

fun GameEngine.questGiverActivate(id: Int) {
    if (state.ordinal < 3) return
    send<QuestGiverApproachedRequest> { questGiverId = id }
}

fun GameEngine.assetActivate(id: Int) {
    if (state.ordinal < 3) return
    send<MapAssetActivationRequest> { mapAssetId = id }
}
