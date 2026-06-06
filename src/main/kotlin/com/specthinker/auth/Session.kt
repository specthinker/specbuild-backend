package com.specthinker.auth

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("sessions")
data class Session(
    @Id
    val id: String,
    @Column("user_id")
    val userId: String,
    @Column("created_at")
    val createdAt: Instant,
    @Column("last_seen_at")
    val lastSeenAt: Instant,
    @Column("expires_at")
    val expiresAt: Instant,
)
