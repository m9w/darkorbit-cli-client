package com.github.m9w

import com.github.m9w.client.GameEngine
import com.github.m9w.client.auth.AuthenticationProvider
import com.github.m9w.metaplugins.*
import com.github.m9w.metaplugins.PathTracerModule
import com.github.m9w.metaplugins.proxy.HttpProxyModule
import com.github.m9w.util.ProcessIdentifier

fun main() {
    ProcessIdentifier.check()
    EntitiesDebugUiModule { auth, cont ->
        Scheduler(GameEngine(),
            auth,
            cont,
            LoginModule(LoginModule.Type.FLASH),
            //HttpProxyModule(),
            BasicRepairModule(),
            PingModule(),
            EntitiesModule(),
            MapModule(),
            PathTracerModule(),
            MoveModule(),
        ).start()
    }
}
