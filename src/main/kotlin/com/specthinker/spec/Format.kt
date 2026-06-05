package com.specthinker.spec

enum class Format(val mediaType: String) {
    MARKDOWN("text/markdown;charset=UTF-8"),
    TEXT("text/plain;charset=UTF-8"),
    HTML("text/html;charset=UTF-8");

    companion object {
        fun parse(raw: String?): Format = when (raw?.lowercase()) {
            null, "", "md", "markdown" -> MARKDOWN
            "text", "plain", "txt" -> TEXT
            "html", "htm" -> HTML
            else -> throw IllegalArgumentException("Unknown format: $raw")
        }
    }
}
