package com.github.m9w.plugins.dao

class DynamicModuleInstance(val module: DynamicModule, val instance: Any) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DynamicModuleInstance
        return instance == other.instance
    }

    override fun hashCode(): Int = module.abstraction.hashCode()
}