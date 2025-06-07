package com.github.m9w

import com.github.m9w.client.auth.AuthenticationProvider
import com.github.m9w.metaplugins.*
import com.github.m9w.metaplugins.game.PathTracerModule
import com.github.m9w.util.ProcessIdentifier
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    ProcessIdentifier.check()
    if (args.isEmpty()) {
        println("Use `login <login> <password>` arguments to run demo code using login and password.")
        println("  Sample: gradlew run --args='login userName1 userPassword'")
        println("Use `sid <server> <sessionId>` arguments to run demo code using server and sid.")
        println("  Sample: gradlew run --args='sid gbl1 a0cd1234d79f057c7745db24f3rt4sx3'")
        exitProcess(0)
    }

    val auth = when (args[0]) {
        "login" -> AuthenticationProvider.byLoginPassword(args[1], args[2])
        "sid" -> AuthenticationProvider.byServerSid(args[1], args[2])
        else -> throw RuntimeException()
    }

    Bootstrap(auth,
        LoginModule,
        BasicRepairModule,
        PingModule,
        EntitiesModule(),
        EntitiesDebugUiModule(),
        MapModule(),
        PathTracerModule()
    )
}
