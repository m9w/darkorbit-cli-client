package com.github.m9w.util

import kotlin.script.experimental.host.StringScriptSource

object ResourceUtil {
    operator fun get(path: String) = String(ResourceUtil::class.java.classLoader.resources(path).findFirst().get().openStream().readAllBytes())

    fun getScript(path: String) = StringScriptSource(ResourceUtil[path], path)
}