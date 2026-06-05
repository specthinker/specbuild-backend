package com.specthinker.spec

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RendererTest {

    private val sections = Sections(
        goal = "Ship the polish feature.",
        scope = "Just this.",
        files = "src/llm/*",
        rules = "No streaming.",
        acceptanceCriteria = "Returns polished spec.",
        verification = "Integration test.",
        output = "Working endpoint.",
    )

    @Test
    fun `markdown renderer emits title H1 and section H2s`() {
        val out = MarkdownRenderer().render("My Spec", sections)
        assertTrue(out.startsWith("# My Spec\n\n"), "got: $out")
        for (name in listOf("Goal", "Scope", "Files", "Rules", "Acceptance Criteria", "Verification", "Output")) {
            assertTrue(out.contains("## $name"), "missing section: $name\n$out")
        }
    }

    @Test
    fun `markdown renderer omits empty sections`() {
        val out = MarkdownRenderer().render("T", Sections(goal = "x"))
        assertTrue(out.contains("## Goal"))
        for (name in listOf("Scope", "Files", "Rules", "Acceptance Criteria", "Verification", "Output")) {
            assertTrue(!out.contains("## $name"), "unexpected empty section: $name\n$out")
        }
    }

    @Test
    fun `text renderer emits uppercase title and underlined section heads`() {
        val out = TextRenderer().render("My Spec", sections)
        assertTrue(out.startsWith("MY SPEC\n"), "got: $out")
        assertTrue(out.contains("\nGOAL\n----\n"), "missing GOAL block\n$out")
    }

    @Test
    fun `html renderer escapes special characters`() {
        val out = HtmlRenderer().render("A & B", Sections(goal = "<script>"))
        assertTrue(out.contains("A &amp; B"), "title not escaped\n$out")
        assertTrue(out.contains("&lt;script&gt;"), "goal not escaped\n$out")
        assertTrue(!out.contains("<script>"), "raw script tag found\n$out")
    }

    @Test
    fun `html renderer wraps in html head and body`() {
        val out = HtmlRenderer().render("T", sections)
        assertTrue(out.contains("<!DOCTYPE html>"))
        assertTrue(out.contains("<title>T</title>"))
        assertTrue(out.contains("<h1>T</h1>"))
        assertTrue(out.contains("<h2>Goal</h2>"))
    }

    @Test
    fun `format parse accepts common aliases`() {
        assertEquals(Format.MARKDOWN, Format.parse("markdown"))
        assertEquals(Format.MARKDOWN, Format.parse("md"))
        assertEquals(Format.TEXT, Format.parse("text"))
        assertEquals(Format.TEXT, Format.parse("txt"))
        assertEquals(Format.HTML, Format.parse("html"))
        assertEquals(Format.HTML, Format.parse("htm"))
        assertEquals(Format.MARKDOWN, Format.parse(null))
    }

    @Test
    fun `format parse rejects unknown values`() {
        try {
            Format.parse("pdf")
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("pdf") == true)
        }
    }
}
