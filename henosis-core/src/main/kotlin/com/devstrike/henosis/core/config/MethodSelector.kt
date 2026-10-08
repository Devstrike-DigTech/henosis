package com.devstrike.henosis.core.config

import com.devstrike.henosis.core.api.ApiFunction
import com.devstrike.henosis.core.types.TypeRef

/**
 * A parsed `methods:` entry: `name`, `name(Type, Type)` to pick one overload, optionally
 * followed by `as alias` to choose the exposed name.
 *
 * Parameter types are matched against the Kotlin rendering of the SDK signature, with simple or
 * fully qualified names (`divide(Int, Int)`, `greetMany(List<String>)`, `f(kotlin.String?)`).
 */
data class MethodSelector(
    val name: String,
    /** `null` when no parentheses were given, meaning "the only method with this name". */
    val parameterTypes: List<String>?,
    val alias: String? = null,
) {
    /** The name the method will have in Dart/TS. */
    val exposedName: String get() = alias ?: name

    fun matches(fn: ApiFunction): Boolean {
        if (fn.name != name) return false
        val wanted = parameterTypes ?: return true
        if (wanted.size != fn.parameters.size) return false
        return wanted.zip(fn.parameters).all { (spec, p) -> typeMatches(p.type, spec) }
    }

    /** Renders the selector back to its canonical text. */
    fun render(): String =
        buildString {
            append(name)
            if (parameterTypes != null) append(parameterTypes.joinToString(", ", "(", ")"))
            if (alias != null) append(" as ").append(alias)
        }

    companion object {
        private val identifier = Regex("[A-Za-z_][A-Za-z0-9_]*")
        private val syntax = Regex("""^\s*($identifier)\s*(?:\((.*)\))?\s*(?:as\s+($identifier))?\s*$""")

        /** Parses a selector; throws [IllegalArgumentException] with a reason when the syntax is wrong. */
        fun parse(text: String): MethodSelector {
            val match =
                syntax.matchEntire(text)
                    ?: throw IllegalArgumentException(
                        "'$text' is not a valid method selector; use `name`, `name(Type, Type)` or `name(Type) as alias`",
                    )
            val (name, params, alias) = match.destructured
            val parameterTypes =
                if (match.groups[2] == null) {
                    null
                } else {
                    splitTopLevel(params).map { it.trim() }.also { types ->
                        require(types.all { it.isNotEmpty() }) { "'$text' has an empty parameter type; remove the stray comma" }
                    }
                }
            return MethodSelector(name, parameterTypes, alias.ifEmpty { null })
        }

        /** Splits on commas that are not inside `<...>`, so `Map<String, Int>, Int` gives two entries. */
        private fun splitTopLevel(text: String): List<String> {
            if (text.isBlank()) return emptyList()
            val parts = mutableListOf<String>()
            var depth = 0
            val current = StringBuilder()
            for (ch in text) {
                when (ch) {
                    '<' -> depth++
                    '>' -> depth--
                }
                if (ch == ',' && depth == 0) {
                    parts += current.toString()
                    current.clear()
                } else {
                    current.append(ch)
                }
            }
            parts += current.toString()
            return parts
        }

        private fun typeMatches(
            type: TypeRef,
            spec: String,
        ): Boolean {
            val normalized = spec.replace(" ", "")
            return normalized == type.render().replace(" ", "") || normalized == type.render(qualified = true).replace(" ", "")
        }
    }
}
