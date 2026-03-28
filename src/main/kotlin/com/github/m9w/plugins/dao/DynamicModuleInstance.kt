package com.github.m9w.plugins.dao

import com.github.m9w.context.Context
import com.github.m9w.util.ms
import java.util.WeakHashMap

class DynamicModuleInstance(val module: DynamicModule, val instance: Any) {
    private val uses = WeakHashMap<Context, Long>()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DynamicModuleInstance
        return instance == other.instance
    }

    fun usedBy(context: Context) {
        uses[context] = ms()
    }

    override fun hashCode(): Int = module.abstraction.hashCode()
}