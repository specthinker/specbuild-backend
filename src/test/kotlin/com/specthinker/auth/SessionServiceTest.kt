package com.specthinker.auth

import com.specthinker.testutil.FixedClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Instant
import java.util.Optional

class SessionServiceTest {

    private val now = Instant.parse("2026-01-15T12:00:00Z")

    private fun service(
        secret: String = "test-secret-32-bytes-or-more-yes",
        repo: SessionRepository = mock(SessionRepository::class.java),
        userRepo: UserRepository = mock(UserRepository::class.java),
    ): SessionService {
        val props = AuthProperties(sessionSecret = secret, sessionTtlDays = 30)
        return SessionService(repo, userRepo, props, FixedClock(now))
    }

    @Test
    fun `cookie value format is sessionId dot base64url hmac`() {
        val svc = service()
        val cookieValue = svc.buildSessionCookieValue("abc123")
        assertTrue(cookieValue.contains("."))
        val parts = cookieValue.split(".")
        assertEquals(2, parts.size)
        assertEquals("abc123", parts[0])
    }

    @Test
    fun `verify returns empty when cookie is null or blank`() {
        val svc = service()
        assertFalse(svc.verifyCookieValue(null).isPresent)
        assertFalse(svc.verifyCookieValue("").isPresent)
        assertFalse(svc.verifyCookieValue("no-dot").isPresent)
    }

    @Test
    fun `verify rejects tampered HMAC`() {
        val repo = mock(SessionRepository::class.java)
        val userRepo = mock(UserRepository::class.java)
        val svc = service(repo = repo, userRepo = userRepo)
        val cookie = svc.buildSessionCookieValue("abc")
        val tampered = cookie.substringBefore(".") + ".AAAAAAAAAAAAAAAAAAAAAA"
        assertFalse(svc.verifyCookieValue(tampered).isPresent)
        verify(repo, never()).findById(org.mockito.ArgumentMatchers.anyString())
    }

    @Test
    fun `verify rejects unknown session id`() {
        val repo = mock(SessionRepository::class.java)
        val userRepo = mock(UserRepository::class.java)
        `when`(repo.findById("abc")).thenReturn(Optional.empty())
        val svc = service(repo = repo, userRepo = userRepo)
        val cookie = svc.buildSessionCookieValue("abc")
        assertFalse(svc.verifyCookieValue(cookie).isPresent)
    }

    @Test
    fun `verify refreshes last_seen and returns the user when session is valid`() {
        val repo = InMemorySessionRepository()
        val userRepo = mock(UserRepository::class.java)
        val user = user("usr_1", "alice@example.com", "free")
        `when`(userRepo.findById("usr_1")).thenReturn(Optional.of(user))
        val session = Session(
            id = "abc",
            userId = "usr_1",
            createdAt = now.minusSeconds(60),
            lastSeenAt = now.minusSeconds(60),
            expiresAt = now.plusSeconds(3600),
        )
        repo.save(session)
        val svc = service(repo = repo, userRepo = userRepo)
        val cookie = svc.buildSessionCookieValue("abc")
        val result = svc.verifyCookieValue(cookie)
        assertTrue(result.isPresent)
        assertEquals("usr_1", result.get().user.id)
        val refreshed = repo.findById("abc").orElseThrow()
        assertEquals(now, refreshed.lastSeenAt)
        assertTrue(refreshed.expiresAt.isAfter(now))
    }

    @Test
    fun `verify deletes session when user has been deleted`() {
        val repo = InMemorySessionRepository()
        val userRepo = mock(UserRepository::class.java)
        val session = Session(
            id = "abc",
            userId = "usr_1",
            createdAt = now,
            lastSeenAt = now,
            expiresAt = now.plusSeconds(3600),
        )
        repo.save(session)
        `when`(userRepo.findById("usr_1")).thenReturn(Optional.empty())
        val svc = service(repo = repo, userRepo = userRepo)
        val cookie = svc.buildSessionCookieValue("abc")
        assertFalse(svc.verifyCookieValue(cookie).isPresent)
        assertFalse(repo.existsById("abc"))
    }

    @Test
    fun `verify rejects expired sessions`() {
        val repo = InMemorySessionRepository()
        val session = Session(
            id = "abc",
            userId = "usr_1",
            createdAt = now.minusSeconds(7200),
            lastSeenAt = now.minusSeconds(7200),
            expiresAt = now.minusSeconds(60),
        )
        repo.save(session)
        val svc = service(repo = repo)
        val cookie = svc.buildSessionCookieValue("abc")
        assertFalse(svc.verifyCookieValue(cookie).isPresent)
        assertFalse(repo.existsById("abc"))
    }

    @Test
    fun `createSession stores a session row`() {
        val repo = InMemorySessionRepository()
        val svc = service(repo = repo)
        val id = svc.createSession("usr_1")
        assertNotNull(id)
        val s = repo.findById(id).orElseThrow()
        assertEquals(id, s.id)
        assertEquals("usr_1", s.userId)
        assertEquals(now, s.createdAt)
    }

    @Test
    fun `revoke deletes by id`() {
        val repo = InMemorySessionRepository()
        val svc = service(repo = repo)
        repo.save(
            Session(
                id = "abc",
                userId = "usr_1",
                createdAt = now,
                lastSeenAt = now,
                expiresAt = now.plusSeconds(3600),
            ),
        )
        svc.revoke("abc")
        assertFalse(repo.existsById("abc"))
    }

    @Test
    fun `Hashing sha256Hex is stable for same input`() {
        val a = Hashing.sha256Hex("hello")
        val b = Hashing.sha256Hex("hello")
        assertEquals(a, b)
        assertEquals(64, a.length)
    }

    @Test
    fun `Hashing secureToken returns non-empty url-safe string`() {
        val t = Hashing.secureToken(32)
        assertTrue(t.isNotEmpty())
        assertNull(t.firstOrNull { it == '+' || it == '/' || it == '=' })
    }

    private fun user(id: String, email: String, plan: String) = User(
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

private class InMemorySessionRepository : SessionRepository {
    private val map = mutableMapOf<String, Session>()
    @Suppress("UNCHECKED_CAST")
    override fun <S : Session> save(entity: S): S {
        map[entity.id] = entity
        return entity
    }
    override fun <S : Session> saveAll(entities: MutableIterable<S>): MutableIterable<S> {
        entities.forEach { save(it) }
        return entities
    }
    override fun findById(id: String): Optional<Session> = Optional.ofNullable(map[id])
    override fun existsById(id: String): Boolean = map.containsKey(id)
    override fun findAll(): MutableIterable<Session> = map.values.toMutableList()
    override fun findAllById(ids: MutableIterable<String>): MutableIterable<Session> =
        ids.mapNotNull { map[it] }.toMutableList()
    override fun count(): Long = map.size.toLong()
    override fun deleteById(id: String) {
        map.remove(id)
    }
    override fun delete(entity: Session) {
        map.remove(entity.id)
    }
    override fun deleteAllById(ids: MutableIterable<String>) {
        ids.forEach { map.remove(it) }
    }
    override fun deleteAll(entities: MutableIterable<Session>) {
        entities.forEach { map.remove(it.id) }
    }
    override fun deleteAll() {
        map.clear()
    }
}
