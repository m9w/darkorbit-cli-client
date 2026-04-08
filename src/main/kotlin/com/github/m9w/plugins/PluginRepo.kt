package com.github.m9w.plugins

import com.github.m9w.feature.Scheduler
import com.github.m9w.client.GameEngine
import com.github.m9w.config.module.PersistYamlConfig
import com.github.m9w.config.staticConfig
import com.github.m9w.metaplugins.*
import com.github.m9w.metaplugins.proxy.EnvProxyPool
import com.github.m9w.metaplugins.proxy.ProxyModule
import com.github.m9w.plugins.dao.Plugin
import com.github.m9w.plugins.dao.PluginDefinition
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Stream
import kotlin.io.path.*
import kotlin.script.experimental.api.ScriptDiagnostic


object PluginRepo {
    private const val IDE_PATH = "/src/main/kotlin-scripting"
    private const val DEFAULT_PATH = "/plugins"

    var plugins: MutableList<String> by staticConfig(mutableListOf())

    val coreClasses = listOf(Scheduler::class, AuthModule::class, GameEngine::class, LoginModule::class, ProxyModule::class, EnvProxyPool::class, BasicRepairModule::class, EntitiesModule::class, MapModule::class, PathTracerModule::class, PersistYamlConfig::class, MoveModule::class)

    inline fun <reified T>getCoreModule() = corePlugin.definition!!.modules.first { it.abstraction == T::class }

    val corePlugin: Plugin by lazy {
        Plugin("core", "", PluginType.CORE).apply {
            val logs = mutableListOf<ScriptDiagnostic>()
            val modules = coreClasses.mapNotNull { Loader.dynamicModuleBuilder(it, logs) }
            definition = PluginDefinition(Thread.currentThread().contextClassLoader, System::class, null, logs, true, modules)
        }
    }

    val builtinPlugins: List<Plugin> by lazy {
        val uri = runCatching { PluginRepo::class.java.getResource(DEFAULT_PATH)!!.toURI() }.getOrNull() ?: return@lazy emptyList()
        if (uri.scheme == "jar") {
            FileSystems.newFileSystem(uri, mutableMapOf<String, Any>()).use { fileSystem ->
                Files.walk(fileSystem.getPath(DEFAULT_PATH), 50).use { uri.walk(it, PluginType.BUILTIN) }
            }
        } else {
            Files.walk(Paths.get(uri), 50).use { uri.walk(it, PluginType.BUILTIN) }
        }
    }

    val externalPlugins: List<Plugin> get() {
        val fromIDE = Paths.get(IDE_PATH.removePrefix("/")).toAbsolutePath().takeIf { it.isDirectory() }?.toAbsolutePath()?.toUri()?.let {
                uri -> Files.walk(Paths.get(uri), 50).use { uri.walk(it, PluginType.EXTERNAL) }
        } ?: listOf()
        val default = Paths.get(DEFAULT_PATH.removePrefix("/")).takeIf { it.isDirectory() }?.toAbsolutePath()?.toUri()?.let {
            uri -> Files.walk(Paths.get(uri), 50).use { uri.walk(it, PluginType.EXTERNAL) }
        } ?: listOf()
        return default + fromIDE
    }

    val allPlugins: List<Plugin> get() = listOf(corePlugin) + builtinPlugins + externalPlugins

    private fun URI.walk(paths: Stream<Path>, type: PluginType): List<Plugin> = paths.filter { it.isRegularFile() && it.extension == "kts" }.map {
        Plugin(toPath().relativize(it).toString().replace("\\", "/").removeSuffix(".kts"), it.readText(), type)
    }.toList()
}