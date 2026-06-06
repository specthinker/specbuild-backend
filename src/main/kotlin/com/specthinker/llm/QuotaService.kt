package com.specthinker.llm

import com.specthinker.auth.Quotas
import com.specthinker.auth.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class QuotaState(
    val used: Long,
    val limit: Long,
    val resetsAtEpochMillis: Long,
)

@Service
class QuotaService(
    private val props: LlmProperties,
    private val userRepository: UserRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val counters = ConcurrentHashMap<String, AtomicLong>()

    fun snapshot(clientId: String): QuotaState {
        val limit = if (props.quota.enabled) props.quota.perClientPerDay else Long.MAX_VALUE
        val day = todayKey()
        val used = counters[dayKey(clientId, day)]?.get() ?: 0L
        return QuotaState(used = used, limit = limit, resetsAtEpochMillis = nextMidnightMillis())
    }

    fun consume(clientId: String): QuotaState {
        if (!props.quota.enabled) {
            return QuotaState(used = 0, limit = Long.MAX_VALUE, resetsAtEpochMillis = nextMidnightMillis())
        }
        val limit = props.quota.perClientPerDay
        val day = todayKey()
        val key = dayKey(clientId, day)
        val counter = counters.computeIfAbsent(key) { AtomicLong(0) }
        while (true) {
            val current = counter.get()
            if (current >= limit) {
                throw QuotaExceededException(
                    used = current,
                    limit = limit,
                    resetsAtEpochMillis = nextMidnightMillis(),
                )
            }
            if (counter.compareAndSet(current, current + 1)) {
                return QuotaState(used = current + 1, limit = limit, resetsAtEpochMillis = nextMidnightMillis())
            }
        }
    }

    fun refund(clientId: String) {
        if (!props.quota.enabled) return
        val day = todayKey()
        val key = dayKey(clientId, day)
        counters[key]?.updateAndGet { v -> (v - 1).coerceAtLeast(0) }
    }

    fun peekAnonymousUsed(clientId: String): Long {
        if (clientId.isBlank()) return 0L
        val day = todayKey()
        return counters[dayKey(clientId, day)]?.get() ?: 0L
    }

    @Transactional
    fun snapshotForUser(userId: String, plan: String): QuotaState {
        if (!props.quota.enabled) {
            return QuotaState(used = 0, limit = Long.MAX_VALUE, resetsAtEpochMillis = nextMidnightMillis())
        }
        val user = userRepository.findById(userId).orElse(null)
            ?: return QuotaState(used = 0, limit = Quotas.polishLimit(plan), resetsAtEpochMillis = nextMidnightMillis())
        val now = Instant.now(clock)
        val (used, _) = ensureFresh(user.polishUsed, user.periodStart, now)
        return QuotaState(
            used = used,
            limit = Quotas.polishLimit(plan),
            resetsAtEpochMillis = nextMidnightMillis(),
        )
    }

    @Transactional
    fun consumeForUser(userId: String, plan: String): QuotaState {
        if (!props.quota.enabled) {
            return QuotaState(used = 0, limit = Long.MAX_VALUE, resetsAtEpochMillis = nextMidnightMillis())
        }
        val limit = Quotas.polishLimit(plan)
        val now = Instant.now(clock)
        val user = userRepository.findById(userId).orElseThrow {
            IllegalStateException("Cannot consume quota: user not found: $userId")
        }
        val (currentUsed, periodStart) = ensureFresh(user.polishUsed, user.periodStart, now)
        if (currentUsed >= limit) {
            throw QuotaExceededException(
                used = currentUsed,
                limit = limit,
                resetsAtEpochMillis = nextMidnightMillis(),
            )
        }
        val newUsed = currentUsed + 1
        val updatedUser = user.copy(
            polishUsed = newUsed,
            periodStart = periodStart,
        )
        userRepository.save(updatedUser)
        return QuotaState(used = newUsed, limit = limit, resetsAtEpochMillis = nextMidnightMillis())
    }

    @Transactional
    fun refundForUser(userId: String, plan: String) {
        if (!props.quota.enabled) return
        val user = userRepository.findById(userId).orElse(null) ?: return
        val now = Instant.now(clock)
        val (currentUsed, periodStart) = ensureFresh(user.polishUsed, user.periodStart, now)
        val newUsed = (currentUsed - 1).coerceAtLeast(0)
        userRepository.save(user.copy(polishUsed = newUsed, periodStart = periodStart))
    }

    private fun ensureFresh(used: Long, periodStart: Instant, now: Instant): Pair<Long, Instant> {
        val today = LocalDate.now(clock.withZone(ZoneOffset.UTC))
        val periodDate = periodStart.atZone(ZoneOffset.UTC).toLocalDate()
        if (periodDate.isBefore(today)) {
            return 0L to now
        }
        return used to periodStart
    }

    private fun todayKey(): String =
        LocalDate.now(clock.withZone(ZoneOffset.UTC)).toString()

    private fun todayStart(): Instant =
        LocalDate.now(clock.withZone(ZoneOffset.UTC)).atStartOfDay().toInstant(ZoneOffset.UTC)

    private fun dayKey(clientId: String, day: String): String = "$clientId:$day"

    private fun nextMidnightMillis(): Long {
        val tomorrow = LocalDate.now(clock.withZone(ZoneOffset.UTC)).plusDays(1)
        return tomorrow.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    }
}
