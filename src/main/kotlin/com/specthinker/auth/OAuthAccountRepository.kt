package com.specthinker.auth

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant

data class OAuthAccount(
    val provider: String,
    val providerSubject: String,
    val userId: String,
    val createdAt: Instant,
)

interface OAuthAccountRepository {
    fun insert(account: OAuthAccount)
    fun findByProviderAndSubject(provider: String, subject: String): OAuthAccount?
}

@Repository
class JdbcOAuthAccountRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : OAuthAccountRepository {
    override fun insert(account: OAuthAccount) {
        val sql = """
            INSERT INTO oauth_accounts (provider, provider_subject, user_id, created_at)
            VALUES (:provider, :providerSubject, :userId, :createdAt)
        """.trimIndent()
        jdbc.update(sql, MapSqlParameterSource()
            .addValue("provider", account.provider)
            .addValue("providerSubject", account.providerSubject)
            .addValue("userId", account.userId)
            .addValue("createdAt", account.createdAt.toString()))
    }

    override fun findByProviderAndSubject(provider: String, subject: String): OAuthAccount? {
        val sql = """
            SELECT provider, provider_subject, user_id, created_at
            FROM oauth_accounts
            WHERE provider = :provider AND provider_subject = :subject
        """.trimIndent()
        val rows = jdbc.query(
            sql,
            MapSqlParameterSource().addValue("provider", provider).addValue("subject", subject),
        ) { rs, _ ->
            OAuthAccount(
                provider = rs.getString("provider"),
                providerSubject = rs.getString("provider_subject"),
                userId = rs.getString("user_id"),
                createdAt = Instant.parse(rs.getString("created_at")),
            )
        }
        return rows.firstOrNull()
    }
}
