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

fun main(args: Array<String>) {
    File("pid").apply { deleteOnExit() }.writeText(ManagementFactory.getRuntimeMXBean().name.split("@").first())
    Bootstrap(AuthenticationProvider.static(args[0].toInt(), args[1], args[2]),
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
