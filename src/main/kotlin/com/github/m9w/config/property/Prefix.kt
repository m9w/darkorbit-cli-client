package com.github.m9w.config.property

import com.github.m9w.client.auth.AuthenticationProvider
import com.github.m9w.config.module.ConfigModule
import com.github.m9w.context.context

enum class Prefix (val build: ()->String) {
    STATIC( {"STATIC"}),
    ACCOUNT ({
        val auth: AuthenticationProvider by context
        "ACCOUNT/${auth.userID}"
    }),
    CONFIG_SET ({
        val config: ConfigModule by context
        "CONFIG/${config.configName}"
    }),
}