package com.devstrike.henosis.gen.bridge

import com.devstrike.henosis.core.ir.Ir
import com.devstrike.henosis.core.ir.IrTypeKind
import com.devstrike.henosis.core.ir.IrTypeRef
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BYTE
import com.squareup.kotlinpoet.BYTE_ARRAY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.UNIT

/** Maps IR types back to the SDK's Kotlin types. */
internal object KotlinTypeNames {
    fun of(
        type: IrTypeRef,
        ir: Ir,
    ): TypeName {
        val base: TypeName =
            when (type.kind) {
                IrTypeKind.INT -> INT
                IrTypeKind.LONG -> LONG
                IrTypeKind.SHORT -> SHORT
                IrTypeKind.BYTE -> BYTE
                IrTypeKind.DOUBLE -> DOUBLE
                IrTypeKind.FLOAT -> FLOAT
                IrTypeKind.BOOLEAN -> BOOLEAN
                IrTypeKind.STRING -> STRING
                IrTypeKind.UNIT -> UNIT
                IrTypeKind.BYTE_ARRAY -> BYTE_ARRAY
                IrTypeKind.LIST -> LIST.parameterizedBy(of(type.element!!, ir))
                IrTypeKind.MAP -> MAP.parameterizedBy(STRING, of(type.element!!, ir))
                IrTypeKind.DATA_CLASS, IrTypeKind.ENUM -> nativeClass(type.name!!, ir)
            }
        return base.copy(nullable = type.nullable)
    }

    /** The SDK class behind an exposed type name. */
    fun nativeClass(
        exposedName: String,
        ir: Ir,
    ): ClassName {
        val native = ir.types.firstOrNull { it.name == exposedName }?.nativeClass ?: error("unknown IR type $exposedName")
        return ClassName.bestGuess(native)
    }
}
