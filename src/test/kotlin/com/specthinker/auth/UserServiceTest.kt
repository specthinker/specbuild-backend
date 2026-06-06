package com.specthinker.auth

import com.specthinker.testutil.FixedClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Instant
import java.util.Optional

class UserServiceTest {

    private val now = Instant.parse("2026-01-15T12:00:00Z")
    private val clock = FixedClock(now)

    private fun userRepo(): UserRepository = mock(UserRepository::class.java)
    private fun oauthRepo(): OAuthAccountRepository = InMemoryOAuthAccountRepository()
    private fun service(): UserService = UserService(userRepo(), oauthRepo(), clock)

    @Test
    fun `findOrCreateByEmail returns existing user when email matches`() {
        val userRepo = userRepo()
        val svc = UserService(userRepo, oauthRepo(), clock)
        val existing = user(id = "usr_abc", email = "alice@example.com", plan = "free")
        `when`(userRepo.findByEmail("alice@example.com")).thenReturn(Optional.of(existing))
        val result = svc.findOrCreateByEmail("ALICE@example.com")
        assertEquals("usr_abc", result.id)
        verify(userRepo, times(0)).save(org.mockito.ArgumentMatchers.any(User::class.java))
    }

    @Test
    fun `findOrCreateByEmail normalizes email and creates a free user when missing`() {
        val userRepo = userRepo()
        val svc = UserService(userRepo, oauthRepo(), clock)
        `when`(userRepo.findByEmail("alice@example.com")).thenReturn(Optional.empty())
        val saved = user(id = "usr_xyz", email = "alice@example.com", plan = "free")
        `when`(userRepo.save(org.mockito.ArgumentMatchers.any(User::class.java))).thenReturn(saved)
        val result = svc.findOrCreateByEmail("  Alice@Example.COM ")
        assertEquals("usr_xyz", result.id)
        verify(userRepo, times(1)).save(org.mockito.ArgumentMatchers.any(User::class.java))
    }

    @Test
    fun `findOrCreateByOAuth returns existing user linked by provider and subject`() {
        val userRepo = userRepo()
        val oauthRepo = oauthRepo()
        val svc = UserService(userRepo, oauthRepo, clock)
        val existing = user(id = "usr_abc", email = "alice@example.com", plan = "free")
        oauthRepo.insert(
            OAuthAccount(
                provider = "google",
                providerSubject = "google-sub-1",
                userId = "usr_abc",
                createdAt = now,
            ),
        )
        `when`(userRepo.findById("usr_abc")).thenReturn(Optional.of(existing))
        val result = svc.findOrCreateByOAuth("google", "google-sub-1", "alice@example.com", emailVerified = true)
        assertEquals("usr_abc", result.id)
    }

    @Test
    fun `findOrCreateByOAuth links an existing email-matched user`() {
        val userRepo = userRepo()
        val oauthRepo = oauthRepo()
        val svc = UserService(userRepo, oauthRepo, clock)
        val existing = user(id = "usr_abc", email = "alice@example.com", plan = "free")
        `when`(userRepo.findByEmail("alice@example.com")).thenReturn(Optional.of(existing))
        `when`(userRepo.save(org.mockito.ArgumentMatchers.any(User::class.java))).thenAnswer { it.arguments[0] }
        val result = svc.findOrCreateByOAuth("google", "google-sub-2", "alice@example.com", emailVerified = true)
        assertEquals("usr_abc", result.id)
        val link = oauthRepo.findByProviderAndSubject("google", "google-sub-2")
        assertEquals("usr_abc", link?.userId)
    }

    @Test
    fun `findOrCreateByOAuth throws when email is not verified`() {
        val svc = service()
        assertThrows(OAuthEmailNotVerifiedException::class.java) {
            svc.findOrCreateByOAuth("google", "sub", "alice@example.com", emailVerified = false)
        }
    }

    @Test
    fun `setPlanAndResetQuota updates plan, resets usage, and stamps the new period`() {
        val userRepo = userRepo()
        val svc = UserService(userRepo, oauthRepo(), clock)
        val original = user(id = "usr_abc", email = "alice@example.com", plan = "free")
            .copy(polishUsed = 7L, periodStart = now.minusSeconds(3600))
        `when`(userRepo.findById("usr_abc")).thenReturn(Optional.of(original))
        `when`(userRepo.save(org.mockito.ArgumentMatchers.any(User::class.java))).thenAnswer { it.arguments[0] }
        val updated = svc.setPlanAndResetQuota("usr_abc", "basic", "cus_123")
        assertEquals("basic", updated.plan)
        assertEquals(0L, updated.polishUsed)
        assertEquals(0L, updated.specsUsed)
        assertEquals("cus_123", updated.stripeCustomerId)
        assertEquals(now, updated.periodStart)
    }

    @Test
    fun `mergeAnonymousIntoUser takes the max polish count`() {
        val userRepo = userRepo()
        val svc = UserService(userRepo, oauthRepo(), clock)
        val original = user(id = "usr_abc", email = "alice@example.com", plan = "free")
            .copy(polishUsed = 3L)
        `when`(userRepo.findById("usr_abc")).thenReturn(Optional.of(original))
        `when`(userRepo.save(org.mockito.ArgumentMatchers.any(User::class.java))).thenAnswer { it.arguments[0] }
        svc.mergeAnonymousIntoUser("anon-1", "usr_abc", 5L)
        verify(userRepo, times(1)).save(org.mockito.ArgumentMatchers.any(User::class.java))
    }

    @Test
    fun `mergeAnonymousIntoUser is noop when user already has higher count`() {
        val userRepo = userRepo()
        val svc = UserService(userRepo, oauthRepo(), clock)
        val original = user(id = "usr_abc", email = "alice@example.com", plan = "free")
            .copy(polishUsed = 10L)
        `when`(userRepo.findById("usr_abc")).thenReturn(Optional.of(original))
        svc.mergeAnonymousIntoUser("anon-1", "usr_abc", 5L)
        verify(userRepo, times(0)).save(org.mockito.ArgumentMatchers.any(User::class.java))
    }

    @Test
    fun `mergeAnonymousIntoUser is noop when anonymousPolishUsed is zero`() {
        val userRepo = userRepo()
        val svc = UserService(userRepo, oauthRepo(), clock)
        svc.mergeAnonymousIntoUser("anon-1", "usr_abc", 0L)
        verify(userRepo, times(0)).findById(org.mockito.ArgumentMatchers.anyString())
    }

    private fun user(id: String, email: String?, plan: String) = User(
        id = id,
        email = email,
        plan = plan,
        planSetAt = now,
        periodStart = now,
        specsUsed = 0L,
        polishUsed = 0L,
        stripeCustomerId = null,
        createdAt = now,
    )
}

private class InMemoryOAuthAccountRepository : OAuthAccountRepository {
    private val links = mutableListOf<OAuthAccount>()
    override fun insert(account: OAuthAccount) {
        links.add(account)
    }
    override fun findByProviderAndSubject(provider: String, subject: String): OAuthAccount? =
        links.firstOrNull { it.provider == provider && it.providerSubject == subject }
}
