package com.specthinker.llm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatModelsTest {

    @Test
    fun `buildChatRequestBody produces OpenAI-shaped payload`() {
        val body = buildChatRequestBody(
            model = "deepseek-chat",
            systemPrompt = "sys",
            userPrompt = "user",
            temperature = 0.2,
            maxTokens = 2000,
        )
        val obj = Json.parseToJsonElement(body).jsonObject
        assertEquals("deepseek-chat", obj["model"]?.jsonPrimitive?.content)
        assertEquals(0.2, obj["temperature"]?.jsonPrimitive?.content?.toDouble())
        assertEquals(2000, obj["max_tokens"]?.jsonPrimitive?.content?.toInt())
        val messages = obj["messages"]?.jsonArray ?: error("missing messages")
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("sys", messages[0].jsonObject["content"]?.jsonPrimitive?.content)
        assertEquals("user", messages[1].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("user", messages[1].jsonObject["content"]?.jsonPrimitive?.content)
    }

    @Test
    fun `chat response parser extracts first choice content`() {
        val raw = """
            {
              "id": "x",
              "model": "deepseek-chat",
              "choices": [
                { "index": 0, "message": { "role": "assistant", "content": "polished spec" }, "finish_reason": "stop" }
              ],
              "usage": { "prompt_tokens": 5, "completion_tokens": 7, "total_tokens": 12 }
            }
        """.trimIndent()
        val parsed = ChatJson.decodeFromString(ChatResponse.serializer(), raw)
        assertEquals("polished spec", parsed.firstContent())
    }

    @Test
    fun `chat response parser tolerates missing content`() {
        val raw = """{ "choices": [ { "index": 0 } ] }"""
        val parsed = ChatJson.decodeFromString(ChatResponse.serializer(), raw)
        assertEquals(null, parsed.firstContent())
    }
}
