package com.specthinker.auth

import com.specthinker.stripe.ProcessedStripeEventRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Component
@EnableScheduling
class ExpiredDataCleanupJob(
    private val magicLinks: MagicLinkTokenRepository,
    private val oauthStates: OAuthStateTokenRepository,
    private val sessions: com.specthinker.auth.SessionMaintenanceRepository,
    private val processedStripe: ProcessedStripeEventRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(ExpiredDataCleanupJob::class.java)

    @Scheduled(fixedDelayString = "\${specthinker.auth.cleanup-interval-ms:3600000}")
    @Transactional
    fun cleanup() {
        val now = Instant.now(clock)
        try {
            magicLinks.deleteExpired(now)
            oauthStates.deleteExpired(now)
            sessions.deleteExpired(now)
            processedStripe.deleteOlderThan(now.minus(Duration.ofDays(30)))
        } catch (e: Exception) {
            log.warn("Cleanup job failed: {}", e.message)
        }
    }
}
