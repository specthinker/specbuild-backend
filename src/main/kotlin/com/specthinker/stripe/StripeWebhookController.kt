package com.specthinker.stripe

import com.stripe.exception.SignatureVerificationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/stripe")
class StripeWebhookController(
    private val service: StripeService,
) {
    private val log = LoggerFactory.getLogger(StripeWebhookController::class.java)

    @PostMapping("/webhook")
    fun webhook(
        @RequestBody rawBody: String,
        @RequestHeader("Stripe-Signature", required = false) signature: String?,
    ): ResponseEntity<Map<String, Any>> {
        return try {
            val outcome = service.handle(rawBody, signature)
            ResponseEntity.ok(mapOf("received" to outcome.received))
        } catch (e: SignatureVerificationException) {
            log.warn("Stripe signature verification failed: {}", e.message)
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(mapOf("error" to "invalid_signature", "message" to (e.message ?: "Invalid signature")))
        } catch (e: IllegalStateException) {
            log.error("Stripe webhook misconfiguration: {}", e.message)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "misconfigured", "message" to (e.message ?: "Server misconfiguration")))
        } catch (e: IllegalArgumentException) {
            log.warn("Stripe webhook rejected: {}", e.message)
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(mapOf("error" to "bad_request", "message" to (e.message ?: "Bad request")))
        }
    }
}
