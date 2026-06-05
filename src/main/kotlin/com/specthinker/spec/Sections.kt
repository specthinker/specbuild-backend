package com.specthinker.spec

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Sections(
    val goal: String = "",
    val scope: String = "",
    val files: String = "",
    val rules: String = "",
    val acceptanceCriteria: String = "",
    val verification: String = "",
    val output: String = "",
) {
    fun allBlank(): Boolean =
        goal.isBlank() &&
            scope.isBlank() &&
            files.isBlank() &&
            rules.isBlank() &&
            acceptanceCriteria.isBlank() &&
            verification.isBlank() &&
            output.isBlank()

    companion object {
        val EMPTY: Sections = Sections()

        val ORDER: List<Pair<String, (Sections) -> String>> = listOf(
            "Goal" to { it.goal },
            "Scope" to { it.scope },
            "Files" to { it.files },
            "Rules" to { it.rules },
            "Acceptance Criteria" to { it.acceptanceCriteria },
            "Verification" to { it.verification },
            "Output" to { it.output },
        )

        val JSON: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }
    }
}
