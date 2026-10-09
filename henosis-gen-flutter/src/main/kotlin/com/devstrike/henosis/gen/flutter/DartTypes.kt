package com.devstrike.henosis.gen.flutter

import com.devstrike.henosis.core.ir.Ir
import com.devstrike.henosis.core.ir.IrTypeKind
import com.devstrike.henosis.core.ir.IrTypeRef

/** Dart type names and value expressions for IR types. */
internal class DartTypes(
    private val ir: Ir,
) {
    /** The Dart type for [type], e.g. `Map<String, List<Profile>>?`. */
    fun name(type: IrTypeRef): String {
        val base =
            when (type.kind) {
                IrTypeKind.INT, IrTypeKind.LONG, IrTypeKind.SHORT, IrTypeKind.BYTE -> "int"
                IrTypeKind.DOUBLE, IrTypeKind.FLOAT -> "double"
                IrTypeKind.BOOLEAN -> "bool"
                IrTypeKind.STRING -> "String"
                IrTypeKind.UNIT -> "void"
                IrTypeKind.BYTE_ARRAY -> "Uint8List"
                IrTypeKind.LIST -> "List<${name(type.element!!)}>"
                IrTypeKind.MAP -> "Map<String, ${name(type.element!!)}>"
                IrTypeKind.DATA_CLASS, IrTypeKind.ENUM -> type.name!!
            }
        return if (type.nullable && type.kind != IrTypeKind.UNIT) "$base?" else base
    }

    /** True when the channel delivers this type as-is (no conversion needed in Dart). */
    fun isIdentity(type: IrTypeRef): Boolean =
        when (type.kind) {
            IrTypeKind.INT, IrTypeKind.LONG, IrTypeKind.SHORT, IrTypeKind.BYTE -> true

            IrTypeKind.BOOLEAN, IrTypeKind.STRING, IrTypeKind.BYTE_ARRAY -> true

            IrTypeKind.DOUBLE, IrTypeKind.FLOAT -> false

            // integral doubles arrive as int; always convert through num
            IrTypeKind.LIST, IrTypeKind.MAP -> isIdentity(type.element!!)

            else -> false
        }

    /** Dart expression decoding channel value [expr] (an identifier, typed `Object?`) into [type]. */
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
            IrTypeKind.INT, IrTypeKind.LONG, IrTypeKind.SHORT, IrTypeKind.BYTE -> {
                "$expr as int"
            }

            IrTypeKind.DOUBLE, IrTypeKind.FLOAT -> {
                "($expr as num).toDouble()"
            }

            IrTypeKind.BOOLEAN -> {
                "$expr as bool"
            }

            IrTypeKind.STRING -> {
                "$expr as String"
            }

            IrTypeKind.UNIT -> {
                "null"
            }

            IrTypeKind.BYTE_ARRAY -> {
                "$expr as Uint8List"
            }

            IrTypeKind.LIST -> {
                val element = type.element!!
                if (isIdentity(element)) {
                    "($expr as List<Object?>).cast<${name(element)}>()"
                } else {
                    "($expr as List<Object?>).map((Object? e) => ${decode(element, "e")}).toList()"
                }
            }

            IrTypeKind.MAP -> {
                val element = type.element!!
                if (isIdentity(element)) {
                    "($expr as Map<Object?, Object?>).cast<String, ${name(element)}>()"
                } else {
                    "($expr as Map<Object?, Object?>).map((Object? k, Object? e) => MapEntry(k as String, ${decode(element, "e")}))"
                }
            }

            IrTypeKind.DATA_CLASS -> {
                "${type.name}.fromMap($expr as Map<Object?, Object?>)"
            }

            IrTypeKind.ENUM -> {
                "${type.name}.fromNative($expr as String)"
            }
        }

    /** Dart expression encoding Dart value [expr] of [type] for the channel. */
    fun encode(
        type: IrTypeRef,
        expr: String,
    ): String {
        if (isIdentity(type) || type.kind == IrTypeKind.DOUBLE || type.kind == IrTypeKind.FLOAT) return expr
        val access = if (type.nullable) "$expr?" else expr
        return when (type.kind) {
            IrTypeKind.LIST -> "$access.map((e) => ${encode(type.element!!, "e")}).toList()"
            IrTypeKind.MAP -> "$access.map((k, e) => MapEntry(k, ${encode(type.element!!, "e")}))"
            IrTypeKind.DATA_CLASS -> "$access.toMap()"
            IrTypeKind.ENUM -> "$access.nativeName"
            else -> expr
        }
    }

    /** A plausible sample value of [type], used by the generated example app. */
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
                "null"
            }

            IrTypeKind.BYTE_ARRAY -> {
                "Uint8List.fromList(<int>[1, 2, 3])"
            }

            IrTypeKind.LIST -> {
                "<${name(type.element!!)}>[${sample(type.element!!)}]"
            }

            IrTypeKind.MAP -> {
                "<String, ${name(type.element!!)}>{'key': ${sample(type.element!!)}}"
            }

            IrTypeKind.DATA_CLASS -> {
                val dataClass = ir.types.first { it.name == type.name }
                val args =
                    dataClass.fields.filter { !it.type.nullable }.joinToString(
                        ", ",
                    ) { "${DartNames.identifier(it.name)}: ${sample(it.type)}" }
                "${type.name}($args)"
            }

            IrTypeKind.ENUM -> {
                "${type.name}.${DartNames.enumValue(
                    ir.types
                        .first { it.name == type.name }
                        .entries
                        .first(),
                )}"
            }
        }

    /** The channel (wire) form of [sample], as a mocked native side would reply. */
    fun sampleWire(type: IrTypeRef): String =
        when (type.kind) {
            IrTypeKind.LIST -> {
                "<Object?>[${sampleWire(type.element!!)}]"
            }

            IrTypeKind.MAP -> {
                "<Object?, Object?>{'key': ${sampleWire(type.element!!)}}"
            }

            IrTypeKind.DATA_CLASS -> {
                val dataClass = ir.types.first { it.name == type.name }
                val entries =
                    dataClass.fields.joinToString(
                        ", ",
                    ) { "'${it.name}': ${if (it.type.nullable) "null" else sampleWire(it.type)}" }
                "<Object?, Object?>{$entries}"
            }

            IrTypeKind.ENUM -> {
                "'${ir.types.first { it.name == type.name }.entries.first()}'"
            }

            else -> {
                sample(type)
            }
        }

    /** Whether rendering [type] needs `dart:typed_data`. */
    fun usesTypedData(type: IrTypeRef): Boolean = type.kind == IrTypeKind.BYTE_ARRAY || (type.element?.let { usesTypedData(it) } ?: false)

    /** Whether [type] refers to a generated data class or enum (so `types.dart` must be imported). */
    fun usesTypes(type: IrTypeRef): Boolean =
        type.kind == IrTypeKind.DATA_CLASS || type.kind == IrTypeKind.ENUM || (type.element?.let { usesTypes(it) } ?: false)
}
