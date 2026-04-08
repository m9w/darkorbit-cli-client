import com.darkorbit.*
import com.github.m9w.client.GameEngine
import com.github.m9w.util.collectRequest
import com.github.m9w.config.config
import com.github.m9w.context.context
import com.github.m9w.feature.annotations.OnPackage
import com.github.m9w.feature.annotations.Repeat
import com.github.m9w.metaplugins.EntitiesModule
import com.github.m9w.game.Point
import com.github.m9w.game.PositionImpl.Companion.distanceTo
import com.github.m9w.game.entities.BoxImpl
import com.github.m9w.game.entities.BoxImpl.Companion.name
import com.github.m9w.game.entities.HeroShip
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
 * AyZC9t8pVFsxq0ElvuaTxxSqIOYzm648NlqfAR4AjL7Fwcjd7wGtJGL0JVpHdRKQYF6TniDkzNi1QhBFD4pmKOwcA0iIz65ioOjS
 * /x+9Oenymenrfpv+AhY1AoVVS8Wm09MJ2rgahrMxPBo4GaiP9hrAIMdue8sNNLb+4TppViYt++ecUlNA9hT1xW7rqxW4L0WRxc6g
 * /MTmjKV91AueQGxUKKJgcnIfhNQM7/UDsYBEXsP0S13Q8TA+0XBAR9SJt1TvHTgMuT/PCekovh74ah38qUQMeQeQH6UJhsnXLjdn
 * 26VCuVSXWI7JnLYQXHQ6x/BtVII/75JlXctDSYknuaTAyPmhXZPeNNzipFmKsAQr19SUC3OuGn9zmN8Rb2YO5J11ODOgaqOZbwMJ
 * Kz2gThSYmmvJkKIrsSAtJ3SeorGCBKHgn06gLjZnAYAkIoGDkYHJUkn6bmyB3RUg6PcKB1WEiA3H3K8wPXMzT8LQVwsT9++33X7H
 * BpXbcowXgPJr1vp4x758M9d0KlYhwBuNBxS66m8v0h99I2u5aQv270xHtc3toNnG8eJo93gGeuc6riDcEtNcfN3R07DYapWyE40z
 * Ho1FkHkV2Bt9aSucVvifkpw7QvU14X3ZlnjvBQWSSNxUFNqMnUETq3Chkrzk72f8Bgu3dYZwcPYnKouF9Ds=
 * */