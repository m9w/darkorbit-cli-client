import com.darkorbit.AttackHitCommand
import com.darkorbit.BeaconCommand
import com.darkorbit.MenuActionRequest
import com.darkorbit.MenuActionRequestActionType
import com.darkorbit.SourceType
import com.github.m9w.Scheduler
import com.github.m9w.client.GameEngine
import com.github.m9w.context.context
import com.github.m9w.feature.annotations.OnPackage
import com.github.m9w.feature.waitMs
import com.github.m9w.feature.waitOnPackage
import com.github.m9w.metaplugins.EntitiesModule
import java.io.InterruptedIOException

class ManualRepairRobotModule {
    private val syncEventName = ManualRepairRobotModule::class.simpleName!!

    private val entities: EntitiesModule by context
    private val engine: GameEngine by context
    private val scheduler: Scheduler by context

    private var repairRobotActive: Boolean = false
    private var repairRobotLootId: String = ""
    private var repairBotSkilled: Boolean = false

    @OnPackage
    private suspend fun onAttackEvent(attack: AttackHitCommand) {
        if (!repairBotSkilled) return
        if (attack.victimId.toLong() != entities.hero.id) return
        if (attack.victimHitpoints >= entities.hero.health.healthMax * 0.9) return
        scheduler.cancelWaitMs(syncEventName)
        waitMs(10000, syncEventName)
        if (repairRobotActive) return
        if (repairRobotLootId.isEmpty()) return
        try {
            waitOnPackage<BeaconCommand>(timeout = 3000) {
                engine.send<MenuActionRequest> {
                    menuItemId = repairRobotLootId
                    actionType = MenuActionRequestActionType.ACTIVATE
                    sourceType = SourceType.ITEM_BAR
                }
            }.takeIf { !it.repairRobotActive }?.let { onAttackEvent(attack) }
        } catch (_: InterruptedIOException) {
            onAttackEvent(attack)
        }
    }

    @OnPackage
    private fun onBeacon(beacon: BeaconCommand) {
        repairRobotActive = beacon.repairRobotActive
        repairRobotLootId = beacon.repairRobotLootId
        repairBotSkilled = beacon.repairBotSkilled
    }
}
/** @m9w/darkorbit-cli-client
 * lOIjWj36Wp/dpTWBNXH4VLDROqF0V/cnxN608akrRBS7DA1bGnUrxeUFUkrqjr4h23tMhoSdQzhA2xk4WG/e+lxski2gzg7Lwho6
 * pckwyYvfpY9FYvi281Q1lOITq5S8XrkxkgOGplYKMAmJJ8fw0TN6hiub8GkOVS9GNJeKt5AYA5pz7ZkvldEnHzGq8RP8lD/SKvjc
 * K/pwQi1bNkyxClxE7l581eYYARk/XlEYvAskanwGL5nfPK+5vxKEzueFZr54IoZH+9x75BbrorCAA00XUNUu4ENr4LwbheHcDVja
 * m3hXXtCFLW4noZD7wlSwnm+pzYxm3IqUQHL5KSDcM/tOUEKLqVQ8dgOO/qr7WGeDenSArroILl6qZmTUpSxPAQfz2n2NOW2Y1LEF
 * nCh2nOcnv/T7inF4Tv3V1kWtZreWO+YYDVlWYK6QK7HDSSNDhaTANmnr+7oJXqvKu77DvNoUp+f7FynDye7Wy/Pi/clOw3nC5MrX
 * 2ARqzq23uIz9C90s1ll+1NGHkcgupwV+b3VTqxBe2hIYxND2sZTMf5U3R2LTKZ+U1x/U3eE0pqGygKHqQdltdTbE9zI0rw94h/4Q
 * a+lLuRT/Xn1qvyXUt3adDWXvwN7Y7cVtTffsm2aCU90ffgdtEiOxO8USXz86fV43agupY1GZBwvK+b3i+Bs=
 * */