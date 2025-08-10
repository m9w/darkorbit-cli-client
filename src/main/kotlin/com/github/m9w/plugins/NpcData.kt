package com.github.m9w.plugins

import java.util.concurrent.ConcurrentHashMap

data class NpcInfo(
    val name: String,
    var killable: Boolean = false,
    var attackRadius: Int = 500
)

object NpcData {
    private val discoveredNpcs: ConcurrentHashMap<String, NpcInfo> = ConcurrentHashMap()

    fun getNpcInfo(npcName: String): NpcInfo {
        return discoveredNpcs.computeIfAbsent(npcName) { name ->
            NpcInfo(name)
        }
    }

    fun setKillable(npcName: String, killable: Boolean) {
        getNpcInfo(npcName).killable = killable
    }

    fun setRadius(npcName: String, radius: Int) {
        println("NpcData.setRadius called for $npcName with radius: $radius")
        getNpcInfo(npcName).attackRadius = radius
    }

    fun getAllNpcNames(): List<String> {
        return discoveredNpcs.keys().toList().sorted()
    }
}
