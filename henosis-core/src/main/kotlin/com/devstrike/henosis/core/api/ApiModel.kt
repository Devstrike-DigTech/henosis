package com.devstrike.henosis.core.api

import com.devstrike.henosis.core.types.TypeRef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The public API surface of one native SDK, as read from its bytecode and Kotlin metadata.
 *
 * This is the parser's output and the input to facade selection. It records facts only
 * (names, types, modifiers); it carries no judgement about what Henosis can bind. That
 * judgement lives in [com.devstrike.henosis.core.types.TypeRules].
 *
 * Every list is sorted so the model, and anything derived from it, is deterministic.
 */
@Serializable
data class ApiModel(
    val classes: List<ApiClass>,
) {
    /** Looks up a class by its fully qualified name, or returns `null`. */
    fun findClass(name: String): ApiClass? = classes.firstOrNull { it.name == name }
}

/** Where a class's signatures came from, which decides how nullability is interpreted. */
@Serializable
enum class ApiOrigin {
    /** Compiled from Kotlin; nullability, `suspend` and class kinds come from Kotlin metadata. */
    @SerialName("kotlin")
    KOTLIN,

    /** Compiled from Java; reference types are platform types with unknown nullability. */
    @SerialName("java")
    JAVA,
}

/** The kind of a class declaration. */
@Serializable
enum class ApiClassKind {
    @SerialName("class")
    CLASS,

    @SerialName("data_class")
    DATA_CLASS,

    @SerialName("enum_class")
    ENUM_CLASS,

    @SerialName("interface")
    INTERFACE,

    @SerialName("object")
    OBJECT,

    @SerialName("annotation_class")
    ANNOTATION_CLASS,

    @SerialName("other")
    OTHER,
}

/**
 * One public class in the SDK.
 *
 * @property name fully qualified name with `.` separators, nested classes as `Outer.Inner`.
 * @property typeParameters names of the class's own generic type parameters (empty when not generic).
 * @property enumEntries entry names, in declaration order, when [kind] is [ApiClassKind.ENUM_CLASS].
 */
@Serializable
data class ApiClass(
    val name: String,
    val kind: ApiClassKind,
    val origin: ApiOrigin,
    val typeParameters: List<String> = emptyList(),
    val constructors: List<ApiConstructor> = emptyList(),
    val functions: List<ApiFunction> = emptyList(),
    val properties: List<ApiProperty> = emptyList(),
    val enumEntries: List<String> = emptyList(),
) {
    /** The simple (unqualified) class name, with `Outer.Inner` kept for nested classes. */
    val simpleName: String get() =
        name.substringAfterLast('.').let { last ->
            // Nested classes are "pkg.Outer.Inner"; keep "Outer.Inner" if the package part is lowercase.
            val parts = name.split('.')
            val firstUpper = parts.indexOfFirst { it.firstOrNull()?.isUpperCase() == true }
            if (firstUpper in 0 until parts.lastIndex) parts.drop(firstUpper).joinToString(".") else last
        }
}

/** How strongly a member is deprecated. [ERROR] and [HIDDEN] members cannot be called from generated code. */
@Serializable
enum class ApiDeprecationLevel {
    @SerialName("warning")
    WARNING,

    @SerialName("error")
    ERROR,

    @SerialName("hidden")
    HIDDEN,
}

/** A `@Deprecated` annotation on a member (Kotlin or Java). */
@Serializable
data class ApiDeprecation(
    val level: ApiDeprecationLevel,
    val message: String? = null,
)

/** A public constructor. [jvmDescriptor] is the JVM method descriptor, used by the bridge generator. */
@Serializable
data class ApiConstructor(
    val parameters: List<ApiParameter> = emptyList(),
    val jvmDescriptor: String,
)

/**
 * A public function (method).
 *
 * @property jvmName the JVM method name (differs from [name] under `@JvmName`).
 * @property jvmDescriptor the JVM descriptor; for `suspend` functions it includes the trailing `Continuation`.
 */
@Serializable
data class ApiFunction(
    val name: String,
    val parameters: List<ApiParameter> = emptyList(),
    val returnType: TypeRef,
    val isSuspend: Boolean = false,
    val typeParameters: List<String> = emptyList(),
    val jvmName: String,
    val jvmDescriptor: String,
    val deprecation: ApiDeprecation? = null,
)

/** A public property (or, for Java classes, a public field). */
@Serializable
data class ApiProperty(
    val name: String,
    val type: TypeRef,
    val isMutable: Boolean = false,
    val getterJvmName: String? = null,
    val deprecation: ApiDeprecation? = null,
)

/** One value parameter. [hasDefault] is true when the Kotlin declaration gives a default argument. */
@Serializable
data class ApiParameter(
    val name: String,
    val type: TypeRef,
    val hasDefault: Boolean = false,
)
