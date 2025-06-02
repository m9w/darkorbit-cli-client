package com.github.m9w

import com.darkorbit.ConfigChangeRequest
import com.darkorbit.JumpRequest
import com.darkorbit.MoveRequest
import com.darkorbit.PetRequest
import com.darkorbit.PetRequestType
import com.github.m9w.client.GameEngine


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
