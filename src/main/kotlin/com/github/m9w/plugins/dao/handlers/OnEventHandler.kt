package com.github.m9w.plugins.dao.handlers

import kotlin.reflect.KFunction

class OnEventHandler(override val method: KFunction<*>, val event: String) : Handler