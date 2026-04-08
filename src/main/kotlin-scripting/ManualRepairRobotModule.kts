import com.darkorbit.AttackHitCommand
import com.darkorbit.BeaconCommand
import com.darkorbit.MenuActionRequest
import com.darkorbit.MenuActionRequestActionType
import com.darkorbit.SourceType
import com.github.m9w.client.GameEngine
import com.github.m9w.context.context
import com.github.m9w.feature.Scheduler
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
 * MDGqHrrFb+JBzmbyl/KhC77q5HRbIuPsxunbHToO/7gKQklTuh1bKq9BmCvCEvHErggA2LDP1GXiIPrqsW0sTUPaDOWB9mVS4t3f
 * GkKh1JvxYcXa55WPxUqnRKiRLFMwhFjaI0CErb6HJf0+vixtU+YtUf6fpFDaasTFO6ND2thWIcDy0FliPz+3ONf6jO597f97KrMQ
 * WKJ1EOqMHn5QrYLVE3wTUshTt59SNa3czhO3vCaTFnOpercP6y93MskUmVCgpRG5TKRuOEl30NoB3gV4EIN4BrMvLZCkg/xkhXtW
 * 5Fi4GF7RlPoxYT6i7EBaccT3NY+MkgCcPSOvbWURDc5weQTrwKmwUlgXw3Usbw6eZVq9m387k3dfErsgf/TInmvclpTFS6D83UWn
 * cnza7eb5G0oXaWzkvBdIFN8FVRQQr1u6nfB89YppciZELQi6LKtayhtFVfOu66XlPNknv7F0bz2MdVzt3+9AY5UckF9e0nnN+e4z
 * p00AA90HKNr57v35uTTfatrykrh8sbZWmD2pHlMKxLBa7klxGobaZVZ9hW4z3Bvid8QH0Rygkgb7do5xh60CS7ey9HXHdMVjPSAX
 * Ik2PbtDzp23QqCd065VAMEgtwHFhFN1kRONppMQ+jyuC+3zntlOYUWasPyabR+LThwZW7/MMwnCGlOr9S7Y=
 * */