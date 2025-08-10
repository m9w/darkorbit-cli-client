package com.github.m9w

import com.github.m9w.client.GameEngine
import com.github.m9w.metaplugins.*
import com.github.m9w.metaplugins.PathTracerModule
import com.github.m9w.plugins.BasicBoxCollector
import com.github.m9w.plugins.MapTraveler
import com.github.m9w.plugins.NpcKiller
import com.github.m9w.util.ProcessIdentifier

fun main() {
    ProcessIdentifier.check()
    val npcKiller = NpcKiller()
    EntitiesDebugUiModule(npcKiller) { auth, cont ->
        Scheduler(GameEngine(),
            auth,
            cont,
            LoginModule(LoginModule.Type.UNITY),
            //HttpProxyModule(),
            BasicRepairModule(),
            PingModule(),
            EntitiesModule(),
            MapModule(),
            PathTracerModule(),
            MoveModule(),
            BasicBoxCollector(),
            MapTraveler(),
            npcKiller
        ).start()
    }
}
