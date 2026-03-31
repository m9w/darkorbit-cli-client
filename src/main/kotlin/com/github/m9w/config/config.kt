package com.github.m9w.config

import com.github.m9w.config.entity.ConfigNode
import com.github.m9w.config.property.BasicFactory
import com.github.m9w.config.property.BasicProperty
import com.github.m9w.config.property.Prefix


fun <T> staticConfig(default: T, persist: Boolean = true, externalUpdate: (String)->Unit = {}): StaticFactory<T> = StaticFactory(default, persist, externalUpdate)
fun <T> accountConfig(default: T, persist: Boolean = true, externalUpdate: (String)->Unit = {}): AccountFactory<T> = AccountFactory(default, persist, externalUpdate)
fun <T> config(default: T, persist: Boolean = true, externalUpdate: (String)->Unit = {}): ConfigSetFactory<T> = ConfigSetFactory(default, persist, externalUpdate)

class StaticProp<T>(node: ConfigNode.Root, default: T, externalUpdate: (String)->Unit = {}) : BasicProperty<T>(node, default, externalUpdate)
class AccountProp<T>(node: ConfigNode.Root, default: T, externalUpdate: (String)->Unit = {}) : BasicProperty<T>(node, default, externalUpdate)
class ConfigSetProp<T>(node: ConfigNode.Root, default: T, externalUpdate: (String)->Unit = {}) : BasicProperty<T>(node, default, externalUpdate)

class StaticFactory<T>(default: T, persist: Boolean = true, externalUpdate: (String)->Unit = {})
        : BasicFactory<T, StaticProp<T>>({ StaticProp(it, default, externalUpdate) }, persist) {
    override val prefix: Prefix = Prefix.STATIC
}

class AccountFactory<T>(default: T, persist: Boolean = true, externalUpdate: (String)->Unit = {})
        : BasicFactory<T, AccountProp<T>>({ AccountProp(it, default, externalUpdate) }, persist) {
    override val prefix: Prefix = Prefix.ACCOUNT
}

class ConfigSetFactory<T>(default: T, persist: Boolean = true, externalUpdate: (String)->Unit = {})
        : BasicFactory<T, ConfigSetProp<T>>({ ConfigSetProp(it, default, externalUpdate) }, persist) {
    override val prefix: Prefix = Prefix.CONFIG_SET
}