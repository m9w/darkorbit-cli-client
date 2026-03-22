package com.github.m9w.plugins.dao

import com.github.m9w.plugins.Loader
import com.github.m9w.plugins.PluginType
import kotlin.text.prependIndent

class Plugin(val name: String, val text: String, val type: PluginType) {
    val isLoaded: Boolean get() = definition != null
    var definition: PluginDefinition? = null

    override fun toString(): String = "Plugin $name, ${text.length} bytes" + if(isLoaded) "\n${definition.toString().prependIndent("  ")}" else " not loaded"

    @Synchronized
    fun load() {
        if (!isLoaded) definition = Loader.load(this)
    }
}