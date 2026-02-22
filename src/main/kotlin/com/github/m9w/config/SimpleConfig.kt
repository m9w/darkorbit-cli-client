package com.github.m9w.config

import com.github.m9w.client.auth.AuthenticationProvider
import com.github.m9w.context.context
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType

private typealias rwProp<T> = ReadWriteProperty<Any, T>
private typealias Factory<T> = PropertyDelegateProvider<Any, rwProp<T>>

/**
 * Supported types (`x` - mean anyone from this list):
 * [Boolean], [Byte], [Short], [Int], [Long], [Float], [Double],
 * [String], [Enum], [List]`<x>`, [Map]`<String, x>`, `data class`
 *
 * Cycle references is restricted
 */
fun <T> config(default: T, persist: Boolean = true): Factory<T> = DelegatorFactory(Types.CONFIG, default, persist)
fun <T> accountConfig(default: T, persist: Boolean = true): Factory<T> = DelegatorFactory(Types.ACCOUNT_CONFIG, default, persist)
fun <T> staticConfig(default: T, persist: Boolean = true): Factory<T> = DelegatorFactory(Types.STATIC_CONFIG, default, persist)

private enum class Types(val builder: (String, KType, Any?, Boolean) -> rwProp<Any?>) {
    CONFIG(::Config),
    ACCOUNT_CONFIG(::AccountConfig),
    STATIC_CONFIG ({ cls, type, default, persist -> staticDelegators.computeIfAbsent(cls) { StaticConfig(cls, type, default, persist) } });
    companion object {
        private val staticDelegators = mutableMapOf<String, rwProp<Any?>>()
    }
}

private class DelegatorFactory<T>(val type: Types, val default: T, val persist: Boolean) : Factory<T> {
    @Suppress("UNCHECKED_CAST")
    override fun provideDelegate(thisRef: Any, property: KProperty<*>): rwProp<T> {
        val key = thisRef::class.qualifiedName!! + "#" + property.name + "%" + (property.returnType.classifier as? KClass<*>)?.simpleName
        return type.builder(key, property.returnType, default, persist) as rwProp<T>
    }
}

private abstract class AbstractConfig<T> : rwProp<T> {
    protected val config : ConfigModule by context
    abstract val type: KType
    abstract val default: T
    abstract val key: String
    private var prevKey = ""
    private var local: T = default

    override fun getValue(thisRef: Any, property: KProperty<*>): T {
        if (key == prevKey) {
            if (config.hasUpdates(key)) config.readProperty<T>(key, type).onSuccess { local = it }
        } else config.readProperty<T>(key, type).onSuccess { local = it }.also { prevKey = key }
        return local
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) =
        config.writeProperty(key, type, value.also { local = it })
}

private class Config<T>(val cls: String, override val type: KType, override val default: T, val persist: Boolean) : AbstractConfig<T>() {
    override val key: String get() = "${if (persist) "" else "!"}CONFIG.${config.configName}.$cls"
}

private class AccountConfig<T>(val cls: String, override val type: KType, override val default: T, val persist: Boolean) : AbstractConfig<T>() {
    val auth: AuthenticationProvider by context
    override val key: String get() = "${if (persist) "" else "!"}ACCOUNT.${auth.userID}.$cls"
}

private class StaticConfig<T>(val cls: String, override val type: KType, override val default: T, val persist: Boolean) : AbstractConfig<T>() {
    override val key: String get() = "${if (persist) "" else "!"}STATIC.$cls"
}