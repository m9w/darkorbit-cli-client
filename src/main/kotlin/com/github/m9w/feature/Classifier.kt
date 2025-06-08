package com.github.m9w.feature

import kotlin.reflect.KClass
import kotlin.reflect.KClassifier

interface Classifier<T> {
    val classifier: KClassifier
        get() {
            var c = this::class.supertypes
            while (c.isNotEmpty()) {
                c.find { it.classifier == Classifier::class }?.arguments[0]?.type?.classifier?.let { return it }
                c = c.flatMap { (it.classifier as KClass<*>).supertypes }
            }
            throw ClassNotFoundException()
        }
}
