package com.github.m9w.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.m9w.client.auth.AuthenticationProvider
import com.github.m9w.context.context
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType

private val mapper: ObjectMapper = ObjectMapper().registerKotlinModule()

/**
 * Supported types (`x` - mean anyone from this list):
 * [Boolean], [Byte], [Short], [Int], [Long], [Float], [Double],
 * [String], [Enum], [List]`<x>`, [Map]`<String, x>`, `data class`
 *
 * Cycle references is restricted
 */
fun <T> config(default: T, persist: Boolean = true): FactoryC<T> = DelegatorFactoryC(default, persist)
fun <T> accountConfig(default: T, persist: Boolean = true): FactoryA<T> = DelegatorFactoryA(default, persist) as FactoryA<T>
fun <T> staticConfig(default: T, persist: Boolean = true): FactoryS<T> = DelegatorFactoryS(default, persist) as FactoryS<T>

interface RwPropC<T> : ReadWriteProperty<Any, T>
interface RwPropA<T> : ReadWriteProperty<Any, T>
interface RwPropS<T> : ReadWriteProperty<Any, T>

interface FactoryC<T> :  PropertyDelegateProvider<Any, RwPropC<T>>
interface FactoryA<T> :  PropertyDelegateProvider<Any, RwPropA<T>>
interface FactoryS<T> :  PropertyDelegateProvider<Any, RwPropS<T>>

private abstract class DelegatorFactory<T, R>(val builder: (String, KType, T, Boolean) -> R) {
    abstract val default: T
    abstract val persist: Boolean
    fun provideDelegate(thisRef: Any, property: KProperty<*>): R = builder(thisRef::class.qualifiedName!! + "#" + property.name, property.returnType, default, persist)
}

private val staticDelegators = mutableMapOf<String, RwPropS<Any?>>()
private fun <T> getStaticDelegator(cls: String, type: KType, default: T, persist: Boolean): StaticConfig<T> {
    @Suppress("UNCHECKED_CAST")
    return staticDelegators.computeIfAbsent(cls) { StaticConfig(cls, type, default, persist) } as StaticConfig<T>
}

private class DelegatorFactoryC<T>(override val default: T, override val persist: Boolean) : DelegatorFactory<T, RwPropC<T>>(::Config), FactoryC<T>
private class DelegatorFactoryA<T>(override val default: T, override val persist: Boolean) : DelegatorFactory<T, RwPropA<T>>(::AccountConfig), FactoryA<T>
private class DelegatorFactoryS<T>(override val default: T, override val persist: Boolean) : DelegatorFactory<T, RwPropS<T>>(::getStaticDelegator), FactoryS<T>

private abstract class AbstractConfig<T>(val default: T) : ReadWriteProperty<Any, T> {
    protected val config : ConfigModule by context
    abstract val type: KType
    abstract val key: String
    private var prevKey = ""
    private var local: T = default

    override fun getValue(thisRef: Any, property: KProperty<*>): T {
        if (key == prevKey) {
            if (config.hasUpdates(key)) config.readProperty<T>(key, type).onSuccess { local = it }
        } else {
            config.readProperty<T>(key, type).onSuccess { local = it }
                .onFailure {
                    local = default.clone(type)
                    config.writeProperty(key, type, local)
                }
                .also { prevKey = key }
        }
        return local
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) = config.writeProperty(key, type, value.also { local = it })

    private fun T.clone(type: KType): T = mapper.convertValue(this, mapper.typeFactory.constructType(type.javaType))
}

private class Config<T>(val cls: String, override val type: KType, default: T, val persist: Boolean) : AbstractConfig<T>(default), RwPropC<T> {
    override val key: String get() = "${if (persist) "" else "!"}CONFIG.${config.configName}.$cls"
}

private class AccountConfig<T>(val cls: String, override val type: KType, default: T, val persist: Boolean) : AbstractConfig<T>(default), RwPropA<T> {
    val auth: AuthenticationProvider by context
    override val key: String get() = "${if (persist) "" else "!"}ACCOUNT.${auth.userID}.$cls"
}

private class StaticConfig<T>(val cls: String, override val type: KType, default: T, val persist: Boolean) : AbstractConfig<T>(default), RwPropS<T> {
    override val key: String get() = "${if (persist) "" else "!"}STATIC.$cls"
}