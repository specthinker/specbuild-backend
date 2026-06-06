package com.specthinker.auth

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Optional

data class VerifiedSession(
    val sessionId: String,
    val user: User,
)

@Service
class SessionService(
    private val sessions: SessionRepository,
    private val userRepository: UserRepository,
    private val props: AuthProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val random = SecureRandom()
    private val cookieSeparator = "."

    fun buildSessionCookieValue(sessionId: String): String {
        val secret = requireSecret()
        val mac = hmacSha256(secret, sessionId)
        val macB64 = base64UrlEncode(mac)
        return "$sessionId$cookieSeparator$macB64"
    }

    @Transactional
    fun createSession(userId: String): String {
        val sessionId = randomToken(24)
        val now = Instant.now(clock)
        val expiresAt = now.plus(Duration.ofDays(props.sessionTtlDays))
        sessions.save(
            Session(
                id = sessionId,
                userId = userId,
                createdAt = now,
                lastSeenAt = now,
                expiresAt = expiresAt,
            ),
        )
        return sessionId
    }

    @Transactional
    fun verifyCookieValue(cookieValue: String?): Optional<VerifiedSession> {
        if (cookieValue.isNullOrBlank()) return Optional.empty()
        val parts = cookieValue.split(cookieSeparator)
        if (parts.size != 2) return Optional.empty()
        val sessionId = parts[0]
        val providedMac = try {
            base64UrlDecode(parts[1])
        } catch (_: IllegalArgumentException) {
            return Optional.empty()
        }
        val expectedMac = try {
            hmacSha256(requireSecret(), sessionId)
        } catch (_: IllegalStateException) {
            return Optional.empty()
        }
        if (!constantTimeEquals(providedMac, expectedMac)) return Optional.empty()
        val session = sessions.findById(sessionId).orElse(null) ?: return Optional.empty()
        val now = Instant.now(clock)
        if (session.expiresAt.isBefore(now)) {
            sessions.deleteById(sessionId)
            return Optional.empty()
        }
        val newExpiresAt = now.plus(Duration.ofDays(props.sessionTtlDays))
        val updated = session.copy(lastSeenAt = now, expiresAt = newExpiresAt)
        sessions.save(updated)
        val user = userRepository.findById(session.userId).orElse(null) ?: run {
            sessions.deleteById(sessionId)
            return Optional.empty()
        }
        return Optional.of(VerifiedSession(sessionId = sessionId, user = user))
    }

    @Transactional
    fun revoke(sessionId: String) {
        sessions.deleteById(sessionId)
    }

    private fun requireSecret(): ByteArray {
        val raw = props.sessionSecret
        if (raw.isBlank()) {
            throw IllegalStateException("specthinker.auth.session-secret is not configured")
        }
        return raw.toByteArray(Charsets.UTF_8)
    }

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val keySpec = javax.crypto.spec.SecretKeySpec(key, "HmacSHA256")
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(keySpec)
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun randomToken(byteLen: Int): String {
        val bytes = ByteArray(byteLen)
        random.nextBytes(bytes)
        return base64UrlEncode(bytes)
    }

    private fun base64UrlEncode(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun base64UrlDecode(s: String): ByteArray =
        Base64.getUrlDecoder().decode(s)

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}

object Hashing {
    fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun secureToken(byteLen: Int = 32): String {
        val bytes = ByteArray(byteLen)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun base64UrlEncode(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
