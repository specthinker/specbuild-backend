package com.specthinker.spec

interface Renderer {
    fun render(title: String, sections: Sections): String
}

class MarkdownRenderer : Renderer {
    override fun render(title: String, sections: Sections): String = buildString {
        append("# ").append(title.trim()).append("\n\n")
        for ((name, accessor) in Sections.ORDER) {
            val body = accessor(sections).trim()
            if (body.isEmpty()) continue
            append("## ").append(name).append("\n\n")
            append(body).append("\n\n")
        }
    }
}

class TextRenderer : Renderer {
    override fun render(title: String, sections: Sections): String = buildString {
        appendLine(title.trim().uppercase())
        appendLine("=".repeat(title.trim().length.coerceAtLeast(3)))
        appendLine()
        for ((name, accessor) in Sections.ORDER) {
            val body = accessor(sections).trim()
            if (body.isEmpty()) continue
            appendLine(name.uppercase())
            appendLine("-".repeat(name.length))
            appendLine(body)
            appendLine()
        }
    }
}

class HtmlRenderer : Renderer {
    override fun render(title: String, sections: Sections): String = buildString {
        append("<!DOCTYPE html>\n")
        append("<html lang=\"en\">\n<head>\n")
        append("<meta charset=\"utf-8\">\n")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        append("<title>").append(escape(title.trim())).append("</title>\n")
        append("<style>")
        append("body{font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,sans-serif;")
        append("max-width:780px;margin:2rem auto;padding:0 1rem;line-height:1.55;color:#ddd;background:#111}")
        append("h1{margin-bottom:0}h2{margin-top:2rem;border-bottom:1px solid #333;padding-bottom:0.25rem}")
        append("pre,code{background:#1c1c1c;padding:0.1rem 0.3rem;border-radius:4px}")
        append("pre{padding:0.75rem;overflow:auto}")
        append("@media(prefers-color-scheme:light){body{color:#111;background:#fff}h2{border-color:#ddd}pre,code{background:#f4f4f4}}")
        append("</style>\n</head>\n<body>\n")
        append("<h1>").append(escape(title.trim())).append("</h1>\n")
        for ((name, accessor) in Sections.ORDER) {
            val body = accessor(sections).trim()
            if (body.isEmpty()) continue
            append("<section>\n")
            append("<h2>").append(escape(name)).append("</h2>\n")
            append("<div>").append(escapeMultiline(body)).append("</div>\n")
            append("</section>\n")
        }
        append("</body>\n</html>\n")
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun escapeMultiline(s: String): String =
        s.split('\n').joinToString("<br>") { escape(it) }
}
