package com.github.m9w

import com.darkorbit.*
import com.github.m9w.client.GameEngine
import com.github.m9w.metaplugins.game.entities.BoxImpl
import com.github.m9w.metaplugins.game.entities.HeroShip


fun GameEngine.setPetActive(isActive: Boolean) {
    if (state.ordinal < 3) return
    send<PetRequest> { this.petRequestType = if (isActive) PetRequestType.LAUNCH else PetRequestType.DEACTIVATE }
}

fun GameEngine.buyPetFuel() {
    if (state.ordinal < 3) return
    send<PetRequest> { this.petRequestType = PetRequestType.HOTKEY_BUY_FUEL }
}

fun GameEngine.changeConfig() {
    if (state.ordinal < 3) return
    send<ConfigChangeRequest>{}
}

fun GameEngine.jumpRequest() {
    if (state.ordinal < 3) return
    send<JumpRequest>{}
}

fun GameEngine.moveRequest(pos: Pair<Int, Int>, dest: Pair<Int, Int>) {
    if (state.ordinal < 3) return
    send<MoveRequest> { positionX = pos.first; positionY = pos.second; targetX = dest.first; targetY = dest.second }
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
