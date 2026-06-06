package com.specthinker.auth

import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:sqlite::memory:",
        "spring.sql.init.mode=always",
        "specthinker.llm.providers.deepseek.enabled=false",
        "specthinker.llm.providers.openrouter-deepseek.enabled=false",
        "specthinker.llm.providers.openrouter-free.enabled=false",
        "specthinker.auth.session-secret=test-secret-for-integration-tests",
        "specthinker.auth.mail.mode=logging",
        "specthinker.auth.allowed-redirect-origins=https://app.example.com,http://localhost:5173",
        "specthinker.backend-url=http://localhost:8080",
        "specthinker.auth.cookie.secure=false",
        "specthinker.auth.cookie.samesite=Lax",
        "specthinker.auth.google.client-id=test-client-id.apps.googleusercontent.com",
        "specthinker.auth.google.client-secret=test-client-secret",
    ],
)
class AuthControllerIntegrationTest {

    @Autowired
    lateinit var mvc: MockMvc

    @Test
    fun `me returns 401 when no session cookie`() {
        mvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("no_session"))
    }

    @Test
    fun `me returns 401 when cookie HMAC is tampered`() {
        mvc.perform(get("/api/v1/auth/me").cookie(Cookie("session", "abc.BADBADBAD")))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `logout returns 204 and clears the session cookie`() {
        mvc.perform(post("/api/v1/auth/logout"))
            .andExpect(status().isNoContent)
            .andExpect(cookie().value("session", ""))
            .andExpect(cookie().maxAge("session", 0))
    }

    @Test
    fun `email request always returns 202 even for invalid input`() {
        mvc.perform(
            post("/api/v1/auth/email/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"not-an-email"}"""),
        )
            .andExpect(status().isAccepted)
    }

    @Test
    fun `email request returns 202 for a valid email`() {
        mvc.perform(
            post("/api/v1/auth/email/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"alice@example.com","redirect":"https://app.example.com/"}"""),
        )
            .andExpect(status().isAccepted)
    }

    @Test
    fun `email verify redirects to signed_in=0 when token is invalid`() {
        mvc.perform(get("/api/v1/auth/email/verify?token=does-not-exist"))
            .andExpect(status().isFound)
            .andExpect(redirectedUrl("https://app.example.com?signed_in=0&error=invalid_token"))
    }

    @Test
    fun `google start redirects to accounts google with PKCE`() {
        mvc.perform(get("/api/v1/auth/google/start?redirect=https://app.example.com/"))
            .andExpect(status().isFound)
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("accounts.google.com")))
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("code_challenge=")))
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("code_challenge_method=S256")))
    }
}
