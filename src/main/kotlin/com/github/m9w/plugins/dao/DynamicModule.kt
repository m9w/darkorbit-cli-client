package com.github.m9w.plugins.dao

import com.github.m9w.plugins.dao.handlers.OnEventHandler
import com.github.m9w.plugins.dao.handlers.OnPackageHandler
import com.github.m9w.plugins.dao.handlers.RepeatHandler
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.createInstance

open class DynamicModule (
    val moduleClass: KClass<*>,
    val abstraction: KClass<*>,
    val dependencies: List<KProperty<*>>,
    val optionalDependencies: List<KProperty<*>>,
    val configProps: List<KProperty<*>>,
    val accountProps: List<KProperty<*>>,
    val staticProps: List<KProperty<*>>,
    val onPackageHandlers: List<OnPackageHandler>,
    val repeatableHandlers: List<RepeatHandler>,
    val onEventHandlers: List<OnEventHandler>,
    private val block: (() -> Any)?,
) {
    val newInstance: DynamicModuleInstance get() =
        DynamicModuleInstance(this, block?.invoke() ?: moduleClass.objectInstance ?: moduleClass.createInstance())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DynamicModule
        return moduleClass == other.moduleClass
    }

    override fun hashCode() = moduleClass.hashCode()
    override fun toString(): String {
        return  "Class: ${moduleClass.simpleName}" +
            (if (moduleClass != abstraction) "\nAbstraction: ${abstraction.simpleName}" else "") +
            (if (dependencies.isNotEmpty()) "\nMandatory dependencies:\n${dependencies.joinToString("\n") { it.name + ": " + (it.returnType.classifier as KClass<*>).simpleName }.prependIndent("  ")}" else "") +
            (if (optionalDependencies.isNotEmpty()) "\nOptional dependencies:\n${optionalDependencies.joinToString("\n") { it.name + ": " + (it.returnType.classifier as KClass<*>).simpleName }.prependIndent("  ")}" else "") +
            (if (onPackageHandlers.isNotEmpty() || repeatableHandlers.isNotEmpty() || onEventHandlers.isNotEmpty()) "\nHandlers:" else "") +
            (if (onPackageHandlers.isNotEmpty()) "\n  package:\n${onPackageHandlers.joinToString("\n") { it.method.name + ": "+ it.packet.simpleName }.prependIndent("    ")}" else "") +
            (if (repeatableHandlers.isNotEmpty()) "\n  repeatable:\n${repeatableHandlers.joinToString("\n") { "${it.method.name} each ${it.interval}ms" + if(it.noDelay) " nodelay" else "" }.prependIndent("    ")}" else "") +
            (if (onEventHandlers.isNotEmpty()) "\n  event:\n${onEventHandlers.joinToString("\n") { it.method.name + ": @" + it.event }.prependIndent("    ")}" else "") +
            (if (configProps.isNotEmpty() || accountProps.isNotEmpty() || staticProps.isNotEmpty()) "\nProperties:" else "") +
            (if (configProps.isNotEmpty()) "\n  configProps:\n${configProps.joinToString("\n") { it.name + ": " + (it.returnType.classifier as KClass<*>).simpleName }.prependIndent("    ")}" else "") +
            (if (accountProps.isNotEmpty()) "\n  accountProps:\n${accountProps.joinToString("\n") { it.name + ": " + (it.returnType.classifier as KClass<*>).simpleName }.prependIndent("    ")}" else "") +
            (if (staticProps.isNotEmpty()) "\n  staticProps:\n${staticProps.joinToString("\n") { it.name + ": " + (it.returnType.classifier as KClass<*>).simpleName }.prependIndent("    ")}" else "")
    }
}



