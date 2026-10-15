package org.example.amiepackagerepository.shared.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The cleaning every profile field and note goes through, and the nickname rules. */
class ProfileRulesTest {
    @Test
    fun `invisible characters are removed from every field`() {
        // zero-width space, bidi override, BOM, NUL, DEL
        assertEquals("evil.jar", ProfileRules.line("ev​il‮.j﻿ar\u0000\u007F", 80))
        assertEquals("line one\nline two", ProfileRules.text("line one⁦\r\nline two\u0001", 280))
        // tabs and newlines survive in text, newlines collapse in a line
        assertEquals("a b", ProfileRules.line("a\nb", 10))
        assertEquals("a\tb", ProfileRules.text("a\tb", 10))
    }

    @Test
    fun `lines are collapsed and cut, text keeps paragraphs but not runs of blank lines`() {
        assertEquals("Sam Uel", ProfileRules.line("  Sam   \n Uel  ", 80))
        assertEquals("abcde", ProfileRules.line("abcdefgh", 5))
        assertEquals("one\n\ntwo", ProfileRules.text("one\n\n\n\n\ntwo\n\n", 100))
        assertEquals("", ProfileRules.text("   \n\n  ", 100))
    }

    @Test
    fun `a nickname has 2 to 32 safe characters and nothing else`() {
        assertEquals("Sam the Maker", ProfileRules.nickname("  Sam the Maker "))
        assertEquals("sam_42.dev-x", ProfileRules.nickname("sam_42.dev-x"))
        assertEquals("Zoë Łukasz", ProfileRules.nickname("Zoë Łukasz"))
        assertNull(ProfileRules.nickname(""))
        assertNull(ProfileRules.nickname("   ​ "))
        for (bad in listOf("a", "<script>alert(1)</script>", "sam@home", "sam/../root", "a".repeat(33), "-leading", "trailing ", "tab\tname", "sam‮evil")) {
            val cleaned = runCatching { ProfileRules.nickname(bad) }
            assertTrue(cleaned.isFailure || cleaned.getOrNull() != bad, "accepted '$bad' as $cleaned")
        }
        assertThrows(IllegalArgumentException::class.java) { ProfileRules.nickname("<b>sam</b>") }
        assertThrows(IllegalArgumentException::class.java) { ProfileRules.nickname("x") }
    }

    @Test
    fun `an email is blank or looks like one`() {
        assertEquals("", ProfileRules.email("  "))
        assertEquals("sam@example.org", ProfileRules.email(" sam@example.org "))
        assertThrows(IllegalArgumentException::class.java) { ProfileRules.email("not an email") }
        assertThrows(IllegalArgumentException::class.java) { ProfileRules.email("a@b") }
        assertThrows(IllegalArgumentException::class.java) { ProfileRules.email("x".repeat(120) + "@example.org") }
    }

    @Test
    fun `names that could be confused are the same name`() {
        assertTrue(ProfileRules.sameName("Sam", " sam "))
        assertFalse(ProfileRules.sameName("Sam", "Samuel"))
        assertFalse(ProfileRules.sameName("", ""))
        assertFalse(ProfileRules.sameName(null, "x"))
    }
}
