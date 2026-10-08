package com.devstrike.henosis.core.inspect

import com.devstrike.henosis.core.api.ApiClassKind
import com.devstrike.henosis.core.types.SupportLevel
import kotlinx.serialization.json.Json

/** Renders an [InspectReport] as a human-readable tree or as JSON. Both outputs are deterministic. */
object InspectRenderer {
    private val json =
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

    /** Pretty JSON, ending with a newline. */
    fun json(report: InspectReport): String = json.encodeToString(InspectReport.serializer(), report) + "\n"

    /** A tree of classes and members, each tagged `[ok]`, `[warn]` or `[unsupported]`, with findings indented. */
    fun text(report: InspectReport): String =
        buildString {
            report.classes.forEachIndexed { index, cls ->
                if (index > 0) appendLine()
                append(cls.name)
                if (cls.typeParameters.isNotEmpty()) append(cls.typeParameters.joinToString(", ", "<", ">"))
                append("  ")
                    .append(kindLabel(cls.kind))
                    .append("  ")
                    .append(tag(cls.status))
                    .appendLine()
                if (cls.enumEntries.isNotEmpty()) {
                    append("  entries: ").append(cls.enumEntries.joinToString(", ")).appendLine()
                }
                (cls.constructors + cls.functions + cls.properties).forEach { m ->
                    append("  ")
                        .append(m.signature)
                        .append("  ")
                        .append(tag(m.status))
                        .appendLine()
                    m.findings.forEach { f -> append("      - ").append(f.message).appendLine() }
                }
            }
            appendLine()
            val counts = report.classes.groupingBy { it.status }.eachCount()
            append(report.classes.size).append(" class(es): ")
            append(SupportLevel.entries.joinToString(", ") { "${counts[it] ?: 0} ${it.label}" })
            appendLine()
        }

    private fun tag(level: SupportLevel) = "[${level.label}]"

    private fun kindLabel(kind: ApiClassKind) =
        when (kind) {
            ApiClassKind.CLASS -> "class"
            ApiClassKind.DATA_CLASS -> "data class"
            ApiClassKind.ENUM_CLASS -> "enum class"
            ApiClassKind.INTERFACE -> "interface"
            ApiClassKind.OBJECT -> "object"
            ApiClassKind.ANNOTATION_CLASS -> "annotation class"
            ApiClassKind.OTHER -> "other"
        }
}
