package com.specthinker.llm

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@ConfigurationProperties(prefix = "specthinker.llm")
data class LlmProperties(
    val connectTimeout: Long = 10,
    val requestTimeout: Long = 45,
    @NestedConfigurationProperty
    val quota: Quota = Quota(),
    @NestedConfigurationProperty
    val providers: Providers = Providers(),
) {
    data class Quota(
        val enabled: Boolean = true,
        val perClientPerDay: Long = 50,
        val defaultClientId: String = "anonymous",
    )

    data class Providers(
        @NestedConfigurationProperty
        val deepseek: Provider = Provider(),
        @NestedConfigurationProperty
        val openrouterDeepseek: Provider = Provider(),
        @NestedConfigurationProperty
        val openrouterFree: Provider = Provider(),
    ) {
        data class Provider(
            val enabled: Boolean = false,
            val apiKey: String = "",
            val baseUrl: String = "",
            val model: String = "",
        )
    }
}
