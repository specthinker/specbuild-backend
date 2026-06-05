package com.specthinker.spec

import java.time.Instant

data class SpecSummary(
    val id: String,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
)

data class CreateSpecRequest(
    val title: String,
    val sections: Sections = Sections.EMPTY,
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
    }
}

data class UpdateSpecRequest(
    val title: String,
    val sections: Sections = Sections.EMPTY,
    val version: Long,
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
    }
}

data class RenderRequest(
    val title: String,
    val sections: Sections = Sections.EMPTY,
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
    }
}

data class RenderResponse(
    val format: String,
    val content: String,
)
