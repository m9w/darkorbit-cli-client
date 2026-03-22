package com.github.m9w.plugins.dao

import kotlin.reflect.KClass
import kotlin.script.experimental.api.ScriptDiagnostic

class PluginDefinition(
    val classLoader: ClassLoader,
    val rootClass: KClass<*>?,
    val rootInstance: Any?,
    val errorMessages: List<ScriptDiagnostic>,
    val isValid: Boolean,
    val modules: List<DynamicModule>,
) {
    override fun toString(): String {
        return if (!isValid) errorMessages.joinToString("\n")
        else "rootClass: ${rootClass?.simpleName}\n" +
                "modules(${modules.size}):\n" +
                modules.joinToString("\n\n").prependIndent("  ")
    }
}