package com.specthinker.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CorsConfig(
    private val props: CorsProperties,
) {
    @Bean
    fun corsConfigurer(): WebMvcConfigurer = object : WebMvcConfigurer {
        override fun addCorsMappings(registry: CorsRegistry) {
            val origins = parseOrigins(props.allowedOrigins)
            if (origins.isEmpty()) return
            val allowCredentials = !origins.contains("*")
            registry.addMapping("/api/**")
                .allowedOriginPatterns(*origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Retry-After", "Resets-At", "X-Llm-Provider", "X-Quota-Used", "X-Quota-Limit", "X-Quota-Resets-At")
                .allowCredentials(allowCredentials)
                .maxAge(3600)
        }
    }

    private fun parseOrigins(raw: String): Array<String> =
        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray()
}
