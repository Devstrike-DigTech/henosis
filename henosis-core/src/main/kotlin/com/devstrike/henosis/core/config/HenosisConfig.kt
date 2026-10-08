package com.devstrike.henosis.core.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The `henosis.yaml` file. It only *selects* members of the SDK; every signature comes from the
 * SDK itself. See `docs/config.md` for the meaning of each key.
 */
@Serializable
data class HenosisConfig(
    /** Base name; drives package names. Lowercase letters, digits and underscores. */
    val name: String,
    /** Version of the generated packages. */
    val version: String,
    val source: SourceConfig,
    @SerialName("facades")
    val facadesOrNull: List<FacadeConfig>? = null,
    val types: TypesConfig = TypesConfig(),
    val targets: TargetsConfig = TargetsConfig(),
) {
    /** The facades to expose; an absent or empty `facades:` key means none. */
    val facades: List<FacadeConfig> get() = facadesOrNull ?: emptyList()
}

/** Where the SDK comes from: exactly one of [aar] or [maven]. */
@Serializable
data class SourceConfig(
    /** Path to a local `.aar`, relative to the config file. */
    val aar: String? = null,
    /** Maven coordinates `group:artifact:version`. */
    val maven: String? = null,
    /** Extra Maven repositories, in addition to Google and Maven Central. */
    val repositories: List<String> = emptyList(),
)

/** How the facade class is constructed inside the generated plugin. */
@Serializable
enum class ConstructorKind {
    /** A public no-argument constructor. */
    @SerialName("none")
    NONE,

    /** A public constructor taking exactly one `android.content.Context`; `applicationContext` is injected. */
    @SerialName("context")
    CONTEXT,
}

/** One SDK class to expose, and which of its members. */
@Serializable
data class FacadeConfig(
    /** Fully qualified class name in the SDK. */
    @SerialName("class")
    val className: String,
    /** Name exposed to Dart/TS; defaults to the simple class name. */
    @SerialName("as")
    val exposedAs: String? = null,
    val constructor: ConstructorKind = ConstructorKind.NONE,
    /** Method selectors: `name`, `name(Type, Type)`, optionally followed by `as alias`. */
    val methods: List<String> = emptyList(),
    /** Event selectors: the name of a `Flow<T>` property or zero-argument function. */
    val events: List<String> = emptyList(),
) {
    /** The name Dart/TS will see. */
    val exposedName: String get() = exposedAs ?: className.substringAfterLast('.')
}

/** Extra types to include beyond those reachable from the facades. */
@Serializable
data class TypesConfig(
    val include: List<String> = emptyList(),
)

@Serializable
data class TargetsConfig(
    val flutter: FlutterTargetConfig? = null,
    @SerialName("react_native")
    val reactNative: ReactNativeTargetConfig? = null,
)

@Serializable
data class FlutterTargetConfig(
    /** Dart package name (`sample_sdk`). */
    @SerialName("package")
    val packageName: String,
    /** Reverse-domain organisation for the Android plugin (`com.devstrike`). */
    val org: String,
)

@Serializable
data class ReactNativeTargetConfig(
    /** npm package name (`@devstrike/sample-sdk`). */
    @SerialName("package")
    val packageName: String,
    /** TurboModule name in PascalCase (`SampleSdk`). */
    val module: String,
)
