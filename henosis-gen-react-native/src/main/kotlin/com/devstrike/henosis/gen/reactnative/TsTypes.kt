package com.devstrike.henosis.gen.reactnative

import com.devstrike.henosis.core.ir.Ir
import com.devstrike.henosis.core.ir.IrTypeKind
import com.devstrike.henosis.core.ir.IrTypeRef

/**
 * TypeScript type names and value expressions for IR types.
 *
 * Two views exist: the public API (`Profile`, `ArrayBuffer`, `Record<string, T>`) and the
 * Codegen spec, which only knows `string`, `number`, `boolean`, `Object` and `Array<T>`.
 * Byte arrays cross the bridge as base64 strings because Codegen cannot type them.
 */
internal class TsTypes(
    private val ir: Ir,
) {
    /** The public TypeScript type, e.g. `Record<string, Profile[]> | null`. */
    fun name(
        type: IrTypeRef,
        readonly: Boolean = false,
    ): String {
        val base =
            when (type.kind) {
                IrTypeKind.INT, IrTypeKind.LONG, IrTypeKind.SHORT, IrTypeKind.BYTE, IrTypeKind.DOUBLE, IrTypeKind.FLOAT -> {
                    "number"
                }

                IrTypeKind.BOOLEAN -> {
                    "boolean"
                }

                IrTypeKind.STRING -> {
                    "string"
                }

                IrTypeKind.UNIT -> {
                    "void"
                }

                IrTypeKind.BYTE_ARRAY -> {
                    "ArrayBuffer"
                }

                IrTypeKind.LIST -> {
                    val element = name(type.element!!, readonly)
                    val array = if (element.contains(" | ")) "Array<$element>" else "$element[]"
                    if (readonly) "readonly $array" else array
                }

                IrTypeKind.MAP -> {
                    val record = "Record<string, ${name(type.element!!, readonly)}>"
                    if (readonly) "Readonly<$record>" else record
                }

                IrTypeKind.DATA_CLASS, IrTypeKind.ENUM -> {
                    type.name!!
                }
            }
        return if (type.nullable && type.kind != IrTypeKind.UNIT) "$base | null" else base
    }

    /** The type as the Codegen spec declares it (what the native side sees). */
    fun specName(type: IrTypeRef): String {
        val base =
            when (type.kind) {
                IrTypeKind.INT, IrTypeKind.LONG, IrTypeKind.SHORT, IrTypeKind.BYTE, IrTypeKind.DOUBLE, IrTypeKind.FLOAT -> "number"
                IrTypeKind.BOOLEAN -> "boolean"
                IrTypeKind.STRING, IrTypeKind.BYTE_ARRAY, IrTypeKind.ENUM -> "string"
                IrTypeKind.UNIT -> "void"
                IrTypeKind.LIST -> "Array<${specName(type.element!!.copy(nullable = false))}>"
                IrTypeKind.MAP, IrTypeKind.DATA_CLASS -> "Object"
            }
        return if (type.nullable && type.kind != IrTypeKind.UNIT) "$base | null" else base
    }

    /** True when the wire value is already the public value (no conversion). */
    fun isIdentity(type: IrTypeRef): Boolean =
        when (type.kind) {
            IrTypeKind.BYTE_ARRAY, IrTypeKind.DATA_CLASS -> false
            IrTypeKind.LIST, IrTypeKind.MAP -> isIdentity(type.element!!)
            else -> true
        }

    /** Decodes wire value [expr] (an identifier typed `unknown`) into the public type. */
    fun decode(
        type: IrTypeRef,
        expr: String,
    ): String {
        if (type.nullable) {
            return if (isIdentity(type)) "$expr as ${name(type)}" else "$expr == null ? null : ${decodeNonNull(type, expr)}"
        }
        return decodeNonNull(type, expr)
    }

    private fun decodeNonNull(
        type: IrTypeRef,
        expr: String,
    ): String =
        when (type.kind) {
            IrTypeKind.BYTE_ARRAY -> {
                "base64ToArrayBuffer($expr as string)"
            }

            IrTypeKind.LIST -> {
                val element = type.element!!
                if (isIdentity(element)) "$expr as ${name(type)}" else "($expr as unknown[]).map((e: unknown) => ${decode(element, "e")})"
            }

            IrTypeKind.MAP -> {
                val element = type.element!!
                if (isIdentity(element)) {
                    "$expr as ${name(type)}"
                } else {
                    "Object.fromEntries(Object.entries($expr as Record<string, unknown>).map(([k, e]) => [k, ${decode(element, "e")}]))"
                }
            }

            IrTypeKind.DATA_CLASS -> {
                "decode${type.name}($expr)"
            }

            IrTypeKind.UNIT -> {
                "undefined"
            }

            else -> {
                "$expr as ${name(type)}"
            }
        }

    /** Encodes public value [expr] of [type] for the wire. */
    fun encode(
        type: IrTypeRef,
        expr: String,
    ): String {
        if (type.nullable) {
            return if (isIdentity(type) && type.kind != IrTypeKind.LIST) expr else "$expr == null ? null : ${encodeNonNull(type, expr)}"
        }
        return encodeNonNull(type, expr)
    }

    private fun encodeNonNull(
        type: IrTypeRef,
        expr: String,
    ): String =
        when (type.kind) {
            IrTypeKind.BYTE_ARRAY -> {
                "arrayBufferToBase64($expr)"
            }

            IrTypeKind.LIST -> {
                val element = type.element!!
                // Spreading also turns a `readonly` array into the mutable one the spec expects.
                if (isIdentity(element)) "[...$expr]" else "$expr.map((e) => ${encode(element, "e")})"
            }

            IrTypeKind.MAP -> {
                val element = type.element!!
                if (isIdentity(element)) expr else "Object.fromEntries(Object.entries($expr).map(([k, e]) => [k, ${encode(element, "e")}]))"
            }

            IrTypeKind.DATA_CLASS -> {
                "encode${type.name}($expr)"
            }

            else -> {
                expr
            }
        }

    /** A plausible sample value for the generated example app. */
    fun sample(type: IrTypeRef): String =
        when (type.kind) {
            IrTypeKind.INT, IrTypeKind.LONG, IrTypeKind.SHORT, IrTypeKind.BYTE -> {
                "1"
            }

            IrTypeKind.DOUBLE, IrTypeKind.FLOAT -> {
                "2.5"
            }

            IrTypeKind.BOOLEAN -> {
                "true"
            }

            IrTypeKind.STRING -> {
                "'hello'"
            }

            IrTypeKind.UNIT -> {
                "undefined"
            }

            IrTypeKind.BYTE_ARRAY -> {
                "new Uint8Array([1, 2, 3]).buffer"
            }

            IrTypeKind.LIST -> {
                "[${sample(type.element!!)}]"
            }

            IrTypeKind.MAP -> {
                "{ key: ${sample(type.element!!)} }"
            }

            IrTypeKind.DATA_CLASS -> {
                val dataClass = ir.types.first { it.name == type.name }
                val fields =
                    dataClass.fields.joinToString(
                        ", ",
                    ) { f -> "${TsNames.identifier(f.name)}: ${if (f.type.nullable) "null" else sample(f.type)}" }
                "{ $fields }"
            }

            IrTypeKind.ENUM -> {
                "'${ir.types.first { it.name == type.name }.entries.first()}'"
            }
        }

    fun usesBytes(type: IrTypeRef): Boolean = type.kind == IrTypeKind.BYTE_ARRAY || (type.element?.let { usesBytes(it) } ?: false)

    fun usesDataClass(type: IrTypeRef): Boolean = type.kind == IrTypeKind.DATA_CLASS || (type.element?.let { usesDataClass(it) } ?: false)

    fun usesTypes(type: IrTypeRef): Boolean =
        type.kind == IrTypeKind.DATA_CLASS || type.kind == IrTypeKind.ENUM || (type.element?.let { usesTypes(it) } ?: false)

    /** Names of the generated types referenced by [type], for import lists. */
    fun referencedTypes(type: IrTypeRef): Set<String> =
        when (type.kind) {
            IrTypeKind.DATA_CLASS, IrTypeKind.ENUM -> setOf(type.name!!)
            IrTypeKind.LIST, IrTypeKind.MAP -> referencedTypes(type.element!!)
            else -> emptySet()
        }
}
