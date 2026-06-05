package com.specthinker.spec

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RendererConfig {

    @Bean
    fun renderers(): Map<String, Renderer> = mapOf(
        "markdown" to MarkdownRenderer(),
        "text" to TextRenderer(),
        "html" to HtmlRenderer(),
    )
}
