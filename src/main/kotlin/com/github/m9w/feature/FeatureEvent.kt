package com.github.m9w.feature;

fun interface FeatureEvent<T> {
    suspend fun exec(network: T)
}
