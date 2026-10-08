package com.devstrike.henosis.parser.android

import com.devstrike.henosis.core.types.KotlinTypes
import com.devstrike.henosis.core.types.TypeRef
import org.objectweb.asm.Type

/** Maps JVM descriptors and Java class names onto Kotlin type names, as the Kotlin compiler sees them. */
internal object JvmTypeNames {
    private val javaToKotlin =
        mapOf(
            "java.lang.Object" to KotlinTypes.ANY,
            "java.lang.String" to KotlinTypes.STRING,
            "java.lang.Integer" to KotlinTypes.INT,
            "java.lang.Short" to KotlinTypes.SHORT,
            "java.lang.Byte" to KotlinTypes.BYTE,
            "java.lang.Long" to KotlinTypes.LONG,
            "java.lang.Double" to KotlinTypes.DOUBLE,
            "java.lang.Float" to KotlinTypes.FLOAT,
            "java.lang.Boolean" to KotlinTypes.BOOLEAN,
            "java.lang.Character" to KotlinTypes.CHAR,
            "java.lang.CharSequence" to "kotlin.CharSequence",
            "java.lang.Number" to "kotlin.Number",
            "java.lang.Throwable" to "kotlin.Throwable",
            "java.lang.Iterable" to "kotlin.collections.Iterable",
            "java.util.Collection" to "kotlin.collections.Collection",
            "java.util.List" to KotlinTypes.LIST,
            "java.util.Set" to "kotlin.collections.Set",
            "java.util.Map" to KotlinTypes.MAP,
            "java.util.Map\$Entry" to "kotlin.collections.Map.Entry",
        )

    /** Converts a JVM internal name (`java/util/List`, `com/x/Outer$Inner`) to a Kotlin FQ name. */
    fun fromInternalName(internalName: String): String {
        val dotted = internalName.replace('/', '.').replace('$', '.')
        return javaToKotlin[internalName.replace('/', '.')] ?: dotted
    }

    /** The Kotlin type for a JVM primitive descriptor character (`I`, `Z`, ...). */
    fun primitive(descriptor: Char): TypeRef =
        when (descriptor) {
            'I' -> TypeRef(KotlinTypes.INT)
            'S' -> TypeRef(KotlinTypes.SHORT)
            'B' -> TypeRef(KotlinTypes.BYTE)
            'J' -> TypeRef(KotlinTypes.LONG)
            'D' -> TypeRef(KotlinTypes.DOUBLE)
            'F' -> TypeRef(KotlinTypes.FLOAT)
            'Z' -> TypeRef(KotlinTypes.BOOLEAN)
            'C' -> TypeRef(KotlinTypes.CHAR)
            'V' -> TypeRef(KotlinTypes.UNIT)
            else -> throw IllegalArgumentException("Not a primitive descriptor: $descriptor")
        }

    /** A reference type coming from Java: nullability unknown, so it is a nullable platform type. */
    fun platformClass(
        internalName: String,
        arguments: List<TypeRef> = emptyList(),
    ): TypeRef = TypeRef(fromInternalName(internalName), arguments, nullable = true, platform = true)

    /** An array of [element]: `ByteArray` for bytes, otherwise `kotlin.Array<T>` (unsupported in v0.1). */
    fun array(element: TypeRef): TypeRef =
        if (element.name == KotlinTypes.BYTE && element.arguments.isEmpty() && !element.nullable) {
            TypeRef(KotlinTypes.BYTE_ARRAY, nullable = true, platform = true)
        } else {
            TypeRef(KotlinTypes.ARRAY, listOf(element), nullable = true, platform = true)
        }

    /** Converts a non-generic ASM [Type] (from a descriptor) into a [TypeRef]. */
    fun fromAsmType(type: Type): TypeRef =
        when (type.sort) {
            Type.ARRAY -> {
                var ref = fromAsmType(type.elementType)
                repeat(type.dimensions) { ref = array(ref) }
                ref
            }

            Type.OBJECT -> {
                platformClass(type.internalName)
            }

            Type.METHOD -> {
                throw IllegalArgumentException("Method types cannot be converted to a TypeRef")
            }

            else -> {
                primitive(type.descriptor[0])
            }
        }
}
