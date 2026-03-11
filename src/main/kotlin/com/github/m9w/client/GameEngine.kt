package com.github.m9w.client

import com.darkorbit.ProtocolPacket
import com.github.m9w.Scheduler
import com.github.m9w.client.auth.AuthenticationProvider
import com.github.m9w.client.network.NetworkLayer
import com.github.m9w.config.accountConfig
import com.github.m9w.context.context
import com.github.m9w.feature.annotations.SystemEvents
import com.github.m9w.feature.waitMs
import com.github.m9w.metaplugins.proxy.ProxyModule
import com.github.m9w.context.optionalContext
import com.github.m9w.protocol.Factory
import java.awt.Color

class GameEngine {
    private val authentication: AuthenticationProvider by context
    private val scheduler: Scheduler by context
    private val proxy: ProxyModule? by optionalContext
    val userIdAndSid get() = authentication.run { "$userID|$sessionID" }
    var network: NetworkLayer = NetworkLayer(); private set
    var initState: State by accountConfig(State.NORMAL)
    var state: State by accountConfig(State.NOT_CONNECTED, false)

    enum class State(val color: Color = Color.cyan) {
        NOT_CONNECTED(Color.gray),
        NO_LOGIN(Color.lightGray),
        DESTROYED(Color.darkGray),
        NORMAL(Color.green),
        REPAIRING(Color.magenta),
        TRAVELING(Color.cyan),
        ESCAPING(Color.orange),
        IDLE(Color.yellow),
        STOPPED(Color.black),
    }

    fun connect() {
        try {
            state = State.NOT_CONNECTED
            network.onDisconnect = {}
            network.close()
            network = NetworkLayer(authentication.address, proxy)
            network.onPackageHandler = scheduler::handleEvent
            network.onDisconnect = { handleEvent(SystemEvents.ON_DISCONNECT) }
            handleEvent(SystemEvents.ON_CONNECT)
        } catch (e: Exception) {
            e.printStackTrace()
            handleEvent(SystemEvents.ON_DISCONNECT, async = true)
        }
    }

    fun disconnect() {
        proxy?.releaseProxy()
        network.close()
        state = State.STOPPED
    }

    suspend fun reconnect(reconnectInMs: Long = 0, keepProxy: Boolean = false) {
        network.close()
        if (reconnectInMs > 0) {
            if (!keepProxy) proxy?.releaseProxy()
            network.close()
            state = State.STOPPED
            waitMs(reconnectInMs)
        }
        state = State.NOT_CONNECTED
    }

    fun handleEvent(event: String, body: String = "", async: Boolean = false) = scheduler.handleEvent(event, body, async)

    fun cancelWaitMs(interruptKey: String, body: (()->Exception)?=null) = scheduler.cancelWaitMs(interruptKey, body)

    inline fun <reified T : ProtocolPacket> send(noinline changes: T.() -> Unit) {
        val data = Factory.build(T::class).also { changes.invoke(it) }
        network.send(data)
    }
}
