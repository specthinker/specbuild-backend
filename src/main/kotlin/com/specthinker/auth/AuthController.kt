package com.specthinker.auth

import com.specthinker.llm.QuotaService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.UriComponentsBuilder

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val sessionService: SessionService,
    private val cookieService: CookieService,
    private val userService: UserService,
    private val magicLinkService: MagicLinkService,
    private val googleOAuthService: GoogleOAuthService,
    private val mailService: MailService,
    private val quotaService: QuotaService,
) {

    @GetMapping("/me")
    fun me(request: HttpServletRequest): ResponseEntity<Any> {
        val current = currentUser(request) ?: return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(NoSessionResponse())
        val quota = quotaService.snapshotForUser(current.userId, current.plan)
        return ResponseEntity.ok(
            MeResponse(
                userId = current.userId,
                email = current.email,
                plan = current.plan,
                isAnonymous = false,
                quota = QuotaSnapshot(
                    used = quota.used,
                    limit = quota.limit,
                    resetsAtEpochMillis = quota.resetsAtEpochMillis,
                ),
            ),
        )
    }

    @PostMapping("/email/request")
    fun requestMagicLink(
        @RequestBody(required = false) body: EmailRequestBody?,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, String>> {
        val email = body?.email?.trim().orEmpty()
        if (!magicLinkService.isValidEmail(email)) {
            return ResponseEntity.accepted().body(mapOf<String, String>())
        }
        val redirect = body?.redirect
        val clientId = body?.clientId
        val issued = magicLinkService.issue(
            email = email,
            redirect = redirect.orEmpty(),
            clientId = clientId,
            loginUrl = null,
        )
        try {
            mailService.sendMagicLink(issued.email, issued.verifyUrl)
        } catch (e: Exception) {
            // best-effort; LoggingMailer won't fail
        }
        return ResponseEntity.accepted().body(mapOf<String, String>())
    }

    @GetMapping("/email/verify")
    fun verifyEmail(
        @RequestParam("token") token: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Any> {
        if (token.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(mapOf("error" to "invalid_token", "message" to "Missing token"))
        }
        val result = magicLinkService.verify(token)
        return when (result) {
            is MagicLinkVerification.Failure -> {
                val target = UriComponentsBuilder.fromUriString(
                    (magicLinkService.allowedRedirects().firstOrNull() ?: "https://specthinker.github.io"),
                )
                    .queryParam("signed_in", "0")
                    .queryParam("error", result.reason)
                    .build()
                    .toUriString()
                ResponseEntity.status(HttpStatus.FOUND)
                    .location(java.net.URI.create(target))
                    .build()
            }
            is MagicLinkVerification.Success -> {
                val user = userService.findOrCreateByEmail(result.email)
                val sessionId = sessionService.createSession(user.id)
                val cookieValue = sessionService.buildSessionCookieValue(sessionId)
                cookieService.setSessionCookie(response, cookieValue)
                if (!result.clientId.isNullOrBlank()) {
                    val anonPolish = quotaService.peekAnonymousUsed(result.clientId)
                    if (anonPolish > 0L) {
                        userService.mergeAnonymousIntoUser(
                            anonymousClientId = result.clientId,
                            userId = user.id,
                            anonymousPolishUsed = anonPolish,
                        )
                    }
                }
                val target = UriComponentsBuilder.fromUriString(result.redirectUrl)
                    .queryParam("signed_in", "1")
                    .build()
                    .toUriString()
                ResponseEntity.status(HttpStatus.FOUND)
                    .location(java.net.URI.create(target))
                    .build()
            }
        }
    }

    @GetMapping("/google/start")
    fun googleStart(@RequestParam("redirect", required = false) redirect: String?): ResponseEntity<Void> {
        val start = googleOAuthService.startAuth(redirect)
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(java.net.URI.create(start.authUrl))
            .build()
    }

    @GetMapping("/google/callback")
    fun googleCallback(
        @RequestParam("code", required = false) code: String?,
        @RequestParam("state", required = false) state: String?,
        response: HttpServletResponse,
    ): ResponseEntity<Any> {
        val result = googleOAuthService.handleCallback(code, state)
        return when (result) {
            is GoogleCallbackResult.Failure -> {
                val target = UriComponentsBuilder.fromUriString(
                    (googleOAuthService.allowedRedirects().firstOrNull() ?: "https://specthinker.github.io"),
                )
                    .queryParam("signed_in", "0")
                    .queryParam("error", result.reason)
                    .build()
                    .toUriString()
                ResponseEntity.status(HttpStatus.FOUND)
                    .location(java.net.URI.create(target))
                    .build()
            }
            is GoogleCallbackResult.Success -> {
                val sessionId = sessionService.createSession(result.user.id)
                val cookieValue = sessionService.buildSessionCookieValue(sessionId)
                cookieService.setSessionCookie(response, cookieValue)
                val target = UriComponentsBuilder.fromUriString(result.redirectUrl)
                    .queryParam("signed_in", "1")
                    .build()
                    .toUriString()
                ResponseEntity.status(HttpStatus.FOUND)
                    .location(java.net.URI.create(target))
                    .build()
            }
        }
    }

    @PostMapping("/logout")
    fun logout(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<Void> {
        val sessionId = request.getAttribute(CurrentUserKey.SESSION_ID) as? String
        if (!sessionId.isNullOrBlank()) {
            sessionService.revoke(sessionId)
        }
        cookieService.clearSessionCookie(response)
        return ResponseEntity.noContent().build()
    }

    private fun currentUser(request: HttpServletRequest): CurrentUser? =
        request.getAttribute(CurrentUserKey.ATTRIBUTE) as? CurrentUser
}
