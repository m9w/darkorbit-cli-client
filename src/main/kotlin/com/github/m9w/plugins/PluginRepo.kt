package com.github.m9w.plugins

import com.github.m9w.Scheduler
import com.github.m9w.client.GameEngine
import com.github.m9w.config.PersistYamlConfig
import com.github.m9w.metaplugins.AuthModule
import com.github.m9w.metaplugins.BasicRepairModule
import com.github.m9w.metaplugins.EntitiesModule
import com.github.m9w.metaplugins.LoginModule
import com.github.m9w.metaplugins.MapModule
import com.github.m9w.metaplugins.MoveModule
import com.github.m9w.metaplugins.PathTracerModule
import com.github.m9w.metaplugins.PingModule
import com.github.m9w.metaplugins.proxy.EnvProxyPool
import com.github.m9w.metaplugins.proxy.ProxyModule
import com.github.m9w.plugins.dao.Plugin
import com.github.m9w.plugins.dao.PluginDefinition
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Stream
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.toPath
import kotlin.script.experimental.api.ScriptDiagnostic


object PluginRepo {
    val coreClasses = listOf(Scheduler::class, AuthModule::class, GameEngine::class, LoginModule::class, ProxyModule::class, EnvProxyPool::class, BasicRepairModule::class, PingModule::class, EntitiesModule::class, MapModule::class, PathTracerModule::class, PersistYamlConfig::class, MoveModule::class)

    val corePlugin: Plugin by lazy {
        Plugin("core", "", PluginType.CORE).apply {
            val logs = mutableListOf<ScriptDiagnostic>()
            val modules = coreClasses.mapNotNull { Loader.dynamicModuleBuilder(it, logs) }
            definition = PluginDefinition(Thread.currentThread().contextClassLoader, System::class, null, logs, true, modules)
        }
    }

    val builtinPlugins: List<Plugin> by lazy {
        val path = "/plugins"
        val uri = runCatching { PluginRepo::class.java.getResource(path)!!.toURI() }.getOrNull() ?: return@lazy emptyList()
        fun walk(paths: Stream<Path>): List<Plugin> = paths.filter {
            it.isRegularFile() && it.extension == "kts"
        }.map {
            Plugin(uri.toPath().relativize(it).toString().replace("\\", "/").removeSuffix(".kts"), it.readText(), PluginType.BUILTIN)
        }.toList()

        if (uri.scheme == "jar") {
            FileSystems.newFileSystem(uri, mutableMapOf<String, Any>()).use { fileSystem ->
                Files.walk(fileSystem.getPath(path), 50).use(::walk)
            }
        } else {
            Files.walk(Paths.get(uri), 50).use(::walk)
        }
    }

    val externalPlugins: List<Plugin> = listOf()

    val allPlugins: List<Plugin> get() = listOf(corePlugin) + builtinPlugins + externalPlugins
}