package com.github.m9w.metaplugins.game.entities

import com.darkorbit.*
import com.github.m9w.metaplugins.EntitiesModule

class BoxImpl(root: EntitiesModule, entity: AddMapEntityCommand): EntityImpl(root,entity.hash.toLong(36) + 100000000, entity.name,entity.x,entity.y) {
    val hash: String = entity.hash
    val oreId: OreType? = if(entity is AddOreCommand) entity.oreType.typeValue else null
    val boxType: String? = if(entity is AddBoxCommand) entity.boxType else null
    val mineType: Int? = if(entity is AddMineCommand) entity.typeId else null
    val type = when(entity) {
        is AddOreCommand -> Type.ORE
        is AddBoxCommand -> Type.BOX
        is AddMineCommand -> Type.MINE
        is AddBeaconCommand -> Type.BEACON
        is AddFireworkBoxCommand -> Type.FIREWORK
        else -> throw IllegalArgumentException("Unknown class " + entity::class.simpleName)
    }

    override fun toString(): String {
        return super.toString() + "Hash: $hash"
    }

    enum class Type {
        BOX, ORE, MINE, BEACON, FIREWORK
    }

    companion object {
        val AddMapEntityCommand.name: String get() {
            return when (this) {
                is AddOreCommand -> "ORE_" + oreType.typeValue
                is AddBoxCommand -> boxType
                is AddMineCommand -> "MINE_$typeId"
                is AddBeaconCommand -> "BEACON_$beaconId"
                is AddFireworkBoxCommand -> "FIREWORK_$typeId"
                else -> hash
            }
        }
    }
}
