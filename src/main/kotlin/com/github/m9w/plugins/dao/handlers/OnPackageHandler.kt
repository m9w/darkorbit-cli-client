package com.github.m9w.plugins.dao.handlers

import kotlin.reflect.KClass
import kotlin.reflect.KFunction

class OnPackageHandler(override val method: KFunction<*>, val packet: KClass<*>) : Handler