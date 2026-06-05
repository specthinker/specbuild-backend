package com.specthinker.llm.providers

import com.specthinker.llm.LlmProperties
import com.specthinker.llm.OpenAiCompatibleProvider
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.net.http.HttpClient
import java.time.Duration

@Component
@Order(10)
class DeepseekProvider(
    props: LlmProperties,
    http: HttpClient,
) : OpenAiCompatibleProvider(
    cfg = props.providers.deepseek,
    http = http,
    requestTimeout = Duration.ofSeconds(props.requestTimeout),
) {
    override val name: String = "deepseek-direct"
    override val extraHeaders: Map<String, String> = emptyMap()
}

@Component
@Order(20)
class OpenrouterDeepseekProvider(
    props: LlmProperties,
    http: HttpClient,
) : OpenAiCompatibleProvider(
    cfg = props.providers.openrouterDeepseek,
    http = http,
    requestTimeout = Duration.ofSeconds(props.requestTimeout),
) {
    override val name: String = "openrouter-deepseek"
    override val extraHeaders: Map<String, String> = mapOf(
        "HTTP-Referer" to "https://specthinker.app",
        "X-Title" to "Specthinker",
    )
}

@Component
@Order(30)
class OpenrouterFreeProvider(
    props: LlmProperties,
    http: HttpClient,
) : OpenAiCompatibleProvider(
    cfg = props.providers.openrouterFree,
    http = http,
    requestTimeout = Duration.ofSeconds(props.requestTimeout),
) {
    override val name: String = "openrouter-free"
    override val extraHeaders: Map<String, String> = mapOf(
        "HTTP-Referer" to "https://specthinker.app",
        "X-Title" to "Specthinker",
    )
}
