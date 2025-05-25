package com.github.m9w.client

import com.darkorbit.ProtocolPacket
import com.github.m9w.Scheduler
import com.github.m9w.client.auth.AuthenticationProvider
import com.github.m9w.client.network.NetworkLayer
import com.github.m9w.feature.annotations.SystemEvents
import com.github.m9w.protocol.Factory
import java.net.InetSocketAddress

class GameEngine(val authentication: AuthenticationProvider, private val scheduler: Scheduler) {
    var network: NetworkLayer = NetworkLayer(InetSocketAddress(0)); private set
    var state: State = State.NOT_CONNECTED

    enum class State {
        NOT_CONNECTED, NO_LOGIN, DESTROYED, NORMAL, REPAIRING, TRAVELING, ESCAPING, STOPED
    }

    fun connect() {
        state = State.NOT_CONNECTED
        network.close()
        network = NetworkLayer(authentication.address)
        network.onPackageHandler = scheduler::handleEvent
        network.onDisconnect = { handleEvent(SystemEvents.ON_DISCONNECT) }
        handleEvent(SystemEvents.ON_CONNECT)
    }

    fun handleEvent(event: String, body: String = "") = scheduler.handleEvent(event, body)

    inline fun <reified T : ProtocolPacket> send(noinline changes: T.() -> Unit) {
        val data = Factory.build(T::class).also { changes.invoke(it) }
        network.send(data)
    }

    fun disconnect() {
        state = State.STOPED
        network.close()
    }
}
