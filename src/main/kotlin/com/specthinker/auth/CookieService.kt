package com.specthinker.auth

import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class CookieService(
    private val props: AuthProperties,
) {
    companion object {
        const val SESSION_COOKIE = "session"
    }

    fun setSessionCookie(response: HttpServletResponse, value: String) {
        val cookie = ResponseCookie.from(SESSION_COOKIE, value)
            .httpOnly(true)
            .secure(props.cookie.secure)
            .sameSite(props.cookie.samesite)
            .path("/")
            .maxAge(Duration.ofDays(props.sessionTtlDays))
        if (props.cookie.domain.isNotBlank()) {
            cookie.domain(props.cookie.domain)
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.build().toString())
    }

    fun clearSessionCookie(response: HttpServletResponse) {
        val cookie = ResponseCookie.from(SESSION_COOKIE, "")
            .httpOnly(true)
            .secure(props.cookie.secure)
            .sameSite(props.cookie.samesite)
            .path("/")
            .maxAge(Duration.ZERO)
        if (props.cookie.domain.isNotBlank()) {
            cookie.domain(props.cookie.domain)
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.build().toString())
    }
}
