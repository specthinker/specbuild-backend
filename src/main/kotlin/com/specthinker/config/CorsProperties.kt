package com.specthinker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "specthinker.cors")
data class CorsProperties(
    val allowedOrigins: String = "*",
)
