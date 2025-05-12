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
    val packetController: PacketController = PacketController()
    val timerController: TimerController = TimerController()
    val engine = GameEngine(auth) { packetController.process(it) }
    var isRun = true

    init {
        packetController.timer = timerController
        timerController.packet = packetController
        val x = action.flatMap { instance ->
            instance::class.memberFunctions.mapNotNull { method ->
                if (method.hasAnnotation<OnPackage>()) {
                    if (method.parameters.size != 2) throw IllegalArgumentException("Unexpected argument count in $method")
                    val packetType = method.parameters[1].type.jvmErasure
                    packetController.Handler(packetType, method, instance)
                } else if (method.hasAnnotation<Repeat>()) {
                    if (method.parameters.size != 1) throw IllegalArgumentException("Unexpected argument count in $method")
                    method.findAnnotation<Repeat>()?.let { timerController.Repeatable(it.ms, method, instance) }
                } else null
            }
        }

        x.filterIsInstance<TimerController.Repeatable>().forEach { it.schedule() }
        x.filterIsInstance<PacketController.Handler>().forEach { it.schedule() }

        Thread(this, "Instance ${auth.getUserId()}").start()
        //engine.start()
    }

    override fun run() {
        while (isRun) {
            try {
                packetController.perform()
                Thread.sleep(100)
                timerController.perform()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
