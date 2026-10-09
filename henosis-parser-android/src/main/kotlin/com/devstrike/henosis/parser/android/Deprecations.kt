package com.devstrike.henosis.parser.android

import com.devstrike.henosis.core.api.ApiDeprecation
import com.devstrike.henosis.core.api.ApiDeprecationLevel
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode

/**
 * Reads `@kotlin.Deprecated` (with its level) and `@java.lang.Deprecated` from method bytecode.
 * Kotlin metadata does not carry deprecation, yet an ERROR-level member cannot be called, so the
 * bytecode annotation is the only reliable source.
 */
internal class Deprecations(
    node: ClassNode,
) {
    private val byMethod: Map<String, ApiDeprecation> =
        node.methods
            .mapNotNull { m ->
                deprecationOf(m.visibleAnnotations.orEmpty() + m.invisibleAnnotations.orEmpty())?.let {
                    "${m.name}${m.desc}" to
                        it
                }
            }.toMap()

    /** The deprecation of the JVM method [name] with [descriptor], or `null`. */
    fun of(
        name: String,
        descriptor: String,
    ): ApiDeprecation? = byMethod["$name$descriptor"]

    private fun deprecationOf(annotations: List<AnnotationNode>): ApiDeprecation? {
        val kotlin = annotations.firstOrNull { it.desc == "Lkotlin/Deprecated;" }
        if (kotlin != null) {
            val values =
                kotlin.values
                    .orEmpty()
                    .chunked(2)
                    .associate { it[0] as String to it[1] }
            val level =
                when ((values["level"] as? Array<*>)?.getOrNull(1)) {
                    "ERROR" -> ApiDeprecationLevel.ERROR
                    "HIDDEN" -> ApiDeprecationLevel.HIDDEN
                    else -> ApiDeprecationLevel.WARNING
                }
            return ApiDeprecation(level, values["message"] as? String)
        }
        if (annotations.any { it.desc == "Ljava/lang/Deprecated;" }) return ApiDeprecation(ApiDeprecationLevel.WARNING)
        return null
    }
}
