package com.specthinker.stripe

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "specthinker.stripe")
data class StripeProperties(
    val webhookSecret: String = "",
    val priceToPlan: String = "",
)
