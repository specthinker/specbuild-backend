package com.specthinker.auth

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant

data class OAuthStateToken(
    val stateHash: String,
    val pkceVerifier: String,
    val redirectUrl: String,
    val expiresAt: Instant,
    val usedAt: Instant?,
)

interface OAuthStateTokenRepository {
    fun insert(token: OAuthStateToken)
    fun findByHash(stateHash: String): OAuthStateToken?
    fun markUsed(stateHash: String, usedAt: Instant)
    fun deleteExpired(now: Instant)
}

@Repository
class JdbcOAuthStateTokenRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : OAuthStateTokenRepository {
    override fun insert(token: OAuthStateToken) {
        jdbc.update(
            """
            INSERT INTO oauth_state_tokens (state_hash, pkce_verifier, redirect_url, expires_at, used_at)
            VALUES (:stateHash, :pkceVerifier, :redirectUrl, :expiresAt, :usedAt)
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("stateHash", token.stateHash)
                .addValue("pkceVerifier", token.pkceVerifier)
                .addValue("redirectUrl", token.redirectUrl)
                .addValue("expiresAt", token.expiresAt.toString())
                .addValue("usedAt", token.usedAt?.toString()),
        )
    }

    override fun findByHash(stateHash: String): OAuthStateToken? {
        val rows = jdbc.query(
            """
            SELECT state_hash, pkce_verifier, redirect_url, expires_at, used_at
            FROM oauth_state_tokens WHERE state_hash = :stateHash
            """.trimIndent(),
            MapSqlParameterSource().addValue("stateHash", stateHash),
        ) { rs, _ -> mapRow(rs) }
        return rows.firstOrNull()
    }

    override fun markUsed(stateHash: String, usedAt: Instant) {
        jdbc.update(
            "UPDATE oauth_state_tokens SET used_at = :usedAt WHERE state_hash = :stateHash",
            MapSqlParameterSource()
                .addValue("usedAt", usedAt.toString())
                .addValue("stateHash", stateHash),
        )
    }

    override fun deleteExpired(now: Instant) {
        jdbc.update(
            "DELETE FROM oauth_state_tokens WHERE expires_at < :now",
            MapSqlParameterSource().addValue("now", now.toString()),
        )
    }

    private fun mapRow(rs: java.sql.ResultSet): OAuthStateToken = OAuthStateToken(
        stateHash = rs.getString("state_hash"),
        pkceVerifier = rs.getString("pkce_verifier"),
        redirectUrl = rs.getString("redirect_url"),
        expiresAt = Instant.parse(rs.getString("expires_at")),
        usedAt = rs.getString("used_at")?.let(Instant::parse),
    )
}
