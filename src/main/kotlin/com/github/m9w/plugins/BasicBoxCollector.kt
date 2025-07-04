package com.github.m9w.plugins

import com.darkorbit.*
import com.github.m9w.client.GameEngine
import com.github.m9w.collectRequest
import com.github.m9w.context
import com.github.m9w.feature.annotations.OnPackage
import com.github.m9w.feature.annotations.Repeat
import com.github.m9w.metaplugins.EntitiesModule
import com.github.m9w.metaplugins.game.PositionImpl.Companion.distanceTo
import com.github.m9w.metaplugins.game.PositionImpl.Companion.x
import com.github.m9w.metaplugins.game.PositionImpl.Companion.y
import com.github.m9w.metaplugins.game.entities.BoxImpl
import com.github.m9w.metaplugins.game.entities.BoxImpl.Companion.name

@Suppress("unused")
open class BasicBoxCollector {
    private val entities: EntitiesModule by context
    protected val targetNames = mutableSetOf("BONUS_BOX", "SOLAR_CLASH")
    private val targets: MutableMap<String, AddBoxCommand> = HashMap()
    private var best: AddBoxCommand? = null

    @OnPackage
    private fun onHeroInit(init: ShipInitializationCommand) { targets.clear(); best = null }

    @OnPackage
    private fun boxCreate(event: AddBoxCommand) { if (targetNames.contains(event.name)) targets.put(event.hash, event).also { calcBest() } }

    @OnPackage
    private fun boxRemove(event: RemoveCollectableCommand) { if (event.collected) removeHash(event.hash).also { calcBest() } }

    @Repeat(100)
    private fun calcBest() {
        if (entities.gameEngine.state != GameEngine.State.NORMAL) return
        best = targets.values.minByOrNull { entities.hero.position.distanceTo(it.x to it.y) }
        if (best == null && !entities.hero.isMoving) entities.hero.moveRandom()
        val best = best ?: return
        if (entities.hero.direction.position.run { x == best.x && y == best.y } ) return
        entities.hero.moveTo(best.x to best.y) {
            entities[best.hash.toLong(36)+Int.MAX_VALUE]?.let {
                entities.gameEngine.collectRequest(entities.hero, it as BoxImpl)
                calcBest()
            } ?: removeHash(best.hash)
        }
    }

    private fun removeHash(hash: String) {
        targets.remove(hash)
        if (best?.hash == hash) best = null
    }
}