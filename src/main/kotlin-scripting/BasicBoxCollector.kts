import com.darkorbit.*
import com.github.m9w.client.GameEngine
import com.github.m9w.collectRequest
import com.github.m9w.config.config
import com.github.m9w.context.context
import com.github.m9w.feature.annotations.OnPackage
import com.github.m9w.feature.annotations.Repeat
import com.github.m9w.metaplugins.EntitiesModule
import com.github.m9w.metaplugins.game.Point
import com.github.m9w.metaplugins.game.PositionImpl.Companion.distanceTo
import com.github.m9w.metaplugins.game.entities.BoxImpl
import com.github.m9w.metaplugins.game.entities.BoxImpl.Companion.name
import com.github.m9w.metaplugins.game.entities.HeroShip
import io.ktor.util.date.getTimeMillis
import java.util.concurrent.ConcurrentHashMap

@Suppress("unused")
class BasicBoxCollector {
    val targetNames by config(mutableSetOf("BONUS_BOX"))

    private val entities: EntitiesModule by context
    private val engine: GameEngine by context
    private val targets: MutableMap<String, AddBoxCommand> = ConcurrentHashMap()
    private var best: AddBoxCommand? = null
    private var lastCollect = 0L
    private var timeout = 0L
    private var place: Point = -100 to -100

    @OnPackage
    private fun onHeroInit(init: ShipInitializationCommand) { targets.clear(); best = null }

    @OnPackage
    private fun boxCreate(event: AddBoxCommand) {
        if (!bugged.contains(event.hash) && targetNames.contains(event.name)) {
            targets[event.hash] = event
            calcBest()
        }
    }

    @OnPackage
    private fun boxRemove(event: RemoveCollectableCommand) { if (event.collected) removeHash(event.hash) }

    @Repeat(1000)
    private fun calcBest() {
        if (engine.state != GameEngine.State.NORMAL) return
        val h = best?.hash
        best = targets.values.minByOrNull { entities.hero.position.distanceTo(it.point) }
        if (best?.hash != h) lastCollect = 0
        if (best == null && !entities.hero.isMoving) entities.hero.continueMove()
        val best = best ?: return
        if (entities.hero.position.distanceTo(best.point) < 400) {
            if (place != best.point) {
                timeout = getTimeMillis()
                place = best.point
            } else if (timeout + 30*1000 < getTimeMillis()) {
                timeout = getTimeMillis()
                place = -100 to -100
                bug(best.hash)
                removeHash(best.hash)
            }

            if (lastCollect + 750 < getTimeMillis()) {
                collect(best)
                if (!entities.hero.isMoving)
                    entities.hero.moveTo(best.point) { collect(best) }
            }
        } else entities.hero.moveTo(best.point) { collect(best) }
    }

    @Repeat(333000) fun bugCleanup() = cleanup()

    private fun collect(box: AddBoxCommand) {
        entities[box.hash.toLong(36)+Int.MAX_VALUE]?.also {
            engine.collectRequest(entities.hero, it as BoxImpl)
            lastCollect = getTimeMillis()
            calcBest()
        } ?: removeHash(box.hash)
    }

    private fun removeHash(hash: String) {
        targets.remove(hash)
        val moving = entities.hero.isMoving
        calcBest()
        if (moving && best == null) entities.hero.continueMove()
    }

    private fun HeroShip.continueMove() = moveRandom { calcBest() }

    companion object {
        private val AddBoxCommand.point: Point get() = x to y
        private val bugged = ConcurrentHashMap<String, Long>()
        private fun bug(hash: String) = bugged.put(hash, getTimeMillis())
        private var lastCheck = 0L
        private fun cleanup() {
            if (lastCheck + 5*60*1000 > getTimeMillis()) return
            bugged.entries.removeIf { it.value + 30*60*1000 < getTimeMillis() }
            lastCheck = getTimeMillis()
        }
    }
}
/** @m9w/darkorbit-cli-client
 * K+i6dWlj/ZZ7mEEDbz5oF2gU1xuGsZM4rLDEVt9alscCuS43UxsURHIAmyOMaANFbUpV3wDHoAoSW07r4lEACX1+L6y3MHNuGLvV
 * 7aGqzr7/mhm9X6eR70kvy3fgnKfn5dkz1rtub/al8UI23+afTIl+J4qvEMhtTU7mhEjge0FODj2TURrxIe48WDT/2kFE9IV7glS4
 * 1ycqKIhwgFpmJSGqNhT2XSRr9iGxOmdeAyCLzI7I37cTY5z+jRG5vC+g85+0Bqglwg9/OCSJ8q9/HUCh+PiBaRsw3L9YpOoUg7z1
 * qpZ5EyMOqZHPJhVQcdgdAtI5INd5EP30UjsycJjj1Gv7yAATUCtEf+NOB51dfUarY/+v3xukLEJq1Yd5kWUpAT03Jaw141VEyBox
 * gB2OQMfn37yNvdmksZu2gkV9MtcDSS5EvUqR9bxb4kK9CVo3D2ETeUZSd5umjPjT7LdziiPsIGmA8qx89gJ+31IsHRwlyxUfcdD7
 * rT0i3vftZgFsHRPUZJL2WNEc+GtTc3cEqYB1DMrh11+d5VQM0FqGl3NBI+kwiUP32YBwLEpeRXYJr+5Q24qbU7FIzda1mno2EMU9
 * ns9D/m9GmRMo73+TBNSk76cokdAciqenOz7nznjBY3R4FRd4nqR4xlPvZ8PnORCg7lHUsiwO6aCHBcVTJ5Y=
 * */