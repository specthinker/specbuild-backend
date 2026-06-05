package com.specthinker.llm

import com.specthinker.llm.LlmProperties.Providers.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration

abstract class OpenAiCompatibleProvider(
    protected val cfg: Provider,
    protected val http: HttpClient,
    protected val requestTimeout: Duration,
) : LlmProvider {

    protected abstract val extraHeaders: Map<String, String>

    override suspend fun complete(systemPrompt: String, userPrompt: String): String = withContext(Dispatchers.IO) {
        if (!cfg.enabled) throw IllegalStateException("Provider $name is not enabled")
        require(cfg.apiKey.isNotBlank()) { "Provider $name is missing an API key" }
        require(cfg.baseUrl.isNotBlank()) { "Provider $name is missing a base URL" }
        require(cfg.model.isNotBlank()) { "Provider $name is missing a model" }

        val body = buildChatRequestBody(
            model = cfg.model,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
        )

        val url = cfg.baseUrl.trimEnd('/') + "/chat/completions"
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(requestTimeout)
            .header("Authorization", "Bearer ${cfg.apiKey}")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
        for ((k, v) in extraHeaders) {
            builder.header(k, v)
        }

        val req = builder.POST(BodyPublishers.ofString(body)).build()
        val res = try {
            http.send(req, BodyHandlers.ofString())
        } catch (e: Exception) {
            log.warn("{} transport error: {}", name, e.message)
            throw e
        }

        val status = res.statusCode()
        val text = res.body()
        when {
            status in 200..299 -> {
                val parsed = try {
                    ChatJson.decodeFromString(ChatResponse.serializer(), text)
                } catch (e: Exception) {
                    log.warn("{} parse error: {}", name, e.message)
                    throw LlmHttpException(name, status, "Invalid response: ${e.message}")
                }
                val content = parsed.firstContent()
                    ?: parsed.error?.toString()
                    ?: throw LlmHttpException(name, status, "Empty response")
                content
            }
            status == 401 || status == 403 -> {
                log.warn("{} auth error: {}", name, status)
                throw AuthException(name, status)
            }
            status == 429 -> {
                log.warn("{} rate limit: {}", name, status)
                throw RateLimitException(name, status)
            }
            status in 500..599 -> {
                log.warn("{} server error: {} {}", name, status, text.take(200))
                throw LlmHttpException(name, status, "Upstream server error")
            }
            else -> {
                log.warn("{} unexpected status: {} {}", name, status, text.take(200))
                throw LlmHttpException(name, status, "Unexpected status")
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OpenAiCompatibleProvider::class.java)
    }
}
