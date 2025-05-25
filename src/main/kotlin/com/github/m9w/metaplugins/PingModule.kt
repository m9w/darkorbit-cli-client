package com.github.m9w.metaplugins

import com.darkorbit.KeepAlive
import com.darkorbit.StayinAlive
import com.github.m9w.client.GameEngine
import com.github.m9w.feature.annotations.Inject
import com.github.m9w.feature.annotations.OnEvent
import com.github.m9w.feature.annotations.Repeat
import com.github.m9w.feature.annotations.SystemEvents
import com.github.m9w.feature.timePrefix
import com.github.m9w.feature.waitOnPackage

object PingModule {
    @Inject
    private lateinit var gameEngine: GameEngine
    private var sentKeepAliveTime = System.currentTimeMillis()
    private val pingList = ArrayList<Long>()
    var ping: Double = -1.0; private set

    @Repeat(10_000, true)
    private suspend fun sendKeepAlive() {
        if (gameEngine.state.ordinal < 2) return
        waitOnPackage<StayinAlive> (timeout = 15000) {
            sentKeepAliveTime = System.currentTimeMillis()
            gameEngine.send<KeepAlive> { MouseClick = Math.random() < 0.7 }
        }
        pingList.add(System.currentTimeMillis() - sentKeepAliveTime)
    }

    @OnEvent(SystemEvents.ON_DISCONNECT)
    private fun onDisconnect(body: String) {
        if (gameEngine.state != GameEngine.State.STOPED) gameEngine.connect()
    }

    @Repeat(60_000)
    private fun watchdog() {
        ping = if (pingList.isEmpty()) -1.0 else pingList.average().apply { pingList.clear() }
        if (gameEngine.state == GameEngine.State.STOPED) return
        if (ping != -1.0) return
        println("[$timePrefix] Watchdog restart - connection stuck")
        gameEngine.connect()
    }

    override fun toString(): String = String.format("%.3f", ping)
}