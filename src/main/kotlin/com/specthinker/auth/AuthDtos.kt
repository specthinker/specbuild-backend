package com.specthinker.auth

data class EmailRequestBody(
    val email: String? = null,
    val redirect: String? = null,
    val clientId: String? = null,
)

data class QuotaSnapshot(
    val used: Long,
    val limit: Long,
    val resetsAtEpochMillis: Long,
)

data class MeResponse(
    val userId: String,
    val email: String?,
    val plan: String,
    val isAnonymous: Boolean = false,
    val quota: QuotaSnapshot? = null,
)

data class NoSessionResponse(
    val error: String = "no_session",
    val message: String = "Not signed in.",
)

data class EmptyResponse(
    val status: String = "ok",
)
