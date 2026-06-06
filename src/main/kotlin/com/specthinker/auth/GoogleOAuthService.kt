package com.specthinker.auth

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64

data class GoogleAuthStart(val authUrl: String, val state: String)

sealed interface GoogleCallbackResult {
    data class Success(
        val user: User,
        val redirectUrl: String,
    ) : GoogleCallbackResult

    data class Failure(val reason: String) : GoogleCallbackResult
}

@Service
class GoogleOAuthService(
    private val stateTokens: OAuthStateTokenRepository,
    private val authProps: AuthProperties,
    private val backendProps: com.specthinker.config.BackendProperties,
    private val userService: UserService,
    private val http: HttpClient,
    private val objectMapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC(),
) {
    companion object {
        private const val STATE_TTL_MINUTES = 10L
        private const val GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token"
    }

    private val random = SecureRandom()

    fun allowedRedirects(): List<String> =
        authProps.allowedRedirectOrigins
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun sanitizeRedirect(redirect: String?): String {
        val candidates = allowedRedirects()
        if (candidates.isEmpty()) return backendProps.backendUrl.trimEnd('/')
        if (redirect.isNullOrBlank()) return candidates.first()
        val normalized = redirect.trim()
        for (allowed in candidates) {
            if (originsMatch(allowed, normalized)) return normalized
        }
        return candidates.first()
    }

    private fun originsMatch(allowed: String, candidate: String): Boolean {
        val a = originOf(allowed) ?: return false
        val c = originOf(candidate) ?: return false
        return a == c
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
    fun startAuth(redirect: String?): GoogleAuthStart {
        require(authProps.google.clientId.isNotBlank()) {
            "specthinker.auth.google.client-id is not configured"
        }
        val state = secureToken(16)
        val verifier = secureToken(32)
        val challenge = s256(verifier)
        val sanitizedRedirect = sanitizeRedirect(redirect)
        val now = Instant.now(clock)
        stateTokens.insert(
            OAuthStateToken(
                stateHash = Hashing.sha256Hex(state),
                pkceVerifier = verifier,
                redirectUrl = sanitizedRedirect,
                expiresAt = now.plus(Duration.ofMinutes(STATE_TTL_MINUTES)),
                usedAt = null,
            ),
        )
        val callback = "${backendProps.backendUrl.trimEnd('/')}/api/v1/auth/google/callback"
        val params = listOf(
            "response_type" to "code",
            "client_id" to authProps.google.clientId,
            "redirect_uri" to callback,
            "scope" to "openid email profile",
            "state" to state,
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
            "prompt" to "select_account",
        )
        val qs = params.joinToString("&") { (k, v) -> "${URLEncoder.encode(k, Charsets.UTF_8.name())}=${URLEncoder.encode(v, Charsets.UTF_8.name())}" }
        val authUrl = "$GOOGLE_AUTH_URL?$qs"
        return GoogleAuthStart(authUrl = authUrl, state = state)
    }

    @Transactional
    fun handleCallback(code: String?, state: String?): GoogleCallbackResult {
        if (code.isNullOrBlank() || state.isNullOrBlank()) {
            return GoogleCallbackResult.Failure("invalid_request")
        }
        val stateHash = Hashing.sha256Hex(state)
        val record = stateTokens.findByHash(stateHash)
            ?: return GoogleCallbackResult.Failure("invalid_state")
        if (record.usedAt != null) return GoogleCallbackResult.Failure("invalid_state")
        if (record.expiresAt.isBefore(Instant.now(clock))) {
            return GoogleCallbackResult.Failure("invalid_state")
        }
        stateTokens.markUsed(stateHash, Instant.now(clock))

        require(authProps.google.clientId.isNotBlank() && authProps.google.clientSecret.isNotBlank()) {
            "specthinker.auth.google.client-id/client-secret are not configured"
        }
        val callback = "${backendProps.backendUrl.trimEnd('/')}/api/v1/auth/google/callback"
        val tokenResponse = exchangeCodeForToken(code, callback, record.pkceVerifier)
            ?: return GoogleCallbackResult.Failure("token_exchange_failed")
        val idToken = tokenResponse.idToken
            ?: return GoogleCallbackResult.Failure("token_exchange_failed")
        val claims = decodeJwtPayload(idToken)
            ?: return GoogleCallbackResult.Failure("token_exchange_failed")
        val sub = claims["sub"] as? String
        val email = claims["email"] as? String
        val emailVerified = claims["email_verified"] as? Boolean
            ?: (claims["email_verified"] as? String)?.toBooleanStrictOrNull()
            ?: false
        if (sub.isNullOrBlank() || email.isNullOrBlank()) {
            return GoogleCallbackResult.Failure("token_exchange_failed")
        }
        if (!emailVerified) {
            return GoogleCallbackResult.Failure("email_not_verified")
        }
        val user = userService.findOrCreateByOAuth("google", sub, email, emailVerified = true)
        return GoogleCallbackResult.Success(user = user, redirectUrl = record.redirectUrl)
    }

    private fun exchangeCodeForToken(code: String, redirectUri: String, verifier: String): TokenResponse? {
        val form = listOf(
            "code" to code,
            "client_id" to authProps.google.clientId,
            "client_secret" to authProps.google.clientSecret,
            "redirect_uri" to redirectUri,
            "code_verifier" to verifier,
            "grant_type" to "authorization_code",
        ).joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, StandardCharsets.UTF_8.name())}=${URLEncoder.encode(v, StandardCharsets.UTF_8.name())}"
        }
        val req = HttpRequest.newBuilder()
            .uri(URI.create(GOOGLE_TOKEN_URL))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        return try {
            val res = http.send(req, HttpResponse.BodyHandlers.ofString())
            if (res.statusCode() !in 200..299) {
                null
            } else {
                parseTokenResponse(res.body())
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseTokenResponse(body: String): TokenResponse? {
        val map = parseJsonObject(body) ?: return null
        val idToken = map["id_token"] as? String ?: return null
        val accessToken = map["access_token"] as? String
        return TokenResponse(idToken = idToken, accessToken = accessToken)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseJsonObject(body: String): Map<String, Any?>? = try {
        objectMapper.readValue(body, Map::class.java) as? Map<String, Any?>
    } catch (_: Exception) {
        null
    }

    private fun decodeJwtPayload(jwt: String): Map<String, Any?>? {
        val parts = jwt.split(".")
        if (parts.size < 2) return null
        return try {
            val payload = String(Base64.getUrlDecoder().decode(padBase64(parts[1])), StandardCharsets.UTF_8)
            parseJsonObject(payload)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun padBase64(s: String): String {
        val pad = (4 - s.length % 4) % 4
        return s + "=".repeat(pad)
    }

    private fun secureToken(byteLen: Int): String {
        val bytes = ByteArray(byteLen)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun s256(verifier: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private data class TokenResponse(val idToken: String, val accessToken: String?)
}
