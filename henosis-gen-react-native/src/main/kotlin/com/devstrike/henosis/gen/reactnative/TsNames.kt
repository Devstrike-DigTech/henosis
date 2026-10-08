package com.devstrike.henosis.gen.reactnative

/** TypeScript and npm naming helpers. Pure and deterministic. */
internal object TsNames {
    private val reserved =
        setOf(
            "break",
            "case",
            "catch",
            "class",
            "const",
            "continue",
            "debugger",
            "default",
            "delete",
            "do",
            "else",
            "enum",
            "export",
            "extends",
            "false",
            "finally",
            "for",
            "function",
            "if",
            "import",
            "in",
            "instanceof",
            "new",
            "null",
            "return",
            "super",
            "switch",
            "this",
            "throw",
            "true",
            "try",
            "typeof",
            "var",
            "void",
            "while",
            "with",
            "yield",
            "let",
            "static",
            "implements",
            "interface",
            "package",
            "private",
            "protected",
            "public",
            "await",
            "arguments",
            "eval",
        )

    /** A parameter or field name that is not a JavaScript reserved word. */
    fun identifier(name: String): String = if (name in reserved) "${name}_" else name

    /** `Greeter` → `greeter`, `SampleSdk` → `sampleSdk`. */
    fun fileName(typeName: String): String = typeName.replaceFirstChar { it.lowercase() }

    /** `@devstrike/sample-sdk` → `sample-sdk`: the directory the package is generated into. */
    fun directoryName(npmName: String): String = npmName.substringAfterLast('/')

    /** `@devstrike/sample-sdk` → `com.devstrike.samplesdk`; `sample-sdk` → `com.samplesdk`. */
    fun androidPackage(npmName: String): String {
        val scope = npmName.takeIf { it.startsWith("@") }?.substringBefore('/')?.removePrefix("@")
        val name = npmName.substringAfterLast('/')
        return listOfNotNull("com", scope, name).joinToString(".") { segment(it) }
    }

    /** `@devstrike/sample-sdk` → `devstrike-sample-sdk-source`: the custom export condition Metro resolves. */
    fun sourceCondition(npmName: String): String = npmName.removePrefix("@").replace('/', '-') + "-source"

    private fun segment(text: String): String {
        val cleaned = text.lowercase().replace(Regex("[^a-z0-9]"), "")
        return if (cleaned.isEmpty() || !cleaned[0].isLetter()) "p$cleaned" else cleaned
    }
}
