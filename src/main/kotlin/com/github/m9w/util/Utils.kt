package com.github.m9w.util

import com.darkorbit.ProtocolPacket
import com.github.m9w.feature.FeatureController
import java.util.*
import kotlin.reflect.KClass

val timePrefix: Date get() = Calendar.getInstance().time

suspend fun <T : ProtocolPacket> waitOnPackage(waitFor: Set<KClass<out ProtocolPacket>>,
                                               exceptBy: Set<KClass<out ProtocolPacket>> = emptySet(),
                                               timeout: Long = -1,
                                               postExecute: () -> Unit = {})
= FeatureController.waitOnPackage<T>(waitFor.map { it.simpleName!! }.toSet(), exceptBy.map { it.simpleName!! }.toSet(), timeout, postExecute)

suspend inline fun <reified T : ProtocolPacket> waitOnPackage(exceptBy: Set<KClass<out ProtocolPacket>> = emptySet(),
                                                              timeout: Long = -1,
                                                              noinline postExecute: () -> Unit = {}): T
= waitOnPackage(setOf(T::class), exceptBy, timeout, postExecute)

suspend fun waitMs(ms: Long) = FeatureController.waitMs(ms)
