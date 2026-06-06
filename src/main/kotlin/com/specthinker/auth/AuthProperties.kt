package com.specthinker.auth

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@ConfigurationProperties(prefix = "specthinker.auth")
data class AuthProperties(
    val sessionSecret: String = "",
    val sessionTtlDays: Long = 365,
    val allowedRedirectOrigins: String = "",
    @NestedConfigurationProperty
    val cookie: Cookie = Cookie(),
    @NestedConfigurationProperty
    val google: Google = Google(),
    @NestedConfigurationProperty
    val mail: Mail = Mail(),
) {
    data class Cookie(
        val samesite: String = "Lax",
        val secure: Boolean = false,
        val domain: String = "",
    )

    data class Google(
        val clientId: String = "",
        val clientSecret: String = "",
    )

    data class Mail(
        val resendApiKey: String = "",
        val from: String = "onboarding@resend.dev",
        val mode: String = "logging",
    )
}
