package com.specthinker.auth

object Quotas {

    val PLAN_TO_POLISH_LIMIT: Map<String, Long> = mapOf(
        Plan.FREE.wire to 5L,
        Plan.BASIC.wire to 30L,
        Plan.PRO.wire to 200L,
        Plan.LIFETIME.wire to 1000L,
    )

    val DEFAULT_POLISH_LIMIT: Long = 50L

    fun polishLimit(plan: String, fallback: Long = DEFAULT_POLISH_LIMIT): Long =
        PLAN_TO_POLISH_LIMIT[plan] ?: fallback

    fun parsePriceToPlan(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(",")
            .mapNotNull { entry ->
                val parts = entry.split(":").map { it.trim() }
                if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                    parts[0] to parts[1]
                } else null
            }.toMap()
    }
}
