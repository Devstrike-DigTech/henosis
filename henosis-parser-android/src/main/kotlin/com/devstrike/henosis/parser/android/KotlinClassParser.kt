package com.devstrike.henosis.parser.android

import com.devstrike.henosis.core.api.ApiClass
import com.devstrike.henosis.core.api.ApiClassKind
import com.devstrike.henosis.core.api.ApiConstructor
import com.devstrike.henosis.core.api.ApiFunction
import com.devstrike.henosis.core.api.ApiOrigin
import com.devstrike.henosis.core.api.ApiParameter
import com.devstrike.henosis.core.api.ApiProperty
import com.devstrike.henosis.core.types.TypeRef
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import kotlin.metadata.ClassKind
import kotlin.metadata.KmClass
import kotlin.metadata.KmClassifier
import kotlin.metadata.KmFunction
import kotlin.metadata.KmProperty
import kotlin.metadata.KmType
import kotlin.metadata.KmTypeParameter
import kotlin.metadata.Visibility
import kotlin.metadata.declaresDefaultValue
import kotlin.metadata.isData
import kotlin.metadata.isNullable
import kotlin.metadata.isSuspend
import kotlin.metadata.isVar
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.getterSignature
import kotlin.metadata.jvm.signature
import kotlin.metadata.kind
import kotlin.metadata.visibility

/**
 * Builds an [ApiClass] from a Kotlin-compiled class by reading its `@kotlin.Metadata` annotation.
 *
 * Kotlin metadata is the only source of truth for nullability, `suspend`, data classes, enums,
 * default arguments and Kotlin visibility, none of which survive in plain JVM signatures.
 */
internal object KotlinClassParser {
    private const val METADATA_DESCRIPTOR = "Lkotlin/Metadata;"

    /** Members every class inherits from `Any`; they are never part of a facade. */
    private val anyMembers = setOf("equals", "hashCode", "toString")
    private val componentName = Regex("component\\d+")

    /** Reconstructs the `@kotlin.Metadata` annotation from the class file, or `null` if the class is not Kotlin. */
    fun readMetadata(node: ClassNode): Metadata? {
        val annotation = node.visibleAnnotations?.firstOrNull { it.desc == METADATA_DESCRIPTOR } ?: return null
        val values = annotation.valueMap()
        return Metadata(
            kind = values["k"] as? Int ?: 1,
            metadataVersion = values["mv"].asIntArray(),
            data1 = values["d1"].asStringArray(),
            data2 = values["d2"].asStringArray(),
            extraString = values["xs"] as? String ?: "",
            packageName = values["pn"] as? String ?: "",
            extraInt = values["xi"] as? Int ?: 0,
        )
    }

    /**
     * Parses a Kotlin class. Returns `null` for anything that is not a public class declaration
     * (file facades, lambdas, synthetic classes, `internal`/`private` classes, enum entries).
     */
    fun parse(
        node: ClassNode,
        metadata: Metadata,
    ): ApiClass? {
        val parsed =
            try {
                KotlinClassMetadata.readStrict(metadata)
            } catch (e: IllegalArgumentException) {
                // Metadata written by a newer Kotlin than this library knows: read what we can.
                KotlinClassMetadata.readLenient(metadata)
            }
        val km = (parsed as? KotlinClassMetadata.Class)?.kmClass ?: return null
        if (km.visibility != Visibility.PUBLIC) return null
        val kind =
            when {
                km.isData -> ApiClassKind.DATA_CLASS
                km.kind == ClassKind.CLASS -> ApiClassKind.CLASS
                km.kind == ClassKind.ENUM_CLASS -> ApiClassKind.ENUM_CLASS
                km.kind == ClassKind.INTERFACE -> ApiClassKind.INTERFACE
                km.kind == ClassKind.OBJECT || km.kind == ClassKind.COMPANION_OBJECT -> ApiClassKind.OBJECT
                km.kind == ClassKind.ANNOTATION_CLASS -> ApiClassKind.ANNOTATION_CLASS
                km.kind == ClassKind.ENUM_ENTRY -> return null
                else -> ApiClassKind.OTHER
            }
        val classTypeParameters = km.typeParameters.toNameMap()
        val deprecations = Deprecations(node)
        return ApiClass(
            name = km.name.toKotlinName(),
            kind = kind,
            origin = ApiOrigin.KOTLIN,
            typeParameters = km.typeParameters.map { it.name },
            constructors = constructors(km, classTypeParameters),
            functions = functions(km, kind, classTypeParameters, deprecations),
            properties = properties(km, kind, classTypeParameters, deprecations),
            enumEntries = km.kmEnumEntries.map { it.name },
        )
    }

    private fun constructors(
        km: KmClass,
        typeParameters: Map<Int, String>,
    ): List<ApiConstructor> =
        km.constructors
            .filter { it.visibility == Visibility.PUBLIC }
            .map { ctor ->
                ApiConstructor(
                    parameters = ctor.valueParameters.map { p -> p.toApiParameter(typeParameters) },
                    jvmDescriptor = ctor.signature?.descriptor ?: "",
                )
            }.sortedBy { it.jvmDescriptor }

    private fun functions(
        km: KmClass,
        kind: ApiClassKind,
        classTypeParameters: Map<Int, String>,
        deprecations: Deprecations,
    ): List<ApiFunction> =
        km.functions
            .filter { it.visibility == Visibility.PUBLIC && !it.isGeneratedMember(kind) }
            .map { fn -> fn.toApiFunction(classTypeParameters, deprecations) }
            .sortedWith(compareBy({ it.name }, { it.jvmDescriptor }))

    private fun properties(
        km: KmClass,
        kind: ApiClassKind,
        typeParameters: Map<Int, String>,
        deprecations: Deprecations,
    ): List<ApiProperty> =
        km.properties
            .filter { it.visibility == Visibility.PUBLIC && !(kind == ApiClassKind.ENUM_CLASS && it.name == "entries") }
            .map { prop -> prop.toApiProperty(typeParameters, deprecations) }
            .sortedBy { it.name }

    private fun KmFunction.isGeneratedMember(kind: ApiClassKind): Boolean =
        name in anyMembers ||
            (kind == ApiClassKind.DATA_CLASS && (name == "copy" || componentName.matches(name))) ||
            (kind == ApiClassKind.ENUM_CLASS && (name == "values" || name == "valueOf"))

    private fun KmFunction.toApiFunction(
        classTypeParameters: Map<Int, String>,
        deprecations: Deprecations,
    ): ApiFunction {
        val typeParameters = classTypeParameters + this.typeParameters.toNameMap()
        return ApiFunction(
            name = name,
            parameters = valueParameters.map { it.toApiParameter(typeParameters) },
            returnType = returnType.toTypeRef(typeParameters),
            isSuspend = isSuspend,
            typeParameters = this.typeParameters.map { it.name },
            jvmName = signature?.name ?: name,
            jvmDescriptor = signature?.descriptor ?: "",
            deprecation = signature?.let { deprecations.of(it.name, it.descriptor) },
        )
    }

    private fun KmProperty.toApiProperty(
        typeParameters: Map<Int, String>,
        deprecations: Deprecations,
    ): ApiProperty =
        ApiProperty(
            name = name,
            type = returnType.toTypeRef(typeParameters),
            deprecation = getterSignature?.let { deprecations.of(it.name, it.descriptor) },
            // A `var` with a non-public setter is read-only from outside the SDK.
            isMutable = isVar && setter?.visibility == Visibility.PUBLIC,
            getterJvmName = getterSignature?.name,
        )

    private fun kotlin.metadata.KmValueParameter.toApiParameter(typeParameters: Map<Int, String>): ApiParameter =
        ApiParameter(
            name = name,
            type = type.toTypeRef(typeParameters),
            hasDefault = declaresDefaultValue,
        )

    private fun KmType.toTypeRef(typeParameters: Map<Int, String>): TypeRef =
        when (val c = classifier) {
            is KmClassifier.TypeParameter -> {
                TypeRef.typeParameter(typeParameters[c.id] ?: "T${c.id}", nullable = isNullable)
            }

            is KmClassifier.Class -> {
                TypeRef(
                    name = c.name.toKotlinName(),
                    arguments = arguments.map { it.type?.toTypeRef(typeParameters) ?: TypeRef.STAR },
                    nullable = isNullable,
                    platform = flexibleTypeUpperBound != null,
                )
            }

            is KmClassifier.TypeAlias -> {
                TypeRef(
                    name = c.name.toKotlinName(),
                    arguments = arguments.map { it.type?.toTypeRef(typeParameters) ?: TypeRef.STAR },
                    nullable = isNullable,
                )
            }
        }

    private fun List<KmTypeParameter>.toNameMap(): Map<Int, String> = associate { it.id to it.name }

    /** Metadata class names use `/` for packages and `.` for nesting; Henosis uses `.` for both. */
    private fun String.toKotlinName(): String = replace('/', '.')

    private fun AnnotationNode.valueMap(): Map<String, Any?> {
        val raw = values ?: return emptyMap()
        return (raw.indices step 2).associate { i -> raw[i] as String to raw[i + 1] }
    }

    private fun Any?.asIntArray(): IntArray =
        when (this) {
            is IntArray -> this
            is List<*> -> map { it as Int }.toIntArray()
            else -> IntArray(0)
        }

    private fun Any?.asStringArray(): Array<String> =
        when (this) {
            is List<*> -> map { it as String }.toTypedArray()
            is Array<*> -> map { it as String }.toTypedArray()
            else -> emptyArray()
        }
}
