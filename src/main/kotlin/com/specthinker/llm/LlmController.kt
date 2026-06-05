package com.specthinker.llm

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/llm")
class LlmController(
    private val service: LlmService,
    private val quota: QuotaService,
) {

    @PostMapping("/polish")
    suspend fun polish(@RequestBody req: PolishRequest): ResponseEntity<PolishResponse> {
        val outcome = service.polish(req.title, req.sections, req.clientId)
        return ResponseEntity.ok()
            .header("X-Llm-Provider", outcome.provider)
            .header("X-Quota-Used", outcome.quota.used.toString())
            .header("X-Quota-Limit", outcome.quota.limit.toString())
            .header("X-Quota-Resets-At", outcome.quota.resetsAtEpochMillis.toString())
            .body(
                PolishResponse(
                    content = outcome.content,
                    provider = outcome.provider,
                    quota = outcome.quota,
                ),
            )
    }

    @PostMapping("/quota")
    fun quota(@RequestBody(required = false) body: Map<String, Any?>?): QuotaState {
        val clientId = body?.get("clientId") as? String
        val resolved = clientId?.takeIf { it.isNotBlank() } ?: "anonymous"
        return quota.snapshot(resolved)
    }
}
