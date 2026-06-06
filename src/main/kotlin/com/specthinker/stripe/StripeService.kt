package com.specthinker.stripe

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.specthinker.auth.Quotas
import com.specthinker.auth.User
import com.specthinker.auth.UserService
import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Event
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

data class StripeWebhookOutcome(
    val received: Boolean,
    val duplicate: Boolean = false,
    val plan: String? = null,
    val userId: String? = null,
)

@Service
class StripeService(
    private val props: StripeProperties,
    private val userService: UserService,
    private val processedEvents: ProcessedStripeEventRepository,
    private val objectMapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(StripeService::class.java)

    @Transactional
    fun handle(rawBody: String, signatureHeader: String?): StripeWebhookOutcome {
        if (props.webhookSecret.isBlank()) {
            throw IllegalStateException("specthinker.stripe.webhook-secret is not configured")
        }
        if (signatureHeader.isNullOrBlank()) {
            throw SignatureVerificationException("Missing Stripe-Signature header", null)
        }
        val event: Event = try {
            Webhook.constructEvent(rawBody, signatureHeader, props.webhookSecret)
        } catch (e: SignatureVerificationException) {
            throw e
        }
        val eventId = event.id
        if (eventId.isNullOrBlank()) {
            throw IllegalStateException("Stripe event missing id")
        }
        if (processedEvents.exists(eventId)) {
            log.info("Skipping duplicate Stripe event {}", eventId)
            return StripeWebhookOutcome(received = true, duplicate = true)
        }
        val priceToPlan = Quotas.parsePriceToPlan(props.priceToPlan)
        val outcome = when (event.type) {
            "checkout.session.completed" -> handleCheckoutCompleted(event, priceToPlan)
            "customer.subscription.deleted" -> handleSubscriptionDeleted(event)
            else -> {
                log.info("Ignoring unhandled Stripe event type {}", event.type)
                StripeWebhookOutcome(received = true)
            }
        }
        processedEvents.markProcessed(eventId, Instant.now(clock))
        return outcome
    }

    private fun handleCheckoutCompleted(event: Event, priceToPlan: Map<String, String>): StripeWebhookOutcome {
        val session = event.dataObjectDeserializer.`object`.orElse(null) as? Session
            ?: return StripeWebhookOutcome(received = true)
        val priceId = extractPriceId(session)
        val plan = priceToPlan[priceId] ?: "basic"
        val customerId = session.customer
        val email = session.customerDetails?.email
        val user: User = resolveUser(
            clientReferenceId = session.clientReferenceId,
            email = email,
            customerId = customerId,
        )
        userService.setPlanAndResetQuota(
            userId = user.id,
            plan = plan,
            stripeCustomerId = customerId,
        )
        return StripeWebhookOutcome(received = true, plan = plan, userId = user.id)
    }

    private fun handleSubscriptionDeleted(event: Event): StripeWebhookOutcome {
        val session = event.dataObjectDeserializer.`object`.orElse(null) as? Session
        val customerId = session?.customer
        val email = session?.customerDetails?.email
        val user = resolveUser(
            clientReferenceId = null,
            email = email,
            customerId = customerId,
        ) ?: return StripeWebhookOutcome(received = true)
        userService.setPlanAndResetQuota(user.id, "free", stripeCustomerId = customerId)
        return StripeWebhookOutcome(received = true, plan = "free", userId = user.id)
    }

    private fun resolveUser(clientReferenceId: String?, email: String?, customerId: String?): User {
        clientReferenceId?.let { id ->
            if (id.isNotBlank()) {
                val byId = userService.findById(id).orElse(null)
                if (byId != null) return byId
            }
        }
        if (!email.isNullOrBlank()) {
            val byEmail = userService.findByEmail(email).orElse(null)
            if (byEmail != null) {
                if (!customerId.isNullOrBlank()) userService.setStripeCustomerId(byEmail.id, customerId)
                return byEmail
            }
            val created = userService.findOrCreateByEmail(email)
            if (!customerId.isNullOrBlank()) userService.setStripeCustomerId(created.id, customerId)
            return created
        }
        if (!customerId.isNullOrBlank()) {
            val byCustomer = userService.findByStripeCustomerId(customerId).orElse(null)
            if (byCustomer != null) return byCustomer
        }
        throw IllegalArgumentException("Stripe event has no resolvable user (client_reference_id, email, customer all missing)")
    }

    private fun extractPriceId(session: Session): String? {
        return try {
            val raw = session.toJson()
            val tree: JsonNode = objectMapper.readTree(raw)
            val lineItems = tree.get("line_items")?.get("data")?.firstOrNull()
            val priceId = lineItems?.get("price")?.get("id")?.asText()
            priceId ?: tree.get("display_items")?.firstOrNull()?.get("price")?.get("id")?.asText()
        } catch (_: Exception) {
            null
        }
    }
}
