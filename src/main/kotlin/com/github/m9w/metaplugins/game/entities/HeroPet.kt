package com.github.m9w.metaplugins.game.entities

import com.darkorbit.*
import com.github.m9w.metaplugins.EntitiesModule
import com.github.m9w.protocol.Factory

class HeroPet(root: EntitiesModule, activation: PetHeroActivationCommand) : PetImpl(root, Factory.build(PetActivationCommand::class).apply {
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


}