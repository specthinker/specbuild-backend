package com.specthinker.spec

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("specs")
data class Spec(
    @Id
    val id: String,
    val title: String,
    @Column("sections_json")
    val sections: Sections,
    val createdAt: Instant,
    val updatedAt: Instant,
    @Version
    val version: Long = 0L,
)
