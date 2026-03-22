package com.github.m9w.plugins.dao.handlers

import kotlin.reflect.KFunction

interface Handler { val method: KFunction<*> }