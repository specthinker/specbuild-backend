package com.specthinker.auth

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant

data class MagicLinkIssued(val token: String, val email: String, val verifyUrl: String)

sealed interface MagicLinkVerification {
    data class Success(
        val email: String,
        val redirectUrl: String,
        val clientId: String?,
    ) : MagicLinkVerification

    data class Failure(val reason: String) : MagicLinkVerification
}

@Service
class MagicLinkService(
    private val tokens: MagicLinkTokenRepository,
    private val authProps: AuthProperties,
    private val backendProps: com.specthinker.config.BackendProperties,
    private val mail: MailService,
    private val clock: Clock = Clock.systemUTC(),
) {
    companion object {
        private const val TOKEN_TTL_MINUTES = 15L
        private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }

    fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email.trim())

    fun allowedRedirects(): List<String> =
        authProps.allowedRedirectOrigins
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun sanitizeRedirect(redirect: String?): String {
        val candidates = allowedRedirects()
        if (candidates.isEmpty()) {
            return backendProps.backendUrl.trimEnd('/')
        }
        if (redirect.isNullOrBlank()) {
            return candidates.first()
        }
        val normalized = redirect.trim()
        for (allowed in candidates) {
            if (originsMatch(allowed, normalized)) return normalized
        }
        return candidates.first()
    }

    private fun originsMatch(allowed: String, candidate: String): Boolean {
        val allowedOrigin = originOf(allowed) ?: return false
        val candidateOrigin = originOf(candidate) ?: return false
        return allowedOrigin == candidateOrigin
    }

    private fun originOf(url: String): String? = try {
        val uri = URI.create(if (url.contains("://")) url else "https://$url")
        val host = uri.host ?: return null
        val scheme = uri.scheme ?: "https"
        val port = uri.port
        if (port == -1 || (scheme == "https" && port == 443) || (scheme == "http" && port == 80)) {
            "$scheme://$host"
        } else {
            "$scheme://$host:$port"
        }
    } catch (_: IllegalArgumentException) {
        null
    }

    @Transactional
    fun issue(email: String, redirect: String, clientId: String?, loginUrl: String? = null): MagicLinkIssued {
        val normalized = normalizeEmail(email)
        val token = Hashing.secureToken(32)
        val tokenHash = Hashing.sha256Hex(token)
        val now = Instant.now(clock)
        val expires = now.plus(Duration.ofMinutes(TOKEN_TTL_MINUTES))
        val sanitizedRedirect = sanitizeRedirect(redirect)
        tokens.insert(
            MagicLinkToken(
                tokenHash = tokenHash,
                email = normalized,
                redirectUrl = sanitizedRedirect,
                clientId = clientId,
                expiresAt = expires,
                usedAt = null,
            ),
        )
        val verifyUrl = buildVerifyUrl(token, loginUrl)
        return MagicLinkIssued(token = token, email = normalized, verifyUrl = verifyUrl)
    }

    fun buildVerifyUrl(token: String, baseUrl: String?): String {
        val base = (baseUrl?.takeIf { it.isNotBlank() } ?: backendProps.backendUrl).trimEnd('/')
        return "$base/api/v1/auth/email/verify?token=$token"
    }

    @Transactional
    fun verify(token: String, now: Instant? = null): MagicLinkVerification {
        val effectiveNow = now ?: Instant.now(clock)
        val hash = Hashing.sha256Hex(token)
        val record = tokens.findByHash(hash)
            ?: return MagicLinkVerification.Failure("invalid_token")
        if (record.usedAt != null) {
            return MagicLinkVerification.Failure("invalid_token")
        }
        if (record.expiresAt.isBefore(effectiveNow)) {
            return MagicLinkVerification.Failure("invalid_token")
        }
        tokens.markUsed(hash, effectiveNow)
        return MagicLinkVerification.Success(
            email = record.email,
            redirectUrl = record.redirectUrl,
            clientId = record.clientId,
        )
    }

    private fun normalizeEmail(email: String): String = email.trim().lowercase()
}
