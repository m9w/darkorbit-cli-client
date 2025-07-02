package com.github.m9w.metaplugins.game.entities

import com.darkorbit.*
import com.github.m9w.metaplugins.EntitiesModule
import com.github.m9w.protocol.Factory

open class PetImpl(root: EntitiesModule, activation: PetActivationCommand): ShipImpl(root, Factory.build(ShipCreateCommand::class).apply {
    clanDiplomacy = activation.clanRelationship
    clanId = activation.petClanID
    clanTag = activation.clanTag
    expansionStage = activation.expansionStage.toInt()
    factionId = activation.petFactionId.toInt()
    modifier = mutableListOf()
    motherShipId = activation.ownerId
    userId = activation.petId
    userName = activation.petName
    designSet = activation.petDesignId.toString()
    cloaked = !activation.isVisible
    x = activation.x
    y = activation.y
}) {
    var level: Int = activation.petLevel.toInt(); protected set
    var speed: Int = activation.petSpeed; protected set
    var fuel: Int = 0; protected set
    var fuelMax: Int = 0; protected set
    var experience: Long = 0; protected set
    var experienceNextLevel: Long = 0; protected set
    var isIdle: Boolean = true; protected set

    override fun update(packet: ProtocolPacket) {
        super.update(packet)
        if (packet is PetStatusCommand) update(packet)
        if (packet is PetIdleModeCommand) isIdle = packet.isIdle
    }

    private fun update(packet: PetStatusCommand) {
        level = packet.petLevel
        speed = packet.petSpeed
        fuel = packet.petCurrentFuel
        fuelMax = packet.petMaxFuel
        experience = packet.petExperiencePoints
        experienceNextLevel = packet.petExperiencePointsUntilNextLevel
    }

    override fun toString(): String {
        return super.toString() +
                ("IDLE\n".takeIf { isIdle } ?: "") +
                "Fuel $fuel/$fuelMax\n" +
                "Speed $speed\n" +
                "Level $level\n" +
                (if (level != 20) "Experience $experience/$experienceNextLevel\n" else "")
    }
}
