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
            registry.addMapping("/api/**")
                .allowedOriginPatterns(*origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600)
        }
    }

    private fun parseOrigins(raw: String): Array<String> =
        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray()
}
