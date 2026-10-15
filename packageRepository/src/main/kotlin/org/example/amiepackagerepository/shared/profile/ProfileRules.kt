package org.example.amiepackagerepository.shared.profile

/**
 * What a profile may contain. Everything a person types is cleaned here before it is stored, and the
 * limits are the same ones the website shows, so the backend never trusts the page:
 *
 * - control characters, zero-width characters and bidi overrides are removed from every field (they are
 *   invisible, and the bidi ones can make `evil.jar` read as `raj.live` in a list);
 * - a nickname is 2 to 32 letters, digits, spaces, dots, dashes or underscores, and may not be another
 *   account's username or nickname, so nobody can pass for someone else;
 * - names, emails and bios have a length limit; the bio keeps its line breaks, nothing else does.
 *
 * Stored text is still plain text: the website renders it as text nodes, never as HTML, which is what
 * keeps a note of `<script>` harmless. Cleaning here is defence in depth, not the only line.
 */
object ProfileRules {
    const val FULL_NAME_MAX = 80
    const val EMAIL_MAX = 120
    const val BIO_MAX = 280
    const val NICKNAME_MIN = 2
    const val NICKNAME_MAX = 32
    const val NOTE_MAX = 2000

    private val NICKNAME = Regex("""[\p{L}\p{N}][\p{L}\p{N} ._-]{0,30}[\p{L}\p{N}._-]""")
    private val EMAIL = Regex("""[^\s@]+@[^\s@]+\.[^\s@]+""")
    /** C0/C1 controls (except tab and newline), zero-width characters, bidi controls, BOM */
    private val INVISIBLE = Regex("""[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F-\u009F​-‏‪-‮⁠-⁤⁦-⁩﻿]""")

    /** One line of text: invisible characters gone, whitespace collapsed, trimmed, cut to [max]. */
    fun line(value: String?, max: Int): String =
        INVISIBLE.replace(value.orEmpty(), "").replace(Regex("""\s+"""), " ").trim().take(max)

    /** Several lines: invisible characters gone, line endings normalised, at most two blank lines in a row, cut to [max]. */
    fun text(value: String?, max: Int): String =
        INVISIBLE.replace(value.orEmpty(), "").replace("\r\n", "\n").replace('\r', '\n')
            .replace(Regex("""[ \t]+\n"""), "\n").replace(Regex("""\n{3,}"""), "\n\n").trim().take(max)

    /** The cleaned nickname, or null for an empty one. Throws [IllegalArgumentException] when it breaks the rules. */
    fun nickname(value: String?): String? {
        val cleaned = line(value, NICKNAME_MAX + 1)
        if (cleaned.isEmpty()) return null
        require(cleaned.length in NICKNAME_MIN..NICKNAME_MAX) { "A nickname has $NICKNAME_MIN to $NICKNAME_MAX characters" }
        require(NICKNAME.matches(cleaned)) { "A nickname may only contain letters, digits, spaces, dots, dashes and underscores" }
        return cleaned
    }

    /** The cleaned email, or an empty string; throws when it does not look like an address. */
    fun email(value: String?): String {
        val cleaned = line(value, EMAIL_MAX + 1)
        if (cleaned.isEmpty()) return ""
        require(cleaned.length <= EMAIL_MAX && EMAIL.matches(cleaned)) { "That is not an email address" }
        return cleaned
    }

    private val GITHUB = Regex("[A-Za-z0-9](?:[A-Za-z0-9]|-(?=[A-Za-z0-9])){0,38}")
    private val HUGGING_FACE = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")

    /** A GitHub login (letters, digits, single dashes, at most 39), or null for an empty one. */
    fun githubLogin(value: String?): String? {
        val cleaned = line(value, 64).removePrefix("https://github.com/").removePrefix("github.com/").removePrefix("@").trimEnd('/')
        if (cleaned.isEmpty()) return null
        require(GITHUB.matches(cleaned)) { "That is not a GitHub username" }
        return cleaned
    }

    /** A Hugging Face handle (letters, digits, dots, dashes, underscores, at most 64), or null for an empty one. */
    fun huggingFaceLogin(value: String?): String? {
        val cleaned = line(value, 80).removePrefix("https://huggingface.co/").removePrefix("huggingface.co/").removePrefix("@").trimEnd('/')
        if (cleaned.isEmpty()) return null
        require(HUGGING_FACE.matches(cleaned)) { "That is not a Hugging Face username" }
        return cleaned
    }

    /** Two names the same person could be mistaken for: case and surrounding spaces do not count. */
    fun sameName(a: String?, b: String?): Boolean =
        !a.isNullOrBlank() && !b.isNullOrBlank() && a.trim().equals(b.trim(), ignoreCase = true)
}
