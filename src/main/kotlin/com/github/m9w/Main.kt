package com.github.m9w

import com.darkorbit.*
import com.github.m9w.client.GameEngine
import com.github.m9w.client.auth.AuthenticationProvider
import com.github.m9w.feature.annotations.Inject
import com.github.m9w.feature.annotations.OnPackage
import com.github.m9w.feature.annotations.Repeat
import com.github.m9w.metaplugins.LoginModule
import com.github.m9w.metaplugins.PingModule
import com.github.m9w.util.timePrefix
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    File("pid").apply { deleteOnExit() }.writeText(ManagementFactory.getRuntimeMXBean().name.split("@").first())
    if (args.isEmpty()) {
        println("Use `login <login> <password>` arguments to run demo code using login and password.")
        println("  Sample: gradlew run --args='login userName1 userPassword'")
        println("Use `sid <server> <sessionId>` arguments to run demo code using server and sid.")
        println("  Sample: gradlew run --args='sid gbl1 a0cd1234d79f057c7745db24f3rt4sx3")
        exitProcess(0)
    }

    val auth = when (args[0]) {
        "login" -> AuthenticationProvider.byLoginPassword(args[1], args[2])
        "sid" -> AuthenticationProvider.byServerSid(args[1], args[2])
        else -> throw RuntimeException()
    }

    Bootstrap(auth,
        LoginModule,
        PingModule,
        Main)
}

object Main {
    @Inject
    private lateinit var gameEngine: GameEngine

    @Inject
    private lateinit var ping: PingModule

    @OnPackage
    private fun onShipCreate(shipCreate: ShipCreateCommand) {
        println("[$timePrefix] Ship: ${shipCreate.userName}, (${shipCreate.x}, ${shipCreate.y}), ${shipCreate.userId}")
    }

    @OnPackage
    private fun onJumpgateCreate(jumpgateCreate: JumpgateCreateCommand) {
        println("[$timePrefix] Jumpgate: (${jumpgateCreate.x}, ${jumpgateCreate.y})")
    }

    @OnPackage
    private fun onAssetCreate(assetCreate: AssetCreateCommand) {
        println("[$timePrefix] Asset: ${assetCreate.userName}, (${assetCreate.posX}, ${assetCreate.posY}), ${assetCreate.type.typeValue}")
    }

    @Repeat(15_000)
    private fun pingPrint() {
        println("[$timePrefix] Ping: $ping ms")
    }
}
