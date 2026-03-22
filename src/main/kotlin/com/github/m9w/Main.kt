package com.github.m9w

import com.github.m9w.client.GameEngine
import com.github.m9w.config.PersistYamlConfig
import com.github.m9w.feature.annotations.SystemEvents
import com.github.m9w.metaplugins.*
import com.github.m9w.metaplugins.PathTracerModule
import com.github.m9w.metaplugins.proxy.EnvProxyPool
import com.github.m9w.metaplugins.proxy.ProxyModule
import com.github.m9w.util.ProcessIdentifier

fun main() {
    ProcessIdentifier.check()
    EntitiesDebugUiModule { auth, cont ->
        val scheduler = Scheduler()
        scheduler.sendEvent(SystemEvents.ON_AUTH_SELECT_EVENT, auth.serialized)
        scheduler.init(setOf(
            AuthModule(),
            GameEngine(),
            cont,
            LoginModule(),
            ProxyModule(),
            //EnvProxyPool,
            BasicRepairModule(),
            PingModule(),
            EntitiesModule(),
            MapModule(),
            PathTracerModule(),
            PersistYamlConfig(),
            MoveModule(),
        ))
    }
}
