import com.darkorbit.*
import com.github.m9w.client.GameEngine
import com.github.m9w.context.context
import com.github.m9w.feature.annotations.OnPackage
import com.github.m9w.feature.annotations.Repeat
import com.github.m9w.feature.waitOnPackage
import com.github.m9w.game.PositionImpl.Companion.distanceTo
import com.github.m9w.game.entities.AssetImpl
import com.github.m9w.game.entities.BoxImpl
import com.github.m9w.game.entities.GameMapEnum
import com.github.m9w.metaplugins.EntitiesModule
import com.github.m9w.metaplugins.MapModule
import com.github.m9w.metaplugins.MapNavigation
import com.github.m9w.metaplugins.PathTracerModule
import com.github.m9w.protocol.Factory
import com.github.m9w.util.isTimeout
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.hypot
import kotlin.random.Random

class PalladiumFarmerModule {
    private val entities: EntitiesModule by context
    private val gameEngine: GameEngine by context
    private val mapNavigation: MapNavigation by context
    private val mapModule: MapModule by context
    private val pathTracer: PathTracerModule by context

    private val map51 = GameMapEnum.`5-1`
    private val map52 = GameMapEnum.`5-2`
    private val map53 = GameMapEnum.`5-3`
    private val map45 = GameMapEnum.`4-5`

    // Coordenadas zona Paladio 5-3
    private val zoneMinX = 11857
    private val zoneMaxX = 32315
    private val zoneMinY = 18000
    private val zoneMaxY = 25560

    private val assetTradeTypes = listOf(AssetType.ORE_TRADE_HOME, AssetType.ORE_TRADE_OUTPOST, AssetType.ORE_TRADE_INVISIBLE)

    private val oresToSell: List<OreType> = listOf(
        OreType.PROMETIUM,
        OreType.ENDURIUM,
        OreType.TERBIUM,
        OreType.PROMETID,
        OreType.DURANIUM,
        OreType.PROMERIUM,
        OreType.SEPROM, //todo try laser upgrade
        OreType.PALLADIUM
    )

    enum class State {
        TRAVEL_TO_FIELD,
        COLLECTING,
        TRAVEL_TO_BASE,
        SELLING
    }

    var isEnabled: Boolean = false
    private var currentState = State.TRAVEL_TO_FIELD

    private var isTraveling = false
    private var targetMap: GameMapEnum? = null
    private var lastMapId: GameMapEnum? = null
    private var mapLoadTime = 0L

    // Variables de venta y caché
    private var saleIndex = 0
    private var lastActionTime = 0L

    // Variables de colecta
    private var collectingUntil = 0L
    private var currentTargetBox: BoxImpl? = null
    private var lastMoveAttempt = 0L
    private var lastDestination: Pair<Int, Int>? = null
    private var wanderingDestination: Pair<Int, Int>? = null

    // Gestión de memoria para ignorar cajas fallidas
    private val ignoredBoxes = ConcurrentHashMap<String, Long>()
    private var lastMemoryCleanup = 0L

    private val currentOreCounts = ConcurrentHashMap<OreType, Long>()

    @OnPackage
    private fun onCollectableRemove(cmd: RemoveCollectableCommand) {
        if (!isEnabled) return
        ignoredBoxes.remove(cmd.hash)

        if (currentTargetBox?.hash == cmd.hash) {
            currentTargetBox = null
            collectingUntil = 0L
        }
    }

    @OnPackage
    private fun onOreCountUpdate(cmd: AttributeOreCountUpdateCommand) {
        for (ore in cmd.oreCountList) {
            currentOreCounts[ore.oreType.typeValue] = ore.count
        }
    }

    @Repeat(ms = 150)
    suspend fun onTick() {
        if (!isEnabled) return

        val hero = entities.hero
        val currentMap = mapModule.map.entity ?: return
        val now = System.currentTimeMillis()

        if (isTimeout(lastMemoryCleanup, sec = 10)) {
            ignoredBoxes.entries.removeIf { it.value < now }
            lastMemoryCleanup = System.currentTimeMillis()
        }

        // 2. Control de recarga de mapas
        if (currentMap != lastMapId) {
            lastMapId = currentMap
            mapLoadTime = now
            mapNavigation.interrupt()
            isTraveling = false
            targetMap = null
            lastDestination = null
            wanderingDestination = null
        }

        if (now - mapLoadTime < 2000) return

        // 3. Bloqueo temporal por recolección en curso
        if (collectingUntil > 0L) {
            if (now < collectingUntil) return else collectingUntil = 0L
        }

        // 4. Chequeo de bodega llena
        val isCargoFull = hero.cargoSpaceMax > 0 && hero.cargo >= (hero.cargoSpaceMax - 1)

        if (isCargoFull && currentState == State.COLLECTING) {
            println("[PalladiumModule] Bodega llena. Yendo a base.")
            currentState = State.TRAVEL_TO_BASE
            mapNavigation.interrupt()
            isTraveling = false
            saleIndex = 0
            wanderingDestination = null
            return
        }

        // 5. Máquina de Estados
        when (currentState) {
            State.COLLECTING -> handleCollecting(currentMap, isCargoFull, now)
            State.TRAVEL_TO_FIELD -> handleTravel(currentMap, map53, State.COLLECTING)
            State.TRAVEL_TO_BASE -> handleTravelToBase(currentMap, now)
            State.SELLING -> handleSellingSequence(now)
        }
    }

    /**
     * Mueve la nave usando PathTracer siguiendo los nodos intermedios.
     */
    private fun moveToSafe(targetPos: Pair<Int, Int>, now: Long) {
        val hero = entities.hero
        val (hx, hy) = hero.position

        val path = pathTracer.traceTo(targetPos)

        val finalTarget = if (path.isNotEmpty()) {
            path.firstOrNull { wp ->
                hypot((hx - wp.first).toDouble(), (hy - wp.second).toDouble()) > 30.0
            } ?: path.last()
        } else {
            val distToTarget = hypot((hx - targetPos.first).toDouble(), (hy - targetPos.second).toDouble())
            if (distToTarget > 600.0) {
                println("[PalladiumModule] Ruta A* no encontrada, deteniendo avance.")
                return
            }
            targetPos
        }

        val lastDest = lastDestination
        val distToNewDest = if (lastDest != null) {
            hypot((lastDest.first - finalTarget.first).toDouble(), (lastDest.second - finalTarget.second).toDouble())
        } else Double.MAX_VALUE

        if (!hero.isMoving || distToNewDest > 15.0) {
            hero.moveTo(finalTarget)
            lastDestination = finalTarget
            lastMoveAttempt = now
        }
    }

    private fun handleCollecting(currentMap: GameMapEnum, isCargoFull: Boolean, now: Long) {
        if (isCargoFull) return
        if (currentMap != map53) {
            handleTravel(currentMap, map53, State.COLLECTING)
            return
        }

        val hero = entities.hero
        val (hx, hy) = hero.position

        //todo use PoiZone
        //entities.get<PoiImpl>().find { it.design == POIDesign.NEBULA }

        // 1. Ir a la nebulosa si estamos fuera
        if (!isInPalladiumZone(hx, hy)) {


            val centerNebula = Pair((zoneMinX + zoneMaxX) / 2, (zoneMinY + zoneMaxY) / 2)
            moveToSafe(centerNebula, now)
            return
        }

        // 2. Buscar paladio no ignorado más cercano
        val target = findClosestPalladium(now)
        if (target != null) {
            currentTargetBox = target
            val distance = hero.distanceTo(target)

            if (distance > 60.0) {
                moveToSafe(target.position, now)
            } else {
                gameEngine.send<HarvestRequest> { this.itemHash = target.hash }
                collectingUntil = now + 6000L
                ignoredBoxes[target.hash] = now + 12000L // Lo ignora por 12s si falla
                lastDestination = null
                wanderingDestination = null
            }
            return
        }

        // 3. Patrullaje fluido
        var wander = wanderingDestination
        if (wander == null || hypot((hx - wander.first).toDouble(), (hy - wander.second).toDouble()) < 300.0) {
            wander = getSafeRandomPalladiumPoint()
            if (wander != null) {
                wanderingDestination = wander
            }
        }

        wander?.let { moveToSafe(it, now) }
    }

    private fun getSafeWaypoint(currentMapId: GameMapEnum, finalTarget: GameMapEnum): GameMapEnum {
        val ownX5Map = when (entities.hero.faction) {
            Faction.MMO -> GameMapEnum.`1-5`
            Faction.EIC -> GameMapEnum.`2-5`
            Faction.VRU -> GameMapEnum.`3-5`
            else -> GameMapEnum.`1-5`
        }
        val piratesAnd45 = setOf(map45, map51, map52, map53)
        if (currentMapId in piratesAnd45) return finalTarget
        if (currentMapId == ownX5Map) return map45
        return ownX5Map
    }

    private fun handleTravel(currentMap: GameMapEnum, target: GameMapEnum, nextState: State) {
        if (currentMap == target) {
            mapNavigation.interrupt()
            isTraveling = false
            currentState = nextState
            return
        }

        val safeWaypoint = getSafeWaypoint(currentMap, target)
        if (!isTraveling || targetMap != safeWaypoint) {
            println("[PalladiumModule] Viajando al mapa $target (Vía $safeWaypoint)")
            mapNavigation.travelTo(safeWaypoint)
            targetMap = safeWaypoint
            isTraveling = true
        }
    }

    private suspend fun handleTravelToBase(currentMap: GameMapEnum, now: Long) {
        if (currentMap == map52) {
            val trader = entities.get<AssetImpl>().firstOrNull { it.type in assetTradeTypes }
            var traderPos = trader?.position
            val startAt = System.currentTimeMillis()
            if (traderPos == null) try {
                while (!isTimeout(startAt, sec = 10)) {
                    traderPos = waitOnPackage<AssetCreateCommand>(timeout = 5000)
                        .takeIf { it.type.typeValue in assetTradeTypes }
                        ?.let {it.posX to it.posY}
                }
            } catch (_: InterruptedIOException) {
                println("Trade asset not found on $currentState map. Reconnecting...")
                gameEngine.reconnect()
                return
            }

            if (traderPos == null) return

            if (entities.hero.position.distanceTo(traderPos) > 300.0) {
                if (!entities.hero.isMoving || isTimeout(lastMoveAttempt, sec = 5)) {
                    entities.hero.moveTo(traderPos)
                    lastMoveAttempt = System.currentTimeMillis()
                }
            } else {
                mapNavigation.interrupt()
                isTraveling = false
                currentState = State.SELLING
                saleIndex = 0
                lastActionTime = now
            }
        } else {
            handleTravel(currentMap, map52, State.TRAVEL_TO_BASE)
        }
    }

    private fun handleSellingSequence(now: Long) {
        if (saleIndex > oresToSell.size) {
            if (now < lastActionTime + 1500L) return
            println("[PalladiumModule] Venta finalizada. Volviendo al campo.")
            currentState = State.TRAVEL_TO_FIELD
            saleIndex = 0
            return
        }

        if (saleIndex == 0) {
            if (isTimeout(lastActionTime, sec = 1)) {
                println("[PalladiumModule] Solicitando apertura de comercio a la base...")
                val tradeAsset = entities.get<AssetImpl>().firstOrNull { it.type in assetTradeTypes } ?: return
                gameEngine.send<MapAssetActivationRequest> { this.mapAssetId = tradeAsset.id.toInt() }
                gameEngine.send<TradeRequest> { }
                gameEngine.send<LabUpdateRequest> { }
                saleIndex = 1
                lastActionTime = now + 2500L
            }
            return
        }

        if (now < lastActionTime) return

        val ore = oresToSell[saleIndex - 1]

        val amountToSend = 999999L //todo get ore count per type

        gameEngine.send<TradeSellOreRequest> {
            this.toSell = Factory.build<OreCountModule> {
                this.oreType = Factory.build<OreTypeModule> { this.typeValue = ore }
                this.count = amountToSend
            }
        }

        currentOreCounts[ore] = 0L
        lastActionTime = now + 800L

        saleIndex++
    }

    private fun findClosestPalladium(now: Long): BoxImpl? {
        val hero = entities.hero
        var closest: BoxImpl? = null
        var minDistSq = Double.MAX_VALUE

        for (entity in entities.values) {
            if (entity is BoxImpl) {
                if ((ignoredBoxes[entity.hash] ?: 0L) > now) continue

                if (entity.boxType == "8" || entity.boxType == "ore_8" || entity.oreId == OreType.PALLADIUM) {
                    val dx = hero.position.first - entity.position.first
                    val dy = hero.position.second - entity.position.second
                    val distSq = (dx * dx + dy * dy).toDouble()

                    if (distSq < minDistSq) {
                        minDistSq = distSq
                        closest = entity
                    }
                }
            }
        }
        return closest
    }

    private fun isInPalladiumZone(x: Int, y: Int) = x in zoneMinX..zoneMaxX && y in zoneMinY..zoneMaxY

    private fun getSafeRandomPalladiumPoint(): Pair<Int, Int>? {
        (0..2).forEach { _ ->
            val point = Pair(Random.nextInt(zoneMinX, zoneMaxX), Random.nextInt(zoneMinY, zoneMaxY))
            if (pathTracer.traceTo(point).isNotEmpty()) return point
        }
        return null
    }

    fun start() {
        isEnabled = true
        isTraveling = false
        targetMap = null
        lastMapId = null
        currentTargetBox = null
        ignoredBoxes.clear()
        saleIndex = 0
        collectingUntil = 0L
        currentOreCounts.clear()
        lastDestination = null
        wanderingDestination = null
        lastMemoryCleanup = System.currentTimeMillis()

        gameEngine.send<LabUpdateRequest> { }
        val isFull = entities.hero.cargoSpaceMax > 0 && entities.hero.cargo >= (entities.hero.cargoSpaceMax - 15)
        currentState = if (isFull) State.TRAVEL_TO_BASE else State.TRAVEL_TO_FIELD
    }

    fun stop() {
        isEnabled = false
        mapNavigation.interrupt()
    }
}
/** @m9w/darkorbit-cli-client
 * Ru37gmzpaD/GgS9c0vjQsbltr2gacKCR8ZTEOCbeTHHm/xq8Ss0Jt+eTfNIqFRVXh+OIoy8S6CYI4RPolLUEtxPBXv7p1ynhQxnw
 * HCZ3ucREa4XB4KwMv7PzkJiYuyuJX5c4b6zLV/Nk0mfIFF7g4ow9f6H6O+cT35+pdeLZSQB65vcM2pL/5/6ck2dadFFiwDbv6kSk
 * d8yYi+SK9eR1XoL6r1/rX49uBmSZWKlTItvUXb2tebjvmPXW34H9PmLhi1gyV63jOp0w7vIsi/8RKivA2cPP5Ezz+XlbZkFoi9bM
 * 3o8rb1BuY6JjJHix+EkFae0fWIKj/eDNfZL7jKNtJ/nUAXmuTWgvoRDoxDTUKrtc8VwEL4B9q2FUTrWQbT39SoC86vUkYJfECATQ
 * 0qe+0z4reEDshjO72oLSQLZNLGNtANVnj5lQUP8niw8mlc/LDrzi4BFSiMwXDzH+idc9YidBWyK6c6tobsybm1OxV/5CVnovPYZA
 * A0aPH7UEucnuZHeN2uoxfV/tMumGAXItDc7PwzfjZZn9W5yFkHnCS184Jj0Ayw8Q/vt5sq0eEhKDCVix6FtSytrB+k0UstoFRHy6
 * Wv51GmMKwOxhbg4HRpIJtr2Bmw2s4VRkPhG6bXzntsN42lmxCWQfGhi416gT0NMjqV5EaahOsJOk1E/BZqU=
 * */