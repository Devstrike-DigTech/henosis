package com.devstrike.henosis.gen.flutter

/** Dart naming helpers: file names, enum values and keyword-safe identifiers. All pure and deterministic. */
internal object DartNames {
    private val reserved =
        setOf(
            "abstract",
            "as",
            "assert",
            "async",
            "await",
            "base",
            "break",
            "case",
            "catch",
            "class",
            "const",
            "continue",
            "covariant",
            "default",
            "deferred",
            "do",
            "dynamic",
            "else",
            "enum",
            "export",
            "extends",
            "extension",
            "external",
            "factory",
            "false",
            "final",
            "finally",
            "for",
            "Function",
            "get",
            "hide",
            "if",
            "implements",
            "import",
            "in",
            "interface",
            "is",
            "late",
            "library",
            "mixin",
            "new",
            "null",
            "of",
            "on",
            "operator",
            "part",
            "required",
            "rethrow",
            "return",
            "sealed",
            "set",
            "show",
            "static",
            "super",
            "switch",
            "sync",
            "this",
            "throw",
            "true",
            "try",
            "typedef",
            "var",
            "void",
            "when",
            "while",
            "with",
            "yield",
        )

    /** Members every Dart enum already has; an entry with one of these names must be renamed. */
    private val enumMembers =
        setOf("values", "index", "name", "hashCode", "runtimeType", "toString", "noSuchMethod", "nativeName", "fromNative")

    /** `Greeter` → `greeter`, `SampleSdk` → `sample_sdk`, `HTTPClient` → `http_client`. */
    fun fileName(typeName: String): String =
        typeName
            .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
            .lowercase()

    /** A parameter or field name that is not a Dart keyword. */
    fun identifier(name: String): String = if (name in reserved) "${name}_" else name

    /** `BRONZE` → `bronze`, `GOLD_PLUS` → `goldPlus`, `Bronze` → `bronze`; avoids Enum member names. */
    fun enumValue(entry: String): String {
        val camel =
            if (entry.contains('_') || entry.all { !it.isLowerCase() }) {
                entry
                    .lowercase()
                    .split('_')
                    .filter { it.isNotEmpty() }
                    .mapIndexed { i, part ->
                        if (i ==
                            0
                        ) {
                            part
                        } else {
                            part.replaceFirstChar { it.uppercase() }
                        }
                    }.joinToString("")
            } else {
                entry.replaceFirstChar { it.lowercase() }
            }
        return if (camel in reserved || camel in enumMembers) "${camel}_" else camel
    }

    /** `sample_sdk` → `SampleSdk`. */
    fun pascalCase(snake: String): String =
        snake.split('_', '-').filter { it.isNotEmpty() }.joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
}
