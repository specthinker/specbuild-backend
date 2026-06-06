package com.specthinker.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
class SessionAuthFilter(
    private val sessionService: SessionService,
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(SessionAuthFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (request.method == "OPTIONS") {
            filterChain.doFilter(request, response)
            return
        }
        val cookieValue = request.cookies
            ?.firstOrNull { it.name == CookieService.SESSION_COOKIE }
            ?.value
        if (cookieValue != null) {
            val verified = try {
                sessionService.verifyCookieValue(cookieValue)
            } catch (e: IllegalStateException) {
                log.debug("Session secret not configured: {}", e.message)
                null
            }
            if (verified != null && verified.isPresent) {
                val v = verified.get()
                val current = CurrentUser(
                    userId = v.user.id,
                    email = v.user.email,
                    plan = v.user.plan,
                    sessionId = v.sessionId,
                )
                request.setAttribute(CurrentUserKey.ATTRIBUTE, current)
                request.setAttribute(CurrentUserKey.SESSION_ID, v.sessionId)
            }
        }
        filterChain.doFilter(request, response)
    }
}
