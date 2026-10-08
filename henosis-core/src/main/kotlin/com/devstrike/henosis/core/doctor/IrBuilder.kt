package com.devstrike.henosis.core.doctor

import com.devstrike.henosis.core.api.ApiClass
import com.devstrike.henosis.core.api.ApiClassKind
import com.devstrike.henosis.core.api.ApiConstructor
import com.devstrike.henosis.core.api.ApiFunction
import com.devstrike.henosis.core.api.ApiModel
import com.devstrike.henosis.core.api.ApiParameter
import com.devstrike.henosis.core.config.ConstructorKind
import com.devstrike.henosis.core.config.FacadeConfig
import com.devstrike.henosis.core.config.HenosisConfig
import com.devstrike.henosis.core.config.MethodSelector
import com.devstrike.henosis.core.ir.Ir
import com.devstrike.henosis.core.ir.IrConstructor
import com.devstrike.henosis.core.ir.IrEvent
import com.devstrike.henosis.core.ir.IrEventSource
import com.devstrike.henosis.core.ir.IrFacade
import com.devstrike.henosis.core.ir.IrField
import com.devstrike.henosis.core.ir.IrFlutterTarget
import com.devstrike.henosis.core.ir.IrMethod
import com.devstrike.henosis.core.ir.IrParameter
import com.devstrike.henosis.core.ir.IrReactNativeTarget
import com.devstrike.henosis.core.ir.IrTargets
import com.devstrike.henosis.core.ir.IrType
import com.devstrike.henosis.core.ir.IrTypeKind
import com.devstrike.henosis.core.ir.IrTypeRef
import com.devstrike.henosis.core.types.Finding
import com.devstrike.henosis.core.types.KotlinTypes
import com.devstrike.henosis.core.types.SupportLevel
import com.devstrike.henosis.core.types.TypePosition
import com.devstrike.henosis.core.types.TypeRef
import com.devstrike.henosis.core.types.TypeRefKind
import com.devstrike.henosis.core.types.TypeRules

/**
 * Resolves a [HenosisConfig] against an [ApiModel], collecting a [DoctorFinding] for every rule
 * that fails and building the [Ir] when nothing is wrong. One instance per run.
 */
internal class IrBuilder(
    private val config: HenosisConfig,
    private val model: ApiModel,
    private val henosisVersion: String,
) {
    private val findings = mutableListOf<DoctorFinding>()
    private val rules = TypeRules.forModel(model)

    /** Exposed name → where it was claimed, to report collisions precisely. */
    private val exposedNames = mutableMapOf<String, String>()

    /** Native class name → exposed simple name for every data class and enum that must cross the bridge. */
    private val reachableTypes = linkedMapOf<String, String>()

    fun build(): DoctorResult {
        checkName()
        checkVersion()
        val targets = checkTargets()
        val facades = buildFacades()
        config.types.include.forEachIndexed { k, name -> includeType(name, "types.include[$k]") }
        val types = buildTypes()
        val sorted = findings.sortedWith(DoctorFinding.ORDER)
        val hasErrors = sorted.any { it.level == DoctorLevel.ERROR }
        val ir =
            if (hasErrors) {
                null
            } else {
                Ir(
                    henosisVersion = henosisVersion,
                    name = config.name,
                    version = config.version,
                    facades = facades,
                    types = types,
                    targets = targets,
                )
            }
        return DoctorResult(sorted, ir)
    }

    // ---- top-level keys -------------------------------------------------------------------------

    private fun checkName() {
        if (!PACKAGE_NAME.matches(config.name)) {
            error(
                "name",
                "'${config.name}' is not a valid base name",
                "use lowercase letters, digits and underscores, starting with a letter (e.g. sample_sdk)",
            )
        }
    }

    private fun checkVersion() {
        if (!SEMVER.matches(config.version)) {
            warning("version", "'${config.version}' is not a semantic version", "use MAJOR.MINOR.PATCH, e.g. 0.1.0")
        }
    }

    private fun checkTargets(): IrTargets {
        val t = config.targets
        if (t.flutter == null && t.reactNative == null) {
            error("targets", "no target configured", "add targets.flutter and/or targets.react_native")
        }
        t.flutter?.let { f ->
            if (!PACKAGE_NAME.matches(f.packageName)) {
                error(
                    "targets.flutter.package",
                    "'${f.packageName}' is not a valid Dart package name",
                    "use lowercase letters, digits and underscores (e.g. sample_sdk)",
                )
            }
            if (!REVERSE_DOMAIN.matches(f.org)) {
                error("targets.flutter.org", "'${f.org}' is not a reverse-domain organisation", "use something like com.example")
            }
        }
        t.reactNative?.let { rn ->
            if (!NPM_NAME.matches(rn.packageName)) {
                error(
                    "targets.react_native.package",
                    "'${rn.packageName}' is not a valid npm package name",
                    "use lowercase, e.g. @devstrike/sample-sdk or sample-sdk",
                )
            }
            if (!PASCAL_CASE.matches(rn.module)) {
                error("targets.react_native.module", "'${rn.module}' is not a valid module name", "use PascalCase, e.g. SampleSdk")
            }
        }
        return IrTargets(
            flutter = t.flutter?.let { IrFlutterTarget(it.packageName, it.org) },
            reactNative = t.reactNative?.let { IrReactNativeTarget(it.packageName, it.module) },
        )
    }

    // ---- facades ----------------------------------------------------------------------------------

    private fun buildFacades(): List<IrFacade> {
        if (config.facades.isEmpty()) {
            error(
                "facades",
                "no facades selected",
                "list at least one SDK class under facades (run `heno inspect <sdk.aar>` to see candidates)",
            )
            return emptyList()
        }
        return config.facades.mapIndexedNotNull { i, facade -> buildFacade(facade, "facades[$i]") }
    }

    private fun buildFacade(
        facade: FacadeConfig,
        path: String,
    ): IrFacade? {
        val cls = model.findClass(facade.className)
        if (cls == null) {
            error(
                "$path.class",
                "class '${facade.className}' not found in the SDK",
                "run `heno inspect <sdk.aar>` to list the public classes",
            )
            return null
        }
        val namePath = if (facade.exposedAs != null) "$path.as" else "$path.class"
        claimName(facade.exposedName, namePath, "facade")
        if (cls.kind != ApiClassKind.CLASS) {
            error(
                "$path.class",
                "'${cls.name}' is ${cls.kind.article()}; v0.1 facades must be plain classes",
                "pick a class with a public constructor",
            )
            return null
        }
        if (cls.typeParameters.isNotEmpty()) {
            error(
                "$path.class",
                "'${cls.name}' is generic; generic classes are not supported in v0.1",
                "expose a non-generic wrapper class instead",
            )
            return null
        }
        checkConstructor(cls, facade, "$path.constructor")
        val methods = mutableListOf<IrMethod>()
        val methodNames = mutableMapOf<String, String>()
        facade.methods.forEachIndexed { j, selector -> buildMethod(cls, selector, "$path.methods[$j]", methodNames)?.let { methods += it } }
        val events = mutableListOf<IrEvent>()
        facade.events.forEachIndexed { j, selector -> buildEvent(cls, selector, "$path.events[$j]", methodNames)?.let { events += it } }
        if (facade.methods.isEmpty() && facade.events.isEmpty()) {
            warning(path, "'${cls.name}' exposes nothing", "list the members to expose under methods and events")
        }
        return IrFacade(
            name = facade.exposedName,
            nativeClass = cls.name,
            constructor = if (facade.constructor == ConstructorKind.CONTEXT) IrConstructor.CONTEXT else IrConstructor.NONE,
            methods = methods,
            events = events,
        )
    }

    private fun checkConstructor(
        cls: ApiClass,
        facade: FacadeConfig,
        path: String,
    ) {
        val wanted: (ApiConstructor) -> Boolean =
            when (facade.constructor) {
                ConstructorKind.NONE -> { c -> c.parameters.isEmpty() }
                ConstructorKind.CONTEXT -> { c -> c.parameters.size == 1 && c.parameters[0].type.name == KotlinTypes.ANDROID_CONTEXT }
            }
        if (cls.constructors.none(wanted)) {
            val needed =
                if (facade.constructor ==
                    ConstructorKind.NONE
                ) {
                    "public no-argument constructor"
                } else {
                    "public constructor taking one android.content.Context"
                }
            val available =
                cls.constructors
                    .joinToString(
                        "; ",
                    ) { c -> "constructor(${c.parameters.joinToString(", ") { "${it.name}: ${it.type.render()}" }})" }
                    .ifEmpty { "none" }
            error(
                path,
                "'${cls.name}' has no $needed (available: $available)",
                "set constructor to none or context to match an existing constructor",
            )
        }
    }

    private fun buildMethod(
        cls: ApiClass,
        text: String,
        path: String,
        claimed: MutableMap<String, String>,
    ): IrMethod? {
        val selector =
            try {
                MethodSelector.parse(text)
            } catch (e: IllegalArgumentException) {
                error(path, e.message ?: "invalid selector", "use `name`, `name(Type, Type)` or `name(Type) as alias`")
                return null
            }
        val sameName = cls.functions.filter { it.name == selector.name }
        val matches = sameName.filter { selector.matches(it) }
        val fn =
            when {
                sameName.isEmpty() -> {
                    error(
                        path,
                        "method '${selector.name}' not found on ${cls.name}",
                        "run `heno inspect <sdk.aar>` to list its public members",
                    )
                    return null
                }

                matches.isEmpty() -> {
                    error(
                        path,
                        "no overload of '${selector.name}' matches ${selector.render()}; available: ${overloads(sameName)}",
                        "copy one of the available signatures",
                    )
                    return null
                }

                matches.size > 1 -> {
                    error(
                        path,
                        "'${selector.name}' has ${matches.size} overloads; selector is ambiguous: ${overloads(matches)}",
                        "add the parameter types, e.g. ${overloads(matches.take(1))}",
                    )
                    return null
                }

                else -> {
                    matches.single()
                }
            }
        val exposed = selector.exposedName
        if (!IDENTIFIER.matches(exposed)) {
            error(path, "'$exposed' is not a valid exposed name", "use letters, digits and underscores, starting with a letter")
            return null
        }
        val previous = claimed.putIfAbsent(exposed, path)
        if (previous != null) {
            error(
                path,
                "exposed name '$exposed' is already used by $previous",
                "rename one of them with `as <alias>`, e.g. ${selector.render()} as ${exposed}2",
            )
            return null
        }
        var ok = true
        val member = "${cls.name}.${fn.name}"
        if (fn.typeParameters.isNotEmpty()) {
            error(
                path,
                "$member is generic (<${fn.typeParameters.joinToString(", ")}>); generic functions are not supported in v0.1",
                "expose a non-generic overload instead",
            )
            return null
        }
        fn.parameters.forEach { p ->
            if (!report(path, "$member parameter '${p.name}'", rules.check(p.type, TypePosition.PARAMETER))) ok = false
        }
        if (!report(path, "$member return type", rules.check(fn.returnType, TypePosition.RETURN))) ok = false
        if (!ok) return null
        fn.parameters.forEach { collectReachable(it.type) }
        collectReachable(fn.returnType)
        return IrMethod(
            name = exposed,
            nativeName = fn.name,
            jvmName = fn.jvmName,
            jvmDescriptor = fn.jvmDescriptor,
            parameters = fn.parameters.map { IrParameter(it.name, toIr(it.type)) },
            returnType = toIr(fn.returnType),
            isSuspend = fn.isSuspend,
        )
    }

    private fun buildEvent(
        cls: ApiClass,
        name: String,
        path: String,
        claimed: MutableMap<String, String>,
    ): IrEvent? {
        if (!IDENTIFIER.matches(name)) {
            error(path, "'$name' is not a valid event name", "use the name of a Flow<T> property or zero-argument function")
            return null
        }
        val property = cls.properties.firstOrNull { it.name == name }
        val function = cls.functions.firstOrNull { it.name == name && it.parameters.isEmpty() }
        val (type, source, jvmName) =
            when {
                property != null -> {
                    Triple(
                        property.type,
                        IrEventSource.PROPERTY,
                        property.getterJvmName ?: "get${name.replaceFirstChar { it.uppercase() }}",
                    )
                }

                function != null -> {
                    Triple(function.returnType, IrEventSource.FUNCTION, function.jvmName)
                }

                else -> {
                    error(
                        path,
                        "event '$name' not found on ${cls.name}",
                        "name a Flow<T> property or a zero-argument function returning Flow<T>",
                    )
                    return null
                }
            }
        if (type.name != KotlinTypes.FLOW || type.kind != TypeRefKind.CLASS || type.arguments.size != 1) {
            error(
                path,
                "${cls.name}.$name is '${type.render()}', but events must be Flow<T>",
                "expose it as a Flow<T> property or function",
            )
            return null
        }
        val previous = claimed.putIfAbsent(name, path)
        if (previous != null) {
            error(
                path,
                "exposed name '$name' is already used by $previous",
                "events and methods share one namespace; rename the method with `as`",
            )
            return null
        }
        val element = type.arguments[0]
        if (!report(path, "${cls.name}.$name element type", rules.check(element, TypePosition.RETURN))) return null
        collectReachable(element)
        return IrEvent(name = name, nativeName = name, source = source, jvmName = jvmName, elementType = toIr(element))
    }

    private fun overloads(fns: List<ApiFunction>) =
        fns.joinToString(" | ") { fn -> "${fn.name}(${fn.parameters.joinToString(", ") { it.type.render() }})" }

    // ---- types ------------------------------------------------------------------------------------

    private fun includeType(
        name: String,
        path: String,
    ) {
        val cls = model.findClass(name)
        when {
            cls == null -> {
                error(path, "type '$name' not found in the SDK", "run `heno inspect <sdk.aar>` to list the public classes")
            }

            cls.kind != ApiClassKind.DATA_CLASS && cls.kind != ApiClassKind.ENUM_CLASS -> {
                error(
                    path,
                    "'$name' is ${cls.kind.article()}; only data classes and enums can be included",
                    "remove it, or expose it as a facade",
                )
            }

            else -> {
                collectReachable(TypeRef(cls.name))
            }
        }
    }

    /** Records every data class and enum referenced by [type], walking into data-class fields. */
    private fun collectReachable(type: TypeRef) {
        type.arguments.forEach { collectReachable(it) }
        if (type.kind != TypeRefKind.CLASS) return
        val cls = model.findClass(type.name) ?: return
        if (cls.kind != ApiClassKind.DATA_CLASS && cls.kind != ApiClassKind.ENUM_CLASS) return
        if (reachableTypes.containsKey(cls.name)) return
        reachableTypes[cls.name] = cls.name.substringAfterLast('.')
        if (cls.kind == ApiClassKind.DATA_CLASS) cls.properties.forEach { collectReachable(it.type) }
    }

    private fun buildTypes(): List<IrType> =
        reachableTypes.keys.sorted().mapNotNull { native ->
            val cls = model.findClass(native) ?: return@mapNotNull null
            val exposed = reachableTypes.getValue(native)
            val path = "types[$native]"
            claimName(exposed, path, cls.kind.label())
            when (cls.kind) {
                ApiClassKind.ENUM_CLASS -> IrType(kind = IrTypeKind.ENUM, name = exposed, nativeClass = native, entries = cls.enumEntries)
                else -> buildDataClass(cls, exposed, path)
            }
        }

    private fun buildDataClass(
        cls: ApiClass,
        exposed: String,
        path: String,
    ): IrType? {
        val propertyNames = cls.properties.map { it.name }.toSet()
        val constructor =
            cls.constructors.firstOrNull { c ->
                c.parameters.map { it.name }.toSet() == propertyNames &&
                    c.parameters.size == propertyNames.size
            }
        if (constructor == null) {
            error(
                path,
                "data class ${cls.name} has no public constructor taking exactly its properties",
                "keep the primary constructor public with one parameter per property",
            )
            return null
        }
        var ok = true
        cls.properties.forEach { p ->
            if (!report("$path.${p.name}", "type", rules.check(p.type, TypePosition.PROPERTY))) ok = false
        }
        if (!ok) return null
        val byName = cls.properties.associateBy { it.name }
        return IrType(
            kind = IrTypeKind.DATA_CLASS,
            name = exposed,
            nativeClass = cls.name,
            fields =
                constructor.parameters.map { p: ApiParameter ->
                    val prop = byName.getValue(p.name)
                    IrField(
                        name = p.name,
                        type = toIr(prop.type),
                        getterJvmName =
                            prop.getterJvmName ?: "get${p.name.replaceFirstChar { it.uppercase() }}",
                    )
                },
            jvmConstructorDescriptor = constructor.jvmDescriptor,
        )
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private fun claimName(
        name: String,
        path: String,
        what: String,
    ) {
        if (!IDENTIFIER.matches(name)) {
            error(path, "'$name' is not a valid exposed name", "use letters, digits and underscores, starting with a letter")
            return
        }
        val previous = exposedNames.putIfAbsent(name, path)
        if (previous != null) {
            error(path, "exposed name '$name' is already used by $previous", "rename the $what with `as`, or rename the type in the SDK")
        }
    }

    /** Converts [TypeRules] findings into doctor findings; returns false when any was an error. */
    private fun report(
        path: String,
        subject: String,
        typeFindings: List<Finding>,
    ): Boolean {
        var ok = true
        typeFindings.forEach { f ->
            when (f.level) {
                SupportLevel.UNSUPPORTED -> {
                    findings += DoctorFinding(DoctorLevel.ERROR, path, "$subject: ${f.message}")
                    ok = false
                }

                SupportLevel.WARN -> {
                    findings += DoctorFinding(DoctorLevel.WARNING, path, "$subject: ${f.message}")
                }

                SupportLevel.OK -> {
                    Unit
                }
            }
        }
        return ok
    }

    /** Maps a checked [TypeRef] into the IR vocabulary. Only called for types the rules accepted. */
    private fun toIr(type: TypeRef): IrTypeRef {
        val nullable = type.nullable || type.platform
        return when (type.name) {
            KotlinTypes.INT -> {
                IrTypeRef(IrTypeKind.INT, nullable)
            }

            KotlinTypes.LONG -> {
                IrTypeRef(IrTypeKind.LONG, nullable)
            }

            KotlinTypes.SHORT -> {
                IrTypeRef(IrTypeKind.SHORT, nullable)
            }

            KotlinTypes.BYTE -> {
                IrTypeRef(IrTypeKind.BYTE, nullable)
            }

            KotlinTypes.DOUBLE -> {
                IrTypeRef(IrTypeKind.DOUBLE, nullable)
            }

            KotlinTypes.FLOAT -> {
                IrTypeRef(IrTypeKind.FLOAT, nullable)
            }

            KotlinTypes.BOOLEAN -> {
                IrTypeRef(IrTypeKind.BOOLEAN, nullable)
            }

            KotlinTypes.STRING -> {
                IrTypeRef(IrTypeKind.STRING, nullable)
            }

            KotlinTypes.UNIT -> {
                IrTypeRef(IrTypeKind.UNIT, nullable)
            }

            KotlinTypes.BYTE_ARRAY -> {
                IrTypeRef(IrTypeKind.BYTE_ARRAY, nullable)
            }

            KotlinTypes.LIST -> {
                IrTypeRef(IrTypeKind.LIST, nullable, element = toIr(type.arguments[0]))
            }

            KotlinTypes.MAP -> {
                IrTypeRef(IrTypeKind.MAP, nullable, element = toIr(type.arguments[1]))
            }

            else -> {
                val cls = model.findClass(type.name) ?: throw IllegalStateException("unchecked type ${type.name}")
                val kind = if (cls.kind == ApiClassKind.ENUM_CLASS) IrTypeKind.ENUM else IrTypeKind.DATA_CLASS
                IrTypeRef(kind, nullable, name = reachableTypes[cls.name] ?: cls.name.substringAfterLast('.'))
            }
        }
    }

    private fun error(
        path: String,
        message: String,
        hint: String? = null,
    ) {
        findings += DoctorFinding(DoctorLevel.ERROR, path, message, hint)
    }

    private fun warning(
        path: String,
        message: String,
        hint: String? = null,
    ) {
        findings += DoctorFinding(DoctorLevel.WARNING, path, message, hint)
    }

    private fun ApiClassKind.label() = name.lowercase().replace('_', ' ')

    /** `an enum class`, `a data class`: the label with its indefinite article. */
    private fun ApiClassKind.article(): String = label().let { if (it[0] in "aeiou") "an $it" else "a $it" }

    private companion object {
        val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")
        val PACKAGE_NAME = Regex("[a-z][a-z0-9_]*")
        val REVERSE_DOMAIN = Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+")
        val NPM_NAME = Regex("(@[a-z0-9-~][a-z0-9-._~]*/)?[a-z0-9-~][a-z0-9-._~]*")
        val PASCAL_CASE = Regex("[A-Z][A-Za-z0-9]*")
        val SEMVER = Regex("\\d+\\.\\d+\\.\\d+([-+][0-9A-Za-z.-]+)?")
    }
}
