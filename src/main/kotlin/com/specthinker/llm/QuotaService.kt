package com.specthinker.llm

import org.springframework.stereotype.Service
import java.time.Clock
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

    private fun todayKey(): String =
        LocalDate.now(clock.withZone(ZoneOffset.UTC)).toString()

    private fun dayKey(clientId: String, day: String): String = "$clientId:$day"

    private fun nextMidnightMillis(): Long {
        val tomorrow = LocalDate.now(clock.withZone(ZoneOffset.UTC)).plusDays(1)
        return tomorrow.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    }
}
