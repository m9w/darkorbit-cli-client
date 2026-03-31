package com.github.m9w.config.module

import com.github.m9w.feature.Classifier

interface ConfigMonitor : Classifier<ConfigMonitor> {
    fun configRead(path: String, result: Result<Any?>)
    fun configWrite(path: String, old: Any?, new: Any?)
}