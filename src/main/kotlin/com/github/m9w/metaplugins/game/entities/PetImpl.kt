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
    cloaked = activation.isVisible
    x = activation.x
    y = activation.y
}) {
    var level: Int = activation.petLevel.toInt()
    var speed: Int = activation.petSpeed
    var fuel: Int = 0; protected set
    var fuelMax: Int = 0; protected set
    var heat: Int = 0; protected set
    var heatMax: Int = 0; protected set
    var experience: Long = 0; protected set
    var experienceNextLevel: Long = 0; protected set

    override fun update(packet: ProtocolPacket) {
        super.update(packet)
        if (packet is PetFuelUpdateCommand) packet.apply { fuelMax = petFuelMax; fuel = petFuelNow }
        if (packet is PetExperiencePointsUpdateCommand) packet.apply { experience = currentExperiencePoints; experienceNextLevel = maxExperiencePoints }
        if (packet is PetLevelUpdateCommand) packet.apply { level = petLevel.toInt(); experienceNextLevel = petExperiencePointsUntilNextLevel }
    }
}