package com.github.m9w.metaplugins.logic

import com.darkorbit.AddBoxCommand
import com.github.m9w.feature.annotations.Inject
import com.github.m9w.feature.annotations.OnPackage
import com.github.m9w.feature.timePrefix
import com.github.m9w.metaplugins.EntitiesDebugUiModule
import com.github.m9w.metaplugins.EntitiesModule
import com.github.m9w.metaplugins.game.PositionImpl
import com.github.m9w.metaplugins.game.entities.BoxImpl
import java.util.*
import kotlin.concurrent.schedule

class BoxCollector {

    @Inject
    lateinit var entitiesModule: EntitiesModule
    @Inject
    lateinit var uiModule: EntitiesDebugUiModule
    private var movedToBoxOnce = false
    private var targetBox: BoxImpl? = null


    @OnPackage
    private fun getbox_info(getbox: AddBoxCommand) {
        if (movedToBoxOnce) return
        if (getbox.boxType == "BONUS_BOX") {
            val hero = entitiesModule.hero ?: return

            println("[$timePrefix] Box found at X: ${getbox.x} and Y: ${getbox.y}")


            val box = BoxImpl(entitiesModule, getbox)
            targetBox = box
            movedToBoxOnce = true
            hero.moveTo(PositionImpl(getbox.x, getbox.y))

            Timer().schedule(delay = 1000, period = 500) {
                if (box.invoke()) {
                    println("[$timePrefix] Box eingesammelt.")
                    uiModule.incrementBoxCount()
                    movedToBoxOnce = false
                    this.cancel()
                }
            }
        }
    }
}
