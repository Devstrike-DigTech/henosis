package com.devstrike.henosis.core.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What a [TypeRef] refers to. */
@Serializable
enum class TypeRefKind {
    /** A class, interface, object or type alias, identified by [TypeRef.name]. */
    @SerialName("class")
    CLASS,

    /** A generic type parameter such as `T`; [TypeRef.name] is the parameter name. */
    @SerialName("type_parameter")
    TYPE_PARAMETER,

    /** A star projection (`*`). */
    @SerialName("star")
    STAR,
}

/**
 * A reference to a type as it appears in an SDK signature, framework-agnostic.
 *
 * Names use Kotlin's fully qualified form with `.` separators (`kotlin.String`,
 * `kotlin.collections.List`, `com.example.Profile`). Java types are normalised to their Kotlin
 * counterparts by the parser (`java.lang.String` becomes `kotlin.String`).
 *
 * @property nullable true for `T?`. For Java platform types this is `true` with [platform] also set.
 * @property platform true when the type came from Java and its nullability is unknown.
 */
@Serializable
data class TypeRef(
    val name: String,
    val arguments: List<TypeRef> = emptyList(),
    val nullable: Boolean = false,
    val kind: TypeRefKind = TypeRefKind.CLASS,
    val platform: Boolean = false,
) {
    /** The unqualified name (`List` for `kotlin.collections.List`). */
    val simpleName: String get() = name.substringAfterLast('.')

    /**
     * Renders the type the way Kotlin source would, e.g. `Map<String, List<Int>>?`.
     * Platform types render with a trailing `!`, as the Kotlin compiler shows them.
     */
    fun render(qualified: Boolean = false): String =
        buildString {
            when (kind) {
                TypeRefKind.STAR -> append("*")
                TypeRefKind.TYPE_PARAMETER -> append(name)
                TypeRefKind.CLASS -> append(if (qualified) name else simpleName)
            }
            if (arguments.isNotEmpty()) {
                append(arguments.joinToString(", ", "<", ">") { it.render(qualified) })
            }
            when {
                platform -> append("!")
                nullable -> append("?")
            }
        }

    companion object {
        /** A star projection. */
        val STAR: TypeRef = TypeRef("*", kind = TypeRefKind.STAR)

        /** A reference to a generic type parameter by name. */
        fun typeParameter(
            name: String,
            nullable: Boolean = false,
        ): TypeRef = TypeRef(name, kind = TypeRefKind.TYPE_PARAMETER, nullable = nullable)
    }
}

/** Fully qualified names of the Kotlin types Henosis knows how to bridge. */
object KotlinTypes {
    const val INT = "kotlin.Int"
    const val SHORT = "kotlin.Short"
    const val BYTE = "kotlin.Byte"
    const val LONG = "kotlin.Long"
    const val DOUBLE = "kotlin.Double"
    const val FLOAT = "kotlin.Float"
    const val BOOLEAN = "kotlin.Boolean"
    const val CHAR = "kotlin.Char"
    const val STRING = "kotlin.String"
    const val UNIT = "kotlin.Unit"
    const val ANY = "kotlin.Any"
    const val BYTE_ARRAY = "kotlin.ByteArray"
    const val ARRAY = "kotlin.Array"
    const val LIST = "kotlin.collections.List"
    const val MAP = "kotlin.collections.Map"
    const val FLOW = "kotlinx.coroutines.flow.Flow"
    const val ANDROID_CONTEXT = "android.content.Context"

    /** Scalar types that map one-to-one without a warning. */
    val SCALARS: Set<String> = setOf(INT, SHORT, BYTE, DOUBLE, FLOAT, BOOLEAN, STRING, UNIT, BYTE_ARRAY)
}
