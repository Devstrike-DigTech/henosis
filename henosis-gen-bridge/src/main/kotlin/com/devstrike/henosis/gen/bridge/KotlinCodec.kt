package com.devstrike.henosis.gen.bridge

import com.devstrike.henosis.core.ir.Ir
import com.devstrike.henosis.core.ir.IrTypeKind
import com.devstrike.henosis.core.ir.IrTypeRef
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.NUMBER

/**
 * Builds the Kotlin expressions that move values between the SDK's types and the
 * channel-friendly representation (`Int`/`Long`/`Double`/`Boolean`/`String`/`ByteArray`,
 * `List<Any?>`, `Map<String, Any?>`, data classes as maps, enums as names).
 *
 * Every nested lambda gets a fresh variable name, so expressions can nest arbitrarily.
 */
internal class KotlinCodec(
    private val ir: Ir,
    private val codecs: ClassName,
) {
    private var counter = 0

    private fun fresh(): String = "v${++counter}"

    /** Kotlin expression converting channel value [expr] (typed `Any?`) into the SDK type. */
    fun decode(
        type: IrTypeRef,
        expr: CodeBlock,
    ): CodeBlock {
        if (!type.nullable) return decodeNonNull(type, expr)
        if (isIdentity(type) && type.kind != IrTypeKind.LIST && type.kind != IrTypeKind.MAP && type.kind != IrTypeKind.BYTE_ARRAY) {
            return CodeBlock.of("(%L as %T)", expr, KotlinTypeNames.of(type, ir))
        }
        val v = fresh()
        return CodeBlock.of("%L?.let { %L -> %L }", expr, v, decodeNonNull(type, CodeBlock.of("%L", v)))
    }

    private fun decodeNonNull(
        type: IrTypeRef,
        expr: CodeBlock,
    ): CodeBlock =
        when (type.kind) {
            IrTypeKind.INT -> {
                CodeBlock.of("(%L as %T).toInt()", expr, NUMBER)
            }

            IrTypeKind.LONG -> {
                CodeBlock.of("(%L as %T).toLong()", expr, NUMBER)
            }

            IrTypeKind.SHORT -> {
                CodeBlock.of("(%L as %T).toShort()", expr, NUMBER)
            }

            IrTypeKind.BYTE -> {
                CodeBlock.of("(%L as %T).toByte()", expr, NUMBER)
            }

            IrTypeKind.DOUBLE -> {
                CodeBlock.of("(%L as %T).toDouble()", expr, NUMBER)
            }

            IrTypeKind.FLOAT -> {
                CodeBlock.of("(%L as %T).toFloat()", expr, NUMBER)
            }

            IrTypeKind.BOOLEAN -> {
                CodeBlock.of("(%L as Boolean)", expr)
            }

            IrTypeKind.STRING -> {
                CodeBlock.of("(%L as String)", expr)
            }

            IrTypeKind.UNIT -> {
                CodeBlock.of("Unit")
            }

            // Flutter sends real byte arrays; React Native sends base64 strings. The codec accepts both.
            IrTypeKind.BYTE_ARRAY -> {
                CodeBlock.of("%T.decodeBytes(%L)", codecs, expr)
            }

            IrTypeKind.LIST -> {
                val v = fresh()
                CodeBlock.of("(%L as List<*>).map { %L -> %L }", expr, v, decode(type.element!!, CodeBlock.of("%L", v)))
            }

            IrTypeKind.MAP -> {
                val e = fresh()
                CodeBlock.of(
                    "(%L as Map<*, *>).entries.associate { %L -> (%L.key as String) to %L }",
                    expr,
                    e,
                    e,
                    decode(type.element!!, CodeBlock.of("%L.value", e)),
                )
            }

            IrTypeKind.DATA_CLASS -> {
                CodeBlock.of("%T.decode%L(%L as Map<*, *>)", codecs, type.name, expr)
            }

            IrTypeKind.ENUM -> {
                CodeBlock.of("%T.valueOf(%L as String)", KotlinTypeNames.of(type.copy(nullable = false), ir), expr)
            }
        }

    /** Kotlin expression converting SDK value [expr] into its channel representation. */
    fun encode(
        type: IrTypeRef,
        expr: CodeBlock,
    ): CodeBlock {
        if (isIdentity(type)) return expr
        if (!type.nullable) return encodeNonNull(type, expr)
        val v = fresh()
        return CodeBlock.of("%L?.let { %L -> %L }", expr, v, encodeNonNull(type.copy(nullable = false), CodeBlock.of("%L", v)))
    }

    private fun encodeNonNull(
        type: IrTypeRef,
        expr: CodeBlock,
    ): CodeBlock =
        when (type.kind) {
            IrTypeKind.SHORT, IrTypeKind.BYTE -> {
                CodeBlock.of("%L.toInt()", expr)
            }

            IrTypeKind.FLOAT -> {
                CodeBlock.of("%L.toDouble()", expr)
            }

            IrTypeKind.UNIT -> {
                CodeBlock.of("null")
            }

            IrTypeKind.LIST -> {
                val v = fresh()
                CodeBlock.of("%L.map { %L -> %L }", expr, v, encode(type.element!!, CodeBlock.of("%L", v)))
            }

            IrTypeKind.MAP -> {
                val e = fresh()
                CodeBlock.of("%L.mapValues { %L -> %L }", expr, e, encode(type.element!!, CodeBlock.of("%L.value", e)))
            }

            IrTypeKind.DATA_CLASS -> {
                CodeBlock.of("%T.encode%L(%L)", codecs, type.name, expr)
            }

            IrTypeKind.ENUM -> {
                CodeBlock.of("%L.name", expr)
            }

            else -> {
                expr
            }
        }

    /** True when the SDK value is already a valid channel value, so encoding is a no-op. */
    fun isIdentity(type: IrTypeRef): Boolean =
        when (type.kind) {
            IrTypeKind.INT, IrTypeKind.LONG, IrTypeKind.DOUBLE, IrTypeKind.BOOLEAN, IrTypeKind.STRING, IrTypeKind.BYTE_ARRAY -> true
            IrTypeKind.LIST, IrTypeKind.MAP -> isIdentity(type.element!!)
            else -> false
        }
}
