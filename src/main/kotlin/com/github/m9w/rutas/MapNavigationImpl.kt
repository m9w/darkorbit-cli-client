package com.github.m9w.rutas

import com.github.m9w.client.GameEngine
import com.github.m9w.metaplugins.EntitiesModule
import com.github.m9w.metaplugins.MapModule
import com.github.m9w.metaplugins.PathTracerModule
import com.darkorbit.MapChangedCommand
import com.darkorbit.JumpRequest
import com.darkorbit.Faction
import com.github.m9w.protocol.Factory
import com.github.m9w.feature.waitMs
import com.github.m9w.feature.annotations.OnPackage
import com.github.m9w.feature.annotations.Repeat
import com.github.m9w.game.entities.JumpgateImpl
import java.util.PriorityQueue
import com.github.m9w.context.context
import com.github.m9w.game.entities.GameMapEnum
import com.github.m9w.game.entities.GameMapEnum.*
import com.github.m9w.metaplugins.MapNavigation
import kotlin.collections.forEach

private data class Step(val from: GameMapEnum, val to: GameMapEnum, val portalId: Int)

private object StarSystem {
    private val graph: Map<GameMapEnum, List<Pair<GameMapEnum, Int>>> = mapOf(
        `3-1` to listOf(`3-1` to 150000191),
        `3-2` to listOf(`3-1` to 150000190, `3-3` to 150000189, `3-4` to 150000181),
        `3-3` to listOf(`3-2` to 150000188, `3-4` to 150000197, `2-4` to 150000187),
        `3-4` to listOf(`3-2` to 150000180, `3-3` to 150000196, `1-4` to 150000179, `4-3` to 150000202),
        `4-1` to listOf(`4-4` to 150000307, `4-2` to 150000204, `4-3` to 150000209, `1-4` to 150000199),
        `4-2` to listOf(`4-4` to 150000309, `4-3` to 150000206, `4-1` to 150000205, `2-4` to 150000201),
        `4-3` to listOf(`4-4` to 150000311, `4-2` to 150000207, `4-1` to 150000208, `3-4` to 150000203),
        `4-4` to listOf(`1-5` to 150000316, `2-5` to 150000326, `3-5` to 150000336, `4-1` to 150000308, `4-2` to 150000310, `4-3` to 150000312),
        `4-5` to listOf(`1-5` to 150000347, `3-5` to 150000351, `2-5` to 150000349, `5-1` to 150000458),
        `5-1` to listOf(`5-2` to 150000460),
        `5-2` to listOf(`5-3` to 150000468, `5-4` to 150000474),
        `5-3` to listOf(`4-4` to 150000482),
        `1-1` to listOf(`1-2` to 150000168),
        `1-2` to listOf(`1-1` to 150000169, `1-3` to 150000170, `1-4` to 150000172),
        `1-3` to listOf(`1-2` to 150000171, `1-4` to 150000194, `2-3` to 150000174),
        `1-4` to listOf(`1-2` to 150000173, `1-3` to 150000195, `3-4` to 150000178, `4-1` to 150000198),
        `2-1` to listOf(`2-2` to 150000183),
        `2-2` to listOf(`2-1` to 150000182, `2-3` to 150000177, `2-4` to 150000184),
        `2-3` to listOf(`2-2` to 150000176, `2-4` to 150000192, `1-3` to 150000175),
        `2-4` to listOf(`2-2` to 150000185, `2-3` to 150000193, `3-3` to 150000186, `4-2` to 150000200),
        `3-5` to listOf(`3-6` to 150000338, `3-7` to 150000340, `4-5` to 150000350, `4-4` to 150000337),
        `3-6` to listOf(`3-5` to 150000339, `3-8` to 150000342),
        `3-7` to listOf(`3-5` to 150000341, `3-8` to 150000344),
        `3-8` to listOf(`3-6` to 150000343, `3-7` to 150000345, `3BL` to 150000218),
        `1-5` to listOf(`4-5` to 150000346, `1-7` to 150000320, `1-6` to 150000318, `4-4` to 150000317),
        `1-7` to listOf(`1-5` to 150000321, `1-8` to 150000324),
        `1-6` to listOf(`1-5` to 150000319, `1-8` to 150000322),
        `1-8` to listOf(`1-6` to 150000323, `1-7` to 150000325, `1BL` to 150000210),
        `2-5` to listOf(`2-6` to 150000328, `2-7` to 150000330, `4-4` to 150000327, `4-5` to 150000348),
        `2-6` to listOf(`2-5` to 150000329, `2-8` to 150000332),
        `2-7` to listOf(`2-8` to 150000334, `2-5` to 150000331),
        `2-8` to listOf(`2-6` to 150000333, `2-7` to 150000335, `2BL` to 150000214),

        // Conexiones de salida de BL ajustadas: solo pueden volver a X-8
        `3BL` to listOf(`3-8` to 150000219),
        `1BL` to listOf(`1-8` to 150000211),
        `2BL` to listOf(`2-8` to 150000215)
    )

    fun findRoute(start: GameMapEnum, goal: GameMapEnum, faction: Faction? = null): List<Step> {
        if (start == goal) return emptyList()

        val queue = PriorityQueue<Pair<GameMapEnum, Int>>(compareBy { it.second })
        val parent = mutableMapOf<GameMapEnum, Pair<GameMapEnum, Int>?>()
        val cost = mutableMapOf<GameMapEnum, Int>()

        queue.add(start to 0)
        cost[start] = 0
        parent[start] = null

        var routeFound = false

        while (queue.isNotEmpty()) {
            val (u, currentCost) = queue.poll()
            if (u == goal) {
                routeFound = true
                break
            }

            if (currentCost > cost.getOrDefault(u, Int.MAX_VALUE)) continue

            val neighbors = graph[u] ?: emptyList()
            neighbors.forEach { (v, pid) ->
                var edgeCost = 1

                if (faction != null && faction != Faction.NONE) {
                    if (!v.name.startsWith(faction.ordinal.toString())) {
                        edgeCost = 100
                    }
                }

                val newCost = currentCost + edgeCost
                if (newCost < cost.getOrDefault(v, Int.MAX_VALUE)) {
                    cost[v] = newCost
                    parent[v] = u to pid
                    queue.add(v to newCost)
                }
            }
        }

        if (!routeFound) return emptyList()

        val steps = mutableListOf<Step>()
        var cur = goal
        while (true) {
            val p = parent[cur] ?: break
            val (prev, pid) = p
            steps.add(Step(prev, cur, pid))
            cur = prev
        }
        return steps.reversed()
    }
}

class MapNavigationImpl : MapNavigation {
    private val game: GameEngine by context
    private val entitiesModule: EntitiesModule by context
    private val mapModule: MapModule by context
    
    private val pathTracer = PathTracerModule()

    private var targetMap: GameMapEnum? = null
    private var isRouting: Boolean = false

    private var route: MutableList<Step> = mutableListOf()
    private var activeStep: Step? = null
    private var waitingJump: Boolean = false
    private var waitSince: Long = 0L
    private val routerLock = Any()

    private var prevSate: GameEngine.State = GameEngine.State.STOPPED
    
    override fun travelTo(destination: GameMapEnum) {
        if (game.state.isNotConnected) return
        if (game.state != GameEngine.State.TRAVELING) {
            prevSate = game.state
            game.state = GameEngine.State.TRAVELING
        }
        
        synchronized(routerLock) {
            val current = mapModule.map.entity ?: return

            if (current == destination) {
                interrupt()
                return
            }

            val newRoute = StarSystem.findRoute(current, destination, entitiesModule.heroOrNull?.faction)

            if (newRoute.isNotEmpty()) {
                this.targetMap = destination
                this.route = newRoute.toMutableList()
                this.activeStep = this.route.first()
                this.isRouting = true
                this.waitingJump = false
                entitiesModule.heroOrNull?.laserAttackTarget?.invoke(attack = false)
            } else {
                interrupt()
            }
        }
    }

    override fun interrupt() {
        synchronized(routerLock) {
            this.isRouting = false
            this.targetMap = null
            this.route.clear()
            this.activeStep = null
            this.waitingJump = false
            game.state = prevSate
            println("GPS: Navegación finalizada o detenida.")
        }
    }

    @OnPackage
    private suspend fun onMapChanged(packet: MapChangedCommand) {
        pathTracer.onChange()

        synchronized(routerLock) {
            val newMap = GameMapEnum.findById(packet.newMapId) ?: return

            if (activeStep?.to == newMap) {
                if (route.isNotEmpty()) {
                    route.removeAt(0)
                    activeStep = route.firstOrNull()
                }
            } else if (isRouting) {
                val current = GameMapEnum.findById(packet.newMapId) ?: return
                if (current != targetMap) {
                    val faction = entitiesModule.heroOrNull?.faction
                    val target = targetMap ?: return
                    val newRoute = StarSystem.findRoute(current, target, faction)

                    if (newRoute.isNotEmpty()) {
                        this.route = newRoute.toMutableList()
                        this.activeStep = this.route.first()
                    } else {
                        interrupt()
                    }
                }
            }

            waitingJump = false
            waitSince = 0

            if (newMap == targetMap) {
                interrupt()
            }
        }

        waitMs(1500, "MAP_CHANGE_COOLDOWN")
    }

    @Repeat(ms = 500)
    private suspend fun navigationLoop() {
        if (game.state != GameEngine.State.TRAVELING) return
        
        val routeActive = synchronized(routerLock) { isRouting }
        if (!routeActive) return
        val current = mapModule.map.entity ?: return
        if (current == targetMap || targetMap == null) {
            interrupt()
            return
        }
        if (game.state != GameEngine.State.NORMAL) return
        val hero = entitiesModule.heroOrNull ?: return
        val step = synchronized(routerLock) { activeStep }
        val rLock = routerLock
        if (step == null) {
            val map = targetMap
            if (map == null) {
                interrupt()
                return
            }
            if (current != targetMap) travelTo(map)
            return
        }
        if (current == step.to) {
            synchronized(rLock) {
                if (route.isNotEmpty()) route.removeAt(0)
                activeStep = route.firstOrNull()
                waitingJump = false
            }
            return
        }
        if (current == step.from) {
            val gate = findGateByDesignId(step.portalId)

            val targetPos = getAssumedGatePosition(step.from, step.to) ?: gate?.position

            if (targetPos == null) {
                if (!hero.isMoving) {
                    hero.moveTo(hero.position)
                }
                return
            }

            val dx = (hero.position.first - targetPos.first).toDouble()
            val dy = (hero.position.second - targetPos.second).toDouble()
            val dist = kotlin.math.sqrt(dx*dx + dy*dy)

            if (dist > 800) {
                synchronized(rLock) { waitingJump = false }

                val path = pathTracer.traceTo(targetPos)
                val nextPoint = if (path.size > 1) path[1] else targetPos

                val isWrongDestination = if (hero.isMoving) {
                    val destDx = (hero.destination.position.first - nextPoint.first).toDouble()
                    val destDy = (hero.destination.position.second - nextPoint.second).toDouble()
                    val distToTargetDest = kotlin.math.sqrt(destDx * destDx + destDy * destDy)
                    distToTargetDest > 200
                } else {
                    false
                }

                if (!hero.isMoving || isWrongDestination) {
                    hero.moveTo(nextPoint)
                }

            } else {
                val isWaiting = synchronized(rLock) { waitingJump }
                if (!isWaiting) {
                    synchronized(rLock) {
                        waitingJump = true
                        waitSince = System.currentTimeMillis()
                    }
                    try {
                        game.network.send(Factory.build(JumpRequest::class))
                    } catch (_: Exception) {
                        gate?.invoke(false)
                    }

                    waitMs(8000, "JUMP_TIMEOUT")
                    synchronized(rLock) { waitingJump = false }
                }
            }
        } else {
            waitMs(2000, "RECALCULATE_WAIT")
            val map = targetMap
            if (map == null) {
                interrupt()
                return
            }
            travelTo(map)
        }
    }

    private fun findGateByDesignId(portalId: Int): JumpgateImpl? {
        return entitiesModule.get<JumpgateImpl>().find { it.id == portalId.toLong() || it.designId == portalId }
    }

    private fun getAssumedGatePosition(from: GameMapEnum, to: GameMapEnum): Pair<Int, Int>? {
        if ((from == `1-8` && to == `1BL`) ||
            (from == `2-8` && to == `2BL`) ||
            (from == `3-8` && to == `3BL`)) {
            return 10500 to 6500
        }

        if (from.name.endsWith("BL") && to.name.endsWith("-8")) {
            return 10500 to 6500
        }
        return null
    }
}
