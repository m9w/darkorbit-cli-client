package com.github.m9w.monitor

import java.net.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

object UdpServer {
    private val socket = DatagramSocket(7788, InetAddress.getByName("0.0.0.0"))
    private val clients = ConcurrentHashMap<SocketAddress, Long>()
    private val buffer = ByteArray(65535)
    private val packet = DatagramPacket(buffer, buffer.size)
    init {
        thread(name = "udp-server", isDaemon = true) {
            while (true) {
                try {
                    socket.receive(packet)
                    val clientAddress = packet.socketAddress
                    val msg = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    if (msg == "Ping") {
                        clients[clientAddress] = System.currentTimeMillis()
                        sendTo(clientAddress, "Pong")
                    }

                } catch (e: Exception) {
                    System.err.println("Error receiving packet: ${e.message}")
                }
            }
        }
    }

    fun send(msg: String) {
        val msgBytes = msg.toByteArray(Charsets.UTF_8)
        val now = System.currentTimeMillis()
        clients.entries.removeIf { now - it.value  > 900_000L }
        clients.keys.mapNotNull {
            try {
                socket.send(DatagramPacket(msgBytes, msgBytes.size, it as InetSocketAddress))
                null
            } catch (_: Exception) { it }
        }.forEach(clients::remove)
    }

    private fun sendTo(client: SocketAddress, msg: String) {
        try {
            val bytes = msg.toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(bytes, bytes.size, (client as InetSocketAddress))
            socket.send(packet)
        } catch (e: Exception) {
            System.err.println("Failed to reply to $client: ${e.message}")
        }
    }
}
