package com.specthinker.web

import com.specthinker.llm.AllProvidersFailedException
import com.specthinker.llm.QuotaExceededException
import com.specthinker.llm.RelatedProviderError
import com.specthinker.spec.SpecNotFoundException
import com.specthinker.spec.SpecVersionMismatchException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.http.converter.HttpMessageNotReadableException
import java.time.Instant

data class ErrorBody(
    val error: String,
    val message: String,
    val details: Map<String, Any?>? = null,
    val timestamp: String = Instant.now().toString(),
)

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(SpecNotFoundException::class)
    fun specNotFound(e: SpecNotFoundException): ResponseEntity<ErrorBody> =
        error(HttpStatus.NOT_FOUND, "spec_not_found", e.message ?: "Spec not found")

    @ExceptionHandler(SpecVersionMismatchException::class)
    fun specVersionMismatch(e: SpecVersionMismatchException): ResponseEntity<ErrorBody> =
        error(HttpStatus.CONFLICT, "spec_version_mismatch", e.message ?: "Version mismatch")

    @ExceptionHandler(QuotaExceededException::class)
    fun quotaExceeded(e: QuotaExceededException): ResponseEntity<ErrorBody> {
        val body = ErrorBody(
            error = "quota_exceeded",
            message = "Daily AI polish quota exceeded.",
            details = mapOf("used" to e.used, "limit" to e.limit),
        )
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, ((e.resetsAtEpochMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(0).toString())
            .header("Resets-At", e.resetsAtEpochMillis.toString())
            .body(body)
    }

    @ExceptionHandler(AllProvidersFailedException::class)
    fun allProvidersFailed(e: AllProvidersFailedException): ResponseEntity<ErrorBody> {
        val providerErrors = e.suppressed
            .filterIsInstance<RelatedProviderError>()
            .map { it.message ?: "" }
        log.warn("All LLM providers failed: {}", providerErrors)
        val body = ErrorBody(
            error = "polish_unavailable",
            message = "AI polish is temporarily down.",
            details = if (providerErrors.isEmpty()) null else mapOf("providers" to providerErrors),
        )
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun illegalArgument(e: IllegalArgumentException): ResponseEntity<ErrorBody> =
        error(HttpStatus.BAD_REQUEST, "bad_request", e.message ?: "Bad request")

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(e: MethodArgumentNotValidException): ResponseEntity<ErrorBody> =
        error(HttpStatus.BAD_REQUEST, "validation_failed", e.bindingResult.allErrors.joinToString("; ") { it.defaultMessage ?: "invalid" })

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun typeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorBody> =
        error(HttpStatus.BAD_REQUEST, "bad_request", "Invalid value for parameter '${e.name}'")

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun notReadable(e: HttpMessageNotReadableException): ResponseEntity<ErrorBody> =
        error(HttpStatus.BAD_REQUEST, "bad_request", "Request body is missing or malformed")

    @ExceptionHandler(Exception::class)
    fun unexpected(e: Exception): ResponseEntity<ErrorBody> {
        log.error("Unhandled exception", e)
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Unexpected error")
    }

    private fun error(status: HttpStatus, code: String, message: String): ResponseEntity<ErrorBody> =
        ResponseEntity.status(status).body(ErrorBody(error = code, message = message))
}
