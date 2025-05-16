package com.github.m9w

import com.darkorbit.ProtocolPacket
import com.github.m9w.client.GameEngine
import com.github.m9w.client.auth.AuthenticationProvider
import com.github.m9w.feature.Inject
import com.github.m9w.feature.OnPackage
import com.github.m9w.feature.Repeat
import com.github.m9w.feature.SchedulerEntity
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.jvmErasure


class Bootstrap(auth: AuthenticationProvider, vararg action: Any) {
    val scheduler: Scheduler = Scheduler()
    val engine = GameEngine(auth, scheduler::handleEvent)
    private val context: Map<KClass<*>, *> = (action.toList() + engine).associateBy { it::class }

    init {
        action.flatMap { instance ->
            instance::class.memberProperties.forEach { property ->
                property.findAnnotation<Inject>()?.let { inject ->
                    val value = context[property.returnType.jvmErasure]
                    if (inject.mandatory && value == null) throw IllegalArgumentException("Mandatory field not found in context")
                    if (value == null) return@let
                    if (property is KMutableProperty<*>) {
                        property.isAccessible = true
                        property.setter.call(value)
                    }
                }
            }

            instance::class.memberFunctions.mapNotNull { method ->
                if (method.hasAnnotation<OnPackage>()) {
                    if (method.parameters.size != 2) throw IllegalArgumentException("Unexpected argument count in $method")
                    val packetType = method.parameters[1].type.jvmErasure
                    if (packetType.isSubclassOf(ProtocolPacket::class)) scheduler.Handler(packetType as KClass<ProtocolPacket>, method, instance)
                    else throw IllegalArgumentException("Unexpected argument type in $method. Method should have single ProtocolPacket type arg")
                } else if (method.hasAnnotation<Repeat>()) {
                    if (method.parameters.size != 1) throw IllegalArgumentException("Unexpected argument count in $method")
                    method.findAnnotation<Repeat>()?.let { scheduler.Repeatable(it.ms, method, instance) }
                } else null
            }
        }.forEach(SchedulerEntity::schedule)
        Thread(scheduler, "Instance ${auth.getUserId()}").start()
        //engine.start()
    }
}
