package com.github.m9w.metaplugins.game

import com.darkorbit.*

class HealthHolder {
    var health: Long = 0; private set
    var healthMax: Long = 0; private set
    var shield: Int = 0; private set
    var shieldMax: Int = 0; private set
    var nano: Int = 0; private set
    var nanoMax: Int = 0; private set

    override fun toString(): String =
        if (health + healthMax + shield + shieldMax + nano + nanoMax == 0L) ""
        else "HP(${(health+nano)/1000}k/${healthMax/1000}k)" + if (shieldMax>0) " SH(${shield/1000}k/${shieldMax/1000}k)\n" else "\n"

    fun update(packet: ProtocolPacket) {
        when (packet) {
            is ShipInitializationCommand -> update(packet)
            is HealthModule -> update(packet)
            is ShipSelectionCommand -> update(packet)
            is HealCommand -> update(packet)
            is HitpointInfoCommand -> update(packet)
            is AttackHitCommand -> update(packet)
            is PetHitpointsUpdateCommand -> update(packet)
            is PetShieldUpdateCommand -> update(packet)
            is PetStatusCommand -> update(packet)
            is AttributeShieldUpdateCommand -> update(packet)
            is AssetInfoCommand -> update(packet)
            is AttackHitAssetCommand -> update(packet)
            is StationModuleModule -> update(packet)
            is ModuleStatusUpdateCommand -> update(packet)
            is AttackHitNoLockCommand -> update(packet)
            is AttributeHitpointUpdateCommand -> update(packet)
        }
    }

    fun update(init: ShipInitializationCommand) {
        health = init.hitPoints
        healthMax = init.hitMax
        shield = init.shield
        shieldMax = init.shieldMax
        nano = init.nanoHull
        nanoMax = init.maxNanoHull
    }

    fun update(healthModule: HealthModule) {
        health = healthModule.hp
        healthMax = healthModule.hpMax
        shield = healthModule.shield
        shieldMax = healthModule.shieldMax
        nano = healthModule.nanoHull
        nanoMax = healthModule.nanoHullMax
    }

    fun update(selectedShip: ShipSelectionCommand) {
        health = selectedShip.hitpoints
        healthMax = selectedShip.hitpointsMax
        shield = selectedShip.shield
        shieldMax = selectedShip.shieldMax
        nano = selectedShip.nanoHull
        nanoMax = selectedShip.maxNanoHull
    }

    fun update(heal: HealCommand) {
        health = heal.currentHitpoints.toLong()
    }

    fun update(hitPoints: HitpointInfoCommand) {
        health = hitPoints.hitpoints
        healthMax = hitPoints.hitpointsMax
        nano = hitPoints.nanoHull
        nanoMax = hitPoints.nanoHullMax
    }

    fun update(hitPoints: AttackHitCommand) {
        health = hitPoints.victimHitpoints
        shield = hitPoints.victimShield
        nano = hitPoints.victimNanoHull
    }

    fun update(petHitPoints: PetHitpointsUpdateCommand) {
        health = petHitPoints.hitpointsNow.toLong()
        healthMax = petHitPoints.hitpointsMax.toLong()
    }

    fun update(petShield: PetShieldUpdateCommand) {
        shield = petShield.petShieldNow
        shieldMax = petShield.petShieldMax
    }

    fun update(petStatus: PetStatusCommand) {
        health = petStatus.petHitPoints.toLong()
        healthMax = petStatus.petHitPointsMax.toLong()
        shield = petStatus.petShieldEnergyNow
        shieldMax = petStatus.petShieldEnergyMax
    }

    fun update(shieldAttr: AttributeShieldUpdateCommand) {
        shield = shieldAttr.shieldNow
        shieldMax = shieldAttr.shieldMax
    }

    fun update(asset: AssetInfoCommand) {
        health = asset.hitpoints
        healthMax = asset.maxHitpoints
        shield = asset.shieldEnergy
        shieldMax = asset.maxShieldEnergy
    }

    fun update(asset: AttackHitAssetCommand) {
        health = asset.hitpointsNow
        healthMax = asset.hitpointsMax
    }

    fun update(station: StationModuleModule) {
        health = station.currentHitpoints.toLong()
        healthMax = station.maxHitpoints.toLong()
        shield = station.currentShield
        shieldMax = station.maxShield
    }

    fun update(asteroid: ModuleStatusUpdateCommand) {
        health = asteroid.hitpoints.toLong()
        healthMax = asteroid.hitpointsMax.toLong()
        shield = asteroid.shield
        shieldMax = asteroid.shieldMax
    }

    fun update(hitPoints: AttackHitNoLockCommand) {
        health = hitPoints.victimHitpoints
        healthMax = hitPoints.victimMaxHitpoints
        shield = hitPoints.victimShield
        shieldMax = hitPoints.victimMaxShield
        nano = hitPoints.victimMaxShield
        nanoMax = hitPoints.victimMaxNanohull
    }

    fun update(hitPoints: AttributeHitpointUpdateCommand) {
        health = hitPoints.hitpointsNow.toLong()
        healthMax = hitPoints.hitpointsMax.toLong()
    }
}
