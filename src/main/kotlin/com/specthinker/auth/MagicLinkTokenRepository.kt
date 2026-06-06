package com.specthinker.auth

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant

data class MagicLinkToken(
    val tokenHash: String,
    val email: String,
    val redirectUrl: String,
    val clientId: String?,
    val expiresAt: Instant,
    val usedAt: Instant?,
)

interface MagicLinkTokenRepository {
    fun insert(token: MagicLinkToken)
    fun findByHash(tokenHash: String): MagicLinkToken?
    fun markUsed(tokenHash: String, usedAt: Instant)
    fun deleteExpired(now: Instant)
}

@Repository
class JdbcMagicLinkTokenRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : MagicLinkTokenRepository {
    override fun insert(token: MagicLinkToken) {
        jdbc.update(
            """
            INSERT INTO magic_link_tokens (token_hash, email, redirect_url, client_id, expires_at, used_at)
            VALUES (:tokenHash, :email, :redirectUrl, :clientId, :expiresAt, :usedAt)
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("tokenHash", token.tokenHash)
                .addValue("email", token.email)
                .addValue("redirectUrl", token.redirectUrl)
                .addValue("clientId", token.clientId)
                .addValue("expiresAt", token.expiresAt.toString())
                .addValue("usedAt", token.usedAt?.toString()),
        )
    }

    override fun findByHash(tokenHash: String): MagicLinkToken? {
        val rows = jdbc.query(
            """
            SELECT token_hash, email, redirect_url, client_id, expires_at, used_at
            FROM magic_link_tokens WHERE token_hash = :tokenHash
            """.trimIndent(),
            MapSqlParameterSource().addValue("tokenHash", tokenHash),
        ) { rs, _ -> mapRow(rs) }
        return rows.firstOrNull()
    }

    override fun markUsed(tokenHash: String, usedAt: Instant) {
        jdbc.update(
            "UPDATE magic_link_tokens SET used_at = :usedAt WHERE token_hash = :tokenHash",
            MapSqlParameterSource()
                .addValue("usedAt", usedAt.toString())
                .addValue("tokenHash", tokenHash),
        )
    }

    override fun deleteExpired(now: Instant) {
        jdbc.update(
            "DELETE FROM magic_link_tokens WHERE expires_at < :now",
            MapSqlParameterSource().addValue("now", now.toString()),
        )
    }

    private fun mapRow(rs: java.sql.ResultSet): MagicLinkToken = MagicLinkToken(
        tokenHash = rs.getString("token_hash"),
        email = rs.getString("email"),
        redirectUrl = rs.getString("redirect_url"),
        clientId = rs.getString("client_id"),
        expiresAt = Instant.parse(rs.getString("expires_at")),
        usedAt = rs.getString("used_at")?.let(Instant::parse),
    )
}
