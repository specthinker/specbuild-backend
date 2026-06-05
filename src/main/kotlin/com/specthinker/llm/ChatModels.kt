package com.specthinker.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class ChatRequestMessage(
    val role: String,
    val content: String,
)

@Serializable
internal data class ChatRequest(
    val model: String,
    val messages: List<ChatRequestMessage>,
    val temperature: Double = 0.2,
    @SerialName("max_tokens")
    val maxTokens: Int = 2000,
)

@Serializable
internal data class ChatResponseChoiceMessage(
    val role: String? = null,
    val content: String? = null,
)

@Serializable
internal data class ChatResponseChoice(
    val index: Int = 0,
    val message: ChatResponseChoiceMessage? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

@Serializable
internal data class ChatResponseUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int? = null,
    @SerialName("completion_tokens")
    val completionTokens: Int? = null,
    @SerialName("total_tokens")
    val totalTokens: Int? = null,
)

@Serializable
internal data class ChatResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<ChatResponseChoice> = emptyList(),
    val usage: ChatResponseUsage? = null,
    val error: JsonObject? = null,
) {
    fun firstContent(): String? =
        choices.firstOrNull()?.message?.content?.takeIf { it.isNotBlank() }
}

internal val ChatJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    encodeDefaults = true
}

internal fun buildChatRequestBody(
    model: String,
    systemPrompt: String,
    userPrompt: String,
    temperature: Double = 0.2,
    maxTokens: Int = 2000,
): String {
    val payload = ChatRequest(
        model = model,
        messages = listOf(
            ChatRequestMessage("system", systemPrompt),
            ChatRequestMessage("user", userPrompt),
        ),
        temperature = temperature,
        maxTokens = maxTokens,
    )
    return ChatJson.encodeToString(ChatRequest.serializer(), payload)
}
