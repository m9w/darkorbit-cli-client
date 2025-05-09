package com.github.m9w.util

import com.github.m9w.feature.FeatureController
import java.util.*
import kotlin.reflect.KClass

val timePrefix: Date get() = Calendar.getInstance().time

suspend fun <T> waitOnPackage(waitFor: Set<KClass<*>>, exceptBy: Set<KClass<*>> = emptySet(), timeout: Long = -1, postExecute: () -> Unit = {})
        = FeatureController.waitOnPackage<T>(waitFor, exceptBy, timeout, postExecute)

suspend fun waitMs(ms: Long) = FeatureController.waitMs(ms)