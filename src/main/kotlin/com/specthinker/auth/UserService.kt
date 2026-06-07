package com.specthinker.auth

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.Optional

@Service
class UserService(
    private val users: UserRepository,
    private val oauthAccounts: OAuthAccountRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val random = SecureRandom()

    @Transactional
    fun findOrCreateByEmail(email: String): User {
        val normalized = normalizeEmail(email)
        val existing = users.findByEmail(normalized).orElse(null)
        if (existing != null) return existing
        val now = Instant.now(clock)
        val newUser = User(
            id = generateId(),
            email = normalized,
            plan = Plan.FREE.wire,
            planSetAt = now,
            periodStart = now,
            specsUsed = 0L,
            polishUsed = 0L,
            stripeCustomerId = null,
            createdAt = now,
        )
        return saveAndMarkPersisted(newUser)
    }

    @Transactional
    fun findOrCreateByOAuth(provider: String, subject: String, email: String, emailVerified: Boolean): User {
        if (!emailVerified) {
            throw OAuthEmailNotVerifiedException(email)
        }
        val existingLink = oauthAccounts.findByProviderAndSubject(provider, subject)
        if (existingLink != null) {
            return users.findById(existingLink.userId).orElseThrow {
                IllegalStateException("OAuth link references missing user: ${existingLink.userId}")
            }
        }
        val normalized = normalizeEmail(email)
        val byEmail = users.findByEmail(normalized).orElse(null)
        val user = byEmail ?: createFreeUser(normalized)
        val now = Instant.now(clock)
        oauthAccounts.insert(
            OAuthAccount(
                provider = provider,
                providerSubject = subject,
                userId = user.id,
                createdAt = now,
            ),
        )
        return saveAndMarkPersisted(user)
    }

    @Transactional
    fun setPlanAndResetQuota(userId: String, plan: String, stripeCustomerId: String?): User {
        val user = users.findById(userId).orElseThrow {
            IllegalStateException("Cannot set plan: user not found: $userId")
        }
        val now = Instant.now(clock)
        val updated = user.copy(
            plan = plan,
            planSetAt = now,
            periodStart = now,
            specsUsed = 0L,
            polishUsed = 0L,
            stripeCustomerId = stripeCustomerId ?: user.stripeCustomerId,
        )
        return saveAndMarkPersisted(updated)
    }

    @Transactional
    fun setStripeCustomerId(userId: String, customerId: String): User {
        val user = users.findById(userId).orElseThrow {
            IllegalStateException("Cannot set stripe customer: user not found: $userId")
        }
        if (user.stripeCustomerId == customerId) return user
        return saveAndMarkPersisted(user.copy(stripeCustomerId = customerId))
    }

    fun findById(id: String): Optional<User> = users.findById(id)

    fun findByEmail(email: String): Optional<User> = users.findByEmail(normalizeEmail(email))

    fun findByStripeCustomerId(customerId: String): Optional<User> =
        users.findByStripeCustomerId(customerId)

    @Transactional
    fun mergeAnonymousIntoUser(anonymousClientId: String?, userId: String, anonymousPolishUsed: Long) {
        if (anonymousClientId.isNullOrBlank() || anonymousPolishUsed <= 0L) return
        val user = users.findById(userId).orElse(null) ?: return
        if (user.polishUsed >= anonymousPolishUsed) return
        saveAndMarkPersisted(user.copy(polishUsed = anonymousPolishUsed))
    }

    private fun createFreeUser(email: String?): User {
        val now = Instant.now(clock)
        return User(
            id = generateId(),
            email = email,
            plan = Plan.FREE.wire,
            planSetAt = now,
            periodStart = now,
            specsUsed = 0L,
            polishUsed = 0L,
            stripeCustomerId = null,
            createdAt = now,
        )
    }

    private fun generateId(): String {
        val bytes = ByteArray(4)
        random.nextBytes(bytes)
        val hex = bytes.joinToString("") { "%02x".format(it) }
        return "usr_$hex"
    }

    private fun saveAndMarkPersisted(user: User): User {
        val saved = users.save(user)
        saved.markPersisted()
        return saved
    }

    private fun normalizeEmail(email: String): String = email.trim().lowercase()
}

class OAuthEmailNotVerifiedException(email: String) :
    RuntimeException("OAuth provider did not verify email: $email")
