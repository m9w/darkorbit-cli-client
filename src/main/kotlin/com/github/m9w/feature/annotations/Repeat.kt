package com.github.m9w.feature.annotations

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Repeat(val ms: Long, val noInitDelay: Boolean = false)
