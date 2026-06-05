package com.specthinker.llm

import com.specthinker.spec.Sections
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

const val POLISH_SYSTEM_PROMPT: String = """
You are a senior staff engineer who polishes software specifications.
You will receive a draft spec with seven sections: Goal, Scope, Files, Rules, Acceptance Criteria, Verification, Output.
Rewrite it to be clearer, tighter, and more precise.
- Keep the user's voice and any concrete facts they wrote.
- Resolve ambiguity. Prefer concrete language over vague adjectives.
- Use bullets and short paragraphs. Avoid filler.
- Do not invent requirements they did not state; if a section is empty, leave it out.
- Output only the polished spec in the same seven sections, each heading as "## <Section>".
- Do not add commentary, preamble, or explanation outside the spec.
"""

@Service
class LlmService(
    private val providers: List<LlmProvider>,
    private val quota: QuotaService,
    private val props: LlmProperties,
) {
    private val log = LoggerFactory.getLogger(LlmService::class.java)

    init {
        require(providers.isNotEmpty()) { "At least one LlmProvider bean is required" }
    }

    suspend fun polish(title: String, sections: Sections, clientId: String?): PolishOutcome {
        val resolvedClient = (clientId?.takeIf { it.isNotBlank() } ?: props.quota.defaultClientId)
        val quotaState = quota.consume(resolvedClient)
        val userPrompt = renderSectionsForPrompt(title, sections)

        val errors = mutableListOf<Pair<String, Throwable>>()
        for (provider in providers) {
            try {
                val content = provider.complete(POLISH_SYSTEM_PROMPT, userPrompt)
                return PolishOutcome(
                    content = content,
                    provider = provider.name,
                    quota = quotaState,
                )
            } catch (e: Exception) {
                log.warn("Provider {} failed: {}", provider.name, e.message)
                errors += provider.name to e
            }
        }
        quota.refund(resolvedClient)
        val last = errors.lastOrNull()?.second
        throw AllProvidersFailedException(last).apply {
            errors.forEach { (name, err) -> addSuppressed(RelatedProviderError("$name: ${err.message ?: ""}")) }
        }
    }
}

data class PolishOutcome(
    val content: String,
    val provider: String,
    val quota: QuotaState,
)

class RelatedProviderError(message: String) : RuntimeException(message)
