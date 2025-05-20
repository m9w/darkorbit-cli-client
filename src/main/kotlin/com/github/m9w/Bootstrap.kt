package com.github.m9w

import com.github.m9w.client.GameEngine
import com.github.m9w.client.auth.AuthenticationProvider
import com.github.m9w.feature.annotations.Inject
import com.github.m9w.feature.annotations.OnPackage
import com.github.m9w.feature.annotations.Repeat
import com.github.m9w.feature.SchedulerEntity
import com.github.m9w.feature.annotations.OnEvent
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.jvmErasure


class Bootstrap(auth: AuthenticationProvider, vararg action: Any) {
    val scheduler: Scheduler = Scheduler()
    val engine = GameEngine(auth, scheduler)
    private val context: Map<KClass<*>, *> = (action.toList() + engine).associateBy { it::class } + (AuthenticationProvider::class to engine.authentication)

    init {
        action.flatMap { instance ->
            instance::class.memberProperties.forEach { property ->
                property.findAnnotation<Inject>()?.let { inject ->
                    val value = context[property.returnType.jvmErasure]
                    if (inject.mandatory && value == null) throw IllegalArgumentException("Mandatory field not found in context")
                    if (value == null) return@let
                    if (property is KMutableProperty<*>) {
                        property.isAccessible = true
                        try {
                            property.setter.call(value)
                        } catch (_: Exception) {
                            property.setter.call(instance, value)
                        }
                    }
                }
            }

            instance::class.memberFunctions.mapNotNull { method ->
                if (method.hasAnnotation<OnPackage>()) {
                    if (method.parameters.size != 2) throw IllegalArgumentException("Unexpected argument count in $method")
                    val packetType = method.parameters[1].type.jvmErasure
                    scheduler.Handler(packetType.simpleName!!, method, instance)
                } else if (method.hasAnnotation<Repeat>()) {
                    if (method.parameters.size != 1) throw IllegalArgumentException("Unexpected argument count in $method")
                    method.findAnnotation<Repeat>()?.let { scheduler.Repeatable(it.ms, method, instance, it.noInitDelay) }
                } else if (method.hasAnnotation<OnEvent>()) {
                    if (method.parameters.size != 2) throw IllegalArgumentException("Unexpected argument count in $method")
                    scheduler.Handler("@"+method.findAnnotation<OnEvent>()!!.event, method, instance)
                } else null
            }
        }.forEach(SchedulerEntity::schedule)
        scheduler.start()
        engine.connect()
    }
}
