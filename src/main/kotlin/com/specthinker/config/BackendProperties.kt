package com.specthinker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "specthinker")
data class BackendProperties(
    val backendUrl: String = "http://localhost:8080",
)
