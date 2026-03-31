package com.github.m9w.plugins

import com.darkorbit.ProtocolPacket
import com.github.m9w.config.AccountProp
import com.github.m9w.config.ConfigSetProp
import com.github.m9w.config.StaticProp
import com.github.m9w.context.Context
import com.github.m9w.feature.Classifier
import com.github.m9w.feature.annotations.OnEvent
import com.github.m9w.feature.annotations.OnPackage
import com.github.m9w.feature.annotations.Repeat
import com.github.m9w.plugins.dao.DynamicModule
import com.github.m9w.plugins.dao.Plugin
import com.github.m9w.plugins.dao.PluginDefinition
import com.github.m9w.plugins.dao.handlers.OnEventHandler
import com.github.m9w.plugins.dao.handlers.OnPackageHandler
import com.github.m9w.plugins.dao.handlers.RepeatHandler
import java.net.URLClassLoader
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.jvmErasure
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.compilerOptions
import kotlin.script.experimental.api.onFailure
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.host.StringScriptSource
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.dependenciesFromClassloader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost

object Loader {
    val host = BasicJvmScriptingHost()

    fun load(plugin: Plugin): PluginDefinition {
        val classLoader = URLClassLoader(arrayOf())
        val compilationConfiguration = ScriptCompilationConfiguration { jvm {
            dependenciesFromClassloader(wholeClasspath = true)
            compilerOptions.append("-jvm-target", "17")
        } }
        val evaluationConfiguration = ScriptEvaluationConfiguration { jvm { baseClassLoader(classLoader) } }

        val scriptCode = StringScriptSource(plugin.text, plugin.name)

        val errorMessages = mutableListOf<ScriptDiagnostic>()
        val value = host.eval(scriptCode, compilationConfiguration, evaluationConfiguration)
            .onFailure { errorMessages.addAll(it.reports.filter { it.location != null }) }
            .valueOrNull()?.returnValue

        val rootClass = value?.scriptClass
        val rootInstance = value?.scriptInstance

        val modules = rootClass?.nestedClasses?.mapNotNull { moduleClass -> dynamicModuleBuilder(moduleClass, errorMessages) } ?: listOf()
        return PluginDefinition(classLoader, rootClass, rootInstance, errorMessages, rootInstance != null, modules)
    }

    fun dynamicModuleBuilder(moduleClass: KClass<*>, errorMessages: MutableList<ScriptDiagnostic> = mutableListOf(), block: (()->Any)? = null): DynamicModule? {
        val abstraction = moduleClass.allSupertypes.find { it.classifier == Classifier::class }?.arguments[0]?.type?.classifier as? KClass<*> ?: moduleClass

        val dependencies = moduleClass.declaredMemberProperties
            .filter { it.isDelegatedBy(Context::class) && !it.isDelegatedBy(Context.Optional::class) }
            .onEach { it.isAccessible = true }
        val optionalDependencies = moduleClass.declaredMemberProperties
            .filter { it.isDelegatedBy(Context.Optional::class) }
            .onEach { it.isAccessible = true }

        val configProps = moduleClass.declaredMemberProperties.filter { it.isDelegatedBy(ConfigSetProp::class) }.onEach { it.isAccessible = true }
        val accountProps = moduleClass.declaredMemberProperties.filter { it.isDelegatedBy(AccountProp::class) }.onEach { it.isAccessible = true }
        val staticProps = moduleClass.declaredMemberProperties.filter { it.isDelegatedBy(StaticProp::class) }.onEach { it.isAccessible = true }

        val func = moduleClass.memberFunctions
        val onPackageHandlers = func.mapNotNull { method -> method.annotations.filterIsInstance<OnPackage>().firstOrNull()?.let {
            if (method.parameters.size != 2 || !ProtocolPacket::class.isSuperclassOf(method.parameters[1].type.classifier as KClass<*>))
                errorMessages.add(ScriptDiagnostic(0, "Unexpected argument count in $method, @OnPackage handlers should have one parameter with packet type.", ScriptDiagnostic.Severity.WARNING)).let { null }
            else OnPackageHandler(method.apply { isAccessible = true }, method.parameters[1].type.jvmErasure)
        } }
        val repeatableHandlers = func.mapNotNull { method -> method.annotations.filterIsInstance<Repeat>().firstOrNull()?.let {
            if (method.parameters.size != 1)
                errorMessages.add(ScriptDiagnostic(1, "Unexpected argument count in $method, @Repeat handlers shouldn't have parameters.", ScriptDiagnostic.Severity.WARNING)).let { null }
            else RepeatHandler(method.apply { isAccessible = true }, it.ms, it.noInitDelay)
        } }
        val onEventHandlers = func.mapNotNull { method -> method.annotations.filterIsInstance<OnEvent>().firstOrNull()?.let {
            if (method.parameters.size != 2 || method.parameters[1].type.classifier != String::class)
                errorMessages.add(ScriptDiagnostic(2, "Unexpected argument count in $method, @OnEvent handlers should have one String parameter.", ScriptDiagnostic.Severity.WARNING)).let { null }
            else OnEventHandler(method.apply { isAccessible = true }, it.event)
        } }
        if (abstraction == moduleClass && listOf<List<*>>(dependencies, optionalDependencies, configProps, accountProps, staticProps, onPackageHandlers, repeatableHandlers, onEventHandlers).all { it.isEmpty() }) return null
        return DynamicModule(moduleClass, abstraction, dependencies, optionalDependencies, configProps, accountProps, staticProps, onPackageHandlers, repeatableHandlers, onEventHandlers, block)
    }

    fun KProperty<*>.isDelegatedBy(delegateClass: KClass<*>) = javaField?.type?.let(delegateClass.java::isAssignableFrom) ?: false
}