package com.github.m9w.metaplugins.game.entities

import com.darkorbit.Faction
import com.darkorbit.LegacyModule
import com.darkorbit.ProtocolPacket
import com.darkorbit.Type
import com.darkorbit.VisualModifierCommand
import com.github.m9w.metaplugins.EntitiesModule
import com.github.m9w.metaplugins.game.HealthHolder
import com.github.m9w.metaplugins.game.PositionImpl

abstract class EntityImpl(val root: EntitiesModule, val id: Long, val name: String, x: Int, y: Int, var isVisible: Boolean = true): PositionImpl(x,y) {
    val health: HealthHolder = HealthHolder()
    open val designId = -1
    open val faction: Faction = Faction.NONE
    open val diplomacy: Type = Type.NONE
    open val clanTag: String = ""
    open val clanId: Int = -1
    open val modifiers: MutableList<VisualModifierCommand> = ArrayList()

    open fun canInvoke(): Boolean = false
    open fun invoke(): Boolean = false

    override fun update(packet: ProtocolPacket) {
        super.update(packet)
        health.update(packet)
    }

    protected fun <T> LegacyModule.check(prefix: String, block: (List<String>) -> T): T? = if (message.startsWith(prefix)) block.invoke(message.removePrefix(prefix).split('|')) else null

    override fun toString() = name
}
