package com.devstrike.henosis.core.types

import com.devstrike.henosis.core.api.ApiClassKind
import com.devstrike.henosis.core.api.ApiModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How well Henosis can bridge a type or member. Ordered from best to worst. */
@Serializable
enum class SupportLevel {
    @SerialName("ok")
    OK,

    @SerialName("warn")
    WARN,

    @SerialName("unsupported")
    UNSUPPORTED,
    ;

    /** The lowercase label used in CLI output. */
    val label: String get() = name.lowercase()

    companion object {
        /** The worst level among [levels], or [OK] when empty. */
        fun worst(levels: Iterable<SupportLevel>): SupportLevel = levels.maxByOrNull { it.ordinal } ?: OK
    }
}

/** One diagnostic about a type, with a message that includes a fix hint where possible. */
@Serializable
data class Finding(
    val level: SupportLevel,
    val message: String,
) {
    /** Returns a copy whose message is prefixed with `"$prefix: "`, to locate it within a member. */
    fun at(prefix: String): Finding = copy(message = "$prefix: $message")
}

/** Where a type appears; some types are only allowed in some positions. */
enum class TypePosition {
    CONSTRUCTOR_PARAMETER,
    PARAMETER,
    RETURN,
    PROPERTY,
}

/**
 * The v0.1 type-mapping rules (see `docs/type-mapping.md`), applied to a [TypeRef].
 *
 * The rules need to know which SDK classes are data classes and enums, because those are the
 * only user-defined types that can cross the bridge. Build an instance with [forModel].
 */
class TypeRules(
    private val dataClasses: Set<String>,
    private val enums: Set<String>,
) {
    /** Checks [type] in [position] and returns every finding, worst first. Empty means fully supported. */
    fun check(
        type: TypeRef,
        position: TypePosition,
    ): List<Finding> {
        val out = mutableListOf<Finding>()
        check(type, position, out)
        return out.sortedByDescending { it.level.ordinal }
    }

    /** The worst [SupportLevel] for [type] in [position]. */
    fun levelOf(
        type: TypeRef,
        position: TypePosition,
    ): SupportLevel = SupportLevel.worst(check(type, position).map { it.level })

    private fun check(
        type: TypeRef,
        position: TypePosition,
        out: MutableList<Finding>,
    ) {
        when (type.kind) {
            TypeRefKind.STAR -> {
                out += unsupported("star projection '*' is not supported; use a concrete type argument")
                return
            }

            TypeRefKind.TYPE_PARAMETER -> {
                out += unsupported("generic type parameter '${type.name}' is not supported in v0.1; use a concrete type")
                return
            }

            TypeRefKind.CLASS -> {
                Unit
            }
        }
        if (type.platform) {
            out += warn("'${type.render()}' comes from Java, so its nullability is unknown; Henosis treats it as nullable")
        }
        when (type.name) {
            in KotlinTypes.SCALARS -> {
                expectArguments(type, 0, out)
            }

            KotlinTypes.LONG -> {
                expectArguments(type, 0, out)
                out += warn("Long values above 2^53 lose precision in JavaScript (React Native maps Long to number)")
            }

            KotlinTypes.LIST -> {
                if (expectArguments(type, 1, out)) check(type.arguments[0], position, out)
            }

            KotlinTypes.MAP -> {
                if (expectArguments(type, 2, out)) {
                    val key = type.arguments[0]
                    if (key.name != KotlinTypes.STRING || key.kind != TypeRefKind.CLASS || key.nullable) {
                        out += unsupported("Map keys must be non-null String in v0.1 (found '${key.render()}')")
                    }
                    check(type.arguments[1], position, out)
                }
            }

            KotlinTypes.FLOW -> {
                if (position != TypePosition.RETURN && position != TypePosition.PROPERTY) {
                    out += unsupported("Flow is only supported as an event (a property or a function return type), not as a parameter")
                }
                if (expectArguments(type, 1, out)) check(type.arguments[0], TypePosition.RETURN, out)
            }

            KotlinTypes.ANDROID_CONTEXT -> {
                if (position != TypePosition.CONSTRUCTOR_PARAMETER) {
                    out +=
                        unsupported(
                            "android.content.Context is only supported as a constructor parameter (Henosis injects applicationContext)",
                        )
                }
            }

            in dataClasses, in enums -> {
                expectArguments(type, 0, out)
            }

            else -> {
                out += unsupported("type '${type.name}' is not supported in v0.1; see docs/type-mapping.md for the supported set")
            }
        }
    }

    private fun expectArguments(
        type: TypeRef,
        count: Int,
        out: MutableList<Finding>,
    ): Boolean {
        if (type.arguments.size == count) return true
        out += unsupported("'${type.render()}' should have $count type argument(s), found ${type.arguments.size}")
        return false
    }

    private fun warn(message: String) = Finding(SupportLevel.WARN, message)

    private fun unsupported(message: String) = Finding(SupportLevel.UNSUPPORTED, message)

    companion object {
        /** Builds rules that recognise the data classes and enums declared in [model]. */
        fun forModel(model: ApiModel): TypeRules =
            TypeRules(
                dataClasses =
                    model.classes
                        .filter { it.kind == ApiClassKind.DATA_CLASS }
                        .map { it.name }
                        .toSet(),
                enums =
                    model.classes
                        .filter { it.kind == ApiClassKind.ENUM_CLASS }
                        .map { it.name }
                        .toSet(),
            )
    }
}
