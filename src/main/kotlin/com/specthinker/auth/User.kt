package com.specthinker.auth

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

enum class Plan(val wire: String) {
    FREE("free"),
    BASIC("basic"),
    PRO("pro"),
    LIFETIME("lifetime");

    companion object {
        fun fromWire(value: String?): Plan = entries.firstOrNull { it.wire == value } ?: FREE
    }
}

@Table("users")
data class User(
    @Id
    val id: String,
    val email: String?,
    val plan: String,
    @Column("plan_set_at")
    val planSetAt: Instant,
    @Column("period_start")
    val periodStart: Instant,
    @Column("specs_used")
    val specsUsed: Long = 0L,
    @Column("polish_used")
    val polishUsed: Long = 0L,
    @Column("stripe_customer_id")
    val stripeCustomerId: String? = null,
    @Column("created_at")
    val createdAt: Instant,
)
