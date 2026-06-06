package com.specthinker.auth

import org.springframework.data.repository.CrudRepository
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant

interface SessionMaintenanceRepository {
    fun touch(id: String, lastSeenAt: Instant, expiresAt: Instant)
    fun deleteExpired(now: Instant)
}

@Repository
interface SessionRepository : CrudRepository<Session, String>

@Repository
class JdbcSessionMaintenanceRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : SessionMaintenanceRepository {
    override fun touch(id: String, lastSeenAt: Instant, expiresAt: Instant) {
        val sql = """
            UPDATE sessions SET last_seen_at = :lastSeenAt, expires_at = :expiresAt WHERE id = :id
        """.trimIndent()
        jdbc.update(
            sql,
            MapSqlParameterSource()
                .addValue("id", id)
                .addValue("lastSeenAt", lastSeenAt.toString())
                .addValue("expiresAt", expiresAt.toString()),
        )
    }

    override fun deleteExpired(now: Instant) {
        jdbc.update(
            "DELETE FROM sessions WHERE expires_at < :now",
            MapSqlParameterSource().addValue("now", now.toString()),
        )
    }
}
