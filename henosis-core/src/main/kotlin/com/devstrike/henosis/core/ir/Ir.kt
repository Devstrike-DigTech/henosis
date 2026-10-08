package com.devstrike.henosis.core.ir

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The intermediate representation: everything a target generator needs, and nothing
 * framework-specific. Generators read only this. Its JSON form is described by
 * `docs/ir.schema.json` and written by `heno generate --emit-ir`.
 */
@Serializable
data class Ir(
    /** Henosis version that produced this IR; goes into every generated file header. */
    val henosisVersion: String,
    /** Base name from the config; drives package names. */
    val name: String,
    /** Version of the generated packages. */
    val version: String,
    val facades: List<IrFacade>,
    /** Data classes and enums that cross the bridge, sorted by name. */
    val types: List<IrType>,
    val targets: IrTargets,
)

@Serializable
data class IrTargets(
    val flutter: IrFlutterTarget? = null,
    val reactNative: IrReactNativeTarget? = null,
)

@Serializable
data class IrFlutterTarget(
    @SerialName("package")
    val packageName: String,
    val org: String,
)

@Serializable
data class IrReactNativeTarget(
    @SerialName("package")
    val packageName: String,
    val module: String,
)

/** How the native facade instance is created. */
@Serializable
enum class IrConstructor {
    @SerialName("none")
    NONE,

    @SerialName("context")
    CONTEXT,
}

/** One SDK class exposed as a singleton with the selected members. */
@Serializable
data class IrFacade(
    /** Name seen by Dart/TS. */
    val name: String,
    /** Fully qualified Kotlin/Java class name. */
    val nativeClass: String,
    val constructor: IrConstructor,
    val methods: List<IrMethod>,
    val events: List<IrEvent>,
)

@Serializable
data class IrMethod(
    /** Name seen by Dart/TS; unique within the facade. */
    val name: String,
    /** Kotlin name in the SDK (overloads share it). */
    val nativeName: String,
    val jvmName: String,
    val jvmDescriptor: String,
    val parameters: List<IrParameter>,
    val returnType: IrTypeRef,
    val isSuspend: Boolean,
)

@Serializable
data class IrParameter(
    val name: String,
    val type: IrTypeRef,
)

/** Where an event's `Flow` comes from. */
@Serializable
enum class IrEventSource {
    /** A `val x: Flow<T>` property, read through its getter. */
    @SerialName("property")
    PROPERTY,

    /** A zero-argument `fun x(): Flow<T>`. */
    @SerialName("function")
    FUNCTION,
}

@Serializable
data class IrEvent(
    val name: String,
    val nativeName: String,
    val source: IrEventSource,
    /** The getter or function name on the JVM. */
    val jvmName: String,
    /** The `T` of `Flow<T>`. */
    val elementType: IrTypeRef,
)

/** The closed set of types that can cross the bridge. */
@Serializable
enum class IrTypeKind {
    @SerialName("int")
    INT,

    @SerialName("long")
    LONG,

    @SerialName("short")
    SHORT,

    @SerialName("byte")
    BYTE,

    @SerialName("double")
    DOUBLE,

    @SerialName("float")
    FLOAT,

    @SerialName("boolean")
    BOOLEAN,

    @SerialName("string")
    STRING,

    @SerialName("unit")
    UNIT,

    @SerialName("byte_array")
    BYTE_ARRAY,

    /** `List<element>` */
    @SerialName("list")
    LIST,

    /** `Map<String, element>` */
    @SerialName("map")
    MAP,

    /** A data class from [Ir.types], referenced by [IrTypeRef.name]. */
    @SerialName("data_class")
    DATA_CLASS,

    /** An enum from [Ir.types], referenced by [IrTypeRef.name]. */
    @SerialName("enum")
    ENUM,
}

/**
 * A type reference in the IR.
 *
 * @property element the element type for [IrTypeKind.LIST], or the value type for [IrTypeKind.MAP].
 * @property name the exposed type name for [IrTypeKind.DATA_CLASS] and [IrTypeKind.ENUM].
 */
@Serializable
data class IrTypeRef(
    val kind: IrTypeKind,
    val nullable: Boolean = false,
    val element: IrTypeRef? = null,
    val name: String? = null,
)

/** A data class or enum that crosses the bridge. */
@Serializable
data class IrType(
    val kind: IrTypeKind,
    /** Exposed (simple) name; unique across all types and facades. */
    val name: String,
    val nativeClass: String,
    /** Data class fields, in primary-constructor order. */
    val fields: List<IrField> = emptyList(),
    /** Enum entries in declaration order. */
    val entries: List<String> = emptyList(),
    /** JVM descriptor of the data class's primary constructor, used to rebuild instances. */
    val jvmConstructorDescriptor: String? = null,
)

@Serializable
data class IrField(
    val name: String,
    val type: IrTypeRef,
    val getterJvmName: String,
)

/** The one JSON format used for `ir.json`: pretty, stable key order, defaults written out, nulls omitted. */
object IrJson {
    private val json =
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
            encodeDefaults = true
            explicitNulls = false
        }

    fun encode(ir: Ir): String = json.encodeToString(Ir.serializer(), ir) + "\n"

    fun decode(text: String): Ir = json.decodeFromString(Ir.serializer(), text)
}
