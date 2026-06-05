package com.specthinker.llm

import com.specthinker.spec.Sections

interface LlmProvider {
    val name: String

    suspend fun complete(systemPrompt: String, userPrompt: String): String
}

internal fun renderSectionsForPrompt(title: String, sections: Sections): String = buildString {
    appendLine("Title: $title")
    appendLine()
    for ((name, accessor) in Sections.ORDER) {
        val body = accessor(sections).trim()
        appendLine("## $name")
        if (body.isEmpty()) {
            appendLine("(empty)")
        } else {
            appendLine(body)
        }
        appendLine()
    }
}
