package com.github.m9w.client

import com.darkorbit.*
import com.github.m9w.client.auth.AuthenticationProvider
import com.github.m9w.client.network.NetworkLayer
import com.github.m9w.protocol.ProtocolParser
import com.github.m9w.util.timePrefix
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class GameEngine(private val authentication: AuthenticationProvider, private val handler: NetworkLayer.(Any) -> Unit) {
    private var executorService: ScheduledExecutorService? = null
    private var mapId: Int = 1
    private var sentKeepAliveTime = System.currentTimeMillis()
    private var pingList = ArrayList<Long>()
    private var networkLayer: NetworkLayer = NetworkLayer(InetSocketAddress(0)) {}
    private var lastPackage: Long = 0
    private var builtInVersion = ""
    private var init: Boolean = false
    val network get() = networkLayer


    fun start() {
        init = false
        networkLayer.close()
        lastPackage = System.currentTimeMillis() + 60000
        networkLayer = NetworkLayer(getMapAddress(authentication.getServer(), mapId)) {
            lastPackage = System.currentTimeMillis()
            when (it) {
                is VersionCommand -> {
                    if (it.equal) send<LoginRequest> {
                        userID = authentication.getUserId()
                        sessionID = authentication.getSid()
                        instanceId = 68 // 68 - flash, 1396 - unity
                        isMiniClient = true
                    } else {
                        println("close")
                        network.close()
                        ProtocolParser.reload()
                        builtInVersion = it.version
                        println("Protocol updated to latest version $builtInVersion")
                        start()
                    }
                }
                is LegacyModule -> if (it.message.startsWith("0|i|")) mapId = it.message.removePrefix("0|i|").toInt()
                is LoginResponse -> {
                    if (it.status == LoginResponseStatus.Success) {
                        send<ReadyRequest> { readyType = ReadyMessage.MAP_LOADED_2D }
                        send<ReadyRequest> { readyType = ReadyMessage.UI_READY }
                        init = true
                    } else if (it.status == LoginResponseStatus.WrongServer) {
                        println("Change server, next map $mapId")
                        start()
                    } else println(it)
                }

                is StayinAlive -> pingList.add(System.currentTimeMillis() - sentKeepAliveTime)
            }
            handler.invoke(this, it)
        }
        networkLayer.send<VersionRequest> { version = builtInVersion }
        executorService?.shutdown()
        executorService = Executors.newSingleThreadScheduledExecutor().apply {
            scheduleWithFixedDelay(this@GameEngine::watchdog, 0, 60, TimeUnit.SECONDS)
            scheduleWithFixedDelay(this@GameEngine::sendKeepAlive, 10, 10, TimeUnit.SECONDS)
        }
    }

    inline fun <reified T : Any> send(noinline changes: T.() -> Unit) {
        network.send(T::class, changes)
    }

    fun setPetActive(isActive: Boolean) {
        if (!init) return
        println("setPetActive($isActive)")
        networkLayer.send<PetRequest> { this.petRequestType = if (isActive) PetRequestType.LAUNCH else PetRequestType.DEACTIVATE }
    }

    fun buyPetFuel() {
        if (!init) return
        networkLayer.send<PetRequest> { this.petRequestType = PetRequestType.HOTKEY_BUY_FUEL }
    }

    private fun watchdog() {
        println(String.format("[$timePrefix] Ping: %.3f ms", pingList.average()))
        pingList.clear()
        if (lastPackage < System.currentTimeMillis()-15000) {
            println("[$timePrefix] Watchdog restart - timeout")
            start()
        } else if (!networkLayer.isAlive()) {
            println("[$timePrefix] Watchdog restart - connection")
            start()
        }
    }

    private fun sendKeepAlive() {
        if (!init) return
        networkLayer.send(KeepAlive::class) { MouseClick = Math.random() < 0.7 }
        sentKeepAliveTime = System.currentTimeMillis()
    }

    private fun getMapAddress(server: String, mapId: Int): InetSocketAddress {
        val mapRegex = Regex("""<map\s+id="(\d+)">.*?<gameserverIP>([^<]+)</gameserverIP>.*?</map>""", RegexOption.DOT_MATCHES_ALL)
        val xml = String(URI("https://$server.darkorbit.com/spacemap/xml/maps.php").toURL().readBytes())
        for (match in mapRegex.findAll(xml)) {
            val (host, port) = match.groupValues[2].split(":")
            if (mapId == match.groupValues[1].toInt()) return InetSocketAddress(host, port.toInt())
        }
        throw IllegalArgumentException("Map $mapId not found")
    }
}
