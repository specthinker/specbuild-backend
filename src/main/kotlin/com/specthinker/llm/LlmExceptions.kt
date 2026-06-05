package com.specthinker.llm

class AllProvidersFailedException(cause: Throwable?) :
    RuntimeException("All LLM providers failed", cause)

class AuthException(provider: String, status: Int) :
    RuntimeException("Provider $provider returned $status (auth)")

class RateLimitException(provider: String, status: Int) :
    RuntimeException("Provider $provider returned $status (rate limit)")

class LlmHttpException(val provider: String, val status: Int, message: String) :
    RuntimeException("Provider $provider returned $status: $message")

class QuotaExceededException(
    val used: Long,
    val limit: Long,
    val resetsAtEpochMillis: Long,
) : RuntimeException("Quota exceeded: $used/$limit")
