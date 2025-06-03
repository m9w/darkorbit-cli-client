package com.github.m9w.metaplugins.game.entities

import com.darkorbit.Faction
import com.darkorbit.LegacyModule
import com.darkorbit.ProtocolPacket
import com.darkorbit.ShipSelectionCommand
import com.darkorbit.Type
import com.darkorbit.VisualModifierCommand
import com.github.m9w.metaplugins.EntitiesModule
import com.github.m9w.metaplugins.game.HealthHolder
import com.github.m9w.metaplugins.game.PositionImpl

abstract class EntityImpl(val root: EntitiesModule, val id: Long, val name: String, x: Int, y: Int, var isVisible: Boolean = true): PositionImpl(x,y) {
    val health: HealthHolder = HealthHolder()
    open val designId: Int = -1
    open val faction: Faction = Faction.NONE
    open val diplomacy: Type = Type.NONE
    open val clanTag: String = ""
    open val clanId: Int = -1
    open val modifiers: MutableList<VisualModifierCommand> = ArrayList()
    open val isSafe get() = faction == root.hero?.faction || diplomacy == Type.ALLIED || diplomacy == Type.NON_AGGRESSION_PACT

    open fun canInvoke(): Boolean = false
    open fun invoke(): Boolean = false

    override fun update(packet: ProtocolPacket) {
        super.update(packet)
        health.update(packet)
        if (packet is ShipSelectionCommand) root.hero?.target = this
    }

    protected fun <T> LegacyModule.check(prefix: String, block: (List<String>) -> T): T? = if (message.startsWith(prefix)) block.invoke(message.removePrefix(prefix).split('|')) else null

    override fun toString() = (if (faction != Faction.NONE) "<${faction.name}> " else "" ) + (if(clanTag.isEmpty()) "" else "[$clanTag] ") + name + "\n" +
            position.run { "Position ${first/100.0} x ${second/100.0}\n" } +
            health +
            (if(modifiers.isNotEmpty()) modifiers.joinToString(", ") { it.modifier.name + if(it.count > 1) "${it.count}" else "" } + "\n" else "") +
            (if(designId != -1) "Design <$designId>\n" else "")
}
