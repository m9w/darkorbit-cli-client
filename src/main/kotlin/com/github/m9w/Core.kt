package com.github.m9w


import com.github.m9w.client.GameEngine
import com.github.m9w.client.auth.AuthenticationProvider
import com.github.m9w.client.network.NetworkLayer
import com.github.m9w.feature.OnPackage
import com.github.m9w.feature.Repeat
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.jvmErasure

class Core(auth: AuthenticationProvider, vararg action: Any) : Runnable {
    val packetController: PacketController<NetworkLayer> = PacketController()
    val timerController: TimerController<NetworkLayer> = TimerController()
    val engine = GameEngine(auth, packetController::process)
    var isRun = true

    init {
        val x = action.flatMap { instance ->
            instance::class.memberFunctions.mapNotNull { method ->
                if (method.hasAnnotation<OnPackage>()) {
                    if (method.parameters.size != 3) throw IllegalArgumentException("Unexpected argument count in $method")
                    val packetType = method.parameters[1].type.jvmErasure
                    packetController.Handler(packetType, method, instance)
                } else if (method.hasAnnotation<Repeat>()) {
                    if (method.parameters.size != 2) throw IllegalArgumentException("Unexpected argument count in $method")
                    method.findAnnotation<Repeat>()?.let { timerController.Repeatable(it.ms, method, instance) }
                } else null
            }
        }

        val y = x.filterIsInstance<TimerController<NetworkLayer>.Repeatable>()
        y.forEach { it.schedule() }
        x.filterIsInstance<PacketController<NetworkLayer>.Handler>().forEach { handler ->
            packetController.handlers[handler.packetType] = handler
        }

        Thread(this, "Instance ${auth.getUserId()}").start()
        //engine.start()
    }

    override fun run() {
        val x = System.currentTimeMillis()
        try {
            while (isRun) {
                //println("Tick: ${System.currentTimeMillis()-x}")
                packetController.perform(engine.network, timerController)
                try {
                    Thread.sleep(100)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                timerController.perform(engine.network, packetController)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}