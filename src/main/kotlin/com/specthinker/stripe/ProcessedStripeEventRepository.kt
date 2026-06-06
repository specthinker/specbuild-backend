package com.specthinker.stripe

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant

interface ProcessedStripeEventRepository {
    fun exists(eventId: String): Boolean
    fun markProcessed(eventId: String, now: Instant)
    fun deleteOlderThan(cutoff: Instant)
}

@Repository
class JdbcProcessedStripeEventRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : ProcessedStripeEventRepository {
    override fun exists(eventId: String): Boolean {
        val count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM processed_stripe_events WHERE event_id = :eventId",
            MapSqlParameterSource().addValue("eventId", eventId),
            Long::class.java,
        ) ?: 0L
        return count > 0L
    }

    override fun markProcessed(eventId: String, now: Instant) {
        jdbc.update(
            """
            INSERT INTO processed_stripe_events (event_id, received_at)
            VALUES (:eventId, :receivedAt)
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("receivedAt", now.toString()),
        )
    }

    override fun deleteOlderThan(cutoff: Instant) {
        jdbc.update(
            "DELETE FROM processed_stripe_events WHERE received_at < :cutoff",
            MapSqlParameterSource().addValue("cutoff", cutoff.toString()),
        )
    }
}
