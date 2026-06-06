package com.specthinker.auth

data class CurrentUser(
    val userId: String,
    val email: String?,
    val plan: String,
    val sessionId: String,
) {
    val isAnonymous: Boolean = false
    val isAuthenticated: Boolean = true
}

object Anonymous {
    val INSTANCE: CurrentUser? = null
}

object CurrentUserKey {
    const val ATTRIBUTE = "specthinker.currentUser"
    const val SESSION_ID = "specthinker.sessionId"
}
