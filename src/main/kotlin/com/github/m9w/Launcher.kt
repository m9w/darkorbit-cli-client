package com.github.m9w

import com.github.m9w.client.auth.AuthenticationProvider
import com.github.m9w.feature.annotations.SystemEvents
import com.github.m9w.plugins.dao.DynamicModule

object Launcher {
    fun run(modules: List<DynamicModule>, auth: AuthenticationProvider): Scheduler {
        val moduleInstances = modules.map { it.newInstance }
        val scheduler = moduleInstances.map { it.instance }.filterIsInstance<Scheduler>().first()
        scheduler.init(moduleInstances.toSet())
        scheduler.sendEvent(SystemEvents.ON_AUTH_SELECT_EVENT, auth.serialized)
        return scheduler
    }
}