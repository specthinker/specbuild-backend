package com.specthinker.auth

import com.specthinker.config.BackendProperties
import com.specthinker.testutil.FixedClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class MagicLinkServiceTest {

    private val now = Instant.parse("2026-01-15T12:00:00Z")
    private val clock = FixedClock(now)
    private val backendProps = BackendProperties(backendUrl = "https://api.example.com")

    private fun service(
        repo: MagicLinkTokenRepository = InMemoryMagicLinkTokenRepository(),
        mail: MailService = RecordingMailer(),
        allowedOrigins: String = "https://app.example.com,http://localhost:5173",
    ): MagicLinkService {
        val authProps = AuthProperties(
            sessionSecret = "secret",
            allowedRedirectOrigins = allowedOrigins,
        )
        return MagicLinkService(repo, authProps, backendProps, mail, clock)
    }

    @Test
    fun `isValidEmail accepts normal emails`() {
        val svc = service()
        assertTrue(svc.isValidEmail("alice@example.com"))
        assertTrue(svc.isValidEmail("  bob+test@x.y  "))
    }

    @Test
    fun `isValidEmail rejects malformed addresses`() {
        val svc = service()
        assertFalse(svc.isValidEmail("not-an-email"))
        assertFalse(svc.isValidEmail(""))
        assertFalse(svc.isValidEmail("a@b"))
        assertFalse(svc.isValidEmail("@example.com"))
    }

    @Test
    fun `sanitizeRedirect allows origins in the allowlist`() {
        val svc = service(allowedOrigins = "https://app.example.com,http://localhost:5173")
        assertEquals("https://app.example.com/landing", svc.sanitizeRedirect("https://app.example.com/landing"))
        assertEquals("http://localhost:5173/", svc.sanitizeRedirect("http://localhost:5173/"))
    }

    @Test
    fun `sanitizeRedirect falls back to the first allowed origin when given an unknown host`() {
        val svc = service(allowedOrigins = "https://app.example.com,http://localhost:5173")
        assertEquals("https://app.example.com", svc.sanitizeRedirect("https://evil.example.com/callback"))
    }

    @Test
    fun `sanitizeRedirect returns backendUrl when allowlist is empty`() {
        val svc = service(allowedOrigins = "")
        assertEquals("https://api.example.com", svc.sanitizeRedirect("https://anything.example.com"))
        assertEquals("https://api.example.com", svc.sanitizeRedirect(null))
    }

    @Test
    fun `issue stores hash and produces a verify URL`() {
        val repo = InMemoryMagicLinkTokenRepository()
        val svc = service(repo = repo)
        val issued = svc.issue(
            email = "ALICE@example.com",
            redirect = "https://app.example.com/",
            clientId = "client-abc",
            loginUrl = null,
        )
        assertEquals("alice@example.com", issued.email)
        assertTrue(issued.token.isNotEmpty())
        assertEquals(1, repo.stored.size)
        val stored = repo.stored.first()
        assertEquals(Hashing.sha256Hex(issued.token), stored.tokenHash)
        assertEquals("alice@example.com", stored.email)
        assertEquals("https://app.example.com/", stored.redirectUrl)
        assertEquals("client-abc", stored.clientId)
        assertTrue(issued.verifyUrl.startsWith("https://api.example.com/api/v1/auth/email/verify?token="))
    }

    @Test
    fun `verify returns failure for unknown token`() {
        val svc = service()
        val result = svc.verify("not-a-real-token")
        assertTrue(result is MagicLinkVerification.Failure)
    }

    @Test
    fun `verify marks token used and returns success when valid`() {
        val repo = InMemoryMagicLinkTokenRepository()
        val svc = service(repo = repo)
        val issued = svc.issue("alice@example.com", "https://app.example.com/", null, null)
        val result = svc.verify(issued.token)
        assertTrue(result is MagicLinkVerification.Success)
        val success = result as MagicLinkVerification.Success
        assertEquals("alice@example.com", success.email)
        assertEquals("https://app.example.com/", success.redirectUrl)
        val stored = repo.stored.first { it.tokenHash == Hashing.sha256Hex(issued.token) }
        assertEquals(now, stored.usedAt)
    }

    @Test
    fun `verify rejects already-used token`() {
        val svc = service()
        val issued = svc.issue("alice@example.com", "https://app.example.com/", null, null)
        svc.verify(issued.token)
        val second = svc.verify(issued.token)
        assertTrue(second is MagicLinkVerification.Failure)
    }

    @Test
    fun `verify rejects expired token`() {
        val svc = service()
        val issued = svc.issue("alice@example.com", "https://app.example.com/", null, null)
        val result = svc.verify(issued.token, now = Instant.parse("2026-01-15T13:00:00Z"))
        assertTrue(result is MagicLinkVerification.Failure)
    }

    @Test
    fun `build verify url uses provided baseUrl when supplied`() {
        val svc = service()
        val url = svc.buildVerifyUrl("tok", "https://override.example.com")
        assertTrue(url.startsWith("https://override.example.com/api/v1/auth/email/verify?token=tok"))
    }
}

private class InMemoryMagicLinkTokenRepository : MagicLinkTokenRepository {
    val stored = mutableListOf<MagicLinkToken>()
    override fun insert(token: MagicLinkToken) {
        stored.add(token)
    }
    override fun findByHash(tokenHash: String): MagicLinkToken? =
        stored.firstOrNull { it.tokenHash == tokenHash }
    override fun markUsed(tokenHash: String, usedAt: Instant) {
        val idx = stored.indexOfFirst { it.tokenHash == tokenHash }
        if (idx >= 0) stored[idx] = stored[idx].copy(usedAt = usedAt)
    }
    override fun deleteExpired(now: Instant) {
        stored.removeAll { it.expiresAt.isBefore(now) }
    }
}

private class RecordingMailer : MailService {
    val sent = mutableListOf<Pair<String, String>>()
    override fun sendMagicLink(to: String, link: String) {
        sent.add(to to link)
    }
}
