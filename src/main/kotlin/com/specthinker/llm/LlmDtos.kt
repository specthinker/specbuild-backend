package com.specthinker.llm

import com.specthinker.spec.Sections

data class PolishRequest(
    val title: String,
    val sections: Sections = Sections.EMPTY,
    val clientId: String? = null,
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
    }
}

data class PolishResponse(
    val content: String,
    val provider: String,
    val quota: QuotaState,
)
