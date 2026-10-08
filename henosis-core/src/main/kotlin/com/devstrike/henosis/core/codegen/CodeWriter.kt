package com.devstrike.henosis.core.codegen

/**
 * A minimal, deterministic source writer for languages KotlinPoet does not cover (Dart,
 * TypeScript, YAML, Gradle scripts). It tracks indentation and nothing else: no template
 * engine, no formatting magic, so what a generator writes is exactly what comes out.
 */
class CodeWriter(
    private val indentUnit: String = "  ",
) {
    private val out = StringBuilder()
    private var depth = 0

    /** Writes one line at the current indentation. An empty string writes a blank line without trailing spaces. */
    fun line(text: String = ""): CodeWriter {
        if (text.isEmpty()) {
            out.append('\n')
        } else {
            repeat(depth) { out.append(indentUnit) }
            out.append(text).append('\n')
        }
        return this
    }

    /** Writes several lines. */
    fun lines(vararg text: String): CodeWriter {
        text.forEach { line(it) }
        return this
    }

    /** Writes each line of a multi-line [text] at the current indentation. */
    fun raw(text: String): CodeWriter {
        text.trimEnd('\n').split('\n').forEach { line(it) }
        return this
    }

    /** Writes [header], then [body] one level deeper, then [footer]. */
    fun block(
        header: String,
        footer: String = "}",
        body: CodeWriter.() -> Unit,
    ): CodeWriter {
        line(header)
        indent(body)
        line(footer)
        return this
    }

    /** Runs [body] one indentation level deeper. */
    fun indent(body: CodeWriter.() -> Unit): CodeWriter {
        depth++
        try {
            body()
        } finally {
            depth--
        }
        return this
    }

    override fun toString(): String = out.toString()
}
