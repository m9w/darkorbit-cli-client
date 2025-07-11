package com.github.m9w.metaplugins

import com.darkorbit.InventoryItemUpdatedCommand
import com.darkorbit.MenuActionRequest
import com.darkorbit.MenuActionRequestActionType
import com.darkorbit.SourceType
import com.github.m9w.client.GameEngine
import com.github.m9w.context
import com.github.m9w.feature.annotations.OnPackage

class AmmoBuy {
    private val gameEngine: GameEngine by context

    @OnPackage
    fun checkammo(event: InventoryItemUpdatedCommand) {
        val item = event.item
        if (item.lootId == "ammunition_laser_lcb-10") {
            if (item.amount >= 1000) {
                gameEngine.send<MenuActionRequest> {
                    actionType = MenuActionRequestActionType.ACTIVATE
                    menuItemId = "buy_ammunition_laser_lcb-10"
                    sourceType = SourceType.SLOT_BAR
                }
            }
        }
    }
}
