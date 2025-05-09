package com.github.m9w.feature

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Repeat(val ms: Long)