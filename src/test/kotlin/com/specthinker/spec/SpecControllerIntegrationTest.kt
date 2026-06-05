package com.specthinker.spec

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:sqlite::memory:",
        "spring.sql.init.mode=always",
        "specthinker.llm.providers.deepseek.enabled=false",
        "specthinker.llm.providers.openrouter-deepseek.enabled=false",
        "specthinker.llm.providers.openrouter-free.enabled=false",
    ],
)
class SpecControllerIntegrationTest {

    @Autowired
    lateinit var mvc: MockMvc

    private val sampleSpec = """
        {
          "title": "Polished Spec",
          "sections": {
            "goal": "G",
            "scope": "S",
            "files": "F",
            "rules": "R",
            "acceptanceCriteria": "A",
            "verification": "V",
            "output": "O"
          }
        }
    """.trimIndent()

    @Test
    fun `create, get, list, render, update, delete lifecycle`() {
        val createResult = mvc.perform(
            post("/api/v1/specs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(sampleSpec),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.title").value("Polished Spec"))
            .andExpect(jsonPath("$.sections.goal").value("G"))
            .andReturn()
        val id = createResult.response.contentAsString
            .let { Regex("\"id\":\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
            ?: error("no id in response")

        mvc.perform(get("/api/v1/specs/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(id))

        mvc.perform(get("/api/v1/specs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(id))

        mvc.perform(get("/api/v1/specs/$id/render?format=markdown"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/markdown")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("# Polished Spec")))

        mvc.perform(get("/api/v1/specs/$id/render?format=text"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("POLISHED SPEC")))

        mvc.perform(get("/api/v1/specs/$id/render?format=html"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("<!DOCTYPE html>")))

        val updateResult = mvc.perform(
            put("/api/v1/specs/$id")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"v2","sections":{"goal":"G","scope":"S","files":"F","rules":"R","acceptanceCriteria":"A","verification":"V","output":"O"},"version":1}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("v2"))
            .andReturn()
        val newVersion = updateResult.response.contentAsString
            .let { Regex("\"version\":(\\d+)").find(it)?.groupValues?.get(1)?.toLong() }
            ?: error("no version in response")
        org.junit.jupiter.api.Assertions.assertEquals(2L, newVersion)

        mvc.perform(delete("/api/v1/specs/$id"))
            .andExpect(status().isNoContent)

        mvc.perform(get("/api/v1/specs/$id"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("spec_not_found"))
    }

    @Test
    fun `unknown format returns 400`() {
        mvc.perform(get("/api/v1/specs/anything/render?format=pdf"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `ad hoc render returns markdown without saving`() {
        mvc.perform(
            post("/api/v1/specs/render?format=markdown")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Adhoc","sections":{"goal":"G","scope":"","files":"","rules":"","acceptanceCriteria":"","verification":"","output":""}}"""),
        )
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("# Adhoc")))

        mvc.perform(get("/api/v1/specs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }
}
