package com.github.m9w.plugins.dao.handlers

import kotlin.reflect.KFunction

class RepeatHandler(override val method: KFunction<*>, val interval: Long, val noDelay: Boolean) : Handler