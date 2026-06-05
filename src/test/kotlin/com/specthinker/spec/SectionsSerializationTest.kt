package com.specthinker.spec

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SectionsSerializationTest {

    @Test
    fun `roundtrip preserves all seven fields`() {
        val original = Sections(
            goal = "Build a thing",
            scope = "Just this",
            files = "foo.go",
            rules = "Use tabs",
            acceptanceCriteria = "It works",
            verification = "Run tests",
            output = "v1",
        )
        val json = Sections.JSON.encodeToString(Sections.serializer(), original)
        val decoded = Sections.JSON.decodeFromString(Sections.serializer(), json)
        assertEquals(original, decoded)
    }

    @Test
    fun `defaults to all empty strings`() {
        assertEquals(Sections(), Sections.EMPTY)
    }

    @Test
    fun `allBlank is true when every field is empty`() {
        assertTrue(Sections().allBlank())
        assertTrue(Sections(goal = "  ").allBlank())
    }

    @Test
    fun `allBlank is false when any field has content`() {
        assertFalse(Sections(goal = "x").allBlank())
        assertFalse(Sections(output = "v1").allBlank())
    }

    @Test
    fun `ORDER contains exactly the seven sections in the spec order`() {
        val names = Sections.ORDER.map { it.first }
        assertEquals(
            listOf("Goal", "Scope", "Files", "Rules", "Acceptance Criteria", "Verification", "Output"),
            names,
        )
    }
}
