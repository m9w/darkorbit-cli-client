package com.github.m9w

import com.darkorbit.PetRequest
import com.darkorbit.PetRequestType
import com.github.m9w.client.GameEngine


fun GameEngine.setPetActive(isActive: Boolean) {
    if (state.ordinal < 2) return
    send<PetRequest> { this.petRequestType = if (isActive) PetRequestType.LAUNCH else PetRequestType.DEACTIVATE }
}

fun GameEngine.buyPetFuel() {
    if (state.ordinal < 2) return
    send<PetRequest> { this.petRequestType = PetRequestType.HOTKEY_BUY_FUEL }
}
