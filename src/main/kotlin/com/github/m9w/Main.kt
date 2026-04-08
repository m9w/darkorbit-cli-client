package com.github.m9w

import com.github.m9w.metaplugins.EntitiesDebugUiModule
import com.github.m9w.metaplugins.proxy.EnvProxyPool
import com.github.m9w.plugins.PluginRepo
import com.github.m9w.util.ProcessIdentifier

fun main() {
    ProcessIdentifier.check()
    PluginRepo.builtinPlugins.forEach { it.load() }
    val modules = PluginRepo.run{ builtinPlugins + corePlugin }
        .mapNotNull { it.definition?.modules }
        .flatten()
        .filter { !listOf(EnvProxyPool::class).contains(it.moduleClass) }
    EntitiesDebugUiModule { auth, debug -> Launcher.run(modules + debug, auth) }
}
