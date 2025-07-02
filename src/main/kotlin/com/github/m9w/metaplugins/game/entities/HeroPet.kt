package com.github.m9w.metaplugins.game.entities

import com.darkorbit.*
import com.github.m9w.metaplugins.EntitiesModule
import com.github.m9w.protocol.Factory
import com.github.m9w.setPetActive
import com.github.m9w.setPetGear

class HeroPet(root: EntitiesModule, activation: PetHeroActivationCommand) : PetImpl(root, Factory.build<PetActivationCommand> {
    clanRelationship = clanRelationship.apply { type = Type.ALLIED }
    petClanID = activation.clanId
    clanTag = activation.clanTag
    expansionStage = activation.expansionStage
    petFactionId = activation.factionId
    ownerId = activation.ownerId
    petId = activation.petId
    petName = activation.petName
    petDesignId = activation.shipType
    isVisible = true
    x = activation.x
    y = activation.y
}) {
    var heatState: HeatState = HeatState.GAINING_HEAT; private set
    var heat: Int = 0; private set
    var heatMax: Int = 0; private set
    var heatLevel: Int = 0; private set
    var locatorOptions: List<Int> = emptyList(); private set
    var selectedGear: PetGearType = PetGearType.PASSIVE; private set
    val gears: MutableMap<PetGearType, PetGearAddCommand> = HashMap()

    fun gearSelect(gearType: PetGearType, optional: Int = 0) {
        if (gears.containsKey(gearType)) gearType.select(optional)
    }

    fun deactivate() = root.gameEngine.setPetActive(false)

    override fun update(packet: ProtocolPacket) {
        super.update(packet)
        when (packet) {
            is PetFuelUpdateCommand -> update(packet)
            is PetExperiencePointsUpdateCommand -> update(packet)
            is PetLevelUpdateCommand -> update(packet)
            is PetHeatUpdateCommand -> update(packet)
            is PetGearAddCommand -> update(packet)
            is PetGearSelectCommand -> update(packet)
            is PetGearResetCommand -> update(packet)
            is PetGearRemoveCommand -> update(packet)
            is PetStatusCommand -> update(packet)
            is PetLocatorGearInitializationCommand -> update(packet)
        }
    }

    private fun update(packet: PetGearSelectCommand) {
        selectedGear = packet.gearType.typeValue
    }

    private fun update(packet: PetGearResetCommand) {
        gears.clear()
    }

    private fun update(packet: PetGearAddCommand) {
        gears.put(packet.gearType.typeValue, packet)
    }

    private fun update(packet: PetGearRemoveCommand) {
        gears.remove(packet.gearType.typeValue)
    }

    private fun update(packet: PetLocatorGearInitializationCommand) {
        locatorOptions = packet.possibleTargetValues
    }

    private fun update(packet: PetFuelUpdateCommand) {
        fuelMax = packet.petFuelMax
        fuel = packet.petFuelNow
    }

    private fun update(packet: PetExperiencePointsUpdateCommand) {
        experience = packet.currentExperiencePoints
        experienceNextLevel = packet.maxExperiencePoints
    }

    private fun update(packet: PetLevelUpdateCommand) {
        level = packet.petLevel.toInt()
        experienceNextLevel = packet.petExperiencePointsUntilNextLevel
    }

    private fun update(packet: PetHeatUpdateCommand) {
        heat = packet.petHeatAmount.toInt()
        heatMax = packet.petHeatAmountMax.toInt()
        heatLevel = packet.heatLevel
        heatState = packet.heatState
    }

    private fun update(packet: PetStatusCommand) {
        fuel = packet.petCurrentFuel
        experience = packet.petExperiencePoints
        experienceNextLevel = packet.petExperiencePointsUntilNextLevel
        level = packet.petLevel
        fuelMax = packet.petMaxFuel
        speed = packet.petSpeed
    }

    override fun toString() = super.toString() +
        "Heat level $heatLevel next $heat/$heatMax${if(heatState != HeatState.GAINING_HEAT) " - $heatState" else ""}\n" +
        "Gear $selectedGear of ${gears.keys}\n" +
        (if (!locatorOptions.isEmpty()) "Locator options: $locatorOptions\n" else "")

    private fun PetGearType.select(optional: Int) = root.gameEngine.setPetGear(this, optional)
}
